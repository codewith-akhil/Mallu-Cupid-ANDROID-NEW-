package com.mallucupid.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mallucupid.app.MainActivity
import com.mallucupid.app.data.remote.SupabaseClient
import com.mallucupid.app.data.remote.SupabaseConfig
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class MalluCupidMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "mallu_cupid_notifications"
        const val CHANNEL_NAME = "Mallu Cupid Notifications"
        private const val TAG = "MalluFCM"

        /**
         * In-memory stash for an FCM token that arrived before the user signed in
         * (or before SessionManager.init / SupabaseClient.accessToken was set).
         *
         * Lifecycle:
         *  - Set by [onNewToken] when FCM fires before login completes.
         *  - Read + cleared by [flushPendingToken] once SessionManager has
         *    restored/established an access token.
         *
         * `@Volatile` because it is written by FCM's binder thread (onNewToken)
         * and read by the SessionManager init thread.
         */
        @Volatile
        var pendingToken: String? = null

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Matches, messages, super likes, and promotions"
                    enableVibration(true)
                    enableLights(true)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        /**
         * Called by SessionManager.init() (and after a successful sign-in).
         *
         * If we have a stashed [pendingToken] AND a live access token in
         * [SupabaseClient], spawn a daemon thread to PATCH the token to the
         * `profiles` table, then clear the stash. Safe to call repeatedly —
         * returns true if a sync was actually dispatched, false otherwise.
         */
        fun flushPendingToken(): Boolean {
            val token = pendingToken ?: return false
            val accessToken = SupabaseClient.accessToken ?: run {
                Log.i(TAG, "flushPendingToken: token stashed but no access token yet — deferring")
                return false
            }
            // Clear BEFORE the network call so a duplicate flush (e.g. triggered
            // by another sign-in) doesn't fire two PATCHes in parallel.
            pendingToken = null
            val worker = Thread {
                runCatching { syncTokenToSupabaseBlocking(token, accessToken) }
                    .onFailure { Log.e(TAG, "Background FCM sync failed", it) }
            }
            worker.name = "fcm-token-sync"
            worker.isDaemon = true
            worker.start()
            return true
        }

        /**
         * Performs the actual REST PATCH synchronously. Must be called off the
         * main thread. Returns true on HTTP 2xx, false otherwise. Logs the HTTP
         * status on both success and failure paths so the sync can be diagnosed
         * from logcat.
         */
        private fun syncTokenToSupabaseBlocking(token: String, accessToken: String): Boolean {
            val userId = extractUserIdFromJwt(accessToken) ?: run {
                Log.w(TAG, "Cannot sync FCM token: user id not found in JWT")
                return false
            }
            val json = "application/json; charset=utf-8".toMediaType()
            val body = """{"fcm_token":"$token"}""".toRequestBody(json)
            val req = Request.Builder()
                .url("${SupabaseConfig.REST_BASE}/profiles?id=eq.$userId")
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build()
            return try {
                SupabaseClient.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "FCM token sync failed: HTTP ${resp.code} for user $userId")
                        false
                    } else {
                        Log.i(TAG, "FCM token synced for user $userId (HTTP ${resp.code})")
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM token sync network failure for user $userId", e)
                false
            }
        }

        /**
         * Decodes the JWT payload and extracts the `sub` claim (Supabase user id).
         * Uses URL-safe Base64 decoding without depending on any JWT library.
         */
        private fun extractUserIdFromJwt(jwt: String): String? {
            return try {
                val parts = jwt.split(".")
                if (parts.size < 2) {
                    Log.w(TAG, "JWT has fewer than 2 segments — cannot extract sub")
                    return null
                }
                val payload = parts[1]
                val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
                val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)
                val json = String(decoded, Charsets.UTF_8)
                val subRegex = """"sub"\s*:\s*"([^"]+)"""".toRegex()
                subRegex.find(json)?.groupValues?.get(1)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode JWT sub", e)
                null
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken received")
        val accessToken = SupabaseClient.accessToken
        if (accessToken.isNullOrBlank()) {
            // Not signed in yet — stash in memory; SessionManager.init() or
            // saveSession() will flush it once we have an access token.
            Log.i(TAG, "No access token yet — stashing FCM token for later flush")
            pendingToken = token
        } else {
            // Already signed in — sync now on a daemon thread so FCM's binder
            // thread is not blocked.
            val worker = Thread {
                runCatching { syncTokenToSupabaseBlocking(token, accessToken) }
                    .onFailure { Log.e(TAG, "onNewToken direct sync failed", it) }
            }
            worker.name = "fcm-token-onnew"
            worker.isDaemon = true
            worker.start()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val type = data["type"] ?: "message"
        val title = data["title"] ?: "Mallu Cupid"
        val body = data["body"] ?: "You have a new notification"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", type)
            data["match_id"]?.let { putExtra("match_id", it) }
            data["profile_id"]?.let { putExtra("profile_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, type.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
