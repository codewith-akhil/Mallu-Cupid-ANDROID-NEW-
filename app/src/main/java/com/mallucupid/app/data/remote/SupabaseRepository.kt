package com.mallucupid.app.data.remote

import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.PromptItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supabase data repository — thin REST/PostgREST wrapper for all tables.
 * Uses the in-memory access token set by SupabaseAuth. Returns null on error
 * (caller shows a Toast / falls back to local data).
 *
 * Every suspend function switches to Dispatchers.IO internally so callers
 * cannot accidentally trigger NetworkOnMainThreadException.
 */
object SupabaseRepository {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val reqAdapter = moshi.adapter(Map::class.java)
    private val deckAdapter = Types.newParameterizedType(
        List::class.java, SwipeDeckProfileDto::class.java
    ).let { moshi.adapter<List<SwipeDeckProfileDto>>(it) }
    private val matchListAdapter = Types.newParameterizedType(
        List::class.java, MatchDto::class.java
    ).let { moshi.adapter<List<MatchDto>>(it) }
    private val msgListAdapter = Types.newParameterizedType(
        List::class.java, MessageDto::class.java
    ).let { moshi.adapter<List<MessageDto>>(it) }
    private val profileAdapter = moshi.adapter(ProfileUpsert::class.java)
    private val photoAdapter = moshi.adapter(PhotoUpsert::class.java)
    private val promptAdapter = moshi.adapter(PromptUpsert::class.java)
    private val settingsAdapter = moshi.adapter(SettingsUpsert::class.java)
    private val swipeAdapter = moshi.adapter(SwipeInsert::class.java)
    private val msgInsertAdapter = moshi.adapter(MessageInsert::class.java)
    private val reactionAdapter = moshi.adapter(ReactionInsert::class.java)
    private val settingsListAdapter = Types.newParameterizedType(
        List::class.java, SettingsDto::class.java
    ).let { moshi.adapter<List<SettingsDto>>(it) }
    private val profileSettingsListAdapter = Types.newParameterizedType(
        List::class.java, ProfileSettingsDto::class.java
    ).let { moshi.adapter<List<ProfileSettingsDto>>(it) }
    private val profileSettingsPatchAdapter = moshi.adapter(ProfileSettingsPatch::class.java)

    // ---------- Swipe deck ----------

