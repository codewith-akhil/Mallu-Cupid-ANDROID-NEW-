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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    VIDEO
}

data class ChatMessageItem(
    val id: String,
    val text: String = "",
    val isSender: Boolean,
    val timestamp: String,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val mediaUrl: String? = null,
    val videoDuration: String? = null,
    val isLiked: Boolean = false
)

data class ChatThreadItem(
    val profile: DatingProfile,
    val lastMessage: String,
    val timestamp: String,
    val isUnread: Boolean = false,
    val isYourTurn: Boolean = false
)

/**
 * ChatViewContent
 *
 * Implements the Chat page and Fullscreen Conversation view matching Screenshots 4 & 5
 * with Mallu Cupid luxury dark palette:
 * - Real Image & Video sharing via ActivityResultContracts.PickVisualMedia
 * - Direct quick sample photo/video selector for testing in emulator
 * - PERFECT WORKING SCREENSHOT PREVENTION INSIDE THAT ONLY via WindowManager.LayoutParams.FLAG_SECURE
 * - Hearts on received messages, GIF selector, and Kerala safety features
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Initial message history with mixed text, image, and video
    val conversationMessages = remember {
        mutableStateListOf(
            ChatMessageItem(
                id = "m1",
                text = "Hey! Noticed you're also exploring cafes around Fort Kochi!",
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
                text = "Your genuine smile and love for Kerala sunsets!",
                isSender = true,
                timestamp = "Wednesday 10:20"
            ),
            ChatMessageItem(
                id = "m5",
                text = "Check out this sunset view from Marine Drive yesterday evening 🌅",
                isSender = false,
                timestamp = "Wednesday 12:03",
                type = ChatMessageType.IMAGE,
                mediaUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"
            ),
            ChatMessageItem(
                id = "m6",
                text = "Took a short clip at the backwaters during our boat cruise! 🚤",
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
                    videoDuration = if (isVideo) "0:15" else null
                )
            )
            Toast.makeText(context, if (isVideo) "Video sent securely" else "Photo sent securely", Toast.LENGTH_SHORT).show()
        }
    }

    if (activeChatProfile != null) {
        // =========================================================================
        // FULLSCREEN ACTIVE CONVERSATION VIEW WITH SCREENSHOT PREVENTION (FLAG_SECURE)
        // =========================================================================
        val partner = activeChatProfile!!

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

            // Message History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Safety notice banner
                item {
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

                items(conversationMessages, key = { it.id }) { msg ->
                    val isSender = msg.isSender

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
                    ) {
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
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
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

                                    Text(
                                        text = msg.timestamp,
                                        color = if (isSender) Color.White.copy(alpha = 0.7f) else DashboardNavMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )
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
                    }
                }
            }

            // Bottom Input Bar with Media and GIF buttons
            Surface(
                color = DashboardCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
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

                    // Send Button
                    IconButton(
                        onClick = {
                            if (chatMessageInput.isNotBlank()) {
                                conversationMessages.add(
                                    ChatMessageItem(
                                        id = "msg_${System.currentTimeMillis()}",
                                        text = chatMessageInput.trim(),
                                        isSender = true,
                                        timestamp = "Just now"
                                    )
                                )
                                chatMessageInput = ""
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
                        Toast.makeText(context, "Explore Date Night Games in Kochi!", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(18.dp))

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
                        lastMessage = "From your first Like to date night in Fort Kochi...",
                        timestamp = "Oct 24",
                        isUnread = false
                    ),
                    ChatThreadItem(
                        profile = profiles.getOrElse(3) { profiles.first() },
                        lastMessage = "Awesome! Have you visited Kashi Art Cafe?",
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
                                text = "Here's a view from Munnar tea gardens! 🌿",
                                isSender = true,
                                timestamp = "Just now",
                                type = ChatMessageType.IMAGE,
                                mediaUrl = "https://images.unsplash.com/photo-1596895111956-bf1cf0599ce5?auto=format&fit=crop&w=800&q=80"
                            )
                        )
                        Toast.makeText(context, "Munnar photo shared", Toast.LENGTH_SHORT).show()
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
                                text = "Quick Share: Munnar Scenery Photo",
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
                                text = "Check out this Alleppey Houseboat clip! 🚤",
                                isSender = true,
                                timestamp = "Just now",
                                type = ChatMessageType.VIDEO,
                                mediaUrl = "https://images.unsplash.com/photo-1593693397690-362cb9666fc2?auto=format&fit=crop&w=800&q=80",
                                videoDuration = "0:24"
                            )
                        )
                        Toast.makeText(context, "Houseboat video shared", Toast.LENGTH_SHORT).show()
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
                                text = "Quick Share: Backwaters Video Clip",
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
                    text = "Kerala & Trending Stickers / GIFs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(14.dp))

                val sampleGifs = listOf(
                    "Vanakkam! 🙏",
                    "Chaya koodan undo? ☕",
                    "Powli match! 🔥",
                    "Fort Kochi vibes 🌴",
                    "Smile thanks! 😊",
                    "Kidu look! ✨",
                    "Date night? 🍕",
                    "Sed scene aano? 😢"
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
                            "• Photo Verification: Verified selfies ensure authentic dating partners in Kerala.\n\n" +
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
