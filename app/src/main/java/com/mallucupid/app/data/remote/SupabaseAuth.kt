package com.mallucupid.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supabase Auth service.
 *
 * Implements the custom email-OTP flow:
 *   1. sendOtp(email) → POST /functions/v1/send-otp (sends 6-digit code via Resend)
 *   2. verifyOtp(email, code) → POST /functions/v1/verify-otp → returns { token_hash }
 *   3. exchangeToken(token_hash) → POST /auth/v1/verify → returns a real session
 *
 * NEVER uses Supabase's magic-link email — the code is the only thing emailed.
 */
object SupabaseAuth {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val reqAdapter = moshi.adapter(Map::class.java)
    private val sendRespAdapter = moshi.adapter(SendOtpResponse::class.java)
    private val verifyRespAdapter = moshi.adapter(VerifyOtpResponse::class.java)
    private val verifyTokenReqAdapter = moshi.adapter(VerifyTokenRequest::class.java)
    private val sessionRespAdapter = moshi.adapter(SessionResponse::class.java)

    /** Returns null on success, or an error message on failure. */
    suspend fun sendOtp(email: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/send-otp")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                sendRespAdapter.fromJson(text)?.error ?: "Could not send code (HTTP ${resp.code})"
            } else null
        }
    }

    /** Returns the token_hash on success, or null + error on failure. */
    suspend fun verifyOtp(email: String, code: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email, "code" to code))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/verify-otp")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val parsed = verifyRespAdapter.fromJson(text)
            if (!resp.isSuccessful || parsed?.ok != true) {
                null to (parsed?.error ?: "Verification failed (HTTP ${resp.code})")
            } else {
                parsed.token_hash to null
            }
        }
    }

    /** Exchanges the token_hash (from verify-otp) for a real Supabase session. */
    suspend fun exchangeToken(tokenHash: String): Pair<SessionResponse?, String?> = withContext(Dispatchers.IO) {
        val reqBody = verifyTokenReqAdapter.toJson(VerifyTokenRequest(tokenHash))
        val req = Request.Builder()
            .url("${SupabaseConfig.AUTH_BASE}/verify")
            .post(reqBody.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                null to "Session start failed (HTTP ${resp.code}): ${text.take(200)}"
            } else {
                sessionRespAdapter.fromJson(text) to null
            }
        }
    }

    /** Full flow: verify OTP + exchange for session. Returns access_token on success. */
    suspend fun signInWithOtp(email: String, code: String): Pair<String?, String?> {
        val (tokenHash, err) = verifyOtp(email, code)
        if (err != null || tokenHash == null) return null to err
        val (session, sessionErr) = exchangeToken(tokenHash)
        if (sessionErr != null || session?.accessToken == null) {
            return null to (sessionErr ?: "No access token in session response")
        }
        SupabaseClient.accessToken = session.accessToken
        SessionManager.saveSession(session)
        return session.accessToken to null
    }
}
