-- =============================================================================
-- Mallu Cupid — Migration 0010: Countries + age restrictions + Storage bucket
-- =============================================================================
-- 1. Creates a countries table with per-country minimum age for dating consent
-- 2. Seeds it with major countries
-- 3. Creates a Supabase Storage bucket for profile photos
-- 4. RLS policies on both
-- =============================================================================

begin;

-- ── 1. Countries table ───────────────────────────────────────────────────────

create table if not exists public.countries (
  id          int generated always as identity primary key,
  name        text not null unique,
  iso_code    text not null unique,           -- e.g. 'US', 'IN', 'GB'
  min_age     int not null default 18,        -- minimum age for dating consent
  created_at  timestamptz default now()
);

create index if not exists countries_iso_code_idx on public.countries (iso_code);

-- RLS: anyone authenticated can read countries (needed for the onboarding dropdown)
alter table public.countries enable row level security;
drop policy if exists "countries_read_all" on public.countries;
create policy "countries_read_all" on public.countries
  for select to authenticated using (true);

-- ── 2. Seed data ────────────────────────────────────────────────────────────

insert into public.countries (name, iso_code, min_age) values
  ('Australia', 'AU', 18),
  ('India', 'IN', 18),
  ('United States', 'US', 18),
  ('United Kingdom', 'GB', 16),
  ('Canada', 'CA', 18),
  ('Germany', 'DE', 16),
  ('France', 'FR', 15),
  ('Ireland', 'IE', 17),
  ('New Zealand', 'NZ', 16),
  ('United Arab Emirates', 'AE', 21),
  ('Saudi Arabia', 'SA', 21),
  ('Qatar', 'QA', 21),
  ('Kuwait', 'KW', 21),
  ('Bahrain', 'BH', 21),
  ('Oman', 'OM', 21),
  ('Singapore', 'SG', 16),
  ('Malaysia', 'MY', 18),
  ('South Africa', 'ZA', 16),
  ('Nigeria', 'NG', 18),
  ('Brazil', 'BR', 14),
  ('Japan', 'JP', 13),
  ('South Korea', 'KR', 19),
  ('Pakistan', 'PK', 18),
  ('Bangladesh', 'BD', 18),
  ('Sri Lanka', 'LK', 16),
  ('Nepal', 'NP', 18),
  ('Netherlands', 'NL', 16),
  ('Spain', 'ES', 16),
  ('Italy', 'IT', 14),
  ('Sweden', 'SE', 15),
  ('Norway', 'NO', 16),
  ('Denmark', 'DK', 15),
  ('Finland', 'FI', 16),
  ('Switzerland', 'CH', 16),
  ('Austria', 'AT', 14),
  ('Belgium', 'BE', 16),
  ('Portugal', 'PT', 14),
  ('Greece', 'GR', 15),
  ('Poland', 'PL', 15),
  ('Turkey', 'TR', 18),
  ('Egypt', 'EG', 18),
  ('Indonesia', 'ID', 19),
  ('Thailand', 'TH', 15),
  ('Philippines', 'PH', 18),
  ('Vietnam', 'VN', 18),
  ('China', 'CN', 14),
  ('Hong Kong', 'HK', 16),
  ('Israel', 'IL', 16),
  ('Argentina', 'AR', 13),
  ('Mexico', 'MX', 18),
  ('Chile', 'CL', 14),
  ('Colombia', 'CO', 14),
  ('Peru', 'PE', 14),
  ('Kenya', 'KE', 18),
  ('Ghana', 'GH', 16),
  ('Morocco', 'MA', 18),
  ('Algeria', 'DZ', 18),
  ('Tunisia', 'TN', 18),
  ('Iraq', 'IQ', 18),
  ('Jordan', 'JO', 18),
  ('Lebanon', 'LB', 18)
on conflict (iso_code) do update set
  name = excluded.name,
  min_age = excluded.min_age;

-- ── 3. Storage bucket for profile photos ────────────────────────────────────
-- Created via the Supabase Storage API (the SQL below creates the bucket record
-- in storage.buckets). The actual bucket is created by the Management API call
-- in the deploy script, but we set up the RLS policies here.

-- Insert the bucket record if it doesn't exist
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-photos',
  'profile-photos',
  true,                                    -- public read (for swipe deck)
  5242880,                                 -- 5 MB limit
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- RLS: a user can upload/read/update/delete only their own photos
-- (path pattern: {userId}/{timestamp}.jpg)
drop policy if exists "profile_photos_upload" on storage.objects;
create policy "profile_photos_upload" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "profile_photos_read" on storage.objects;
create policy "profile_photos_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'profile-photos');

drop policy if exists "profile_photos_update" on storage.objects;
create policy "profile_photos_update" on storage.objects
  for update to authenticated
  using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "profile_photos_delete" on storage.objects;
create policy "profile_photos_delete" on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

commit;

-- =============================================================================
-- END
-- =============================================================================
