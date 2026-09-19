package com.mallucupid.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------- Auth / OTP ----------

@JsonClass(generateAdapter = true)
data class SendOtpRequest(val email: String)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(val ok: Boolean? = null, val error: String? = null, val expires_in: Int? = null)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(val email: String, val code: String)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val user_id: String? = null,
    val token_hash: String? = null,
)

@JsonClass(generateAdapter = true)
data class VerifyTokenRequest(
    @Json(name = "token_hash") val tokenHash: String,
    @Json(name = "type") val type: String = "magiclink",
)

@JsonClass(generateAdapter = true)
data class SessionResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    val user: SupabaseUser? = null,
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    val id: String? = null,
    val email: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)

// ---------- Swipe deck (from get_swipe_deck RPC) ----------

@JsonClass(generateAdapter = true)
data class SwipeDeckProfileDto(
    val id: String,
    val name: String? = null,
    @Json(name = "birth_year") val birthYear: Int? = null,
    val bio: String? = null,
    val city: String? = null,
    val profession: String? = null,
    @Json(name = "looking_for") val lookingFor: String? = null,
    val zodiac: String? = null,
    val anthem: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = null,
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null,
    val gender: String? = null,
    @Json(name = "communication_style") val communicationStyle: String? = null,
    @Json(name = "love_style") val loveStyle: String? = null,
    val education: String? = null,
    val drinking: String? = null,
    val smoking: String? = null,
    val workout: String? = null,
    val pets: String? = null,
    val height: String? = null,
    val languages: List<String>? = null,
    val interests: List<String>? = null,
    val photos: List<String>? = null,
    val prompts: List<PromptDto>? = null,
    @Json(name = "distance_km") val distanceKm: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PromptDto(val question: String? = null, val answer: String? = null)

// ---------- Swipe insert ----------

@JsonClass(generateAdapter = true)
data class SwipeInsert(
    @Json(name = "swiper_id") val swiperId: String,
    @Json(name = "swiped_id") val swipedId: String,
    val action: String, // "like" | "pass" | "superlike"
)

// ---------- Profile upsert ----------

@JsonClass(generateAdapter = true)
data class ProfileUpsert(
    val id: String,
    val name: String? = null,
    val gender: String? = null,
    @Json(name = "sexual_orientation") val sexualOrientation: String? = null,
    val pronouns: String? = null,
    @Json(name = "birth_day") val birthDay: Int? = null,
    @Json(name = "birth_month") val birthMonth: Int? = null,
    @Json(name = "birth_year") val birthYear: Int? = null,
    val city: String? = null,
    val bio: String? = null,
    val profession: String? = null,
    val company: String? = null,
    val college: String? = null,
    @Json(name = "course_name") val courseName: String? = null,
    @Json(name = "job_title") val jobTitle: String? = null,
    val education: String? = null,
    @Json(name = "marital_status") val maritalStatus: String? = null,
    val zodiac: String? = null,
    val anthem: String? = null,
    @Json(name = "looking_for") val lookingFor: String? = null,
    @Json(name = "relationship_type") val relationshipType: String? = null,
    val goal: String? = null,
    val height: String? = null,
    val languages: List<String>? = null,
    val interests: List<String>? = null,
    @Json(name = "deal_breakers") val dealBreakers: List<String>? = null,
    @Json(name = "communication_style") val communicationStyle: String? = null,
    @Json(name = "love_style") val loveStyle: String? = null,
    val drinking: String? = null,
    val smoking: String? = null,
    val workout: String? = null,
    val pets: String? = null,
    @Json(name = "family_plans") val familyPlans: String? = null,
    @Json(name = "social_media") val socialMedia: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = null,
    @Json(name = "interested_in") val interestedIn: List<String>? = null,
    @Json(name = "max_distance_km") val maxDistanceKm: Int? = null,
    @Json(name = "age_min") val ageMin: Int? = null,
    @Json(name = "age_max") val ageMax: Int? = null,
    @Json(name = "registered_email") val registeredEmail: String? = null,
    @Json(name = "dont_show_age") val dontShowAge: Boolean? = null,
    @Json(name = "dont_show_distance") val dontShowDistance: Boolean? = null,
    @Json(name = "smart_photos") val smartPhotos: Boolean? = null,
    @Json(name = "super_likes_count") val superLikesCount: Int? = null,
    @Json(name = "my_boosts_count") val myBoostsCount: Int? = null,
    @Json(name = "photo_verified_only_chat") val photoVerifiedOnlyChat: Boolean? = null,
    @Json(name = "is_online") val isOnline: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@JsonClass(generateAdapter = true)
data class PhotoUpsert(
    @Json(name = "user_id") val userId: String,
    val url: String,
    val position: Int,
    @Json(name = "is_primary") val isPrimary: Boolean,
)

@JsonClass(generateAdapter = true)
data class PromptUpsert(
    @Json(name = "user_id") val userId: String,
    val position: Int,
    val question: String,
    val answer: String,
)

// ---------- Settings ----------

@JsonClass(generateAdapter = true)
data class SettingsUpsert(
    @Json(name = "user_id") val userId: String,
    @Json(name = "show_active_status") val showActiveStatus: Boolean? = null,
    @Json(name = "show_recently_active_status") val showRecentlyActiveStatus: Boolean? = null,
    @Json(name = "email_sub_matches") val emailSubMatches: Boolean? = null,
    @Json(name = "email_sub_messages") val emailSubMessages: Boolean? = null,
    @Json(name = "email_sub_promos") val emailSubPromos: Boolean? = null,
    @Json(name = "push_matches") val pushMatches: Boolean? = null,
    @Json(name = "push_messages") val pushMessages: Boolean? = null,
    @Json(name = "push_message_likes") val pushMessageLikes: Boolean? = null,
    @Json(name = "push_super_likes") val pushSuperLikes: Boolean? = null,
    @Json(name = "push_promos") val pushPromos: Boolean? = null,
    @Json(name = "push_likes_frequency") val pushLikesFrequency: String? = null,
    @Json(name = "push_likes_enabled") val pushLikesEnabled: Boolean? = null,
)

/**
 * Read-side mirror of the user_settings row. Every field is nullable so the
 * adapter tolerates missing columns if the row was inserted by older code.
 */
@JsonClass(generateAdapter = true)
data class SettingsDto(
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "show_active_status") val showActiveStatus: Boolean? = null,
    @Json(name = "show_recently_active_status") val showRecentlyActiveStatus: Boolean? = null,
    @Json(name = "email_verified") val emailVerified: Boolean? = null,
    @Json(name = "email_sub_matches") val emailSubMatches: Boolean? = null,
    @Json(name = "email_sub_messages") val emailSubMessages: Boolean? = null,
    @Json(name = "email_sub_promos") val emailSubPromos: Boolean? = null,
    @Json(name = "push_matches") val pushMatches: Boolean? = null,
    @Json(name = "push_messages") val pushMessages: Boolean? = null,
    @Json(name = "push_message_likes") val pushMessageLikes: Boolean? = null,
    @Json(name = "push_super_likes") val pushSuperLikes: Boolean? = null,
    @Json(name = "push_promos") val pushPromos: Boolean? = null,
    @Json(name = "push_likes_frequency") val pushLikesFrequency: String? = null,
    @Json(name = "push_likes_enabled") val pushLikesEnabled: Boolean? = null,
)

/**
 * Read-side mirror of the settings-related columns of the profiles row.
 * Used by AccountSettingsScreen to hydrate discovery / chat-privacy toggles.
 */
@JsonClass(generateAdapter = true)
data class ProfileSettingsDto(
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "photo_verified_only_chat") val photoVerifiedOnlyChat: Boolean? = null,
    @Json(name = "max_distance_km") val maxDistanceKm: Int? = null,
    @Json(name = "age_min") val ageMin: Int? = null,
    @Json(name = "age_max") val ageMax: Int? = null,
    @Json(name = "interested_in") val interestedIn: List<String>? = null,
    @Json(name = "dont_show_age") val dontShowAge: Boolean? = null,
    @Json(name = "dont_show_distance") val dontShowDistance: Boolean? = null,
)