    suspend fun getSwipeDeck(limit: Int = 10): List<DatingProfile> = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("p_limit" to limit))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/rpc/get_swipe_deck")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else deckAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().map { it.toDatingProfile() }
        }
    }

    // ---------- Swipes ----------

    suspend fun recordSwipe(swiperId: String, swipedId: String, action: String): Boolean = withContext(Dispatchers.IO) {
        val body = swipeAdapter.toJson(SwipeInsert(swiperId, swipedId, action))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/swipes")
            .header("Prefer", "return=minimal")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    /** Deletes a swipe record (used for rewind — lets the profile reappear in the deck). */
    suspend fun deleteSwipe(swiperId: String, swipedId: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/swipes?swiper_id=eq.$swiperId&swiped_id=eq.$swipedId")
            .header("Prefer", "return=minimal")
            .delete()
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Matches ----------

    suspend fun getMatches(userId: String): List<MatchDto> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/matches" +
                  "?or=(user1_id.eq.$userId,user2_id.eq.$userId)&order=created_at.desc")
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else matchListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
        }
    }

    // ---------- Messages ----------

    suspend fun getMessages(matchId: String): List<MessageDto> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages" +
                  "?match_id=eq.$matchId&order=created_at.asc")
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else msgListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
        }
    }

    suspend fun sendMessage(
        matchId: String, senderId: String, receiverId: String,
        content: String, type: String = "text",
        mediaUrl: String? = null, audioDuration: String? = null,
        replyToId: Long? = null,
    ): MessageDto? = withContext(Dispatchers.IO) {
        val ins = MessageInsert(matchId, senderId, receiverId, content, type, mediaUrl, audioDuration, replyToId)
        val body = msgInsertAdapter.toJson(ins)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages")
            .header("Prefer", "return=representation")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else {
                val list = msgListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
                list.firstOrNull()
            }
        }
    }

    suspend fun markMessageRead(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("is_read" to true))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages?id=eq.$messageId")
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    /**
     * Finds the match id (uuid) between [userId] and [partnerId], regardless of
     * which user is user1 vs user2. Returns null if no match row exists or the
     * request fails — callers fall back to a local sample conversation.
     */
    suspend fun getMatchId(userId: String, partnerId: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(
                "${SupabaseConfig.REST_BASE}/matches" +
                    "?or=(and(user1_id.eq.$userId,user2_id.eq.$partnerId)," +
                    "and(user1_id.eq.$partnerId,user2_id.eq.$userId))&limit=1"
            )
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else matchListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().firstOrNull()?.id
        }
    }

    /**
     * Upserts a reaction row for the given (messageId, userId) pair.
     * The unique(message_id, user_id) constraint on `message_reactions` combined
     * with PostgREST's `Prefer: resolution=merge-duplicates` makes this an upsert
     * so the emoji is replaced if the user already reacted.
     */
    suspend fun addReaction(messageId: Long, userId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        val ins = ReactionInsert(messageId, userId, emoji)
        val body = reactionAdapter.toJson(ins)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/message_reactions")
            .header("Prefer", "return=minimal,resolution=merge-duplicates")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    /**
     * Deletes the calling user's reaction on the given message. Safe to call
     * even if no reaction row exists (PostgREST DELETE is idempotent).
     */
    suspend fun removeReaction(messageId: Long, userId: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(
                "${SupabaseConfig.REST_BASE}/message_reactions" +
                    "?message_id=eq.$messageId&user_id=eq.$userId"
            )
            .header("Prefer", "return=minimal")
            .delete()
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Profile save (onboarding complete) ----------

    suspend fun saveProfile(userId: String, email: String, draft: OnboardingDraft): Boolean = withContext(Dispatchers.IO) {
        val profile = ProfileUpsert(
            id = userId,
            name = draft.name,
            gender = draft.gender,
            sexualOrientation = draft.sexualOrientation,
            pronouns = draft.pronouns,
            birthDay = draft.birthDay.toIntOrNull(),
            birthMonth = draft.birthMonth.toIntOrNull(),
            birthYear = draft.birthYear.toIntOrNull(),
            city = draft.city,
            bio = draft.bio,
            profession = draft.jobTitle,
            company = draft.company,
            college = draft.college,
            courseName = draft.courseName,
            jobTitle = draft.jobTitle,
            education = draft.education,
            maritalStatus = draft.maritalStatus,
            zodiac = draft.zodiac,
            anthem = draft.anthem,
            lookingFor = draft.lookingFor,
            relationshipType = draft.relationshipType,
            goal = draft.goal,
            height = draft.height,
            languages = draft.languages,
            interests = draft.interests,
            dealBreakers = draft.dealBreakers,
            communicationStyle = draft.communicationStyle,
            loveStyle = draft.loveStyle,
            drinking = draft.drinking,
            smoking = draft.smoking,
            workout = draft.workout,
            pets = draft.pets,
            familyPlans = draft.familyPlans,
            socialMedia = draft.socialMedia,
            isVerified = draft.isVerified,
            interestedIn = draft.interestedIn,
            maxDistanceKm = draft.maxDistanceKm,
            ageMin = draft.ageMin,
            ageMax = draft.ageMax,
            registeredEmail = email,
            dontShowAge = draft.dontShowAge,
            dontShowDistance = draft.dontShowDistance,
            smartPhotos = draft.smartPhotos,
            superLikesCount = draft.superLikesCount,
            myBoostsCount = draft.myBoostsCount,
            photoVerifiedOnlyChat = draft.photoVerifiedOnlyChat,
            isOnline = draft.isOnline,
        )
        val body = profileAdapter.toJson(profile)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/profiles?id=eq.$userId")
            .header("Prefer", "return=minimal,resolution=merge-duplicates")
            .patch(body.toRequestBody(json))
            .build()
        val profileOk = SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
        if (!profileOk) return@withContext false

        // Replace photos (delete + insert)
        SupabaseClient.http.newCall(
            Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/profile_photos?user_id=eq.$userId")
                .delete()
                .build()
        ).execute().close()
        draft.photos.forEachIndexed { index, url ->
            val photo = PhotoUpsert(userId, url, index, index == 0)
            val pbody = photoAdapter.toJson(photo)
            SupabaseClient.http.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.REST_BASE}/profile_photos")
                    .header("Prefer", "return=minimal")
                    .post(pbody.toRequestBody(json))
                    .build()
            ).execute().close()
        }

        // Replace prompts
        SupabaseClient.http.newCall(
            Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/profile_prompts?user_id=eq.$userId")
                .delete()
                .build()
        ).execute().close()
        draft.prompts.forEachIndexed { index, p ->
            val prompt = PromptUpsert(userId, index, p.question, p.answer)
            val pbody = promptAdapter.toJson(prompt)
            SupabaseClient.http.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.REST_BASE}/profile_prompts")
                    .header("Prefer", "return=minimal")
                    .post(pbody.toRequestBody(json))
                    .build()
            ).execute().close()
        }

        // Upsert settings
        val settings = SettingsUpsert(
            userId = userId,
            showActiveStatus = draft.showActiveStatus,
            showRecentlyActiveStatus = draft.showRecentlyActiveStatus,
            emailSubMatches = draft.emailSubMatches,
            emailSubMessages = draft.emailSubMessages,
            emailSubPromos = draft.emailSubPromos,
            pushMatches = draft.pushMatches,
            pushMessages = draft.pushMessages,
            pushMessageLikes = draft.pushMessageLikes,
            pushSuperLikes = draft.pushSuperLikes,
            pushPromos = draft.pushPromos,
            pushLikesFrequency = draft.pushLikesFrequency,
        )
        val sbody = settingsAdapter.toJson(settings)
        SupabaseClient.http.newCall(
            Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/user_settings?user_id=eq.$userId")
                .header("Prefer", "return=minimal,resolution=merge-duplicates")
                .patch(sbody.toRequestBody(json))
                .build()
        ).execute().close()

        return@withContext true
    }

    // ---------- Settings (load / save) ----------

    /**
     * Loads the user_settings row for the given user. Returns null on error
     * (caller falls back to in-memory draft defaults).
     */
    suspend fun loadUserSettings(userId: String): SettingsDto? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/user_settings?user_id=eq.$userId")
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else settingsListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().firstOrNull()
        }
    }

    /**
     * Loads just the settings-related columns of the profiles row
     * (online flag, chat privacy, distance, age range, interested_in, dont_show_*).
     */
    suspend fun loadProfileSettings(userId: String): ProfileSettingsDto? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(
                "${SupabaseConfig.REST_BASE}/profiles?id=eq.$userId" +
                    "&select=is_online,photo_verified_only_chat,max_distance_km,age_min,age_max,interested_in,dont_show_age,dont_show_distance"
            )
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else profileSettingsListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().firstOrNull()
        }
    }

    /**
     * Persists a partial user_settings row. Null fields in [settings] are
     * omitted by Moshi, so PostgREST only updates the columns we set.
     * Uses merge-duplicates so the row is upserted if missing.
     */
    suspend fun saveUserSettings(userId: String, settings: SettingsUpsert): Boolean = withContext(Dispatchers.IO) {
        val body = settingsAdapter.toJson(settings)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/user_settings?user_id=eq.$userId")
            .header("Prefer", "return=minimal,resolution=merge-duplicates")
            .patch(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    /**
     * Persists a partial profile patch (online flag, chat privacy, distance,
     * age range, interested_in). Null fields are omitted by Moshi so
     * PostgREST only touches the columns we explicitly set.
     */
    suspend fun saveProfileSettings(patch: ProfileSettingsPatch, userId: String): Boolean = withContext(Dispatchers.IO) {
        val body = profileSettingsPatchAdapter.toJson(patch)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/profiles?id=eq.$userId")
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Blocked users ----------

    /** Unblocks a user. Returns null on success, or error. */
    suspend fun unblockUser(blockerId: String, blockedId: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/blocked_users?blocker_id=eq.$blockerId&blocked_id=eq.$blockedId")
            .header("Prefer", "return=minimal")
            .delete()
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) null else "Could not unblock user. Please try again."
        }
    }

    // ---------- Account deletion ----------

    /** Deletes the user's account via edge function. Returns null on success, or error. */
    suspend fun deleteAccount(userId: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("user_id" to userId))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/delete-account")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) null else "Could not delete account. Please try again or contact support."
        }
    }

    // ---------- DTO → domain mapping ----------

    private fun SwipeDeckProfileDto.toDatingProfile(): DatingProfile {
        val age = birthYear?.let { (2026 - it).coerceIn(18, 99) } ?: 25
        return DatingProfile(
            id = id,
            name = name ?: "Anonymous",
            age = age,
            isVerified = isVerified ?: false,
            location = city ?: "Nearby",
            distanceKm = 0,
            bio = bio.orEmpty(),
            photos = photos.orEmpty().ifEmpty { listOf("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=800&q=80") },
            profession = profession ?: "",
            lookingFor = lookingFor ?: "Long-term partner",
            essentialsGender = gender ?: "Woman",
            astrologyStar = zodiac ?: "Libra",
            musicAnthem = anthem ?: "",
            communicationStyle = communicationStyle ?: "Better in person",
            loveStyle = loveStyle ?: "Quality time",
            education = education ?: "",
            drinking = drinking ?: "Not for me",
            smoking = smoking ?: "Non-smoker",
            workout = workout ?: "Sometimes",
            pets = pets ?: "Pet-free",
            prompts = prompts?.map { PromptItem(it.question.orEmpty(), it.answer.orEmpty()) } ?: emptyList(),
            interests = interests ?: emptyList(),
        )
    }
}
