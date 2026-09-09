package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.ui.theme.*

data class ChatThread(
    val profile: DatingProfile,
    val lastMessage: String,
    val timestamp: String,
    val isUnread: Boolean = false
)

@Composable
fun ChatViewContent(
    profiles: List<DatingProfile>,
    onOpenProfile: (DatingProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeChatProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var chatMessageInput by remember { mutableStateOf("") }
    var showSafetyCenter by remember { mutableStateOf(false) }

    val conversationMessages = remember {
        mutableStateListOf(
            "Hey! Noticed you're also exploring cafes around Kochi!",
            "Haha yes! Fort Kochi cafes on Sunday mornings are an absolute ritual.",
            "That's awesome! Have you visited Kashi Art Cafe recently?",
            "Loved the art installation there last weekend! What about you?"
        )
    }

    if (activeChatProfile != null) {
        // Fullscreen Active Conversation View
        val partner = activeChatProfile!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TinderBg)
        ) {
            // Chat Top Bar
            Surface(
                color = TinderSurface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeChatProfile = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TinderTextPrimary
                        )
                    }

                    AsyncImage(
                        model = partner.photos.firstOrNull() ?: "",
                        contentDescription = partner.name,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { onOpenProfile(partner) },
                        contentScale = ContentScale.Crop
                    )

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
                                color = TinderTextPrimary
                            )
                            if (partner.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = TinderBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = "Active now · ${partner.location}",
                            fontSize = 12.sp,
                            color = TinderGreen
                        )
                    }

                    IconButton(onClick = { showSafetyCenter = true }) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safety Center",
                            tint = TinderTextSecondary
                        )
                    }
                }
            }

            // Message History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversationMessages.indices.toList()) { index ->
                    val isSender = index % 2 == 1
                    val msg = conversationMessages[index]

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSender) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isSender) 18.dp else 4.dp,
                                bottomEnd = if (isSender) 4.dp else 18.dp
                            ),
                            color = if (isSender) TinderCoral else TinderSurface,
                            border = if (isSender) null else BorderStroke(1.dp, TinderBorder),
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg,
                                color = if (isSender) Color.White else TinderTextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = TinderSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatMessageInput,
                        onValueChange = { chatMessageInput = it },
                        placeholder = { Text("Type a message...", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TinderCoral,
                            unfocusedBorderColor = TinderBorder
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (chatMessageInput.isNotBlank()) {
                                conversationMessages.add(chatMessageInput.trim())
                                chatMessageInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(TinderCoral)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Main Chat List View (Matches screenshot 15)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TinderBg)
                .padding(bottom = 76.dp)
        ) {
            // Top Bar with Shield and Game icons
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
                    color = TinderTextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSafetyCenter = true }) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safety Center",
                            tint = TinderTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {}) {
                        Text(text = "🎲", fontSize = 20.sp)
                    }
                }
            }

            // Search Bar (Matches screenshot 15: Search 37 matches)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TinderSurface,
                border = BorderStroke(1.dp, TinderBorder),
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
                        tint = TinderTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search ${profiles.size + 12} matches",
                        color = TinderTextMuted,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // New Matches Horizontal Row (Matches screenshot 15: Rounded square cards with verified badges)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "New Matches",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFE4E4E7))
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
                                        color = TinderBlue,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Verified",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = profile.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TinderTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Messages Section (Matches screenshot 15)
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Messages",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                val sampleThreads = listOf(
                    ChatThread(
                        profile = profiles.first(),
                        lastMessage = "Hey! Loved your prompt answer :)",
                        timestamp = "12:20",
                        isUnread = true
                    ),
                    ChatThread(
                        profile = profiles.getOrElse(1) { profiles.first() },
                        lastMessage = "From your first Like to date night...",
                        timestamp = "Yesterday"
                    ),
                    ChatThread(
                        profile = profiles.getOrElse(2) { profiles.first() },
                        lastMessage = "https://wa.me/qr/...",
                        timestamp = "Oct 24"
                    ),
                    ChatThread(
                        profile = profiles.getOrElse(3) { profiles.first() },
                        lastMessage = "Awesome! Have you visited Kashi Art Cafe?",
                        timestamp = "Sep 15"
                    )
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sampleThreads) { thread ->
                        Surface(
                            onClick = { activeChatProfile = thread.profile },
                            color = TinderSurface,
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
                                            color = TinderTextPrimary
                                        )
                                        if (thread.profile.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = TinderBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = thread.lastMessage,
                                        fontSize = 13.sp,
                                        color = if (thread.isUnread) TinderTextPrimary else TinderTextSecondary,
                                        fontWeight = if (thread.isUnread) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = thread.timestamp,
                                        fontSize = 11.sp,
                                        color = TinderTextMuted
                                    )
                                    if (thread.isUnread) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(TinderCoral)
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

    // Safety Center Dialog
    if (showSafetyCenter) {
        AlertDialog(
            onDismissRequest = { showSafetyCenter = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = TinderBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safety & Well-being", fontWeight = FontWeight.Bold, color = TinderTextPrimary)
                }
            },
            text = {
                Text(
                    text = "Mallu Cupid provides round-the-clock safety tools:\n\n" +
                            "• Photo verification to prevent catfishing\n" +
                            "• Real-time message reporting and block list\n" +
                            "• Private meetup guidelines and emergency contacts in Kerala.",
                    color = TinderTextPrimary,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSafetyCenter = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TinderCoral)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            containerColor = TinderSurface
        )
    }
}
