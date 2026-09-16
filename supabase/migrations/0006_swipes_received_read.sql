-- Allow users to see who swiped on them (for the Likes tab count)
begin;
drop policy if exists "swipes_received_read" on public.swipes;
create policy "swipes_received_read" on public.swipes
  for select to authenticated
  using (swiped_id = auth.uid());
commit;
