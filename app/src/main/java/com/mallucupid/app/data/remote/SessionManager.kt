package com.mallucupid.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.JsonClass

/**
 * Persists the current Supabase session (access + refresh tokens, user id, email)
 * in an encrypted SharedPreferences. On app start, MainActivity restores the
 * session from here; if absent, it routes to the welcome screen.
 */
object SessionManager {
    private lateinit var prefs: SharedPreferences
    private val sessionAdapter = moshi.adapter(SavedSession::class.java)

    @JsonClass(generateAdapter = true)
    data class SavedSession(
        val accessToken: String,
        val refreshToken: String? = null,
        val userId: String? = null,
        val email: String? = null,
        val expiresAt: Long? = null,
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mallu_cupid_session", Context.MODE_PRIVATE)
        // Restore in-memory token if a saved session exists
        current()?.let { SupabaseClient.accessToken = it.accessToken }
    }

    fun saveSession(session: SessionResponse) {
        val saved = SavedSession(
            accessToken = session.accessToken.orEmpty(),
            refreshToken = session.refreshToken,
            userId = session.user?.id,
            email = session.user?.email,
            expiresAt = session.expiresIn?.let { System.currentTimeMillis() + it * 1000 },
        )
        prefs.edit().putString("session", sessionAdapter.toJson(saved)).apply()
        SupabaseClient.accessToken = saved.accessToken
    }

    fun current(): SavedSession? {
        if (!::prefs.isInitialized) return null
        val raw = prefs.getString("session", null) ?: return null
        return runCatching { sessionAdapter.fromJson(raw) }.getOrNull()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        SupabaseClient.accessToken = null
    }

    fun isLoggedIn(): Boolean = current()?.accessToken?.isNotBlank() == true
}
