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
