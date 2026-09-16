package com.mallucupid.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supabase Auth service — password + email OTP verification.
 *
 * Flow:
 *   SignUp:  signUp(email, password) → sendOtp(email) → verifyOtp(email, code) → signInWithPassword(email, password)
 *   SignIn:  signInWithPassword(email, password) → session
 *   Reset:   sendOtp(email) → verifyOtp(email, code) → resetPassword(email, newPassword)
 */
object SupabaseAuth {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val reqAdapter = moshi.adapter(Map::class.java)
    private val sendRespAdapter = moshi.adapter(SendOtpResponse::class.java)
    private val verifyRespAdapter = moshi.adapter(VerifyOtpResponse::class.java)
    private val sessionRespAdapter = moshi.adapter(SessionResponse::class.java)

    // ── SignUp: creates a Supabase auth user (email unconfirmed) ──────────────

    /** Creates a user. Returns null on success, or a user-friendly error message. */
    suspend fun signUp(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email, "password" to password))
        val req = Request.Builder()
            .url("${SupabaseConfig.AUTH_BASE}/signup")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                when {
                    text.contains("already registered", ignoreCase = true) -> "This email is already registered. Try signing in."
                    text.contains("weak", ignoreCase = true) -> "Password is too weak. Use at least 8 characters with a number."
                    else -> "Could not create account. Please try again."
                }
            } else null
        }
    }

    // ── SignIn: password-based login (no OTP needed) ─────────────────────────

    /** Returns access_token on success, or null + user-friendly error. */
    suspend fun signInWithPassword(email: String, password: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email, "password" to password))
        val req = Request.Builder()
            .url("${SupabaseConfig.AUTH_BASE}/token?grant_type=password")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                when {
                    text.contains("Invalid login", ignoreCase = true) -> null to "Invalid email or password."
                    text.contains("Email not confirmed", ignoreCase = true) -> null to "Please verify your email first."
                    else -> null to "Could not sign in. Please try again."
                }
            } else {
                val session = sessionRespAdapter.fromJson(text)
                if (session?.accessToken != null) {
                    SupabaseClient.accessToken = session.accessToken
                    SessionManager.saveSession(session)
                    session.accessToken to null
                } else {
                    null to "Could not sign in. Please try again."
                }
            }
        }
    }

    // ── OTP: send code via Resend SMTP ────────────────────────────────────────

    /** Returns null on success, or a user-friendly error message. */
    suspend fun sendOtp(email: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/send-otp")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                when {
                    text.contains("Too many", ignoreCase = true) -> "Too many attempts. Please wait a few minutes."
                    else -> "Could not send verification code. Please try again."
                }
            } else null
        }
    }

    // ── OTP: verify code + confirm email ─────────────────────────────────────

    /** Verifies the OTP code. Returns null on success, or a user-friendly error. */
    suspend fun verifyOtp(email: String, code: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email, "code" to code))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/verify-otp")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val parsed = verifyRespAdapter.fromJson(text)
            if (!resp.isSuccessful || parsed?.ok != true) {
                when {
                    parsed?.error?.contains("expired", ignoreCase = true) == true -> "Code expired. Please request a new one."
                    parsed?.error?.contains("Invalid", ignoreCase = true) == true -> "Invalid code. Please try again."
                    parsed?.error?.contains("Too many", ignoreCase = true) == true -> "Too many attempts. Please request a new code."
                    else -> "Verification failed. Please try again."
                }
            } else null
        }
    }

    // ── Reset Password: update password after OTP verification ───────────────

    /** Resets the user's password. Returns null on success, or a user-friendly error. */
    suspend fun resetPassword(email: String, newPassword: String): String? = withContext(Dispatchers.IO) {
        val body = reqAdapter.toJson(mapOf("email" to email, "new_password" to newPassword))
        val req = Request.Builder()
            .url("${SupabaseConfig.FUNCTIONS_BASE}/reset-password")
            .post(body.toRequestBody(json))
            .build()
        SupabaseClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                "Could not update password. Please try again."
            } else null
        }
    }

    // ── Password validation rules ─────────────────────────────────────────────

    /** Returns a list of unmet password requirements (empty = valid). */
    fun validatePassword(password: String): List<PasswordRule> {
        val rules = mutableListOf<PasswordRule>()
        rules.add(PasswordRule("At least 8 characters", password.length >= 8))
        rules.add(PasswordRule("At most 128 characters", password.length <= 128))
        rules.add(PasswordRule("At least 1 uppercase letter", password.any { it.isUpperCase() }))
        rules.add(PasswordRule("At least 1 number", password.any { it.isDigit() }))
        return rules
    }

    /** Returns true if all password rules pass. */
    fun isPasswordValid(password: String): Boolean = validatePassword(password).all { it.passed }
}

data class PasswordRule(val label: String, val passed: Boolean)
