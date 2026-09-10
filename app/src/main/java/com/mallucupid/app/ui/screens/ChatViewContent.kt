package com.mallucupid.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
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
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.ui.theme.*

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
    val audioDuration: String? = null
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

    // Feature #19 — Partner typing indicator state.
    var partnerIsTyping by remember { mutableStateOf(false) }
    // Feature #22 — rememberCoroutineScope used for typing simulation + read-receipt update.
    val chatScope = rememberCoroutineScope()
    // Feature #24 — Currently selected message being replied to (quoted preview above input).
    var replyTarget by remember { mutableStateOf<ChatMessageItem?>(null) }
    // Feature #25 — Voice recording UI state. Simulated timer (no MediaRecorder dependency).
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }
    // Feature #27 — Chat tray tab: 0 = Matches, 1 = Requests
    var chatTrayTab by remember { mutableIntStateOf(0) }
    // Feature #27 — Mutable sample message requests list (Accept moves to Matches, Block removes).
    val sampleRequests = remember {
        mutableStateListOf(
            ChatThreadItem(
                profile = profiles.getOrElse(4) { profiles.first() },
                lastMessage = "Sent you a message request",
                timestamp = "2h",
                isUnread = true
            ),
            ChatThreadItem(
                profile = profiles.getOrElse(5) { profiles.first() },
                lastMessage = "Wants to connect with you",
                timestamp = "5h",
                isUnread = true
            ),
            ChatThreadItem(
                profile = profiles.getOrElse(6) { profiles.first() },
                lastMessage = "Liked your profile!",
                timestamp = "1d",
                isUnread = true
            )
        )
    }

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

    // Android Photo & Video Picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            conversationMessages.add(
                ChatMessageItem(
                    id = "msg_${System.currentTimeMillis()}",
                    text = if (isVideo) "Shared a video clip 🎥" else "Shared a photo 📷",
                    isSender = true,
                    timestamp = "Just now",
                    type = if (isVideo) ChatMessageType.VIDEO else ChatMessageType.IMAGE,
                    mediaUrl = uri.toString(),
                    videoDuration = if (isVideo) "0:15" else null,
                    // Feature #20 — new sender messages start unread until partner "reads" them.
                    isRead = false
                )
            )
            Toast.makeText(context, if (isVideo) "Video sent securely" else "Photo sent securely", Toast.LENGTH_SHORT).show()
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

    // Feature #25 — Simulated voice recording timer. Counts up while isRecordingVoice is true.
    LaunchedEffect(isRecordingVoice) {
        while (isRecordingVoice) {
            delay(1000L)
            if (voiceRecordSeconds < 30) {
                voiceRecordSeconds = voiceRecordSeconds + 1
            } else {
                // Hard cap at 30s — auto-stop.
                isRecordingVoice = false
            }
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
                                                                conversationMessages[idx] =
                                                                    conversationMessages[idx].copy(reaction = emoji)
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
                                                    imageVector = Icons.Default.Reply,
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

                // Feature #22 — Scroll-to-bottom FAB. Visible only when not at the bottom of the list.
                val showFab by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 50
                    }
                }
                AnimatedVisibility(
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
                                onClick = {
                                    isRecordingVoice = false
                                    voiceRecordSeconds = 0
                                },
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
                                onClick = {
                                    val secs = voiceRecordSeconds.coerceAtLeast(1)
                                    conversationMessages.add(
                                        ChatMessageItem(
                                            id = "msg_${System.currentTimeMillis()}",
                                            text = "",
                                            isSender = true,
                                            timestamp = "Just now",
                                            type = ChatMessageType.VOICE,
                                            audioDuration = "0:${secs.toString().padStart(2, '0')}",
                                            isRead = false
                                        )
                                    )
                                    // TODO: integrate MediaRecorder for real recording
                                    isRecordingVoice = false
                                    voiceRecordSeconds = 0
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
                                                voiceRecordSeconds = 0
                                                isRecordingVoice = true
                                                // TODO: integrate MediaRecorder for real recording
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
                                    onClick = {
                                        if (chatMessageInput.isNotBlank()) {
                                            val replySnapshot = replyTarget
                                            conversationMessages.add(
                                                ChatMessageItem(
                                                    id = "msg_${System.currentTimeMillis()}",
                                                    text = chatMessageInput.trim(),
                                                    isSender = true,
                                                    timestamp = "Just now",
                                                    isRead = false,
                                                    replyToText = replySnapshot?.text,
                                                    replyToSenderName = if (replySnapshot != null) {
                                                        if (replySnapshot.isSender) "You" else partner.name
                                                    } else null
                                                )
                                            )
                                            chatMessageInput = ""
                                            replyTarget = null

                                            // Feature #19 — Simulate partner typing then canned reply.
                                            if (!partnerIsTyping) {
                                                chatScope.launch {
                                                    partnerIsTyping = true
                                                    delay(1500L)
                                                    val cannedReplies = listOf(
                                                        "Haha that's nice 😄",
                                                        "Oh really? Tell me more 👀",
                                                        "Love that! 🙌",
                                                        "Haha you're cute 😊"
                                                    )
                                                    conversationMessages.add(
                                                        ChatMessageItem(
                                                            id = "msg_${System.currentTimeMillis()}",
                                                            text = cannedReplies.random(),
                                                            isSender = false,
                                                            timestamp = "Just now"
                                                        )
                                                    )
                                                    partnerIsTyping = false

                                                    // Feature #20 — Mark the most recent sender message as read ~2s later.
                                                    delay(2000L)
                                                    val lastSenderIdx = conversationMessages.indexOfLast { it.isSender }
                                                    if (lastSenderIdx >= 0 && !conversationMessages[lastSenderIdx].isRead) {
                                                        conversationMessages[lastSenderIdx] =
                                                            conversationMessages[lastSenderIdx].copy(isRead = true)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(DashboardTerracotta)
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
                if (sampleRequests.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = TinderCoral,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = sampleRequests.size.toString(),
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

                val sampleThreads = listOf(
                    ChatThreadItem(
                        profile = profiles.first(),
                        lastMessage = "+2349135010078",
                        timestamp = "12:20",
                        isUnread = true,
                        isYourTurn = true
                    ),
                    ChatThreadItem(
                        profile = profiles.getOrElse(1) { profiles.first() },
                        lastMessage = "https://wa.me/qr/TZI7IFSB2QZ...",
                        timestamp = "Yesterday",
                        isUnread = false
                    ),
                    ChatThreadItem(
                        profile = profiles.getOrElse(2) { profiles.first() },
                        lastMessage = "From your first Like to date night downtown...",
                        timestamp = "Oct 24",
                        isUnread = false
                    ),
                    ChatThreadItem(
                        profile = profiles.getOrElse(3) { profiles.first() },
                        lastMessage = "Awesome! Have you visited the new cafe downtown?",
                        timestamp = "Sep 15",
                        isUnread = false
                    )
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sampleThreads) { thread ->
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
            } else {
                // Feature #27 — Requests tab: list of pending message requests with Accept/Block actions.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    if (sampleRequests.isEmpty()) {
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
                            items(sampleRequests, key = { it.profile.id }) { request ->
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
                                                // Accept — DashboardTerracotta pill.
                                                Surface(
                                                    onClick = {
                                                        Toast.makeText(
                                                            context,
                                                            "Request accepted",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        sampleRequests.removeAll { it.profile.id == request.profile.id }
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
                                                    onClick = {
                                                        Toast.makeText(
                                                            context,
                                                            "Blocked",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        sampleRequests.removeAll { it.profile.id == request.profile.id }
                                                    },
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

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Demo Photo Sender
                Surface(
                    onClick = {
                        showMediaPickerSheet = false
                        conversationMessages.add(
                            ChatMessageItem(
                                id = "msg_${System.currentTimeMillis()}",
                                text = "Here's a view from the hills! 🌿",
                                isSender = true,
                                timestamp = "Just now",
                                type = ChatMessageType.IMAGE,
                                mediaUrl = "https://images.unsplash.com/photo-1596895111956-bf1cf0599ce5?auto=format&fit=crop&w=800&q=80"
                            )
                        )
                        Toast.makeText(context, "Photo shared", Toast.LENGTH_SHORT).show()
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
                            color = Color(0xFF382D27),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = DashboardPeach,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Quick Share: Hills Scenery Photo",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DashboardCream
                            )
                            Text(
                                text = "Instant high-resolution photo sample",
                                fontSize = 11.sp,
                                color = DashboardNavMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Demo Video Sender
                Surface(
                    onClick = {
                        showMediaPickerSheet = false
                        conversationMessages.add(
                            ChatMessageItem(
                                id = "msg_${System.currentTimeMillis()}",
                                text = "Check out this lake district houseboat clip! 🚤",
                                isSender = true,
                                timestamp = "Just now",
                                type = ChatMessageType.VIDEO,
                                mediaUrl = "https://images.unsplash.com/photo-1593693397690-362cb9666fc2?auto=format&fit=crop&w=800&q=80",
                                videoDuration = "0:24"
                            )
                        )
                        Toast.makeText(context, "Video shared", Toast.LENGTH_SHORT).show()
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
                            color = Color(0xFF382D27),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = DashboardPeach,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Quick Share: Lake District Video Clip",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DashboardCream
                            )
                            Text(
                                text = "Instant video clip with player overlay",
                                fontSize = 11.sp,
                                color = DashboardNavMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
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
                                conversationMessages.add(
                                    ChatMessageItem(
                                        id = "msg_${System.currentTimeMillis()}",
                                        text = gifText,
                                        isSender = true,
                                        timestamp = "Just now"
                                    )
                                )
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
}
