package com.mallucupid.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.JsonClass

/**
 * Persists the current Supabase session (access + refresh tokens, user id, email)
 * in an EncryptedSharedPreferences. On app start, MainActivity restores the
 * session from here; if absent, it routes to the welcome screen.
 *
 * Legacy migration: if a previous plaintext `mallu_cupid_session` SharedPreferences
 * file exists, we read it once, migrate its content into the encrypted store, and
 * clear the plaintext file.
 */
object SessionManager {
    private const val PREF_FILE_ENCRYPTED = "mallu_cupid_session_encrypted"
    private const val PREF_FILE_LEGACY = "mallu_cupid_session"
    private const val KEY_SESSION = "session"
    private const val KEY_PENDING_FCM = "pending_fcm_token"

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
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREF_FILE_ENCRYPTED,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        migrateLegacy(context)
        // Restore in-memory token if a saved session exists
        current()?.let { SupabaseClient.accessToken = it.accessToken }
        // Flush any FCM token we received before the user was signed in
        flushPendingToken()
    }

    /** Migrate plaintext prefs (if present) into the encrypted store, then clear plaintext. */
    private fun migrateLegacy(context: Context) {
        runCatching {
            val legacy = context.getSharedPreferences(PREF_FILE_LEGACY, Context.MODE_PRIVATE)
            val raw = legacy.getString(KEY_SESSION, null) ?: return@runCatching
            if (prefs.getString(KEY_SESSION, null) == null) {
                prefs.edit().putString(KEY_SESSION, raw).apply()
            }
            legacy.edit().clear().apply()
        }
    }

    fun saveSession(session: SessionResponse) {
        val saved = SavedSession(
            accessToken = session.accessToken.orEmpty(),
            refreshToken = session.refreshToken,
            userId = session.user?.id,
            email = session.user?.email,
            expiresAt = session.expiresIn?.let { System.currentTimeMillis() + it * 1000 },
        )
        prefs.edit().putString(KEY_SESSION, sessionAdapter.toJson(saved)).apply()
        SupabaseClient.accessToken = saved.accessToken
        // Now that we have an access token, push any stashed FCM token to Supabase.
        flushPendingToken()
    }

    fun current(): SavedSession? {
        if (!::prefs.isInitialized) return null
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { sessionAdapter.fromJson(raw) }.getOrNull()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        SupabaseClient.accessToken = null
    }

    fun isLoggedIn(): Boolean = current()?.accessToken?.isNotBlank() == true

    // ---------- FCM pending token stash ----------

    fun stashPendingFcmToken(token: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PENDING_FCM, token).apply()
    }

    fun consumePendingFcmToken(): String? {
        if (!::prefs.isInitialized) return null
        val token = prefs.getString(KEY_PENDING_FCM, null) ?: return null
        prefs.edit().remove(KEY_PENDING_FCM).apply()
        return token
    }

    /**
     * If we have a stashed FCM token AND a live access token, push the FCM token to
     * Supabase now. Safe to call repeatedly. Returns true if a sync was attempted.
     */
    fun flushPendingToken(): Boolean {
        val token = consumePendingFcmToken() ?: return false
        val accessToken = SupabaseClient.accessToken ?: run {
            // Still no access token — re-stash so we can retry later.
            stashPendingFcmToken(token)
            return false
        }
        com.mallucupid.app.notifications.MalluCupidMessagingService
            .syncTokenToSupabaseBackground(token, accessToken)
        return true
    }
}
