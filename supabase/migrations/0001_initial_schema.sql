-- =============================================================================
-- Mallu Cupid — Supabase Schema (initial migration)
-- =============================================================================
-- Applied via direct Postgres connection to the Supabase pooler.
-- All tables are created in the `public` schema and extend `auth.users`.
-- RLS is enabled on every table that holds user data.
-- =============================================================================

begin;

-- ---------- helper: updated_at trigger --------------------------------------
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end; $$;

-- ---------- profiles (one row per auth user, public dating data) ------------
create table if not exists public.profiles (
  id              uuid primary key references auth.users(id) on delete cascade,
  name            text        not null default '',
  username        text        unique,
  gender          text        default 'Man',
  sexual_orientation text     default 'Straight',
  pronouns        text        default 'He',
  birth_day       int,
  birth_month     int,
  birth_year      int,
  city            text        default '',
  latitude        double precision,
  longitude       double precision,
  bio             text        default '',
  profession      text        default '',
  company         text        default '',
  college         text        default '',
  course_name     text        default '',
  job_title       text        default '',
  education       text        default '',
  marital_status  text        default 'Single',
  zodiac          text        default '',
  anthem          text        default '',
  looking_for     text        default 'Long-term partner',
  relationship_type text      default 'Open to exploring',
  goal            text        default 'Long-term partner',
  height          text        default '',
  languages       text[]      default '{}',
  interests       text[]      default '{}',
  deal_breakers   text[]      default '{}',
  communication_style text    default 'Better in person',
  love_style      text        default 'Time together',
  drinking        text        default 'Not for me',
  smoking         text        default 'Non-smoker',
  workout         text        default 'Sometimes',
  pets            text        default 'Pet-free',
  family_plans    text        default 'Not sure yet',
  social_media    text        default 'Passive scroller',
  is_verified     boolean     default false,
  is_online       boolean     default false,
  last_seen_at    timestamptz default now(),
  dont_show_age   boolean     default false,
  dont_show_distance boolean  default false,
  smart_photos    boolean     default true,
  super_likes_count int       default 0,
  my_boosts_count int         default 0,
  interested_in   text[]      default '{"Women"}',
  max_distance_km int         default 50,
  age_min         int         default 18,
  age_max         int         default 99,
  photo_verified_only_chat boolean default true,
  registered_email text      default '',
  created_at      timestamptz default now(),
  updated_at      timestamptz default now()
);
create index if not exists profiles_username_idx on public.profiles (username);
create index if not exists profiles_city_idx on public.profiles (city);
create index if not exists profiles_looking_for_idx on public.profiles (looking_for);
drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_updated_at();

-- ---------- profile_photos (ordered, primary flagged) ----------------------
create table if not exists public.profile_photos (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  url         text not null,
  position    int  not null default 0,
  is_primary  boolean not null default false,
  created_at  timestamptz default now()
);
create index if not exists profile_photos_user_idx on public.profile_photos (user_id, position);

-- ---------- profile_prompts (Q + A pairs) -----------------------------------
create table if not exists public.profile_prompts (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  position    int  not null default 0,
  question    text not null,
  answer      text not null default '',
  created_at  timestamptz default now(),
  updated_at  timestamptz default now()
);
create index if not exists profile_prompts_user_idx on public.profile_prompts (user_id, position);
drop trigger if exists profile_prompts_touch on public.profile_prompts;
create trigger profile_prompts_touch before update on public.profile_prompts
  for each row execute function public.touch_updated_at();

-- ---------- user_settings (active/email/push preferences) -------------------
create table if not exists public.user_settings (
  user_id                     uuid primary key references auth.users(id) on delete cascade,
  show_active_status          boolean default true,
  show_recently_active_status boolean default true,
  email_verified              boolean default false,
  email_sub_matches           boolean default true,
  email_sub_messages          boolean default true,
  email_sub_promos            boolean default true,
  push_matches                boolean default true,
  push_messages               boolean default true,
  push_message_likes          boolean default true,
  push_super_likes            boolean default true,
  push_promos                 boolean default false,
  push_likes_frequency        text    default 'Every 1 new like',
  updated_at                  timestamptz default now()
);
drop trigger if exists user_settings_touch on public.user_settings;
create trigger user_settings_touch before update on public.user_settings
  for each row execute function public.touch_updated_at();

-- ---------- blocked_users ---------------------------------------------------
create table if not exists public.blocked_users (
  id          bigint generated always as identity primary key,
  blocker_id  uuid not null references auth.users(id) on delete cascade,
  blocked_id  uuid not null references auth.users(id) on delete cascade,
  created_at  timestamptz default now(),
  unique (blocker_id, blocked_id)
);
create index if not exists blocked_users_blocker_idx on public.blocked_users (blocker_id);

-- ---------- swipes (like / pass / superlike) --------------------------------
create table if not exists public.swipes (
  id          bigint generated always as identity primary key,
  swiper_id   uuid not null references auth.users(id) on delete cascade,
  swiped_id   uuid not null references auth.users(id) on delete cascade,
  action      text not null check (action in ('like','pass','superlike')),
  created_at  timestamptz default now(),
  unique (swiper_id, swiped_id)
);
create index if not exists swipes_swiper_idx on public.swipes (swiper_id);
create index if not exists swipes_swiped_idx on public.swipes (swiped_id, action);

