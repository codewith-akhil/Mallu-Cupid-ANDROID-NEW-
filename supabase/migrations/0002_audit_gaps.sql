-- =============================================================================
-- Mallu Cupid — Migration 0002: Fill gaps from UI audit
-- =============================================================================
-- Gap #1: push_likes_enabled master toggle in PushNotificationsScreen
--         had no column in user_settings.
-- Gap #2: ProfileUpsert was missing dont_show_age, dont_show_distance,
--         smart_photos, super_likes_count, my_boosts_count from the
--         OnboardingDraft → profiles mapping.
-- =============================================================================

begin;

-- Gap #1: push_likes_enabled
alter table public.user_settings
  add column if not exists push_likes_enabled boolean default true;

-- Gap #2: no new columns needed (dont_show_age, dont_show_distance,
-- smart_photos, super_likes_count, my_boosts_count already exist in
-- profiles from migration 0001). The fix is in the Kotlin ProfileUpsert
-- model — see the code diff for Models.kt.

commit;
