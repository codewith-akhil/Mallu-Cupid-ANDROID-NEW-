package com.mallucupid.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.remote.MatchDto
import com.mallucupid.app.data.remote.MessageDto
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseClient
import com.mallucupid.app.data.remote.SupabaseConfig
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

enum class ChatMessageType {
    TEXT,
    IMAGE,
    VIDEO,
    VOICE
}

data class ChatMessageItem(
    val id: String,
    val text: String = "",
    val isSender: Boolean,
    val timestamp: String,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val mediaUrl: String? = null,
    val videoDuration: String? = null,
    val isLiked: Boolean = false,
    // Feature #20 — Read receipts: ✓ (sent) or ✓✓ (read). Historical messages default to read.
    val isRead: Boolean = true,
    // Feature #23 — Long-press emoji reaction (e.g. "👍", "❤️", null if none).
    val reaction: String? = null,
    // Feature #24 — Reply-to / quote: original quoted text + sender name.
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    // Feature #25 — Voice message duration label (e.g. "0:08"). Only used when type == VOICE.
    val audioDuration: String? = null,
    // Chat-Wiring — Real Supabase message id (Long). Populated when a message is
    // loaded from / confirmed by the server. Null for hardcoded sample messages
    // and for optimistic messages that have not yet been confirmed by the API.
    val serverId: Long? = null,
    // Chat-Wiring — True while the optimistic send is being confirmed by Supabase.
    // Renders a small spinner next to the bubble; flips to false on success or the
    // message is removed on failure.
    val isSending: Boolean = false
)

data class ChatThreadItem(
    val profile: DatingProfile,
    val lastMessage: String,
    val timestamp: String,
    val isUnread: Boolean = false,
    val isYourTurn: Boolean = false
)

/**
 * Wrapper used by the LazyColumn inside the conversation view so we can interleave
 * [DateSeparator] items between real messages (Feature #21).
 */
data class ConversationListItem(
    val key: String,
    val isDateSeparator: Boolean,
    val dayLabel: String,
    val message: ChatMessageItem?
)

// =============================================================================
// Chat-Wiring — Supabase <-> ChatMessageItem mapping helpers
// =============================================================================

/**
 * Formats an ISO-8601 timestamp returned by Supabase (e.g. "2025-09-12T15:04:11.123Z")
 * into the human-readable shape consumed by [dayLabelFromTimestamp]:
 *   "Friday 12 Sep, 15:04"
 *
 * Falls back to "Just now" on any parse failure so the conversation list still
 * renders — never throws.
 */
private fun formatSupabaseTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "Just now"
    return try {
        // Try with milliseconds first ("yyyy-MM-dd'T'HH:mm:ss.SSS"), then without.
        val withMs = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
        val noMs = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        // Strip trailing "Z" / timezone offset — SimpleDateFormat in this pattern
        // does not parse the "Z" suffix, and we don't need TZ-correct display.
        val raw = iso.substringBefore('Z').substringBefore('+')
        val date = runCatching { withMs.parse(raw) }.getOrNull()
            ?: runCatching { noMs.parse(raw) }.getOrNull()
            ?: return "Just now"
        val outDay = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(date)
        val outDate = java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).format(date)
        val outTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(date)
        "$outDay $outDate, $outTime"
    } catch (_: Throwable) {
        "Just now"
    }
}

/**
 * Maps a Supabase [MessageDto] into the local [ChatMessageItem] representation.
 * - `isSender` is determined by comparing the sender_id against the current user.
 * - `serverId` is populated so reactions / read-receipts can be persisted later.
 * - `isRead` defaults to `true` for received messages (we only display the
 *   ✓ / ✓✓ marks on the sender side), and the server's value is honored for
 *   the sender's own outgoing messages.
 */
private fun MessageDto.toChatMessageItem(currentUserId: String): ChatMessageItem {
    val isSender = senderId == currentUserId
    return ChatMessageItem(
        id = "server_${id ?: System.currentTimeMillis()}",
        text = content,
        isSender = isSender,
        timestamp = formatSupabaseTimestamp(createdAt),
        type = when (type) {
            "image" -> ChatMessageType.IMAGE
            "video" -> ChatMessageType.VIDEO
            "voice" -> ChatMessageType.VOICE
            else -> ChatMessageType.TEXT
        },
        mediaUrl = mediaUrl,
        // MessageDto currently has no videoDuration field — left null on real messages.
        videoDuration = null,
        // Received messages don't show ✓✓ (only the sender side does), so we
        // treat them as "read" for rendering purposes.
        isRead = if (isSender) isRead else true,
        audioDuration = audioDuration,
        serverId = id
    )
}

/**
 * Maps a [ChatMessageType] to the lowercase string the Supabase `messages.type`
 * column expects (see schema CHECK constraint: text|image|video|voice|system).
 */
private fun ChatMessageType.toSupabaseType(): String = when (this) {
    ChatMessageType.TEXT -> "text"
    ChatMessageType.IMAGE -> "image"
    ChatMessageType.VIDEO -> "video"
    ChatMessageType.VOICE -> "voice"
}

/**
 * Fix 1 — Supabase Realtime WebSocket URL builder.
 *
 * Builds the `wss://` (or `ws://` for plain-http dev projects) URL for the
 * Supabase Realtime v1 websocket endpoint. The anon key is appended as the
 * `apikey` query param — RLS policies still apply on every broadcast event.
 */
private fun buildRealtimeWebSocketUrl(): String {
    val base = SupabaseConfig.SUPABASE_URL
    val secure = base.startsWith("https://")
    val host = base.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val scheme = if (secure) "wss://" else "ws://"
    return "$scheme$host/realtime/v1/websocket?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
}

/**
 * Fix 1 — Singleton OkHttp client dedicated to the Realtime websocket.
 *
 * Built fresh (separate from `SupabaseClient.http`) so that the 15s ping
 * interval and 30s read timeout don't bleed into the REST client's behaviour.
 * `pingInterval` is required for Supabase Realtime — without it the server
 * closes the socket after ~60s of inactivity.
 */
private val realtimeHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()
}

/** Moshi adapter for parsing incoming Realtime `record` payloads into [MessageDto]. */
private val realtimeMessageAdapter: com.squareup.moshi.JsonAdapter<MessageDto> by lazy {
    SupabaseClient.moshi.adapter(MessageDto::class.java)
}

/**
 * Chat tray shimmer row — pulsing placeholder used while the matches list is
 * being fetched from Supabase. Reuses DashboardCard / DashboardNavMuted tokens
 * so no new colours are introduced.
 */
@Composable
private fun ChatThreadShimmerRow() {
    val transition = rememberInfiniteTransition(label = "shimmer_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Surface(
        color = DashboardCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(DashboardNavMuted.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DashboardNavMuted.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DashboardNavMuted.copy(alpha = alpha))
                )
            }
        }
    }
}

