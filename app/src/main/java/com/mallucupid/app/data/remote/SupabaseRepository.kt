package com.mallucupid.app.data.remote

import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.PromptItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.Types

/**
 * Supabase data repository — thin REST/PostgREST wrapper for all tables.
 * Uses the in-memory access token set by SupabaseAuth. Returns null on error
 * (caller shows a Toast / falls back to local data).
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

    // ---------- Swipe deck ----------

    suspend fun getSwipeDeck(limit: Int = 10): List<DatingProfile> {
        val body = reqAdapter.toJson(mapOf("p_limit" to limit))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/rpc/get_swipe_deck")
            .post(body.toRequestBody(json))
            .build()
        return SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else deckAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty().map { it.toDatingProfile() }
        }
    }

    // ---------- Swipes ----------

    suspend fun recordSwipe(swiperId: String, swipedId: String, action: String): Boolean {
        val body = swipeAdapter.toJson(SwipeInsert(swiperId, swipedId, action))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/swipes")
            .header("Prefer", "return=minimal")
            .post(body.toRequestBody(json))
            .build()
        return SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Matches ----------

    suspend fun getMatches(userId: String): List<MatchDto> {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/matches" +
                  "?or=(user1_id.eq.$userId,user2_id.eq.$userId)&order=created_at.desc")
            .get().build()
        return SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else matchListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
        }
    }

    // ---------- Messages ----------

    suspend fun getMessages(matchId: String): List<MessageDto> {
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages" +
                  "?match_id=eq.$matchId&order=created_at.asc")
            .get().build()
        return SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else msgListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
        }
    }

    suspend fun sendMessage(
        matchId: String, senderId: String, receiverId: String,
        content: String, type: String = "text",
        mediaUrl: String? = null, audioDuration: String? = null,
        replyToId: Long? = null,
    ): MessageDto? {
        val ins = MessageInsert(matchId, senderId, receiverId, content, type, mediaUrl, audioDuration, replyToId)
        val body = msgInsertAdapter.toJson(ins)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages")
            .header("Prefer", "return=representation")
            .post(body.toRequestBody(json))
            .build()
        return SupabaseClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else {
                val list = msgListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
                list.firstOrNull()
            }
        }
    }

    suspend fun markMessageRead(messageId: Long): Boolean {
        val body = reqAdapter.toJson(mapOf("is_read" to true))
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/messages?id=eq.$messageId")
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody(json))
            .build()
        return SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
    }

    // ---------- Profile save (onboarding complete) ----------

    suspend fun saveProfile(userId: String, email: String, draft: OnboardingDraft): Boolean {
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
        )
        val body = profileAdapter.toJson(profile)
        val req = Request.Builder()
            .url("${SupabaseConfig.REST_BASE}/profiles?id=eq.$userId")
            .header("Prefer", "return=minimal,resolution=merge-duplicates")
            .patch(body.toRequestBody(json))
            .build()
        val profileOk = SupabaseClient.http.newCall(req).execute().use { it.isSuccessful }
        if (!profileOk) return false

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

        return true
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