-- ---------- matches (mutual likes) ------------------------------------------
create table if not exists public.matches (
  id          uuid primary key default gen_random_uuid(),
  user1_id    uuid not null references auth.users(id) on delete cascade,
  user2_id    uuid not null references auth.users(id) on delete cascade,
  created_at  timestamptz default now(),
  check (user1_id <> user2_id),
  unique (user1_id, user2_id)
);
create index if not exists matches_user1_idx on public.matches (user1_id);
create index if not exists matches_user2_idx on public.matches (user2_id);

-- ---------- messages --------------------------------------------------------
create table if not exists public.messages (
  id              bigint generated always as identity primary key,
  match_id        uuid not null references public.matches(id) on delete cascade,
  sender_id       uuid not null references auth.users(id) on delete cascade,
  receiver_id     uuid not null references auth.users(id) on delete cascade,
  content         text default '',
  type            text not null default 'text' check (type in ('text','image','video','voice','system')),
  media_url       text,
  audio_duration  text,
  reply_to_id     bigint references public.messages(id) on delete set null,
  is_read         boolean default false,
  created_at      timestamptz default now()
);
create index if not exists messages_match_idx on public.messages (match_id, created_at);
create index if not exists messages_receiver_unread_idx on public.messages (receiver_id, is_read);

-- ---------- message_reactions ----------------------------------------------
create table if not exists public.message_reactions (
  id          bigint generated always as identity primary key,
  message_id  bigint not null references public.messages(id) on delete cascade,
  user_id     uuid not null references auth.users(id) on delete cascade,
  emoji       text not null,
  created_at  timestamptz default now(),
  unique (message_id, user_id)
);

-- ---------- otp_codes (custom email OTP, never magic-link) ------------------
create table if not exists public.otp_codes (
  id          bigint generated always as identity primary key,
  email       text not null,
  code        text not null,
  expires_at  timestamptz not null,
  used        boolean default false,
  attempts    int default 0,
  created_at  timestamptz default now()
);
create index if not exists otp_codes_email_idx on public.otp_codes (email, used, expires_at);

-- ---------- subscriptions (premium plans) ----------------------------------
create table if not exists public.subscriptions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  plan        text not null check (plan in ('premium','gold','platinum')),
  status      text not null default 'active' check (status in ('active','cancelled','expired','pending')),
  txn_id      text,
  amount      numeric(10,2),
  started_at  timestamptz default now(),
  expires_at  timestamptz,
  created_at  timestamptz default now()
);
create index if not exists subscriptions_user_idx on public.subscriptions (user_id, status);

-- =============================================================================
-- Row Level Security
-- =============================================================================
alter table public.profiles            enable row level security;
alter table public.profile_photos      enable row level security;
alter table public.profile_prompts    enable row level security;
alter table public.user_settings      enable row level security;
alter table public.blocked_users      enable row level security;
alter table public.swipes              enable row level security;
alter table public.matches             enable row level security;
alter table public.messages            enable row level security;
alter table public.message_reactions   enable row level security;
alter table public.otp_codes           enable row level security;
alter table public.subscriptions       enable row level security;

-- profiles: anyone authenticated can read (needed for swipe deck); self can write
drop policy if exists "profiles_read_all" on public.profiles;
create policy "profiles_read_all" on public.profiles
  for select to authenticated using (true);

drop policy if exists "profiles_self_write" on public.profiles;
create policy "profiles_self_write" on public.profiles
  for all to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- profile_photos: public read, self write
drop policy if exists "photos_read_all" on public.profile_photos;
create policy "photos_read_all" on public.profile_photos
  for select to authenticated using (true);

drop policy if exists "photos_self_write" on public.profile_photos;
create policy "photos_self_write" on public.profile_photos
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- profile_prompts: public read, self write
drop policy if exists "prompts_read_all" on public.profile_prompts;
create policy "prompts_read_all" on public.profile_prompts
  for select to authenticated using (true);

drop policy if exists "prompts_self_write" on public.profile_prompts;
create policy "prompts_self_write" on public.profile_prompts
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- user_settings: only self can read + write
drop policy if exists "settings_self" on public.user_settings;
create policy "settings_self" on public.user_settings
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- blocked_users: self can read own + write own
drop policy if exists "blocked_self_read" on public.blocked_users;
create policy "blocked_self_read" on public.blocked_users
  for select to authenticated using (blocker_id = auth.uid());

drop policy if exists "blocked_self_write" on public.blocked_users;
create policy "blocked_self_write" on public.blocked_users
  for all to authenticated
  using (blocker_id = auth.uid()) with check (blocker_id = auth.uid());

-- swipes: self can read own + write own
drop policy if exists "swipes_self_read" on public.swipes;
create policy "swipes_self_read" on public.swipes
  for select to authenticated using (swiper_id = auth.uid());