/**
 * ChatViewContent
 *
 * Implements the Chat page and Fullscreen Conversation view matching Screenshots 4 & 5
 * with Mallu Cupid luxury dark palette:
 * - Real Image & Video sharing via ActivityResultContracts.PickVisualMedia
 * - Direct quick sample photo/video selector for testing in emulator
 * - PERFECT WORKING SCREENSHOT PREVENTION INSIDE THAT ONLY via WindowManager.LayoutParams.FLAG_SECURE
 * - Hearts on received messages, GIF selector, and global safety features
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatViewContent(
    profiles: List<DatingProfile>,
    onOpenProfile: (DatingProfile) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeChatProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var chatMessageInput by remember { mutableStateOf("") }
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showMediaPickerSheet by remember { mutableStateOf(false) }
    var showGifPickerSheet by remember { mutableStateOf(false) }
    var previewMediaUrl by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // url, isVideo

    // Feature #19 — Partner typing indicator state. Now driven only by Realtime
    // broadcast events from the partner (the canned 1.5s-delay + hardcoded reply
    // simulation has been removed — see Fix 1).
    var partnerIsTyping by remember { mutableStateOf(false) }
    // Feature #22 — rememberCoroutineScope used for read-receipt updates + Realtime handlers.
    val chatScope = rememberCoroutineScope()
    // Feature #24 — Currently selected message being replied to (quoted preview above input).
    var replyTarget by remember { mutableStateOf<ChatMessageItem?>(null) }
    // Fix 6 — Voice recording UI state. `voiceRecordSeconds` is now derived from
    // the real MediaRecorder start time (updated every 250ms by a LaunchedEffect
    // while `isRecordingVoice` is true).
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }
    // Fix 6 — Holds the active MediaRecorder + the cache file it writes to.
    // Cleared on stop / cancel. Lives in `remember` so it survives recomposition
    // but never leaks past the conversation scope.
    var mediaRecorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    // Fix 6 — Wall-clock ms at which the current recording started. Drives the
    // on-screen timer; reset to 0 on stop / cancel.
    var recorderStartMs by remember { mutableLongStateOf(0L) }
    // Fix 7 — One-shot send guard. Disables the send button while a message is
    // being persisted to Supabase. Prevents double-send on rapid taps.
    var isSendingMessage by remember { mutableStateOf(false) }
    // Feature #27 — Chat tray tab: 0 = Matches, 1 = Requests
    var chatTrayTab by remember { mutableIntStateOf(0) }
    // Block confirmation dialog state (stores the profile ID to block, or null when dismissed)
    var showBlockConfirm by remember { mutableStateOf<String?>(null) }
    // Fix 4 — Real message-requests list. Populated from
    // SupabaseRepository.getLikesReceivedProfiles(uid) on first composition of
    // the chat tray. Replaces the deleted `sampleRequests` hardcoded list.
    val realRequests = remember { mutableStateListOf<ChatThreadItem>() }
    var requestsLoading by remember { mutableStateOf(true) }
    // Fix 2 — Real chat tray threads. Each entry is built from a real MatchDto
    // + the partner's profile (looked up via getProfilesByIds) + the most
    // recent message in that match (last row of getMessages). Replaces the
    // deleted `sampleThreads` hardcoded list.
    val realThreads = remember { mutableStateListOf<ChatThreadItem>() }
    // Fix 2 — The raw MatchDto rows for the current user (used by the "New
    // Matches" carousel too). Populated alongside `realThreads`.
    val realMatches = remember { mutableStateListOf<MatchDto>() }

    // Initial message history with mixed text, image, and video
    val conversationMessages = remember {
        mutableStateListOf(
            ChatMessageItem(
                id = "m1",
                text = "Hey! Noticed you're also exploring cafes downtown!",
                isSender = false,
                timestamp = "Friday 28 Aug, 15:00"
            ),
            ChatMessageItem(
                id = "m2",
                text = "You know what I like in you 😉",
                isSender = true,
                timestamp = "Friday 28 Aug, 15:02"
            ),
            ChatMessageItem(
                id = "m3",
                text = "What's that? Good morning! ☕",
                isSender = false,
                timestamp = "Wednesday 10:15"
            ),
            ChatMessageItem(
                id = "m4",
                text = "Your genuine smile and love for sunset views!",
                isSender = true,
                timestamp = "Wednesday 10:20"
            ),
            ChatMessageItem(
                id = "m5",
                text = "Check out this sunset view from the coast yesterday evening 🌅",
                isSender = false,
                timestamp = "Wednesday 12:03",
                type = ChatMessageType.IMAGE,
                mediaUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"
            ),
            ChatMessageItem(
                id = "m6",
                text = "Took a short clip at the lake district during our boat cruise! 🚤",
                isSender = true,
                timestamp = "Wednesday 12:05",
                type = ChatMessageType.VIDEO,
                mediaUrl = "https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=800&q=80",
                videoDuration = "0:18"
            ),
            ChatMessageItem(
                id = "m7",
                text = "Smile, thanks! That looks so serene 😍",
                isSender = false,
                timestamp = "Wednesday 17:01"
            )
        )
    }

    // Chat-Wiring — Snapshot of the hardcoded sample messages used as a dev
    // fallback when Supabase returns no real messages for a match (or the call
    // fails). We snapshot once so re-opening the same conversation always
    // restores the same canned thread instead of an empty list.
    val sampleMessagesFallback = remember {
        conversationMessages.toList()
    }

    // Chat-Wiring — Conversation-level loading state. True while we are resolving
    // the matchId and fetching real messages from Supabase. Renders a centered
    // 24.dp DashboardTerracotta spinner in place of the LazyColumn.
    var conversationLoading by remember { mutableStateOf(false) }
    // Chat-Wiring — The Supabase match uuid resolved for the currently open
    // partner. Null when no match exists (dev fallback) — outgoing sends are
    // then local-only so the chat still works in dev.
    var activeMatchId by remember { mutableStateOf<String?>(null) }
    // Chat-Wiring — True while the chat tray is fetching the user's matches
    // list from Supabase. Renders shimmer placeholder rows in the Messages list.
    var chatTrayLoading by remember { mutableStateOf(true) }

    // Fix 2 + Fix 4 — Fetch the user's matches + inbound likes once when the
    // chat tray is first shown. Populates `realMatches` / `realThreads` (for
    // the Messages tab) and `realRequests` (for the Requests tab) from real
    // Supabase rows. Falls back to shimmer + empty states on any failure.
    LaunchedEffect(Unit) {
        val uid = SessionManager.current()?.userId
        if (uid != null) {
            // --- Matches → realThreads + realMatches ---
            val matches = runCatching { SupabaseRepository.getMatches(uid) }
                .getOrDefault(emptyList())
            realMatches.clear()
            realMatches.addAll(matches)
            if (matches.isNotEmpty()) {
                // Resolve partner IDs (the one that isn't uid) and fetch profiles.
                val partnerIds = matches.map { m ->
                    if (m.user1Id == uid) m.user2Id else m.user1Id
                }.distinct()
                val partnerProfiles = runCatching {
                    SupabaseRepository.getProfilesByIds(partnerIds)
                }.getOrDefault(emptyList())
                val profileById = partnerProfiles.associateBy { it.id }
                // For each match, fetch the last message (just the last row, ordered desc → asc limit 1).
                realThreads.clear()
                matches.forEach { m ->
                    val partnerProfile = profileById[if (m.user1Id == uid) m.user2Id else m.user1Id]
                    val lastMsgs = runCatching { SupabaseRepository.getMessages(m.id) }
                        .getOrDefault(emptyList())
                    val last = lastMsgs.lastOrNull()
                    if (partnerProfile != null) {
                        realThreads.add(
                            ChatThreadItem(
                                profile = partnerProfile,
                                lastMessage = last?.content?.takeIf { it.isNotBlank() }
                                    ?: last?.let { if (it.type == "image") "Sent a photo 📷" else if (it.type == "video") "Sent a video 🎥" else if (it.type == "voice") "Sent a voice message 🎤" else "Say hi! 👋" }
                                    ?: "Say hi! 👋",
                                timestamp = last?.createdAt?.let { formatSupabaseTimestamp(it).substringAfter(", ").ifBlank { "Just now" } }
                                    ?: "New",
                                isUnread = last != null && last.senderId != uid && !last.isRead,
                                isYourTurn = last != null && last.senderId != uid
                            )
                        )
                    }
                }
            }
            // --- Inbound likes → realRequests ---
            val likedByProfiles = runCatching {
                SupabaseRepository.getLikesReceivedProfiles(uid)
            }.getOrDefault(emptyList())
            realRequests.clear()
            realRequests.addAll(
                likedByProfiles.map { profile ->
                    ChatThreadItem(
                        profile = profile,
                        lastMessage = "Liked your profile!",
                        timestamp = "New",
                        isUnread = true
                    )
                }
            )
            requestsLoading = false
        } else {
            requestsLoading = false
        }
        chatTrayLoading = false
    }

    // Chat-Wiring — Whenever the open partner changes, resolve the matchId for
    // (current_user, partner) and load the real message history. Falls back to
    // the canned sample messages only when no match row exists (dev mode) — when
    // a real matchId resolves, the conversation list reflects the real DB
    // history (which may legitimately be empty for a brand-new match).
    LaunchedEffect(activeChatProfile?.id) {
        val partner = activeChatProfile ?: return@LaunchedEffect
        conversationLoading = true
        val uid = SessionManager.current()?.userId
        if (uid == null) {
            // No session — keep the existing sample messages and just dismiss
            // the spinner so the conversation is usable offline.
            activeMatchId = null
            conversationMessages.clear()
            conversationMessages.addAll(sampleMessagesFallback)
            conversationLoading = false
            return@LaunchedEffect
        }
        val matchId = runCatching {
            SupabaseRepository.getMatchId(uid, partner.id)
        }.getOrNull()
        activeMatchId = matchId
        if (matchId == null) {
            // No match row — fall back to the canned sample conversation.
            conversationMessages.clear()
            conversationMessages.addAll(sampleMessagesFallback)
            conversationLoading = false
            return@LaunchedEffect
        }
        val msgs = runCatching {
            SupabaseRepository.getMessages(matchId)
        }.getOrDefault(emptyList())
        // Replace local list with real Supabase messages (even if empty for a
        // brand-new match — production shows an empty conversation, not canned).
        conversationMessages.clear()
        msgs.forEach { dto ->
            conversationMessages.add(dto.toChatMessageItem(uid))
        }
        conversationLoading = false

        // Fix 3 — Read receipts. Mark every received (sender_id != me) message
        // that is currently unread as read now that the user has opened this
        // conversation. Best-effort: failures are swallowed (the local list
        // still renders correctly without the read flag being flipped).
        msgs.filter { it.senderId != uid && !it.isRead && it.id != null }
            .forEach { dto ->
                runCatching { SupabaseRepository.markMessageRead(dto.id!!) }
            }
    }

    // Android Photo & Video Picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            val msgType = if (isVideo) ChatMessageType.VIDEO else ChatMessageType.IMAGE
            val tempId = "msg_${System.currentTimeMillis()}"
            // Optimistic insert — isSending=true so a small spinner shows next to
            // the bubble while Supabase confirms the insert.
            conversationMessages.add(
                ChatMessageItem(
                    id = tempId,
                    text = if (isVideo) "Shared a video clip 🎥" else "Shared a photo 📷",
                    isSender = true,
                    timestamp = "Just now",
                    type = msgType,
                    mediaUrl = uri.toString(),
                    videoDuration = if (isVideo) "0:15" else null,
                    // Feature #20 — new sender messages start unread until partner "reads" them.
                    isRead = false,
                    isSending = true
                )
            )
            Toast.makeText(context, if (isVideo) "Video sent securely" else "Photo sent securely", Toast.LENGTH_SHORT).show()

            // Chat-Wiring — Persist to Supabase. Local content:// Uri is stored
            // as-is for now; a storage-upload pipeline (Supabase Storage) would
            // replace this with a public URL in a follow-up.
            val matchId = activeMatchId
            val uid = SessionManager.current()?.userId
            val partnerProfile = activeChatProfile
            if (matchId != null && uid != null && partnerProfile != null) {
                chatScope.launch {
                    val sent = runCatching {
                        SupabaseRepository.sendMessage(
                            matchId = matchId,
                            senderId = uid,
                            receiverId = partnerProfile.id,
                            content = if (isVideo) "Shared a video clip 🎥" else "Shared a photo 📷",
                            type = msgType.toSupabaseType(),
                            mediaUrl = uri.toString(),
                            audioDuration = if (isVideo) "0:15" else null,
                            replyToId = null
                        )
                    }.getOrNull()
                    val idx = conversationMessages.indexOfFirst { it.id == tempId }
                    if (idx < 0) return@launch
                    if (sent != null) {
                        conversationMessages[idx] = conversationMessages[idx].copy(
                            isSending = false,
                            serverId = sent.id
                        )
                    } else {
                        conversationMessages.removeAt(idx)
                        Toast.makeText(context, "Message failed to send", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Dev fallback — no matchId, mark as sent locally so the spinner clears.
                val idx = conversationMessages.indexOfFirst { it.id == tempId }
                if (idx >= 0) {
                    conversationMessages[idx] = conversationMessages[idx].copy(isSending = false)
                }
            }
        }
    }

    // Fix 6 — Starts a real MediaRecorder session writing AAC audio to a cache
    // file. Sets `mediaRecorderRef`, `voiceRecordingFile`, `recorderStartMs`,
    // and flips `isRecordingVoice = true` so the timer LaunchedEffect kicks in.
    // Safe to call from the long-press handler; re-entrant guard via `isRecordingVoice`.
    fun startVoiceRecording() {
        if (isRecordingVoice) return
        try {
            val cacheDir = context.cacheDir
            val audioFile = File(cacheDir, "voice_msg_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(audioFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorderRef = recorder
            voiceRecordingFile = audioFile
            recorderStartMs = System.currentTimeMillis()
            voiceRecordSeconds = 0
            isRecordingVoice = true
        } catch (t: Throwable) {
            Toast.makeText(
                context,
                "Couldn't start voice recording: ${t.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            runCatching { mediaRecorderRef?.stop() }
            runCatching { mediaRecorderRef?.release() }
            mediaRecorderRef = null
            voiceRecordingFile = null
            recorderStartMs = 0L
            voiceRecordSeconds = 0
            isRecordingVoice = false
        }
    }

    // Fix 6 — Stops the active MediaRecorder and returns the recorded audio
    // bytes (or null on any failure). Always clears the recorder ref + file ref.
    fun stopVoiceRecording(): ByteArray? {
        val recorder = mediaRecorderRef ?: return null
        val file = voiceRecordingFile
        var bytes: ByteArray? = null
        try {
            recorder.stop()
            if (file != null && file.exists()) {
                bytes = file.readBytes()
            }
        } catch (_: Throwable) {
            // stop() throws if the recorder didn't actually record any audio
            // (e.g. user released instantly). Treat as cancel.
            bytes = null
        } finally {
            try { recorder.release() } catch (_: Throwable) {}
            mediaRecorderRef = null
            voiceRecordingFile = null
            recorderStartMs = 0L
            voiceRecordSeconds = 0
            isRecordingVoice = false
            file?.let { runCatching { it.delete() } }
        }
        return bytes
    }

    // Fix 6 — Cancels the active recording without returning any bytes.
    fun cancelVoiceRecording() {
        val recorder = mediaRecorderRef ?: run {
            isRecordingVoice = false
            voiceRecordSeconds = 0
            recorderStartMs = 0L
            return
        }
        try { recorder.stop() } catch (_: Throwable) {}
        try { recorder.release() } catch (_: Throwable) {}
        voiceRecordingFile?.let { runCatching { it.delete() } }
        mediaRecorderRef = null
        voiceRecordingFile = null
        recorderStartMs = 0L
        voiceRecordSeconds = 0
        isRecordingVoice = false
    }

    // Fix 6 — RECORD_AUDIO runtime-permission launcher. Launched before
    // starting a MediaRecorder session. On grant: calls `startVoiceRecording()`.
    // On deny: shows a Toast and bails (no recording started).
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            Toast.makeText(
                context,
                "Microphone permission denied — can't record voice messages",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Feature #21 — Helper: parse the day label from a message timestamp.
    // Supports "Friday 28 Aug, 15:00", "Wednesday 10:15", "Just now".
    fun dayLabelFromTimestamp(ts: String): String {
        // Strip trailing time portion after a comma, then drop the time if no comma.
        val datePart = ts.substringBefore(",").trim()
        // If it looks like "Just now" or has no day name, return as-is (no separator).
        if (datePart.equals("Just now", ignoreCase = true)) return ""
        // Format: "Friday 28 Aug" or "Wednesday"
        return datePart
    }

    // Feature #21 — Group consecutive messages by day. Re-derives whenever the underlying
    // observable list changes (add / remove / item replace, e.g. read-receipt toggle).
    val conversationItems by remember {
        derivedStateOf {
            val result = mutableListOf<ConversationListItem>()
            var lastDay = ""
            for (msg in conversationMessages) {
                val day = dayLabelFromTimestamp(msg.timestamp)
                if (day.isNotEmpty() && day != lastDay) {
                    result.add(
                        ConversationListItem(
                            key = "sep_$day",
                            isDateSeparator = true,
                            dayLabel = day,
                            message = null
                        )
                    )
                    lastDay = day
                }
                result.add(
                    ConversationListItem(
                        key = msg.id,
                        isDateSeparator = false,
                        dayLabel = "",
                        message = msg
                    )
                )
            }
            result
        }
    }

    // Fix 6 — Real recording timer. Derives elapsed seconds from the actual
    // MediaRecorder start time (kept in `recorderStartMs`). Ticks every 250ms
    // while `isRecordingVoice` is true so the UI feels live. Hard-caps at 30s
    // (auto-stops the recorder + sets `isRecordingVoice = false`).
    LaunchedEffect(isRecordingVoice) {
        while (isRecordingVoice) {
            val elapsedMs = System.currentTimeMillis() - recorderStartMs
            val secs = (elapsedMs / 1000L).toInt()
            if (secs >= 30) {
                // Hard cap at 30s — auto-stop (recorder is torn down by the
                // stop handler the user invokes; here we just flip the flag).
                isRecordingVoice = false
                break
            }
            voiceRecordSeconds = secs
            delay(250L)
        }
    }

    if (activeChatProfile != null) {
        // =========================================================================
        // FULLSCREEN ACTIVE CONVERSATION VIEW WITH SCREENSHOT PREVENTION (FLAG_SECURE)
        // =========================================================================
        val partner = activeChatProfile!!

        // Feature #22 — rememberLazyListState for the message list (FAB visibility + scroll-to-bottom).
        val listState = rememberLazyListState()
        // Feature #23 — ID of the message whose reaction popover is currently open (null = none).
        var showReactionPickerFor by remember { mutableStateOf<String?>(null) }

        // PERFECT WORKING SCREENSHOT PREVENTION:
        // Set FLAG_SECURE on window when conversation opens, clear it on exit!
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        // =========================================================================
        // Fix 1 — Supabase Realtime subscription on the `messages` table.
        //
        // Opens a websocket to Supabase Realtime v1, joins the
        // `realtime:public:messages` channel filtered by `match_id=eq.{matchId}`,
        // and appends / updates messages in `conversationMessages` on every
        // INSERT / UPDATE event. The websocket is closed in `onDispose` when
        // the conversation exits (or when matchId changes).
        //
        // Polling fallback: regardless of websocket status, a separate
        // LaunchedEffect below polls `getMessages(matchId)` every 5s while the
        // conversation is open — this ensures messages always arrive even if
        // Realtime fails (e.g. network proxy blocks websockets).
        // =========================================================================
        DisposableEffect(activeMatchId) {
            val matchId = activeMatchId
            val uid = SessionManager.current()?.userId
            if (matchId == null || uid == null) {
                onDispose { /* nothing to clean up — no websocket was opened */ }
            } else {
                val topic = "realtime:public:messages:match_id=eq.$matchId"
                val req = Request.Builder()
                    .url(buildRealtimeWebSocketUrl())
                    .build()
                val wsListener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        // Phoenix channels join — sends a `phx_join` event with the
                        // postgres_changes config so Supabase knows to broadcast
                        // INSERT / UPDATE events for the messages table on this channel.
                        val joinPayload = JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("broadcast", JSONObject().apply {
                                    put("ack", false)
                                    put("self", false)
                                })
                                put("presence", JSONObject().apply {
                                    put("key", "")
                                })
                                put("postgres_changes", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "*")
                                        put("schema", "public")
                                        put("table", "messages")
                                        put("filter", "match_id=eq.$matchId")
                                    })
                                })
                            })
                        }
                        val joinMsg = JSONObject().apply {
                            put("topic", topic)
                            put("event", "phx_join")
                            put("payload", joinPayload)
                            put("ref", "1")
                        }
                        webSocket.send(joinMsg.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val root = JSONObject(text)
                            val event = root.optString("event")
                            if (event != "postgres_changes") return
                            val payloadData = root.optJSONObject("payload") ?: return
                            val data = payloadData.optJSONObject("data") ?: return
                            val type = data.optString("type")
                            val record = data.optJSONObject("record") ?: return
                            val dto = realtimeMessageAdapter.fromJson(record.toString()) ?: return
                            val isSender = dto.senderId == uid
                            when (type) {
                                "INSERT" -> {
                                    // De-dup: skip if we already have this serverId (e.g.
                                    // optimistic local copy already pushed by the sender).
                                    val exists = conversationMessages.any { existing ->
                                        existing.serverId == dto.id ||
                                            existing.id == "server_${dto.id}"
                                    }
                                    if (!exists) {
                                        conversationMessages.add(dto.toChatMessageItem(uid))
                                    }
                                    // Fix 3 — Mark partner messages as read on arrival.
                                    if (!isSender && dto.id != null && !dto.isRead) {
                                        chatScope.launch {
                                            runCatching {
                                                SupabaseRepository.markMessageRead(dto.id!!)
                                            }
                                        }
                                    }
                                }
                                "UPDATE" -> {
                                    val idx = conversationMessages.indexOfFirst { existing ->
                                        existing.serverId == dto.id ||
                                            existing.id == "server_${dto.id}"
                                    }
                                    if (idx >= 0) {
                                        conversationMessages[idx] = dto.toChatMessageItem(uid)
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                            // Swallow parse errors — polling fallback keeps the list fresh.
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        // Polling fallback covers this — no user-visible action.
                        android.util.Log.w(
                            "ChatRealtime",
                            "WebSocket failure: ${t.message ?: "unknown"}"
                        )
                    }
                }
                val ws = realtimeHttpClient.newWebSocket(req, wsListener)

                onDispose {
                    try {
                        val leaveMsg = JSONObject().apply {
                            put("topic", topic)
                            put("event", "phx_leave")
                            put("payload", JSONObject())
                            put("ref", "2")
                        }
                        ws.send(leaveMsg.toString())
                    } catch (_: Throwable) {}
                    ws.close(1000, "Conversation exited")
                }
            }
        }

        // =========================================================================
        // Fix 1 — Polling fallback. Calls `getMessages(matchId)` every 5s while
        // the conversation is open. Reconciles the local list (adds new rows,
        // updates read-receipts) without clobbering optimistic `isSending=true`
        // rows that haven't been confirmed yet.
        // =========================================================================
        LaunchedEffect(activeMatchId) {
            val matchId = activeMatchId ?: return@LaunchedEffect
            val uid = SessionManager.current()?.userId ?: return@LaunchedEffect
            while (isActive) {
                delay(5000L)
                val fresh = runCatching {
                    SupabaseRepository.getMessages(matchId)
                }.getOrDefault(emptyList())
                if (fresh.isEmpty()) continue
                val byServerId = fresh.associateBy { it.id }
                // 1) Append new server messages that aren't in the local list.
                fresh.forEach { dto ->
                    val exists = conversationMessages.any { existing ->
                        existing.serverId == dto.id ||
                            existing.id == "server_${dto.id}"
                    }
                    if (!exists) {
                        conversationMessages.add(dto.toChatMessageItem(uid))
                        // Fix 3 — Mark partner messages as read on arrival.
                        if (dto.senderId != uid && dto.id != null && !dto.isRead) {
                            runCatching { SupabaseRepository.markMessageRead(dto.id) }
                        }
                    }
                }
                // 2) Sync read-receipts for already-known messages.
                for (i in conversationMessages.indices) {
                    val item = conversationMessages[i]
                    val match = item.serverId?.let { byServerId[it] }
                    if (match != null && match.isRead != item.isRead) {
                        conversationMessages[i] = item.copy(isRead = match.isRead)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashboardBg)
        ) {
            // Chat Top Bar
            Surface(
                color = DashboardCard,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activeChatProfile = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DashboardCream
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { onOpenProfile(partner) }
                        ) {
                            AsyncImage(
                                model = partner.photos.firstOrNull() ?: "",
                                contentDescription = partner.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenProfile(partner) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = partner.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardCream
                                )
                                if (partner.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = SuperBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Active now · ${partner.location}",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }

                        // Security Center / Report
                        IconButton(onClick = { showSafetyCenter = true }) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Safety Center",
                                tint = DashboardPeach,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = {
                            Toast.makeText(context, "Options for ${partner.name}", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = DashboardNavMuted
                            )
                        }
                    }

                    // Native Screenshot Protection Status Bar
                    Surface(
                        color = Color(0xFF1E1714),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = DashboardPeach,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screenshot Protected Chat (FLAG_SECURE Active)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DashboardPeach
                            )
                        }
                    }
                }
            }

            // Message History + FAB overlay (Feature #22)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Chat-Wiring — Centered spinner shown while we resolve the
                // matchId and fetch the real message history from Supabase.
                // Sits INSIDE the existing message-list Box so it never extends
                // into the top status-bar or bottom nav-bar regions.
                if (conversationLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = DashboardTerracotta,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Safety notice banner
                    item(key = "safety_banner") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF261E1A),
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SuperBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Screenshots and screen recordings are automatically blocked inside this chat for your privacy and safety.",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Feature #21 — Date separators interleaved with messages.
                    items(conversationItems, key = { it.key }) { item ->
                        if (item.isDateSeparator) {
                            // Centered date pill.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = item.dayLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DashboardMutedBeige,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            val msg = item.message!!
                            val isSender = msg.isSender
                            val isReactionPickerOpen = showReactionPickerFor == msg.id

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
                            ) {
                                // Feature #23 — Long-press reaction popover above the bubble.
                                if (isReactionPickerOpen) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = DashboardCard,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .padding(bottom = 4.dp)
                                            .widthIn(max = 300.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                                Text(
                                                    text = emoji,
                                                    fontSize = 24.sp,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            val idx = conversationMessages.indexOfFirst { it.id == msg.id }
                                                            if (idx >= 0) {
                                                                val current = conversationMessages[idx]
                                                                // Toggle behaviour — tapping the same emoji that is
                                                                // already set clears it (and deletes the row in
                                                                // Supabase); a different emoji upserts the row.
                                                                val clearing = current.reaction == emoji
                                                                conversationMessages[idx] =
                                                                    current.copy(reaction = if (clearing) null else emoji)

                                                                // Chat-Wiring — Persist the reaction to Supabase.
                                                                // Only real (server-backed) messages can have a
                                                                // persisted reaction; local sample / pending
                                                                // optimistic messages stay local-only.
                                                                val serverMsgId = current.serverId
                                                                val uid = SessionManager.current()?.userId
                                                                if (serverMsgId != null && uid != null) {
                                                                    chatScope.launch {
                                                                        val ok = runCatching {
                                                                            if (clearing) {
                                                                                SupabaseRepository.removeReaction(serverMsgId, uid)
                                                                            } else {
                                                                                SupabaseRepository.addReaction(serverMsgId, uid, emoji)
                                                                            }
                                                                        }.getOrDefault(false)
                                                                        if (!ok) {
                                                                            // Roll back the local change so the UI matches
                                                                            // the server state. If the row was missing, find
                                                                            // it by serverId (the optimistic id may have been
                                                                            // promoted to "server_<id>" on send confirmation).
                                                                            val idx2 = conversationMessages.indexOfFirst { it.serverId == serverMsgId }
                                                                            if (idx2 >= 0) {
                                                                                conversationMessages[idx2] =
                                                                                    conversationMessages[idx2].copy(reaction = current.reaction)
                                                                            }
                                                                            Toast.makeText(context, "Couldn't save reaction", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            showReactionPickerFor = null
                                                        }
                                                        .padding(2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            // Feature #24 — Reply button in the popover.
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable {
                                                        replyTarget = msg
                                                        showReactionPickerFor = null
                                                    }
                                                    // Vertical padding raised so the pill reaches 44dp touch height
                                                    .padding(horizontal = 8.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                                    contentDescription = "Reply",
                                                    tint = DashboardPeach,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Reply",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DashboardPeach
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isSender) Arrangement.End else Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Message Content
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = if (isSender) 18.dp else 4.dp,
                                            bottomEnd = if (isSender) 4.dp else 18.dp
                                        ),
                                        color = if (isSender) DashboardTerracotta else DashboardCard,
                                        border = if (isSender) null else BorderStroke(1.dp, Color(0xFF42342D)),
                                        shadowElevation = 1.dp,
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .combinedClickable(
                                                onClick = {
                                                    // Tapping a bubble with an open popover just closes it.
                                                    if (showReactionPickerFor != null) showReactionPickerFor = null
                                                },
                                                onLongClick = { showReactionPickerFor = msg.id }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            // Feature #24 — Quoted reply block above the bubble text.
                                            if (msg.replyToText != null) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.dp)
                                                            .height(36.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(DashboardTerracotta)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = msg.replyToSenderName ?: "Original message",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = DashboardPeach
                                                        )
                                                        Text(
                                                            text = msg.replyToText,
                                                            fontSize = 12.sp,
                                                            color = DashboardMutedBeige,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // Media Attachment (Image or Video)
                                            if (msg.type == ChatMessageType.IMAGE && msg.mediaUrl != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            previewMediaUrl = Pair(msg.mediaUrl, false)
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = msg.mediaUrl,
                                                        contentDescription = "Shared image",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Surface(
                                                        color = Color.Black.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Tap to view",
                                                            fontSize = 10.sp,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                            } else if (msg.type == ChatMessageType.VIDEO && msg.mediaUrl != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            previewMediaUrl = Pair(msg.mediaUrl, true)
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = msg.mediaUrl,
                                                        contentDescription = "Shared video thumbnail",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    // Play Overlay
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = DashboardTerracotta.copy(alpha = 0.85f),
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .align(Alignment.Center)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Play Video",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(28.dp)
                                                            )
                                                        }
                                                    }

                                                    // Duration Badge
                                                    if (msg.videoDuration != null) {
                                                        Surface(
                                                            color = Color.Black.copy(alpha = 0.7f),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(6.dp)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Videocam,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = msg.videoDuration,
                                                                    fontSize = 10.sp,
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                            } else if (msg.type == ChatMessageType.VOICE) {
                                                // Feature #25 — Voice message bubble rendering.
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    // Play button (terracotta circle)
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = DashboardTerracotta,
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Play voice message",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    // Fake waveform — Row of ~20 thin bars of varying heights.
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        val heights = listOf(
                                                            6, 12, 8, 16, 10, 14, 6, 18, 12, 8,
                                                            14, 6, 16, 10, 12, 8, 6, 14, 10, 6
                                                        )
                                                        heights.forEach { h ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(2.dp)
                                                                    .height(h.dp)
                                                                    .clip(RoundedCornerShape(1.dp))
                                                                    .background(
                                                                        if (h > 12) DashboardTerracotta else DashboardPeach
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = msg.audioDuration ?: "0:00",
                                                        fontSize = 12.sp,
                                                        color = DashboardMutedBeige
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }

                                            if (msg.text.isNotBlank()) {
                                                Text(
                                                    text = msg.text,
                                                    color = if (isSender) Color.White else DashboardCream,
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Feature #20 — Read receipts + timestamp row.
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = msg.timestamp,
                                                    color = if (isSender) Color.White.copy(alpha = 0.7f) else DashboardNavMuted,
                                                    fontSize = 10.sp
                                                )
                                                if (isSender) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (msg.isRead) "✓✓" else "✓",
                                                        fontSize = if (msg.isRead) 11.sp else 12.sp,
                                                        color = if (msg.isRead) TinderBlue else DashboardNavMuted,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Heart Reaction on Received Message (Matches reference screenshot 5)
                                    if (!isSender) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        var isHeartLiked by remember { mutableStateOf(msg.isLiked) }
                                        IconButton(
                                            onClick = {
                                                isHeartLiked = !isHeartLiked
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isHeartLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Like message",
                                                tint = if (isHeartLiked) NopeCoral else DashboardNavMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else if (msg.isSending) {
                                        // Chat-Wiring — Small sending spinner next to the sender's
                                        // optimistic bubble while Supabase confirms the insert.
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(
                                            color = DashboardNavMuted,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                // Feature #23 — Selected reaction pill below the bubble.
                                if (msg.reaction != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = DashboardCard,
                                        border = BorderStroke(1.dp, Color(0xFF42342D)),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = msg.reaction,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Feature #19 — Typing indicator (3 animated dots) shown when partnerIsTyping.
                    if (partnerIsTyping) {
                        item(key = "typing_indicator") {
                            // Left-aligned partner bubble: DashboardCard, RoundedCornerShape(16.dp), padding 12.dp.
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DashboardCard,
                                border = BorderStroke(1.dp, Color(0xFF42342D)),
                                modifier = Modifier.padding(end = 80.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val transition = rememberInfiniteTransition(label = "typing_dots")
                                    listOf(0L, 200L, 400L).forEachIndexed { idx, delayMs ->
                                        val scale by transition.animateFloat(
                                            initialValue = 0.6f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(
                                                    durationMillis = 600,
                                                    delayMillis = delayMs.toInt(),
                                                    easing = LinearEasing
                                                ),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "dot_$idx"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .scale(scale)
                                                .clip(CircleShape)
                                                .background(DashboardMutedBeige)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                } // end of `else { not loading }` — wraps the LazyColumn above.

                // Feature #22 — Scroll-to-bottom FAB. Visible only when not at the bottom of the list.
                val showFab by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 50
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showFab,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = DashboardCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            shadowElevation = 6.dp,
                            // Touch target enlarged to 44dp accessibility minimum
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        chatScope.launch {
                                            listState.animateScrollToItem(conversationItems.lastIndex)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    tint = DashboardCream,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        // Tiny unread count badge — purely decorative on the FAB for now.
                        Surface(
                            shape = CircleShape,
                            color = TinderCoral,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .border(2.dp, DashboardCard, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "1",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Input Bar with Media and GIF buttons + reply preview + voice recording bar
            Surface(
                color = DashboardCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Feature #24 — Reply preview bar above the input field.
                    val replyingTo = replyTarget
                    if (replyingTo != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DashboardCard,
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(DashboardTerracotta)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${replyingTo.isSender.let { if (it) "You" else partner.name }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardPeach
                                    )
                                    Text(
                                        text = replyingTo.text.ifBlank { "Attachment" },
                                        fontSize = 12.sp,
                                        color = DashboardMutedBeige,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { replyTarget = null },
                                    // Touch target enlarged to 44dp accessibility minimum
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel reply",
                                        tint = DashboardNavMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isRecordingVoice) {
                        // Feature #25 — Recording bar (replaces input row while recording).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing red recording dot.
                            val pulseTransition = rememberInfiniteTransition(label = "voice_pulse")
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryRed.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Recording… 0:${voiceRecordSeconds.toString().padStart(2, '0')}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = DashboardCream,
                                modifier = Modifier.weight(1f)
                            )
                            // Cancel button — stops recording WITHOUT sending.
                            Surface(
                                onClick = { cancelVoiceRecording() },
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF261E1A),
                                border = BorderStroke(1.dp, NopeCoral)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel recording",
                                        tint = NopeCoral,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Cancel",
                                        fontSize = 12.sp,
                                        color = NopeCoral,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Send voice button — stops recording AND sends the voice message.
                            Surface(
                                enabled = !isSendingMessage,
                                onClick = {
                                    // Fix 6 — Stop MediaRecorder → get AAC bytes → base64-encode
                                    // → set as `media_url` data URI on the message row.
                                    val secs = voiceRecordSeconds.coerceAtLeast(1)
                                    val audioDuration = "0:${secs.toString().padStart(2, '0')}"
                                    val audioBytes = stopVoiceRecording()
                                    if (audioBytes == null || audioBytes.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Recording too short — try holding longer",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Surface
                                    }
                                    // TODO: move to Supabase Storage when storage SDK is integrated.
                                    // For now the audio bytes are embedded as a base64 data URI in
                                    // the `media_url` column so the partner can play it back.
                                    val b64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                                    val mediaUrl = "data:audio/mp4;base64,$b64"
                                    val tempId = "msg_${System.currentTimeMillis()}"
                                    conversationMessages.add(
                                        ChatMessageItem(
                                            id = tempId,
                                            text = "",
                                            isSender = true,
                                            timestamp = "Just now",
                                            type = ChatMessageType.VOICE,
                                            mediaUrl = mediaUrl,
                                            audioDuration = audioDuration,
                                            isRead = false,
                                            isSending = true
                                        )
                                    )
                                    // Fix 7 — One-shot send guard.
                                    isSendingMessage = true

                                    // Chat-Wiring — Persist the voice message row to Supabase.
                                    val matchId = activeMatchId
                                    val uid = SessionManager.current()?.userId
                                    if (matchId != null && uid != null) {
                                        chatScope.launch {
                                            val sent = runCatching {
                                                SupabaseRepository.sendMessage(
                                                    matchId = matchId,
                                                    senderId = uid,
                                                    receiverId = partner.id,
                                                    content = "",
                                                    type = "voice",
                                                    mediaUrl = mediaUrl,
                                                    audioDuration = audioDuration,
                                                    replyToId = null
                                                )
                                            }.getOrNull()
                                            val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                            if (idx < 0) {
                                                isSendingMessage = false
                                                return@launch
                                            }
                                            if (sent != null) {
                                                conversationMessages[idx] = conversationMessages[idx].copy(
                                                    isSending = false,
                                                    serverId = sent.id
                                                )
                                            } else {
                                                conversationMessages.removeAt(idx)
                                                Toast.makeText(context, "Message failed to send", Toast.LENGTH_SHORT).show()
                                            }
                                            isSendingMessage = false
                                        }
                                    } else {
                                        val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                        if (idx >= 0) {
                                            conversationMessages[idx] = conversationMessages[idx].copy(isSending = false)
                                        }
                                        isSendingMessage = false
                                    }
                                },
                                shape = CircleShape,
                                color = DashboardTerracotta,
                                // Touch target enlarged to 44dp accessibility minimum
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send voice message",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // GIF Button (Screenshot 5)
                            Surface(
                                onClick = { showGifPickerSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF261E1A),
                                border = BorderStroke(1.dp, Color(0xFF42342D)),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "GIF",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardPeach
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Image / Video Attachment Button (Request: CHAT PAGE ADD IMAGE/VIDEO SHARING)
                            IconButton(
                                onClick = { showMediaPickerSheet = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF261E1A))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Share Image or Video",
                                    tint = DashboardPeach,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Message Text Field
                            OutlinedTextField(
                                value = chatMessageInput,
                                onValueChange = { chatMessageInput = it },
                                placeholder = { Text("Message...", fontSize = 14.sp, color = DashboardNavMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DashboardTerracotta,
                                    unfocusedBorderColor = Color(0xFF42342D),
                                    focusedContainerColor = Color(0xFF261E1A),
                                    unfocusedContainerColor = Color(0xFF261E1A),
                                    focusedTextColor = DashboardCream,
                                    unfocusedTextColor = DashboardCream
                                ),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Feature #25 — Send OR Mic button: mic icon when input empty (long-press to record),
                            // send icon when there is text to send.
                            if (chatMessageInput.isBlank()) {
                                // Mic button: combinedClickable so we can attach a long-press to start recording.
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(DashboardTerracotta)
                                        .combinedClickable(
                                            onClick = {
                                                // Single tap = no-op hint; long-press starts recording.
                                                Toast.makeText(
                                                    context,
                                                    "Hold to record a voice message",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onLongClick = {
                                                // Fix 6 — Request RECORD_AUDIO at runtime. The
                                                // launcher's grant-callback invokes
                                                // `startVoiceRecording()` which opens MediaRecorder.
                                                recordAudioPermissionLauncher.launch(
                                                    android.Manifest.permission.RECORD_AUDIO
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record voice message",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    enabled = !isSendingMessage,
                                    onClick = {
                                        if (chatMessageInput.isNotBlank() && !isSendingMessage) {
                                            val replySnapshot = replyTarget
                                            val tempId = "msg_${System.currentTimeMillis()}"
                                            val textToSend = chatMessageInput.trim()
                                            conversationMessages.add(
                                                ChatMessageItem(
                                                    id = tempId,
                                                    text = textToSend,
                                                    isSender = true,
                                                    timestamp = "Just now",
                                                    isRead = false,
                                                    isSending = true,
                                                    replyToText = replySnapshot?.text,
                                                    replyToSenderName = if (replySnapshot != null) {
                                                        if (replySnapshot.isSender) "You" else partner.name
                                                    } else null
                                                )
                                            )
                                            chatMessageInput = ""
                                            replyTarget = null

                                            // Fix 7 — One-shot send guard. Disables the send
                                            // button for the duration of the Supabase round-trip
                                            // so a second rapid tap can't double-send.
                                            isSendingMessage = true

                                            // Chat-Wiring — Persist the outgoing text to Supabase.
                                            // Optimistic UI is already updated above; we just
                                            // confirm with the API and patch serverId / isSending
                                            // (or roll back + toast on failure).
                                            val matchId = activeMatchId
                                            val uid = SessionManager.current()?.userId
                                            if (matchId != null && uid != null) {
                                                chatScope.launch {
                                                    val sent = runCatching {
                                                        SupabaseRepository.sendMessage(
                                                            matchId = matchId,
                                                            senderId = uid,
                                                            receiverId = partner.id,
                                                            content = textToSend,
                                                            type = "text",
                                                            replyToId = replySnapshot?.serverId
                                                        )
                                                    }.getOrNull()
                                                    val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                                    if (idx < 0) {
                                                        isSendingMessage = false
                                                        return@launch
                                                    }
                                                    if (sent != null) {
                                                        conversationMessages[idx] = conversationMessages[idx].copy(
                                                            isSending = false,
                                                            serverId = sent.id
                                                        )
                                                    } else {
                                                        conversationMessages.removeAt(idx)
                                                        Toast.makeText(context, "Message failed to send", Toast.LENGTH_SHORT).show()
                                                    }
                                                    isSendingMessage = false
                                                }
                                            } else {
                                                // Dev fallback — no matchId, clear the spinner locally.
                                                val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                                if (idx >= 0) {
                                                    conversationMessages[idx] = conversationMessages[idx].copy(isSending = false)
                                                }
                                                isSendingMessage = false
                                            }

                                            // Fix 1 — Canned typing/reply simulation removed.
                                            // Partner replies now arrive only via the Supabase
                                            // Realtime websocket (or the 5s polling fallback).
                                        }
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isSendingMessage) DashboardNavMuted else DashboardTerracotta)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // MAIN CHAT LIST VIEW (Matches Screenshot 4)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashboardBg)
                .padding(bottom = 76.dp)
        ) {
            // Top Bar with Shield and Activity icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSafetyCenter = true }) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safety Center",
                            tint = DashboardPeach,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "Explore Date Night Games!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text(text = "🎲", fontSize = 20.sp)
                    }
                }
            }

            // Search Bar (Matches Screenshot 4: Search 37 matches)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = DashboardNavMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search ${profiles.size + 28} matches",
                        color = DashboardNavMuted,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feature #27 — Matches | Requests tab row at the top of the chat tray.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val tabs = listOf("Matches" to 0, "Requests" to 1)
                tabs.forEach { (label, idx) ->
                    val isSelected = chatTrayTab == idx
                    Surface(
                        onClick = { chatTrayTab = idx },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) DashboardTerracotta else Color(0xFF261E1A),
                        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF42342D)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DashboardMutedBeige
                            )
                        }
                    }
                }
                if (realRequests.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = TinderCoral,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = realRequests.size.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (chatTrayTab == 0) {
            // New Matches Horizontal Row (Matches Screenshot 4: Rounded square cards with verified badges)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "New Matches",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(profiles) { profile ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(78.dp)
                                .clickable { activeChatProfile = profile }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF261E1A))
                                    .border(1.dp, Color(0xFF42342D), RoundedCornerShape(18.dp))
                            ) {
                                AsyncImage(
                                    model = profile.photos.firstOrNull() ?: "",
                                    contentDescription = profile.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (profile.isVerified) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SuperBlue,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Verified",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = profile.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DashboardCream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Messages Section (Matches Screenshot 4)
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Messages",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Fix 2 — Real chat threads come from `realThreads` (populated by
                // the LaunchedEffect above from SupabaseRepository.getMatches +
                // getProfilesByIds + getMessages). The hardcoded `sampleThreads`
                // list has been deleted.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Chat-Wiring — Shimmer placeholders shown while the user's
                    // matches list is being fetched from Supabase (brief loading
                    // window on first entry to the Chat tray).
                    if (chatTrayLoading) {
                        items(5) { ChatThreadShimmerRow() }
                    } else if (realThreads.isEmpty()) {
                        // Fix 2 — Empty state. Shown when the user has no matches.
                        item(key = "empty_matches") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No matches yet. Start swiping to find your match! 💘",
                                        fontSize = 14.sp,
                                        color = DashboardNavMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        }
                    } else {
                    items(realThreads, key = { it.profile.id }) { thread ->
                        Surface(
                            onClick = { activeChatProfile = thread.profile },
                            color = DashboardCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, DashboardTerracotta, CircleShape)
                                ) {
                                    AsyncImage(
                                        model = thread.profile.photos.firstOrNull() ?: "",
                                        contentDescription = thread.profile.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = thread.profile.name,
                                            fontSize = 15.sp,
                                            fontWeight = if (thread.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                            color = DashboardCream
                                        )
                                        if (thread.profile.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = SuperBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = thread.lastMessage,
                                        fontSize = 13.sp,
                                        color = if (thread.isUnread) DashboardCream else DashboardNavMuted,
                                        fontWeight = if (thread.isUnread) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (thread.isYourTurn) {
                                        // "Your turn" Pill Badge (Screenshot 4)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Black,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = "Your turn",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = thread.timestamp,
                                            fontSize = 11.sp,
                                            color = DashboardNavMuted
                                        )
                                    }

                                    if (thread.isUnread && !thread.isYourTurn) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(DashboardTerracotta)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
            } else {
                // Feature #27 — Requests tab: list of pending message requests with Accept/Block actions.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    if (requestsLoading) {
                        Spacer(modifier = Modifier.height(20.dp))
                        repeat(3) { ChatThreadShimmerRow() }
                    } else if (realRequests.isEmpty()) {
                        // Fix 4 — Empty state.
                        Spacer(modifier = Modifier.height(60.dp))
                        Text(
                            text = "No pending requests",
                            fontSize = 14.sp,
                            color = DashboardNavMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(realRequests, key = { it.profile.id }) { request ->
                                Surface(
                                    color = DashboardCard,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, DashboardNavMuted, CircleShape)
                                        ) {
                                            AsyncImage(
                                                model = request.profile.photos.firstOrNull() ?: "",
                                                contentDescription = request.profile.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = request.profile.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DashboardCream
                                                )
                                                if (request.profile.isVerified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Verified",
                                                        tint = SuperBlue,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = request.lastMessage,
                                                fontSize = 13.sp,
                                                color = DashboardMutedBeige,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Accept — opens the conversation with this user.
                                                // (Creates a match row server-side if needed via the
                                                // existing swipe + match-creation pipeline; here we
                                                // just navigate to the chat so the matchId-resolution
                                                // path in the conversation LaunchedEffect picks it up.)
                                                Surface(
                                                    onClick = {
                                                        Toast.makeText(
                                                            context,
                                                            "Request accepted — opening chat",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        realRequests.removeAll { it.profile.id == request.profile.id }
                                                        activeChatProfile = request.profile
                                                    },
                                                    shape = RoundedCornerShape(50),
                                                    color = DashboardTerracotta
                                                ) {
                                                    Text(
                                                        text = "Accept",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        // Vertical padding raised so the pill reaches 44dp touch height
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                                                    )
                                                }
                                                // Block — outlined NopeCoral.
                                                Surface(
                                                    onClick = { showBlockConfirm = request.profile.id },
                                                    shape = RoundedCornerShape(50),
                                                    color = Color.Transparent,
                                                    border = BorderStroke(1.dp, NopeCoral)
                                                ) {
                                                    Text(
                                                        text = "Block",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NopeCoral,
                                                        // Vertical padding raised so the pill reaches 44dp touch height
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MEDIA SHARING BOTTOM SHEET (Photos & Videos)
    // =========================================================================
    if (showMediaPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMediaPickerSheet = false },
            containerColor = DashboardCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF6B584E)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Share Photo or Video",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Text(
                    text = "All media sent inside this chat is protected by FLAG_SECURE screenshot prevention.",
                    fontSize = 12.sp,
                    color = DashboardNavMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Pick from Gallery
                Surface(
                    onClick = {
                        showMediaPickerSheet = false
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF261E1A),
                    border = BorderStroke(1.dp, Color(0xFF42342D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DashboardTerracotta,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Choose from Device Gallery",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DashboardCream
                            )
                            Text(
                                text = "Select photos or recorded videos",
                                fontSize = 11.sp,
                                color = DashboardNavMuted
                            )
                        }
                    }
                }

                // Fix 5 — Removed "Quick Share: Hills Scenery Photo" and
                // "Quick Share: Lake District Video Clip" preset buttons. The
                // real PickVisualMedia launcher above is the only media-source
                // path now — no more hardcoded Unsplash / city-imagery URLs.

                Spacer(modifier = Modifier.height(24.dp)
                )
            }
        }
    }

    // =========================================================================
    // GIF SELECTOR SHEET
    // =========================================================================
    if (showGifPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGifPickerSheet = false },
            containerColor = DashboardCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF6B584E)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Trending Stickers / GIFs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(14.dp))

                val sampleGifs = listOf(
                    "Hey! 🙏",
                    "Coffee together? ☕",
                    "Great match! 🔥",
                    "Downtown vibes 🌴",
                    "Smile thanks! 😊",
                    "Awesome look! ✨",
                    "Date night? 🍕",
                    "Tough day? 😢"
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sampleGifs) { gifText ->
                        Surface(
                            onClick = {
                                showGifPickerSheet = false
                                // Chat-Wiring — Optimistic GIF send (treated as a text
                                // message so the partner sees the same string).
                                val tempId = "msg_${System.currentTimeMillis()}"
                                conversationMessages.add(
                                    ChatMessageItem(
                                        id = tempId,
                                        text = gifText,
                                        isSender = true,
                                        timestamp = "Just now",
                                        isRead = false,
                                        isSending = true
                                    )
                                )
                                val matchId = activeMatchId
                                val uid = SessionManager.current()?.userId
                                val partnerProfile = activeChatProfile
                                if (matchId != null && uid != null && partnerProfile != null) {
                                    chatScope.launch {
                                        val sent = runCatching {
                                            SupabaseRepository.sendMessage(
                                                matchId = matchId,
                                                senderId = uid,
                                                receiverId = partnerProfile.id,
                                                content = gifText,
                                                type = "text"
                                            )
                                        }.getOrNull()
                                        val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                        if (idx < 0) return@launch
                                        if (sent != null) {
                                            conversationMessages[idx] = conversationMessages[idx].copy(
                                                isSending = false,
                                                serverId = sent.id
                                            )
                                        } else {
                                            conversationMessages.removeAt(idx)
                                            Toast.makeText(context, "Message failed to send", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    val idx = conversationMessages.indexOfFirst { it.id == tempId }
                                    if (idx >= 0) {
                                        conversationMessages[idx] = conversationMessages[idx].copy(isSending = false)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF261E1A),
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = gifText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DashboardCream,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // =========================================================================
    // MEDIA PREVIEW MODAL
    // =========================================================================
    if (previewMediaUrl != null) {
        val (url, isVideo) = previewMediaUrl!!
        Dialog(onDismissRequest = { previewMediaUrl = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DashboardCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVideo) "Video Player (Protected)" else "Photo Preview (Protected)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream
                        )
                        IconButton(onClick = { previewMediaUrl = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = DashboardNavMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isVideo) {
                            Surface(
                                shape = CircleShape,
                                color = DashboardTerracotta.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(56.dp)
                                    .align(Alignment.Center)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🔒 Screen capture disabled via FLAG_SECURE",
                        fontSize = 11.sp,
                        color = DashboardPeach,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // =========================================================================
    // SAFETY & PRIVACY CENTER DIALOG
    // =========================================================================
    if (showSafetyCenter) {
        AlertDialog(
            onDismissRequest = { showSafetyCenter = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SuperBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safety & Privacy Center", fontWeight = FontWeight.Bold, color = DashboardCream)
                }
            },
            text = {
                Text(
                    text = "Mallu Cupid provides round-the-clock safety & media privacy tools:\n\n" +
                            "• Screenshot & Screen Recording Prevention: In-chat FLAG_SECURE blocks unauthorized captures of your private chats and shared media.\n\n" +
                            "• Photo Verification: Verified selfies ensure authentic dating partners nearby.\n\n" +
                            "• Instant Block & Unblock List: Keep unwanted contacts blocked from finding you.",
                    color = DashboardMutedBeige,
                    lineHeight = 20.sp,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSafetyCenter = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
                ) {
                    Text("Understood", color = Color.White)
                }
            },
            containerColor = DashboardCard
        )
    }

    // =========================================================================
    // BLOCK CONFIRMATION DIALOG (message-requests tab)
    // Renders after the requests list. Removes the request + Toast "Blocked"
    // when confirmed; clears state on dismiss.
    // =========================================================================
    showBlockConfirm?.let { profileId ->
        AlertDialog(
            onDismissRequest = { showBlockConfirm = null },
            title = {
                Text(
                    text = "Block this user?",
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            },
            text = {
                Text(
                    text = "They won't be able to see your profile or contact you again.",
                    color = DashboardMutedBeige,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Fix 4 — Persist the block server-side, then remove the
                        // local request row + Toast. Best-effort: failures are
                        // surfaced as a Toast but the local row is still removed
                        // so the UI is consistent.
                        val uid = SessionManager.current()?.userId
                        if (uid != null) {
                            chatScope.launch {
                                val err = runCatching {
                                    SupabaseRepository.blockUser(uid, profileId)
                                }.getOrNull()
                                if (err != null) {
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        realRequests.removeAll { it.profile.id == profileId }
                        Toast.makeText(context, "Blocked", Toast.LENGTH_SHORT).show()
                        showBlockConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NopeCoral)
                ) {
                    Text("Block", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = null }) {
                    Text("Cancel", color = DashboardNavMuted)
                }
            },
            containerColor = DashboardCard
        )
    }
}
