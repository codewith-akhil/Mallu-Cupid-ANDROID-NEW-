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
    private val idsListAdapter = Types.newParameterizedType(
        List::class.java, Map::class.java
    ).let { moshi.adapter<List<Map<String, Any?>>>(it) }
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

    // ---------- Likes received / sent ----------

    /**
     * Counts rows in `swipes` where the given user was swiped-on with a
     * 'like' or 'superlike' action. Uses PostgREST's `Prefer: count=exact`
     * + `Range: 0-0` headers and reads the total from the `content-range`
     * response header.
     *
     * TODO(RLS): the current `swipes_self_read` policy only allows reading
     * rows where `swiper_id = auth.uid()`, so this query returns 0 until
     * either a `swipes_received_read` policy (or a SECURITY DEFINER RPC)
     * is added server-side. Wired end-to-end now so the UI works as soon
     * as the policy lands.
     */
    suspend fun getLikesReceivedCount(userId: String): Int = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/swipes?swiped_id=eq.$userId&action=in.(like,superlike)&select=id")
            .header("Prefer", "count=exact")
            .header("Range", "0-0")
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val range = resp.header("content-range")
            range?.substringAfter("/")?.toIntOrNull() ?: 0
        }
    }

    /**
     * Fetches the profiles the given user has swiped 'like' on. Two REST
     * calls: first the swiped_ids from `swipes`, then the matching rows
     * from `profiles`. Photos live in the separate `profile_photos` table
     * and are NOT fetched here (callers fall back to the default photo URL
     * baked into `SwipeDeckProfileDto.toDatingProfile()`).
     */
    suspend fun getLikesSent(userId: String): List<DatingProfile> = withContext(Dispatchers.IO) {
        // Step 1: fetch swiped_ids for likes sent
        val idsReq = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/swipes?swiper_id=eq.$userId&action=eq.like&select=swiped_id")
            .get().build()
        val ids = SupabaseClient.http.newCall(idsReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val raw = resp.body?.string().orEmpty()
            idsListAdapter.fromJson(raw).orEmpty().mapNotNull { it["swiped_id"] as? String }
        }
        if (ids.isEmpty()) return@withContext emptyList()

        // Step 2: fetch profiles for those ids
        val csv = ids.joinToString(",")
        val profReq = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/profiles?id=in.($csv)")
            .get().build()
        SupabaseClient.http.newCall(profReq).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else deckAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().map { it.toDatingProfile() }
        }
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

    // ---------- Profile-by-id ----------

    /**
     * Fetches the full `profiles` rows for a list of user ids (used by the chat
     * tray to look up partner profiles for each match). Reuses the same
     * `SwipeDeckProfileDto` adapter + `toDatingProfile()` mapping as the swipe
     * deck RPC. Photos are pulled from the same JSON shape (`photos` array).
     */
    suspend fun getProfilesByIds(ids: List<String>): List<DatingProfile> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val csv = ids.joinToString(",")
        val profReq = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/profiles?id=in.($csv)")
            .get().build()
        SupabaseClient.http.newCall(profReq).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else deckAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().map { it.toDatingProfile() }
        }
    }

    // ---------- Inbound likes / message requests ----------

    /**
     * Returns the profiles of users who liked/superliked the given user —
     * the "message requests" inbox.
     *
     * Two REST calls: first the swiper_ids from `swipes`, then the matching
     * rows from `profiles`.
     *
     * NOTE: depends on a `swipes_received_read` RLS policy (or SECURITY DEFINER
     * RPC) to return non-empty results. The query is wired end-to-end now so
     * the UI works as soon as the policy lands server-side. Until then,
     * returns an empty list (which the UI surfaces as "No pending requests").
     */
    suspend fun getLikesReceivedProfiles(userId: String): List<DatingProfile> = withContext(Dispatchers.IO) {
        val idsReq = Request.Builder()
            .url(
                "${SupabaseConfig.REST_BASE}/swipes" +
                    "?swiped_id=eq.$userId&action=in.(like,superlike)&select=swiper_id"
            )
            .get().build()
        val ids = SupabaseClient.http.newCall(idsReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val raw = resp.body?.string().orEmpty()
            idsListAdapter.fromJson(raw).orEmpty().mapNotNull { it["swiper_id"] as? String }
        }
        if (ids.isEmpty()) return@withContext emptyList()
        getProfilesByIds(ids)
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
            latitude = draft.latitude,
            longitude = draft.longitude,
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

    /** Blocks a user. Returns null on success, or an error message string. */
    suspend fun blockUser(blockerId: String, blockedId: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(
            mapOf("blocker_id" to blockerId, "blocked_id" to blockedId)
        )
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/blocked_users")
            .header("Prefer", "return=minimal,resolution=merge-duplicates")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) null else "Could not block user. Please try again."
        }
    }

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

    // ---------- Subscriptions ----------

    /** Checks if user has an active Pro subscription. Returns the expiry date or null. */
    suspend fun getActiveSubscription(userId: String): SubscriptionDto? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/subscriptions?user_id=eq.$userId&status=eq.active&order=expires_at.desc&limit=1")
            .get().build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val text = resp.body?.string().orEmpty()
            runCatching {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, SubscriptionDto::class.java)
                val adapter = SupabaseClient.moshi.adapter<List<SubscriptionDto>>(type)
                val list = adapter.fromJson(text).orEmpty()
                list.firstOrNull()
            }.getOrNull()
        }
    }

    /** Verifies a Google Play purchase with the server + saves subscription. Returns true on success. */
    suspend fun verifyPurchase(
        userId: String, productId: String, purchaseToken: String, orderId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf(
            "user_id" to userId,
            "product_id" to productId,
            "purchase_token" to purchaseToken,
            "order_id" to (orderId ?: "")
        ))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/verify-purchase")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            resp.isSuccessful && runCatching {
                val text = resp.body?.string().orEmpty()
                val parsed = SupabaseClient.moshi.adapter(Map::class.java).fromJson(text)
                parsed?.get("ok") == true
            }.getOrDefault(false)
        }
    }

    /** Convenience: is the user a Pro subscriber? */
    suspend fun isPro(userId: String): Boolean = withContext(Dispatchers.IO) {
        val sub = getActiveSubscription(userId) ?: return@withContext false
        val expiresAt = sub.expiresAt ?: return@withContext false
        // Parse ISO 8601 timestamp and compare with now
        runCatching {
            java.time.OffsetDateTime.parse(expiresAt).isAfter(java.time.OffsetDateTime.now())
        }.getOrDefault(false)
    }

    /**
     * Checks if an email is registered. Uses the `email_exists()` SECURITY
     * DEFINER RPC (bypasses RLS) so the anon key can check any email.
     * Used by the reset-password flow to refuse OTP for unregistered emails.
     */
    suspend fun emailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            val body = reqAdapter.toJson(mapOf("p_email" to cleanEmail))
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/rpc/email_exists")
                .post(body.toRequestBody(json))
                .build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext true // fail open — don't block users on API errors
                val text = resp.body?.string().orEmpty().trim()
                text.equals("true", ignoreCase = true)
            }
        } catch (e: Exception) {
            true // fail open — if the check fails, let the OTP flow proceed
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
            distanceKm = distanceKm ?: 0,
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
