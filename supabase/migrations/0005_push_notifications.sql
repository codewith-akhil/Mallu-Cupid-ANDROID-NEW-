-- Push notifications support
begin;

-- Add FCM token column to profiles
alter table public.profiles
  add column if not exists fcm_token text;

-- Allow users to update their own fcm_token (read is already open to authenticated)
-- The existing "profiles_self_update" policy already covers this (id = auth.uid())

-- Notification log table (for debugging + analytics)
create table if not exists public.push_notifications_log (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  type        text not null,
  title       text,
  body        text,
  data        json,
  sent_at     timestamptz default now(),
  delivered   boolean default false
);
create index if not exists push_notif_log_user_idx on public.push_notifications_log (user_id, sent_at desc);

commit;
