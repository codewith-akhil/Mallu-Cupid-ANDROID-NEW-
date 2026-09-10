package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.SampleProfiles
import com.mallucupid.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userDraft: OnboardingDraft,
    onOpenOnboarding: () -> Unit,
    onSignOut: () -> Unit
) {
    val profiles = remember { mutableStateListOf(*SampleProfiles.list.toTypedArray()) }
    var currentDraft by remember { mutableStateOf(userDraft) }
    var showEditProfileScreen by remember { mutableStateOf(false) }
    var showAccountSettingsScreen by remember { mutableStateOf(false) }
    var expandedProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var firstImpressionProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var activeCategoryFilter by remember { mutableStateOf<String?>(null) }

    var currentProfileIndex by remember { mutableIntStateOf(0) }
    var activeTab by remember { mutableStateOf("For you") } // "For you", "Astrology", "Music"
    var activeNav by remember { mutableStateOf("Swipe") } // "Swipe", "Explore", "Likes", "Chat", "Profile"

    var showFilterSheet by remember { mutableStateOf(false) }
    var showMatchModal by remember { mutableStateOf(false) }
    var matchedProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var actionToast by remember { mutableStateOf<String?>(null) }

    val displayProfiles = remember(profiles, activeCategoryFilter) {
        val filter = activeCategoryFilter
        if (filter.isNullOrBlank()) {
            profiles
        } else {
            val filtered = profiles.filter {
                it.lookingFor.contains(filter, ignoreCase = true) ||
                it.bio.contains(filter, ignoreCase = true) ||
                it.interests.any { interest -> interest.contains(filter, ignoreCase = true) } ||
                it.location.contains(filter, ignoreCase = true)
            }
            if (filtered.isNotEmpty()) filtered else profiles
        }
    }

    LaunchedEffect(actionToast) {
        if (actionToast != null) {
            kotlinx.coroutines.delay(2000)
            actionToast = null
        }
    }

    if (showEditProfileScreen) {
        EditProfileScreen(
            initialDraft = currentDraft,
            onSaveAndClose = { updatedDraft ->
                currentDraft = updatedDraft
                showEditProfileScreen = false
                actionToast = "Profile updated successfully"
            },
            onBack = { showEditProfileScreen = false },
            onSignOut = {
                showEditProfileScreen = false
                onSignOut()
            }
        )
        return
    }

    if (showAccountSettingsScreen) {
        AccountSettingsScreen(
            initialDraft = currentDraft,
            onSaveAndClose = { updatedDraft ->
                currentDraft = updatedDraft
                showAccountSettingsScreen = false
                actionToast = "Settings updated successfully"
            },
            onBack = { showAccountSettingsScreen = false },
            onSignOut = {
                showAccountSettingsScreen = false
                onSignOut()
            },
            onAccountDeleted = {
                showAccountSettingsScreen = false
                onSignOut()
            }
        )
        return
    }

    if (firstImpressionProfile != null) {
        FirstImpressionScreen(
            profile = firstImpressionProfile!!,
            onDismiss = { firstImpressionProfile = null },
            onSendMessage = { targetProfile, message ->
                firstImpressionProfile = null
                actionToast = "First Impression sent to ${targetProfile.name}! 💌"
                if (displayProfiles.isNotEmpty()) {
                    currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                }
            }
        )
        return
    }

    if (expandedProfile != null) {
        val activeExp = expandedProfile!!
        ExpandedProfileSheet(
            profile = activeExp,
            onDismiss = { expandedProfile = null },
            onLike = {
                val p = activeExp
                expandedProfile = null
                matchedProfile = p
                showMatchModal = true
                actionToast = "Liked ${p.name} ♥"
                if (displayProfiles.isNotEmpty()) {
                    currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                }
            },
            onDislike = {
                val p = activeExp
                expandedProfile = null
                actionToast = "Passed on ${p.name}"
                if (displayProfiles.isNotEmpty()) {
                    currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                }
            },
            onSuperLike = {
                actionToast = "Super Liked ${activeExp.name}!"
                expandedProfile = null
            },
            onReplyPrompt = { topic, msg ->
                actionToast = "Message sent to ${activeExp.name}!"
            },
            onFirstImpression = {
                val target = activeExp
                expandedProfile = null
                firstImpressionProfile = target
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main view depending on bottom nav
        when (activeNav) {
            "Swipe" -> {
                TinderSwipeableCardStack(
                    profiles = displayProfiles,
                    currentIndex = currentProfileIndex,
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    onOpenFilter = { showFilterSheet = true },
                    onLike = { likedProfile ->
                        matchedProfile = likedProfile
                        showMatchModal = true
                        actionToast = "Liked ${likedProfile.name} ♥"
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                        }
                    },
                    onDislike = { dislikedProfile ->
                        actionToast = "Passed on ${dislikedProfile.name}"
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                        }
                    },
                    onRewind = {
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex - 1 + displayProfiles.size) % displayProfiles.size
                            actionToast = "Rewound to previous profile"
                        }
                    },
                    onExpandProfile = { profile ->
                        expandedProfile = profile
                    },
                    onResetStack = {
                        currentProfileIndex = 0
                        activeCategoryFilter = null
                        actionToast = "Reset profiles stack"
                    },
                    activeCategoryFilter = activeCategoryFilter,
                    onClearFilter = {
                        activeCategoryFilter = null
                        actionToast = "Cleared filter"
                    },
                    onSuperLike = { superLikedProfile ->
                        actionToast = "Super Liked ${superLikedProfile.name}! ⭐"
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                        }
                    },
                    onFirstImpression = { targetProfile ->
                        firstImpressionProfile = targetProfile
                    }
                )
            }

            "Explore" -> {
                ExploreViewContent(
                    onSelectCategory = { cat ->
                        activeCategoryFilter = cat
                        activeNav = "Swipe"
                        actionToast = "Showing $cat in Kerala"
                    }
                )
            }

            "Likes" -> {
                LikesViewContent(
                    profiles = profiles,
                    onSelectProfile = { p ->
                        expandedProfile = p
                    }
                )
            }

            "Chat" -> {
                ChatViewContent(
                    profiles = profiles,
                    onOpenProfile = { p ->
                        expandedProfile = p
                    }
                )
            }

            "Profile" -> {
                ProfileViewContent(
                    userDraft = currentDraft,
                    onEditProfile = { showEditProfileScreen = true },
                    onOpenSettings = { showAccountSettingsScreen = true },
                    onSignOut = onSignOut
                )
            }
        }

        // Action feedback toast
        AnimatedVisibility(
            visible = actionToast != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = DashboardTerracotta,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = actionToast ?: "",
                    color = DashboardCream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }

        // Bottom Navigation Bar (Swipe, Explore, Likes, Chat, Profile)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = DashboardBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    NavTabItem("Swipe", "🔥"),
                    NavTabItem("Explore", "⊞"),
                    NavTabItem("Likes", "✦"),
                    NavTabItem("Chat", "💬"),
                    NavTabItem("Profile", "👤")
                )

                navItems.forEach { item ->
                    val isActive = activeNav == item.label
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                activeNav = item.label
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.icon,
                            fontSize = 21.sp,
                            color = if (isActive) TinderCoral else DashboardNavMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color.White else DashboardNavMuted
                        )
                    }
                }
            }
        }

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = DashboardCard,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
            ) {
                FilterSheetContent(
                    userDraft = userDraft,
                    onApply = {
                        showFilterSheet = false
                        actionToast = "Filters applied successfully"
                    }
                )
            }
        }

        // Match celebration modal
        if (showMatchModal && matchedProfile != null) {
            MatchCelebrationDialog(
                userPhoto = userDraft.photos.firstOrNull() ?: "",
                matchedProfile = matchedProfile!!,
                onDismiss = { showMatchModal = false },
                onSendMessage = {
                    showMatchModal = false
                    activeNav = "Chat"
                }
            )
        }
    }
}

