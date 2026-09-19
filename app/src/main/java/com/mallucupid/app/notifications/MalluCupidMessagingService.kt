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
import com.mallucupid.app.data.remote.SessionManager
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
         * Called from SessionManager.flushPendingToken() once a user signs in.
         * Schedules the FCM token sync on a daemon thread so the calling thread
         * (usually main or SessionManager.init) is never blocked.
         */
        fun syncTokenToSupabaseBackground(token: String, accessToken: String) {
            val worker = Thread {
                runCatching { syncTokenToSupabaseBlocking(token, accessToken) }
                    .onFailure { Log.e(TAG, "Background FCM sync failed", it) }
            }
            worker.name = "fcm-token-sync"
            worker.isDaemon = true
            worker.start()
        }

        /**
         * Performs the actual REST PATCH synchronously. Must be called off the
         * main thread. Public so SessionManager can call it on its own worker.
         */
        fun syncTokenToSupabaseBlocking(token: String, accessToken: String) {
            val userId = extractUserIdFromJwt(accessToken) ?: run {
                Log.w(TAG, "Cannot sync FCM token: user id not found in JWT")
                return
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
            SupabaseClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "FCM token sync failed: HTTP ${resp.code}")
                } else {
                    Log.d(TAG, "FCM token synced for user $userId")
                }
            }
        }

        /**
         * Called by SessionManager after a successful login — re-flushes any
         * stashed pending token. Safe to call repeatedly.
         */
        fun flushPendingToken() {
            SessionManager.flushPendingToken()
        }

        private fun extractUserIdFromJwt(jwt: String): String? {
            return try {
                val parts = jwt.split(".")
                if (parts.size < 2) return null
                val padded = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
                val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)
                val json = String(decoded, Charsets.UTF_8)
                val subRegex = """"sub"\s*:\s*"([^"]+)"""".toRegex()
                subRegex.find(json)?.groupValues?.get(1)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode JWT sub", e); null
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken received")
        val accessToken = SupabaseClient.accessToken
        if (accessToken.isNullOrBlank()) {
            // Not signed in yet — stash the token; SessionManager will flush it on login.
            Log.d(TAG, "No access token yet — stashing FCM token for later")
            SessionManager.stashPendingFcmToken(token)
        } else {
            // Already signed in — sync now on a daemon thread so FCM's binder thread is not blocked.
            syncTokenToSupabaseBackground(token, accessToken)
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