drop policy if exists "swipes_self_write" on public.swipes;
create policy "swipes_self_write" on public.swipes
  for insert to authenticated with check (swiper_id = auth.uid());

-- matches: either participant can read; system (edge function) creates rows
drop policy if exists "matches_participant_read" on public.matches;
create policy "matches_participant_read" on public.matches
  for select to authenticated
  using (user1_id = auth.uid() or user2_id = auth.uid());

drop policy if exists "matches_participant_insert" on public.matches;
create policy "matches_participant_insert" on public.matches
  for insert to authenticated
  with check (user1_id = auth.uid() or user2_id = auth.uid());

-- messages: participants can read + write
drop policy if exists "messages_participant_read" on public.messages;
create policy "messages_participant_read" on public.messages
  for select to authenticated
  using (sender_id = auth.uid() or receiver_id = auth.uid());

drop policy if exists "messages_self_insert" on public.messages;
create policy "messages_self_insert" on public.messages
  for insert to authenticated with check (sender_id = auth.uid());

drop policy if exists "messages_self_update" on public.messages;
create policy "messages_self_update" on public.messages
  for update to authenticated using (receiver_id = auth.uid());

-- message_reactions: participants can read; self can write/delete own
drop policy if exists "reactions_read" on public.message_reactions;
create policy "reactions_read" on public.message_reactions
  for select to authenticated using (true);

drop policy if exists "reactions_self_write" on public.message_reactions;
create policy "reactions_self_write" on public.message_reactions
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- otp_codes: NO direct client access (only edge functions with service role)
-- (RLS enabled above with no policy = blocked for anon/authenticated)

-- subscriptions: self can read; self can insert own; service role manages status
drop policy if exists "subs_self_read" on public.subscriptions;
create policy "subs_self_read" on public.subscriptions
  for select to authenticated using (user_id = auth.uid());

drop policy if exists "subs_self_insert" on public.subscriptions;
create policy "subs_self_insert" on public.subscriptions
  for insert to authenticated with check (user_id = auth.uid());

-- =============================================================================
-- Auto-create profile + settings row when a new auth user signs up
-- =============================================================================
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, registered_email, name)
  values (new.id, coalesce(new.email, ''), '')
  on conflict (id) do nothing;

  insert into public.user_settings (user_id)
  values (new.id)
  on conflict (user_id) do nothing;

  return new;
end; $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- =============================================================================
-- Helper: get a profile with its photos + prompts (for the swipe deck)
-- Exposed via RPC so the client gets a single payload per card.
-- =============================================================================
create or replace function public.get_swipe_deck(p_limit int default 10)
returns table (
  id uuid, name text, birth_year int, bio text, city text,
  profession text, looking_for text, zodiac text, anthem text,
  is_verified boolean, is_online boolean, last_seen_at timestamptz,
  gender text, communication_style text, love_style text,
  education text, drinking text, smoking text, workout text, pets text,
  height text, languages text[], interests text[], photos text[], prompts json
)
language sql security definer set search_path = public as $$
  select p.id, p.name, p.birth_year, p.bio, p.city,
         p.profession, p.looking_for, p.zodiac, p.anthem,
         p.is_verified, p.is_online, p.last_seen_at,
         p.gender, p.communication_style, p.love_style,
         p.education, p.drinking, p.smoking, p.workout, p.pets,
         p.height, p.languages, p.interests,
         coalesce(array_agg(pp.url order by pp.position, pp.id) filter (where pp.url is not null), '{}') as photos,
         coalesce(json_agg(json_build_object('question', pr.question, 'answer', pr.answer) order by pr.position, pr.id) filter (where pr.question is not null), '[]') as prompts
  from public.profiles p
  left join public.profile_photos pp on pp.user_id = p.id
  left join public.profile_prompts pr on pr.user_id = p.id
  where p.id <> auth.uid()
    and p.id not in (select swiped_id from public.swipes where swiper_id = auth.uid())
    and p.id not in (select blocked_id from public.blocked_users where blocker_id = auth.uid())
    and p.id not in (select blocker_id from public.blocked_users where blocked_id = auth.uid())
  group by p.id
  limit p_limit;
$$;

-- grant execute to authenticated
grant execute on function public.get_swipe_deck(int) to authenticated;

-- =============================================================================
-- Auto-create a match when both users have liked each other
-- =============================================================================
create or replace function public.maybe_create_match()
returns trigger language plpgsql security definer set search_path = public as $$
declare other uuid;
begin
  if new.action <> 'like' then return new; end if;
  select swiper_id into other
    from public.swipes
   where swiper_id = new.swiped_id
     and swiped_id = new.swiper_id
     and action = 'like'
   limit 1;
  if found then
    insert into public.matches (user1_id, user2_id)
    values (least(new.swiper_id, new.swiped_id), greatest(new.swiper_id, new.swiped_id))
    on conflict (user1_id, user2_id) do nothing;
  end if;
  return new;
end; $$;

drop trigger if exists on_swipe_insert on public.swipes;
create trigger on_swipe_insert
  after insert on public.swipes
  for each row execute function public.maybe_create_match();

commit;

-- =============================================================================
-- END
-- =============================================================================