private data class NavTabItem(val label: String, val icon: String)

@Composable
private fun TinderActionButton(
    symbol: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        onClick = {
            coroutineScope.launch {
                scale.animateTo(0.82f, tween(60))
                scale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            onClick()
        },
        shape = CircleShape,
        color = Color(0xF22A211D),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = elevation,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = tint,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// EXPLORE VIEW
// -------------------------------------------------------------
@Composable
private fun ExploreView(onSelectCategory: (String) -> Unit) {
    val categories = listOf(
        ExploreCategory("Kochi Nightlife", "Cocktails, Marine drive & late dinners", "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80"),
        ExploreCategory("Munnar Trekkers", "Mountain road trips & scenic views", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80"),
        ExploreCategory("Kozhikode Foodies", "Halwa, biryani & Calicut beach sunset talks", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=600&q=80"),
        ExploreCategory("Bangalore Malayalis", "Weekend return trips to Kerala", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 78.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Explore Kerala",
            color = DashboardCream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "Curated spaces to meet like-minded singles",
            color = DashboardCream.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(categories) { cat ->
                Surface(
                    onClick = { onSelectCategory(cat.title) },
                    shape = RoundedCornerShape(18.dp),
                    color = DashboardCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = cat.image,
                            contentDescription = cat.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xEB201B18), Color(0x73201B18))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = cat.title,
                                color = DashboardCream,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cat.subtitle,
                                color = DashboardPeach,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ExploreCategory(val title: String, val subtitle: String, val image: String)

// -------------------------------------------------------------
// LIKES VIEW
// -------------------------------------------------------------
@Composable
private fun LikesView(
    profiles: List<DatingProfile>,
    onMatch: (DatingProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 78.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Interested In You",
            color = DashboardCream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "People in Kerala who swiped right on your profile",
            color = DashboardCream.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(profiles) { profile ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DashboardCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = profile.photos.firstOrNull() ?: "",
                            contentDescription = profile.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${profile.name}, ${profile.age}",
                                color = DashboardCream,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = profile.location,
                                color = DashboardPeach,
                                fontSize = 12.sp
                            )
                            Text(
                                text = profile.profession,
                                color = DashboardCream.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { onMatch(profile) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Match", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CHAT LIST VIEW
// -------------------------------------------------------------
@Composable
private fun ChatListView(profiles: List<DatingProfile>) {
    var selectedChatProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var chatMessageInput by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            "Hey! Saw your profile, love your vibe!",
            "Thank you! Which part of Kochi are you staying in?",
            "Near Fort Kochi! Love the cafes around here.",
            "Awesome! Have you visited Kashi Art Cafe recently?"
        )
    }

    if (selectedChatProfile != null) {
        // Individual Conversation Screen
        val partner = selectedChatProfile!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 78.dp)
        ) {
            // Chat Top Bar
            Surface(
                color = DashboardCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedChatProfile = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = DashboardCream
                        )
                    }
                    AsyncImage(
                        model = partner.photos.firstOrNull() ?: "",
                        contentDescription = partner.name,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = partner.name,
                            color = DashboardCream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active now · ${partner.location}",
                            color = DashboardPeach,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Message Bubble List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages.size) { idx ->
                    val isMe = idx % 2 == 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            ),
                            color = if (isMe) DashboardTerracotta else DashboardCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = messages[idx],
                                color = DashboardCream,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatMessageInput,
                    onValueChange = { chatMessageInput = it },
                    placeholder = { Text("Type a message...", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DashboardCard,
                        unfocusedContainerColor = DashboardCard,
                        focusedBorderColor = DashboardTerracotta,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = DashboardCream,
                        unfocusedTextColor = DashboardCream
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (chatMessageInput.isNotBlank()) {
                            messages.add(chatMessageInput)
                            chatMessageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DashboardTerracotta)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    } else {
        // Chat List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 78.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Conversations",
                color = DashboardCream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = "Your active matches & messages",
                color = DashboardCream.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // New matches horizontal stories
            Text(
                text = "New Matches",
                color = DashboardPeach,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(profiles) { p ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedChatProfile = p }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .border(2.dp, DashboardTerracotta, CircleShape)
                        ) {
                            AsyncImage(
                                model = p.photos.firstOrNull() ?: "",
                                contentDescription = p.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = p.name,
                            color = DashboardCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Message threads
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles) { p ->
                    Surface(
                        onClick = { selectedChatProfile = p },
                        shape = RoundedCornerShape(16.dp),
                        color = DashboardCard,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = p.photos.firstOrNull() ?: "",
                                contentDescription = p.name,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    color = DashboardCream,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Hey! Loved your prompt answer :)",
                                    color = DashboardCream.copy(alpha = 0.65f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "12:20",
                                color = DashboardPeach,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// USER PROFILE VIEW (Allows reviewing & re-entering Onboarding)
// -------------------------------------------------------------
@Composable
private fun UserProfileView(
    userDraft: OnboardingDraft,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 78.dp)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Profile",
                color = DashboardCream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )

            Button(
                onClick = onEditProfile,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", color = Color.White, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Main User Card Preview
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DashboardCard,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mainPhoto = userDraft.photos.firstOrNull()
                        ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80"
                    AsyncImage(
                        model = mainPhoto,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(2.dp, DashboardTerracotta, CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "You, ${userDraft.calculatedAge}",
                            color = DashboardCream,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "⌖ ${userDraft.city.ifBlank { "Kochi" }} · Seeking ${userDraft.lookingFor.ifBlank { "Women" }}",
                            color = DashboardPeach,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Goal: ${userDraft.goal.ifBlank { "Something serious" }}",
                            color = DashboardCream.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = userDraft.bio.ifBlank { "No bio added yet. Tap edit profile to customize." },
                    color = DashboardCream.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (userDraft.interests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        userDraft.interests.take(4).forEach { interest ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = interest,
                                    color = DashboardCream,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Prompts Section
        Text(
            text = "My Prompts",
            color = DashboardCream,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Spacer(modifier = Modifier.height(10.dp))

        userDraft.prompts.forEach { p ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = p.question,
                        color = DashboardPeach,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = p.answer.ifBlank { "Tap edit profile to answer" },
                        color = DashboardCream,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Onboarding checklist / Profile completion
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x38201B18),
            border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile Strength: 100%",
                        color = DashboardCream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All 9 Steps Done",
                        color = DashboardPeach,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = DashboardTerracotta,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardPeach)
        ) {
            Text("Sign Out", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// FILTER BOTTOM SHEET CONTENT
// -------------------------------------------------------------
@Composable
private fun FilterSheetContent(
    userDraft: OnboardingDraft,
    onApply: () -> Unit
) {
    var distance by remember { mutableFloatStateOf(userDraft.distance.toFloat()) }
    var ageMin by remember { mutableFloatStateOf(userDraft.ageMin.toFloat()) }
    var ageMax by remember { mutableFloatStateOf(userDraft.ageMax.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Discovery Filters",
            color = DashboardCream,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "Filter matches based in Kerala",
            color = DashboardCream.copy(alpha = 0.65f),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Maximum Distance", color = DashboardCream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${distance.toInt()} km", color = DashboardPeach, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = distance,
            onValueChange = { distance = it },
            valueRange = 5f..200f,
            colors = SliderDefaults.colors(thumbColor = DashboardTerracotta, activeTrackColor = DashboardTerracotta)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Age Range", color = DashboardCream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${ageMin.toInt()} – ${ageMax.toInt()}", color = DashboardPeach, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = ageMax,
            onValueChange = { ageMax = it },
            valueRange = 18f..60f,
            colors = SliderDefaults.colors(thumbColor = DashboardTerracotta, activeTrackColor = DashboardTerracotta)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
        ) {
            Text("Apply Filters", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// MATCH CELEBRATION MODAL
// -------------------------------------------------------------
@Composable
private fun MatchCelebrationDialog(
    userPhoto: String,
    matchedProfile: DatingProfile,
    onDismiss: () -> Unit,
    onSendMessage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DashboardCard,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "It’s a Match! 🎉",
                    color = DashboardCream,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You and ${matchedProfile.name} liked each other",
                    color = DashboardPeach,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = userPhoto.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80" },
                    contentDescription = "You",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .border(3.dp, DashboardTerracotta, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("♥", fontSize = 28.sp, color = DashboardTerracotta)
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(
                    model = matchedProfile.photos.firstOrNull() ?: "",
                    contentDescription = matchedProfile.name,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .border(3.dp, DashboardTerracotta, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSendMessage,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
            ) {
                Text("Send a Message", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keep Swiping", color = DashboardCream.copy(alpha = 0.8f))
            }
        }
    )
}
