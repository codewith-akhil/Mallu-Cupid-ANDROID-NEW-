package com.mallucupid.app.data.remote

import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.PromptItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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
    private val swipeStatusAdapter = moshi.adapter(SwipeStatusDto::class.java)

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
        // Photos may be local content:// or file:// URIs from the picker — they MUST be
        // uploaded to Supabase Storage first; storing a content:// URI in the DB
        // would persist a permission that dies with the picker activity and the
        // photo would never render anywhere outside this device.
        //
        // We upload each photo, collect the resulting public URL, and SKIP the
        // entry entirely on failure (so the user's profile is never saved with
        // a dead photo slot pointing at a content:// or file:// URI). Remote
        // https:// URLs are preserved as-is — they are already in Storage.
        val resolvedPhotos: List<String> = draft.photos.mapNotNull { source ->
            when {
                source.startsWith("content://") || source.startsWith("file://") -> {
                    runCatching { uploadPhoto(userId, android.net.Uri.parse(source)) }
                        .getOrNull()  // null = upload failed → skip this slot
                }
                source.startsWith("https://") -> source  // already in Storage
                else -> null  // unknown scheme → skip rather than persist garbage
            }
        }
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

        // Replace photos (delete + insert). Use the resolved (uploaded) URLs —
        // never the raw content:// / file:// URIs. Failed uploads were already
        // filtered out of `resolvedPhotos` above, so every entry here is a valid
        // Storage URL.
        SupabaseClient.http.newCall(
            Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/profile_photos?user_id=eq.$userId")
                .delete()
                .build()
        ).execute().close()
        resolvedPhotos.forEachIndexed { index, url ->
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

    /** Verifies a Google Play purchase with the server + saves subscription. Returns true on success.
     *  The user_id is no longer sent in the body — the verify-purchase edge function
     *  derives it from the JWT (auth.uid()) so a malicious client can't credit
     *  purchases to other users. */
    suspend fun verifyPurchase(
        userId: String, productId: String, purchaseToken: String, orderId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        // userId kept in signature for back-compat with callers; intentionally not
        // serialised into the body.
        val body = reqAdapter.toJson(mapOf(
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

    /**
     * Uploads a profile photo to Supabase Storage (bucket: profile-photos).
     * Path: {userId}/{timestamp}.jpg
     * Returns the public URL on success, or null on failure.
     *
     * Auth guard: the access token MUST be present — Storage buckets are RLS-
     * protected and the upload will 403 with the anon key. We also add an
     * explicit Authorization: Bearer header so we don't rely on the OkHttp
     * interceptor when the request is constructed from a freshly-acquired token.
     */
    suspend fun uploadPhoto(userId: String, photoUri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val accessToken = SupabaseClient.accessToken ?: return@withContext null
            val context = com.mallucupid.app.MalluCupidApp.appContext
            val inputStream = context.contentResolver.openInputStream(photoUri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val fileName = "${System.currentTimeMillis()}.jpg"
            val storagePath = "$userId/$fileName"
            val mimeType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
            val mediaType = mimeType.toMediaType()
            val requestBody = bytes.toRequestBody(mediaType)

            val req = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/profile-photos/$storagePath")
                .header("Content-Type", mimeType)
                .header("Authorization", "Bearer $accessToken")
                .header("x-upsert", "false")
                .post(requestBody)
                .build()

            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                // Return the public URL
                "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/profile-photos/$storagePath"
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Uploads a voice-message audio file (raw bytes from the in-app MediaRecorder)
     * to Supabase Storage (bucket: voice-messages).
     * Path: {userId}/{timestamp}.m4a
     * Returns the public URL on success, or null on failure.
     *
     * Auth guard: the access token MUST be present — Storage buckets are RLS-
     * protected and the upload will 403 with the anon key. We also add an
     * explicit Authorization: Bearer header so we don't rely on the OkHttp
     * interceptor when the request is constructed from a freshly-acquired token.
     */
    suspend fun uploadVoiceMessage(userId: String, audioBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val accessToken = SupabaseClient.accessToken ?: return@withContext null
            val fileName = "${System.currentTimeMillis()}.m4a"
            val storagePath = "$userId/$fileName"
            val mimeType = "audio/mp4"
            val mediaType = mimeType.toMediaType()
            val requestBody = audioBytes.toRequestBody(mediaType)

            val req = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/voice-messages/$storagePath")
                .header("Content-Type", mimeType)
                .header("Authorization", "Bearer $accessToken")
                .header("x-upsert", "false")
                .post(requestBody)
                .build()

            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/voice-messages/$storagePath"
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Uploads a chat media file (photo or video) to Supabase Storage (bucket: chat-media).
     * Path: {userId}/{timestamp}.{ext}
     * Returns the public URL on success, or null on failure.
     *
     * Auth guard: the access token MUST be present — Storage buckets are RLS-
     * protected and the upload will 403 with the anon key. We also add an
     * explicit Authorization: Bearer header so we don't rely on the OkHttp
     * interceptor when the request is constructed from a freshly-acquired token.
     */
    suspend fun uploadChatMedia(userId: String, mediaUri: android.net.Uri, isVideo: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val accessToken = SupabaseClient.accessToken ?: return@withContext null
            val context = com.mallucupid.app.MalluCupidApp.appContext
            val inputStream = context.contentResolver.openInputStream(mediaUri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val ext = if (isVideo) "mp4" else "jpg"
            val fileName = "${System.currentTimeMillis()}.$ext"
            val storagePath = "$userId/$fileName"
            val mimeType = context.contentResolver.getType(mediaUri)
                ?: if (isVideo) "video/mp4" else "image/jpeg"
            val mediaType = mimeType.toMediaType()
            val requestBody = bytes.toRequestBody(mediaType)

            val req = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/chat-media/$storagePath")
                .header("Content-Type", mimeType)
                .header("Authorization", "Bearer $accessToken")
                .header("x-upsert", "false")
                .post(requestBody)
                .build()

            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/chat-media/$storagePath"
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Countries ----------

    /**
     * Returns the current user's swipe quota + Pro status.
     * Calls the `get_swipe_status` RPC. The RPC derives the caller identity
     * from the JWT (auth.uid()) so we do NOT send a user_id parameter — the
     * server enforces ownership.
     */
    suspend fun getSwipeStatus(): SwipeStatusDto? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/rpc/get_swipe_status")
                .post("{}".toRequestBody(json))
                .build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string().orEmpty()
                swipeStatusAdapter.fromJson(text)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the total unread-message count across all matches for the given
     * user. Calls the `get_unread_message_count` SECURITY DEFINER RPC (the RPC
     * itself enforces auth.uid() — the [userId] parameter is retained in the
     * signature for caller clarity but is NOT sent in the body; the server
     * resolves the caller from the JWT).
     */
    suspend fun getUnreadMessageCount(userId: String): Int = withContext(Dispatchers.IO) {
        try {
            // userId intentionally not serialised — RPC enforces auth.uid().
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/rpc/get_unread_message_count")
                .post("{}".toRequestBody(json))
                .build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 0
                val text = resp.body?.string().orEmpty().trim()
                text.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Soft-deletes a message the current user owns (sender_id = auth.uid()).
     * The `messages_self_delete` RLS policy enforces ownership on the server,
     * so this call is safe against tampering with [messageId].
     */
    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages?id=eq.$messageId")
            .header("Prefer", "return=minimal")
            .delete()
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    /**
     * Edits a message's content (only the sender may update per RLS policy
     * `messages_self_update_content`). Only the content column is touched.
     */
    suspend fun editMessage(messageId: Long, newContent: String): Boolean = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("content" to newContent, "is_edited" to true))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages?id=eq.$messageId")
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Countries ----------

    suspend fun getCountries(): List<CountryDto> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/countries?select=id,name,iso_code,min_age&order=name.asc")
                .get().build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val type = Types.newParameterizedType(List::class.java, CountryDto::class.java)
                val adapter = moshi.adapter<List<CountryDto>>(type)
                adapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the minimum age for a country by ISO code. Falls back to 18 if
     * the country is not found or the call fails.
     */
    suspend fun getMinAgeForCountry(isoCode: String): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/countries?iso_code=eq.$isoCode&select=min_age")
                .get().build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 18
                val text = resp.body?.string().orEmpty()
                val type = Types.newParameterizedType(List::class.java, CountryDto::class.java)
                val adapter = moshi.adapter<List<CountryDto>>(type)
                val list = adapter.fromJson(text).orEmpty()
                list.firstOrNull()?.minAge ?: 18
            }
        } catch (e: Exception) {
            18
        }
    }

    // ---------- DTO → domain mapping ----------

    private fun SwipeDeckProfileDto.toDatingProfile(): DatingProfile {
        // No hardcoded fallbacks — every field uses `.orEmpty()` / `0` so a
        // profile with missing DB columns renders as empty strings instead of
        // fabricated defaults (e.g. "Anonymous", "Nearby", "Libra") that would
        // mislead users into thinking the data was real. Age uses the current
        // year so the math stays correct as time passes (no hardcoded 2026).
        val age = birthYear?.let { java.time.LocalDate.now().year - it } ?: 0
        return DatingProfile(
            id = id,
            name = name.orEmpty(),
            age = age,
            isVerified = isVerified ?: false,
            location = city.orEmpty(),
            distanceKm = distanceKm ?: 0,
            bio = bio.orEmpty(),
            photos = photos.orEmpty(),
            profession = profession.orEmpty(),
            lookingFor = lookingFor.orEmpty(),
            essentialsGender = gender.orEmpty(),
            astrologyStar = zodiac.orEmpty(),
            musicAnthem = anthem.orEmpty(),
            communicationStyle = communicationStyle.orEmpty(),
            loveStyle = loveStyle.orEmpty(),
            education = education.orEmpty(),
            drinking = drinking.orEmpty(),
            smoking = smoking.orEmpty(),
            workout = workout.orEmpty(),
            pets = pets.orEmpty(),
            prompts = prompts
                ?.map { PromptItem(it.question.orEmpty(), it.answer.orEmpty()) }
                ?.filter { it.question.isNotBlank() || it.answer.isNotBlank() }
                ?: emptyList(),
            interests = interests.orEmpty(),
        )
    }

    // ---------- Profile Options (DB-driven dropdowns) ----------

    suspend fun getProfileOptions(category: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/profile_options?category=eq.$category&select=value&order=sort_order.asc")
                .get().build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val type = Types.newParameterizedType(List::class.java, Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter<List<Map<String, Any?>>>(type).fromJson(resp.body?.string().orEmpty()).orEmpty()
                list.mapNotNull { it["value"] as? String }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
