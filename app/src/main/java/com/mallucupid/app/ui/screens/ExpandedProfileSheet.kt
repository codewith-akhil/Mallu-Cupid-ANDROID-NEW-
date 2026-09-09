package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.PromptItem
import com.mallucupid.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedProfileSheet(
    profile: DatingProfile,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSuperLike: () -> Unit,
    onReplyPrompt: (String, String) -> Unit = { _, _ -> },
    onFirstImpression: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyTopic by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var actionToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actionToast) {
        if (actionToast != null) {
            kotlinx.coroutines.delay(2200)
            actionToast = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp)
        ) {
            // Main Photo with indicator dashes and floating down arrow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                val currentPhoto = profile.photos.getOrElse(selectedPhotoIndex) {
                    profile.photos.firstOrNull() ?: ""
                }

                AsyncImage(
                    model = currentPhoto,
                    contentDescription = profile.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (profile.photos.isNotEmpty()) {
                                selectedPhotoIndex = (selectedPhotoIndex + 1) % profile.photos.size
                            }
                        },
                    contentScale = ContentScale.Crop
                )

                // Top photo dashes indicator
                if (profile.photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.photos.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (index == selectedPhotoIndex) Color.White
                                        else Color.White.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }
                }

                // Top Down Arrow button to collapse back to swipe mode (matches screenshot 1 & 4)
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 16.dp)
                        .size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse profile",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Gradient shadow at bottom of photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, TinderBg)
                            )
                        )
                )

                // Quick reply chip on main photo
                Surface(
                    onClick = {
                        replyTopic = "Photo of ${profile.name}"
                        showReplyDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reply",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Profile Info Header Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Name, Age and Verification badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${profile.name}, ${profile.age}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                    if (profile.isVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = TinderBlue,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Looking for card (Matches screenshot 4)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TinderTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Looking for",
                                fontSize = 12.sp,
                                color = TinderTextSecondary
                            )
                            Text(
                                text = profile.lookingFor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Essentials card (Matches screenshot 4: Distance, Gender, Location)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = TinderTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Essentials",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TinderTextPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = null,
                                tint = TinderTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        DetailItemRow(
                            icon = Icons.Default.LocationOn,
                            label = "${profile.distanceKm} km away"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailItemRow(
                            icon = Icons.Default.Person,
                            label = profile.essentialsGender
                        )
                        if (profile.profession.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DetailItemRow(
                                icon = Icons.Default.Work,
                                label = profile.profession
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Prompts section with Reply button (Matches screenshot 3)
                profile.prompts.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TinderSurface,
                        border = BorderStroke(1.dp, TinderBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "❝  ${prompt.question}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TinderTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = null,
                                    tint = TinderTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = prompt.answer,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary,
                                lineHeight = 26.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    onClick = {
                                        replyTopic = prompt.question
                                        showReplyDialog = true
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = TinderBg,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = TinderTextPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reply",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TinderTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Lifestyle Card (Matches screenshot 3: Drinking, Smoking, Workout, Pets)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TinderTextPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lifestyle",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LifestyleRow(title = "Drinking", value = profile.drinking, icon = "🍷")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "How often do you smoke?", value = profile.smoking, icon = "🚬")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "Workout", value = profile.workout, icon = "🏋️")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "Pets", value = profile.pets, icon = "🐾")

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                onClick = {
                                    replyTopic = "Lifestyle of ${profile.name}"
                                    showReplyDialog = true
                                },
                                shape = RoundedCornerShape(50),
                                color = TinderBg,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = TinderTextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reply",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TinderTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // More about me Card (Matches screenshot 2: Communication, Love style, Education, Zodiac)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TinderTextPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "More about me",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LifestyleRow(title = "Communication style", value = profile.communicationStyle, icon = "💬")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "Love style", value = profile.loveStyle, icon = "💌")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "Education", value = profile.education, icon = "🎓")
                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 10.dp))

                        LifestyleRow(title = "Zodiac", value = profile.astrologyStar, icon = "✨")

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                onClick = {
                                    replyTopic = "More about ${profile.name}"
                                    showReplyDialog = true
                                },
                                shape = RoundedCornerShape(50),
                                color = TinderBg,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = TinderTextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reply",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TinderTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interests Card (Matches screenshot 1 & 2: Art, Painting, Drawing, Foodie)
                if (profile.interests.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TinderSurface,
                        border = BorderStroke(1.dp, TinderBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = TinderCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Interests",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TinderTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OptInInterestsRow(interests = profile.interests)

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    onClick = {
                                        replyTopic = "Interests of ${profile.name}"
                                        showReplyDialog = true
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = TinderBg,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = TinderTextPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reply",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TinderTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons (Matches screenshot 1: Share, Block, Report)
                Surface(
                    onClick = { actionToast = "Profile link copied to clipboard" },
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Share ${profile.name}'s profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TinderTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = { actionToast = "${profile.name} has been blocked" },
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Block ${profile.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TinderTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = { actionToast = "Report submitted. Thank you for keeping our community safe." },
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Report ${profile.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TinderCoral
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Floating Action Buttons bar docked at bottom (Matches screenshots 1-4: ✕, ★, ♥)
        Surface(
            color = TinderSurface.copy(alpha = 0.95f),
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Dislike (✕)
                Surface(
                    onClick = {
                        onDislike()
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = TinderSurface,
                    border = BorderStroke(1.5.dp, TinderBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dislike",
                            tint = TinderCoral,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // 2. Like (♥)
                Surface(
                    onClick = {
                        onLike()
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = TinderSurface,
                    border = BorderStroke(1.5.dp, TinderBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = TinderCoral,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. First Impression (✈)
                if (onFirstImpression != null) {
                    Spacer(modifier = Modifier.width(22.dp))
                    Surface(
                        onClick = {
                            onDismiss()
                            onFirstImpression()
                        },
                        shape = CircleShape,
                        color = TinderSurface,
                        border = BorderStroke(1.5.dp, TinderBorder),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "First Impression",
                                tint = Color(0xFF5AB9F8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Toast feedback
        AnimatedVisibility(
            visible = actionToast != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = TinderTextPrimary,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = actionToast ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        // Interactive Reply Dialog
        if (showReplyDialog) {
            AlertDialog(
                onDismissRequest = { showReplyDialog = false },
                title = {
                    Text(
                        text = "Reply to ${profile.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = replyTopic,
                            fontSize = 12.sp,
                            color = TinderTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Send an icebreaker...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onReplyPrompt(replyTopic, replyText)
                                actionToast = "Message sent to ${profile.name}!"
                                replyText = ""
                                showReplyDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TinderCoral),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Send", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplyDialog = false }) {
                        Text("Cancel", color = TinderTextSecondary)
                    }
                },
                containerColor = TinderSurface
            )
        }
    }
}

@Composable
private fun DetailItemRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TinderTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = TinderTextPrimary
        )
    }
}

@Composable
private fun LifestyleRow(title: String, value: String, icon: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            color = TinderTextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TinderTextPrimary
            )
        }
    }
}

@Composable
private fun OptInInterestsRow(interests: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        interests.take(4).forEach { interest ->
            Surface(
                shape = RoundedCornerShape(50),
                color = TinderBg,
                border = BorderStroke(1.dp, TinderBorder)
            ) {
                Text(
                    text = interest,
                    fontSize = 12.sp,
                    color = TinderTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
