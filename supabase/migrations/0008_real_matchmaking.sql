-- =============================================================================
-- Mallu Cupid — Migration 0008: Real matchmaking with lat/lng + haversine + filters
-- =============================================================================
-- Rewrites get_swipe_deck to apply:
--   1. Age range filter (caller's age_min / age_max)
--   2. Max distance filter (haversine distance from caller's lat/lng)
--   3. Gender preference filter (caller's interested_in[] matches profile's gender)
--   4. Returns real distance_km for each profile
-- =============================================================================

begin;

-- Drop old function (signature changed — added distance_km return column)
drop function if exists public.get_swipe_deck(int);

create or replace function public.get_swipe_deck(p_limit int default 10)
returns table (
  id uuid, name text, birth_year int, bio text, city text,
  profession text, looking_for text, zodiac text, anthem text,
  is_verified boolean, is_online boolean, last_seen_at timestamptz,
  gender text, communication_style text, love_style text,
  education text, drinking text, smoking text, workout text, pets text,
  height text, languages text[], interests text[], photos text[], prompts json,
  distance_km int
)
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_caller_id uuid := auth.uid();
  v_caller_lat double precision;
  v_caller_lng double precision;
  v_age_min int;
  v_age_max int;
  v_max_dist int;
  v_interested text[];
  v_current_year int := extract(year from now())::int;
begin
  -- Get caller's preferences
  select latitude, longitude, age_min, age_max, max_distance_km, interested_in
    into v_caller_lat, v_caller_lng, v_age_min, v_age_max, v_max_dist, v_interested
    from public.profiles
   where id = v_caller_id;

  -- Defaults if not set
  if v_age_min is null then v_age_min := 18; end if;
  if v_age_max is null then v_age_max := 99; end if;
  if v_max_dist is null then v_max_dist := 9999; end if;

  return query
    select p.id, p.name, p.birth_year, p.bio, p.city,
           p.profession, p.looking_for, p.zodiac, p.anthem,
           p.is_verified, p.is_online, p.last_seen_at,
           p.gender, p.communication_style, p.love_style,
           p.education, p.drinking, p.smoking, p.workout, p.pets,
           p.height, p.languages, p.interests,
           coalesce(array_agg(pp.url order by pp.position, pp.id) filter (where pp.url is not null), '{}') as photos,
           coalesce(json_agg(json_build_object('question', pr.question, 'answer', pr.answer) order by pr.position, pr.id) filter (where pr.question is not null), '[]') as prompts,
           -- Calculate haversine distance if both have lat/lng, else 0
           case
             when v_caller_lat is not null and v_caller_lng is not null
                  and p.latitude is not null and p.longitude is not null
             then
               round(
                 6371 * (
                   2 * degrees(
                     asin(
                       sqrt(
                         power(sin(radians((p.latitude - v_caller_lat) / 2)), 2) +
                         cos(radians(v_caller_lat)) * cos(radians(p.latitude)) *
                         power(sin(radians((p.longitude - v_caller_lng) / 2)), 2)
                       )
                     )
                   )
                 )
               )::int
             else 0
           end as distance_km
    from public.profiles p
    left join public.profile_photos pp on pp.user_id = p.id
    left join public.profile_prompts pr on pr.user_id = p.id
    where p.id <> v_caller_id
      -- Not already swiped
      and p.id not in (select swiped_id from public.swipes where swiper_id = v_caller_id)
      -- Not blocked by caller
      and p.id not in (select blocked_id from public.blocked_users where blocker_id = v_caller_id)
      -- Didn't block caller
      and p.id not in (select blocker_id from public.blocked_users where blocked_id = v_caller_id)
      -- Age filter (calculate age from birth_year)
      and (
        p.birth_year is null
        or (v_current_year - p.birth_year) >= v_age_min
        and (v_current_year - p.birth_year) <= v_age_max
      )
      -- Gender filter (if caller has preferences)
      and (
        v_interested is null
        or array_length(v_interested, 1) is null
        or p.gender = any(v_interested)
        or p.gender is null
      )
      -- Distance filter (only if caller has lat/lng and profile has lat/lng)
      and (
        v_caller_lat is null
        or p.latitude is null
        or (
          6371 * (
            2 * degrees(
              asin(
                sqrt(
                  power(sin(radians((p.latitude - v_caller_lat) / 2)), 2) +
                  cos(radians(v_caller_lat)) * cos(radians(p.latitude)) *
                  power(sin(radians((p.longitude - v_caller_lng) / 2)), 2)
                )
              )
            )
          )
        ) <= v_max_dist
      )
    group by p.id
    order by distance_km asc nulls last, p.created_at desc
    limit p_limit;
end;
$$;

-- Re-grant (function signature changed)
grant execute on function public.get_swipe_deck(int) to authenticated;

commit;
