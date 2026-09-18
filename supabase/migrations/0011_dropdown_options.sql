-- =============================================================================
-- Mallu Cupid — Migration 0011: Dropdown options table (DB-driven, no hardcode)
-- =============================================================================
begin;

create table if not exists public.profile_options (
  id          int generated always as identity primary key,
  category    text not null,          -- 'gender', 'looking_for', 'marital_status', 'family_plans', 'pets', 'drinking', 'smoking'
  value       text not null,          -- the display value
  sort_order  int default 0,
  unique(category, value)
);

create index if not exists profile_options_category_idx on public.profile_options (category, sort_order);

alter table public.profile_options enable row level security;
drop policy if exists "profile_options_read" on public.profile_options;
create policy "profile_options_read" on public.profile_options
  for select to authenticated using (true);

-- Seed all dropdown options
insert into public.profile_options (category, value, sort_order) values
  -- gender
  ('gender', 'Man', 1),
  ('gender', 'Woman', 2),
  ('gender', 'Transman', 3),
  ('gender', 'Transwoman', 4),
  ('gender', 'Non-binary', 5),
  -- looking_for
  ('looking_for', 'Serious relationship', 1),
  ('looking_for', 'Casual relationship', 2),
  ('looking_for', 'Dating', 3),
  ('looking_for', 'New friends', 4),
  ('looking_for', 'Friends with benefits', 5),
  -- marital_status
  ('marital_status', 'Single', 1),
  ('marital_status', 'Never Married', 2),
  ('marital_status', 'In a Relationship', 3),
  ('marital_status', 'Divorced', 4),
  ('marital_status', 'Separated', 5),
  ('marital_status', 'Widowed', 6),
  -- family_plans
  ('family_plans', 'Want children', 1),
  ('family_plans', E'Don''t want children', 2),
  ('family_plans', 'Have children & want more', 3),
  ('family_plans', E'Have children & don''t want more', 4),
  ('family_plans', 'Not sure yet', 5),
  -- pets
  ('pets', 'Dog lover', 1),
  ('pets', 'Cat lover', 2),
  ('pets', 'Have pets', 3),
  ('pets', E'Don''t have, but love pets', 4),
  ('pets', 'Pet-free', 5),
  ('pets', 'Allergic to pets', 6),
  -- drinking
  ('drinking', 'Not for me', 1),
  ('drinking', 'Sober', 2),
  ('drinking', 'Socially', 3),
  ('drinking', 'Regularly', 4),
  ('drinking', 'On special occasions', 5),
  -- smoking
  ('smoking', 'Non-smoker', 1),
  ('smoking', 'Social smoker', 2),
  ('smoking', 'Smoker', 3),
  ('smoking', 'Trying to quit', 4)
on conflict (category, value) do update set
  value = excluded.value,
  sort_order = excluded.sort_order;

commit;
