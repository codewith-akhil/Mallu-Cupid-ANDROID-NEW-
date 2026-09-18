package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*

@Composable
fun LikesViewContent(
    userId: String?,
    profiles: List<DatingProfile>,
    onSelectProfile: (DatingProfile) -> Unit
) {
    var selectedLikesTab by remember { mutableStateOf(0) } // 0: Likes received, 1: Likes sent, 2: Top Picks

    // Real DB state — loaded async on first composition / when userId changes.
    var likesReceivedCount by remember { mutableStateOf(0) }
    var likesSentProfiles by remember { mutableStateOf<List<DatingProfile>>(emptyList()) }
    var likesLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        if (userId.isNullOrBlank()) {
            likesLoading = false
            return@LaunchedEffect
        }
        try {
            likesReceivedCount = SupabaseRepository.getLikesReceivedCount(userId)
            likesSentProfiles = SupabaseRepository.getLikesSent(userId)
        } finally {
            likesLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
            .padding(bottom = 76.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Likes",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TinderTextPrimary
            )
        }

        // Three Tabs: 0 likes | Likes sent | Top Picks (Matches screenshots 16, 17, 18)
        TabRow(
            selectedTabIndex = selectedLikesTab,
            containerColor = TinderSurface,
            contentColor = TinderTextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedLikesTab]),
                    color = TinderCoral
                )
            }
        ) {
            Tab(
                selected = selectedLikesTab == 0,
                onClick = { selectedLikesTab = 0 },
                text = {
                    Text(
                        text = "$likesReceivedCount likes",
                        fontWeight = if (selectedLikesTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedLikesTab == 0) TinderTextPrimary else TinderTextSecondary
                    )
                }
            )

            Tab(
                selected = selectedLikesTab == 1,
                onClick = { selectedLikesTab = 1 },
                text = {
                    Text(
                        text = "Likes sent",
                        fontWeight = if (selectedLikesTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedLikesTab == 1) TinderTextPrimary else TinderTextSecondary
                    )
                }
            )

            Tab(
                selected = selectedLikesTab == 2,
                onClick = { selectedLikesTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Top Picks",
                            fontWeight = if (selectedLikesTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedLikesTab == 2) TinderTextPrimary else TinderTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Red notification dot (Matches screenshot 16-18)
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TinderCoral)
                        )
                    }
                }
            )
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedLikesTab) {
                0 -> {
                    // Likes Tab — FREE (no paywall; payments removed entirely)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 3D Glossy Cupid Heart Graphic
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFFFFCCD5), Color(0xFFFFF0F3))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💘",
                                fontSize = 64.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = if (likesReceivedCount > 0)
                                "$likesReceivedCount ${if (likesReceivedCount == 1) "person likes" else "people like"} you!"
                            else
                                "No likes yet — keep swiping!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "When someone likes you back, it's an instant match and you can start chatting right away.",
                            fontSize = 14.sp,
                            color = TinderTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                1 -> {
                    // Likes Sent Tab — free, no upgrade button
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Grid of profiles (real DB-loaded list, with a centered
                        // loading spinner while the fetch is in flight).
                        if (likesLoading && likesSentProfiles.isEmpty()) {
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
                        } else if (likesSentProfiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "You haven't liked anyone yet.",
                                    fontSize = 14.sp,
                                    color = TinderTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                items(likesSentProfiles) { profile ->
                                    ProfileGridCard(
                                        profile = profile,
                                        timeBadge = "24h left",
                                        onClick = { onSelectProfile(profile) }
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Top Picks Tab — everything unlocked (payments removed)
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (profiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Top Picks will appear as new singles join nearby.",
                                    fontSize = 14.sp,
                                    color = TinderTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                items(profiles) { profile ->
                                    ProfileGridCard(
                                        profile = profile,
                                        timeBadge = "12h left",
                                        onClick = { onSelectProfile(profile) }
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

@Composable
private fun ProfileGridCard(
    profile: DatingProfile,
    timeBadge: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = TinderSurface,
        border = BorderStroke(1.dp, TinderBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = profile.photos.firstOrNull() ?: "",
                contentDescription = profile.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient at bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 180f
                        )
                    )
            )

            // Top Badge (e.g. 24h left / 12h left)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = timeBadge,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Star / Super button in bottom right
            Surface(
                shape = CircleShape,
                color = TinderBlue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Name and Age at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "${profile.name}, ${profile.age}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

