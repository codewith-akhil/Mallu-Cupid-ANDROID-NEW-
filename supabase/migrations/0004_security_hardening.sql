-- =============================================================================
-- Mallu Cupid — Migration 0004: Security hardening + rate limiting
-- =============================================================================
-- Fixes from end-to-end audit:
--   1. OTP code stored in plain text → add code_hash column (SHA-256)
--   2. matches_participant_insert allows forged matches → revoke client INSERT
--   3. Add per-IP rate limit table for OTP endpoint
--   4. Add atomic verify-and-consume function for OTP (fixes TOCTOU)
--   5. Add index on otp_codes for cleanup
--   6. Revoke client DELETE on profiles (already done in 0003, double-check)
-- =============================================================================

begin;

-- 1. OTP: add code_hash column (SHA-256 hex of the code)
--    The `code` column stays for backward compat during migration, but
--    verify-otp will compare against code_hash, not code.
alter table public.otp_codes
  add column if not exists code_hash text;

-- 2. Matches: revoke client INSERT — only the trigger can create matches
revoke insert on public.matches from authenticated;
drop policy if exists "matches_participant_insert" on public.matches;

-- 3. Per-IP rate limit table for OTP endpoint
create table if not exists public.otp_ip_throttle (
  ip          text not null,
  window_start timestamptz not null default now(),
  hits        int not null default 1,
  primary key (ip, window_start)
);
create index if not exists otp_ip_throttle_ip_idx on public.otp_ip_throttle (ip, window_start);

-- 4. Atomic verify-and-consume function for OTP
--    Fixes TOCTOU race on attempts + used flag.
--    Returns the OTP row if successfully consumed, or NULL if not.
create or replace function public.consume_otp(p_email text, p_code text)
returns public.otp_codes
language plpgsql security definer set search_path = public as $$
declare
  v_row public.otp_codes;
begin
  -- Find the most recent unused, non-expired code for this email
  select * into v_row
    from public.otp_codes
   where email = p_email
     and used = false
     and expires_at > now()
   order by created_at desc
   limit 1
   for update skip locked;  -- prevents concurrent consume

  if not found then
    return null;  -- no active code
  end if;

  -- Check attempt cap
  if v_row.attempts >= 5 then
    update public.otp_codes set attempts = attempts + 1 where id = v_row.id;
    return null;  -- too many attempts
  end if;

  -- Increment attempts atomically
  update public.otp_codes
     set attempts = attempts + 1
   where id = v_row.id
   returning * into v_row;

  -- Check code match (supports both plaintext `code` and `code_hash`)
  if v_row.code_hash is not null then
    -- Hash comparison (preferred)
    if encode(digest(p_code, 'sha256'), 'hex') != v_row.code_hash then
      return null;  -- wrong code
    end if;
  else
    -- Plaintext fallback (legacy rows)
    if v_row.code != p_code then
      return null;  -- wrong code
    end if;
  end if;

  -- Atomically mark as used ONLY if still unused (prevents replay)
  update public.otp_codes
     set used = true
   where id = v_row.id
     and used = false
   returning * into v_row;

  if not found then
    return null;  -- someone else consumed it between our SELECT and UPDATE
  end if;

  return v_row;
end; $$;

grant execute on function public.consume_otp(text, text) to authenticated;

-- 5. Cleanup index for expired OTP rows
create index if not exists otp_codes_cleanup_idx
  on public.otp_codes (expires_at) where used = false;

-- 6. Verify no client DELETE on profiles (defense in depth)
revoke delete on public.profiles from authenticated;

commit;