/**
 * Partial PATCH body for the profiles row. All fields nullable so Moshi omits
 * nulls and PostgREST only updates the columns we actually want to change.
 */
@JsonClass(generateAdapter = true)
data class ProfileSettingsPatch(
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "photo_verified_only_chat") val photoVerifiedOnlyChat: Boolean? = null,
    @Json(name = "max_distance_km") val maxDistanceKm: Int? = null,
    @Json(name = "age_min") val ageMin: Int? = null,
    @Json(name = "age_max") val ageMax: Int? = null,
    @Json(name = "interested_in") val interestedIn: List<String>? = null,
)

// ---------- Messages ----------

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: Long? = null,
    @Json(name = "match_id") val matchId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "receiver_id") val receiverId: String,
    val content: String = "",
    val type: String = "text",
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "audio_duration") val audioDuration: String? = null,
    @Json(name = "reply_to_id") val replyToId: Long? = null,
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MessageInsert(
    @Json(name = "match_id") val matchId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "receiver_id") val receiverId: String,
    val content: String,
    val type: String = "text",
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "audio_duration") val audioDuration: String? = null,
    @Json(name = "reply_to_id") val replyToId: Long? = null,
)

// ---------- Matches ----------

@JsonClass(generateAdapter = true)
data class MatchDto(
    val id: String,
    @Json(name = "user1_id") val user1Id: String,
    @Json(name = "user2_id") val user2Id: String,
    @Json(name = "created_at") val createdAt: String? = null,
)

// ---------- Message reactions ----------
//
// Schema (supabase/migrations/0001_initial_schema.sql):
//   message_reactions(id bigint pk, message_id bigint, user_id uuid, emoji text,
//                     created_at timestamptz, unique(message_id, user_id))
// The unique(message_id, user_id) constraint lets us upsert a user's reaction
// on a given message via PostgREST's `Prefer: resolution=merge-duplicates` header.

@JsonClass(generateAdapter = true)
data class ReactionInsert(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "user_id") val userId: String,
    val emoji: String,
)

// ---------- Subscriptions ----------

@JsonClass(generateAdapter = true)
data class SubscriptionDto(
    val id: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    val plan: String? = null,
    @Json(name = "plan_name") val planName: String? = null,
    val status: String? = null,
    @Json(name = "txn_id") val txnId: String? = null,
    val amount: Double? = null,
    @Json(name = "duration_days") val durationDays: Int? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "google_purchase_token") val googlePurchaseToken: String? = null,
    @Json(name = "google_product_id") val googleProductId: String? = null,
)

// ---------- Countries (for onboarding + age validation) ----------

@JsonClass(generateAdapter = true)
data class CountryDto(
    val id: Int? = null,
    val name: String? = null,
    @Json(name = "iso_code") val isoCode: String? = null,
    @Json(name = "min_age") val minAge: Int? = null,
)

// ---------- Swipe quota / status ----------

@JsonClass(generateAdapter = true)
data class SwipeStatusDto(
    @Json(name = "swipes_used") val swipesUsed: Int? = null,
    @Json(name = "swipe_limit") val swipeLimit: Int? = null,
    @Json(name = "swipe_remaining") val swipeRemaining: Int? = null,
    @Json(name = "is_pro") val isPro: Boolean? = null,
    @Json(name = "resets_at") val resetsAt: String? = null,
)
