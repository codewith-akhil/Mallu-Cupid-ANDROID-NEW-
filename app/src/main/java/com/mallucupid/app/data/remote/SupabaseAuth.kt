package com.mallucupid.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Supabase Auth service — password + email OTP verification.
 *
 * Flow:
 *   SignUp:  signUp(email, password) → sendOtp(email) → verifyOtp(email, code) → signInWithPassword(email, password)
 *   SignIn:  signInWithPassword(email, password) → session
 *   Reset:   sendOtp(email) → verifyOtp(email, code) → resetPassword(email, newPassword)
 *
 * EVERY network call is wrapped in try/catch so that IOExceptions (timeout,
 * DNS failure, connection refused) return a user-friendly error message
 * instead of crashing the coroutine and leaving the loading spinner on forever.
 */
object SupabaseAuth {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val reqAdapter = moshi.adapter(Map::class.java)
    private val verifyRespAdapter = moshi.adapter(VerifyOtpResponse::class.java)
    private val sessionRespAdapter = moshi.adapter(SessionResponse::class.java)

    // ── SignUp: creates a Supabase auth user (email unconfirmed) ──────────────

    /** Creates a user. Returns null on success, or a user-friendly error message. */
    suspend fun signUp(email: String, password: String): String? = withContext(Dispatchers.IO) {
        try {
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
                        text.contains("invalid", ignoreCase = true) -> "Wrong email or password."
                        else -> "Wrong email or password."
                    }
                } else null
            }
        } catch (e: IOException) {
            "No internet. Check your connection and try again."
        } catch (e: Exception) {
            "Wrong email or password."
        }
    }

    // ── SignIn: password-based login (no OTP needed) ─────────────────────────

    /** Returns access_token on success, or null + user-friendly error. */
    suspend fun signInWithPassword(email: String, password: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
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
                        text.contains("Invalid login", ignoreCase = true) -> null to "Wrong email or password."
                        text.contains("Email not confirmed", ignoreCase = true) -> null to "Please verify your email first."
                        else -> null to "Wrong email or password."
                    }
                } else {
                    val session = sessionRespAdapter.fromJson(text)
                    if (session?.accessToken != null) {
                        SupabaseClient.accessToken = session.accessToken
                        SessionManager.saveSession(session)
                        session.accessToken to null
                    } else {
                        null to "Wrong email or password."
                    }
                }
            }
        } catch (e: IOException) {
            null to "No internet. Check your connection and try again."
        } catch (e: Exception) {
            null to "Wrong email or password."
        }
    }

    // ── OTP: send code via edge function → Resend SMTP ────────────────────────

    /**
     * Sends an OTP code to the email. Returns null on success, or an error.
     * If the edge function returns a dev_code (when ALLOW_DEV_CODE=true and
     * Resend is not configured), the code is stored in [lastDevCode] so the
     * caller can surface it for testing.
     */
    @Volatile
    var lastDevCode: String? = null
        private set

    suspend fun sendOtp(email: String): String? = withContext(Dispatchers.IO) {
        try {
            lastDevCode = null
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
                        else -> "Couldn't send the code. Please try again."
                    }
                } else {
                    // Check for dev_code in the response (testing mode — Resend not configured)
                    try {
                        val parsed = moshi.adapter(Map::class.java).fromJson(text)
                        val devCode = parsed?.get("dev_code") as? String
                        if (devCode != null) {
                            lastDevCode = devCode
                        }
                    } catch (_: Exception) { /* not JSON or no dev_code — fine */ }
                    null
                }
            }
        } catch (e: IOException) {
            "No internet. Check your connection and try again."
        } catch (e: Exception) {
            "Couldn't send the code. Please try again."
        }
    }

    // ── OTP: verify code + confirm email ─────────────────────────────────────

    /** Verifies the OTP code. Returns null on success, or a user-friendly error. */
    suspend fun verifyOtp(email: String, code: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = reqAdapter.toJson(mapOf("email" to email, "code" to code))
            val req = Request.Builder()
                .url("${SupabaseConfig.FUNCTIONS_BASE}/verify-otp")
                .post(body.toRequestBody(json))
                .build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val parsed = try { verifyRespAdapter.fromJson(text) } catch (_: Exception) { null }
                if (!resp.isSuccessful || parsed?.ok != true) {
                    when {
                        parsed?.error?.contains("expired", ignoreCase = true) == true -> "Code expired. Request a new one."
                        parsed?.error?.contains("Invalid", ignoreCase = true) == true -> "Wrong code. Please try again."
                        parsed?.error?.contains("Too many", ignoreCase = true) == true -> "Too many attempts. Please request a new code."
                        else -> "Wrong code. Please try again."
                    }
                } else null
            }
        } catch (e: IOException) {
            "No internet. Check your connection and try again."
        } catch (e: Exception) {
            "Wrong code. Please try again."
        }
    }

    // ── Reset Password: update password after OTP verification ───────────────

    /** Resets the user's password. Returns null on success, or a user-friendly error. */
    suspend fun resetPassword(email: String, newPassword: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = reqAdapter.toJson(mapOf("email" to email, "new_password" to newPassword))
            val req = Request.Builder()
                .url("${SupabaseConfig.FUNCTIONS_BASE}/reset-password")
                .post(body.toRequestBody(json))
                .build()
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    "Couldn't update your password. Please try again."
                } else null
            }
        } catch (e: IOException) {
            "No internet. Check your connection and try again."
        } catch (e: Exception) {
            "Couldn't update your password. Please try again."
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
