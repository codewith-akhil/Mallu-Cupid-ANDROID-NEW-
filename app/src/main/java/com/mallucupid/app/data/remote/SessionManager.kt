package com.mallucupid.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mallucupid.app.data.remote.SupabaseClient.moshi
import com.squareup.moshi.JsonClass

/**
 * Persists the current Supabase session (access + refresh tokens, user id, email)
 * in an EncryptedSharedPreferences backed by the Android Keystore. On app start,
 * MainActivity restores the session from here; if absent, it routes to the
 * welcome screen.
 *
 * Legacy migration: if a previous plaintext `mallu_cupid_session` SharedPreferences
 * file exists, we read it once, migrate its content into the encrypted store, and
 * clear the plaintext file.
 *
 * Keystore fallback: if `MasterKeys.getOrCreate(...)` throws (rare — broken
 * keystore on rooted ROMs, MDM-locked keychain, hardware attestation failure),
 * we fall back to plain `MODE_PRIVATE` SharedPreferences so the app still works
 * (the user is never blocked at the login screen). The failure is logged SEVERE
 * via `Log.e` so we can spot devices that aren't encrypting tokens at rest.
 *
 * NOTE: We use the deprecated `MasterKeys` API rather than `MasterKey.Builder`
 * because the latter has an unresolved reference issue in this Kotlin/AGP setup
 * (the `MasterKey` interface is not resolvable on the compile classpath despite
 * the androidx.security.crypto dependency being present). `MasterKeys` is
 * deprecated but stable and still ships in security-crypto 1.1.0-alpha06.
 */
object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREF_FILE_ENCRYPTED = "mallu_cupid_session_enc"
    private const val PREF_FILE_LEGACY = "mallu_cupid_session"
    private const val KEY_SESSION = "session"

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
        prefs = try {
            // MasterKeys.getOrCreate is the deprecated-but-stable API. It creates
            // an AES256-GCM master key alias in the Android Keystore (non-rotatable,
            // which is what we want for a session store).
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREF_FILE_ENCRYPTED,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // Keystore is broken on this device — fall back to MODE_PRIVATE so the
            // app still functions. Tokens are NOT encrypted at rest on this device.
            Log.e(
                TAG,
                "EncryptedSharedPreferences unavailable — falling back to MODE_PRIVATE. " +
                    "Session tokens are NOT encrypted at rest on this device.",
                t
            )
            context.getSharedPreferences(PREF_FILE_ENCRYPTED, Context.MODE_PRIVATE)
        }

        migrateLegacy(context)
        // Restore in-memory token if a saved session exists
        current()?.let { SupabaseClient.accessToken = it.accessToken }
        // Flush any FCM token we received before the user was signed in. This must
        // run AFTER `current()?.let { ... }` so the access token is restored first.
        com.mallucupid.app.notifications.MalluCupidMessagingService.flushPendingToken()
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
        }.onFailure { Log.w(TAG, "Legacy prefs migration failed", it) }
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
        // Now that we have a fresh access token, push any stashed FCM token to
        // Supabase. Safe no-op if nothing is stashed.
        com.mallucupid.app.notifications.MalluCupidMessagingService.flushPendingToken()
    }

    fun current(): SavedSession? {
        if (!::prefs.isInitialized) return null
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { sessionAdapter.fromJson(raw) }
            .onFailure { Log.w(TAG, "Failed to parse saved session JSON", it) }
            .getOrNull()
    }

    fun signOut() {
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
        SupabaseClient.accessToken = null
    }

    fun isLoggedIn(): Boolean = current()?.accessToken?.isNotBlank() == true
}
