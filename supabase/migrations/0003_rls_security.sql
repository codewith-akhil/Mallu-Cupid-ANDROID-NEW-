-- =============================================================================
-- Mallu Cupid — Migration 0003: RLS security hardening
-- =============================================================================
-- Fixes from the end-to-end audit:
--   1. messages_self_insert: cross-user message injection — must be a
--      participant of the match.
--   2. messages_self_update: missing WITH CHECK lets receiver rewrite any
--      column — restrict to is_read only via column-level grant.
--   3. subs_self_insert: self-grant premium — restrict to status='pending'.
--   4. profiles_self_write: for-all includes DELETE — split to INSERT/UPDATE.
--   5. Add swipes_swiped_only_idx for the get_swipe_deck NOT IN subquery.
-- =============================================================================

begin;

-- 1. messages: insert must be a participant of the match
drop policy if exists "messages_self_insert" on public.messages;
create policy "messages_self_insert" on public.messages
  for insert to authenticated
  with check (
    sender_id = auth.uid()
    and exists (
      select 1 from public.matches m
      where m.id = messages.match_id
        and (m.user1_id = auth.uid() or m.user2_id = auth.uid())
    )
  );

-- 2. messages: receiver may UPDATE only is_read, nothing else.
--    Revoke the broad UPDATE, then grant column-level.
drop policy if exists "messages_self_update" on public.messages;
revoke update on public.messages from authenticated;
grant update (is_read) on public.messages to authenticated;
create policy "messages_self_update_is_read" on public.messages
  for update to authenticated
  using (receiver_id = auth.uid())
  with check (receiver_id = auth.uid());

-- 3. subscriptions: self-insert only as 'pending' (server sets 'active')
drop policy if exists "subs_self_insert" on public.subscriptions;
create policy "subs_self_insert" on public.subscriptions
  for insert to authenticated
  with check (user_id = auth.uid() and status = 'pending');

-- 4. profiles: split self-write into INSERT + UPDATE (no DELETE —
--    cascade-on-delete from auth.users handles row removal)
drop policy if exists "profiles_self_write" on public.profiles;
create policy "profiles_self_insert" on public.profiles
  for insert to authenticated with check (id = auth.uid());
create policy "profiles_self_update" on public.profiles
  for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- 5. index for get_swipe_deck NOT IN subquery
create index if not exists swipes_swiped_only_idx on public.swipes (swiped_id);

commit;
