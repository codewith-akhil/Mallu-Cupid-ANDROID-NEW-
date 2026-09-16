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
    onSelectProfile: (DatingProfile) -> Unit,
    onUpgradeToPremium: () -> Unit = {}
) {
    var selectedLikesTab by remember { mutableStateOf(0) } // 0: 0 likes, 1: Likes sent, 2: Top Picks
    var showGoldModal by remember { mutableStateOf(false) }
    var modalFeatureTitle by remember { mutableStateOf("Mallu Cupid Gold") }

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
                    // 0 Likes Tab (Matches screenshot 16)
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
                            text = "See people who liked you with Mallu Cupid Gold",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Match instantly with singles nearby who have already swiped right on your profile.",
                            fontSize = 14.sp,
                            color = TinderTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = onUpgradeToPremium,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "See who likes you · ₹49/week",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                1 -> {
                    // Likes Sent Tab (Matches screenshot 17)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Subtitle banner
                        Surface(
                            color = TinderSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "With Platinum, we'll prioritise your likes.",
                                fontSize = 13.sp,
                                color = TinderTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

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

                    // Floating Upgrade button at bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Button(
                            onClick = {
                                modalFeatureTitle = "Mallu Cupid Platinum"
                                showGoldModal = true
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Upgrade Likes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                2 -> {
                    // Top Picks Tab (Matches screenshot 18)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Subtitle banner
                        Surface(
                            color = TinderSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Upgrade to Mallu Cupid Gold for more Top Picks!",
                                fontSize = 13.sp,
                                color = TinderTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        // Grid with some unlocked and some blurred/locked cards (Matches screenshot 18)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            items(profiles) { profile ->
                                val isLocked = profile.id.toIntOrNull() ?: 0 > 2
                                ProfileGridCard(
                                    profile = profile,
                                    timeBadge = "12h left",
                                    isLocked = isLocked,
                                    onClick = {
                                        if (isLocked) {
                                            modalFeatureTitle = "Mallu Cupid Gold Top Picks"
                                            showGoldModal = true
                                        } else {
                                            onSelectProfile(profile)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Floating Unlock All button at bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Button(
                            onClick = {
                                modalFeatureTitle = "Mallu Cupid Gold Top Picks"
                                showGoldModal = true
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Unlock all Top Picks",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Upgrade Modal Dialog
    if (showGoldModal) {
        AlertDialog(
            onDismissRequest = { showGoldModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "👑", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = modalFeatureTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Upgrade your experience with exclusive features:",
                        fontSize = 13.sp,
                        color = TinderTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    BenefitBullet("✓ See Who Likes You before swiping")
                    BenefitBullet("✓ Daily curated Top Picks near you")
                    BenefitBullet("✓ Priority Likes seen faster by singles")
                    BenefitBullet("✓ Unlimited Rewinds and zero ads")
                    BenefitBullet("✓ Free Super Likes every week")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoldModal = false
                        onUpgradeToPremium()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = TinderGold)
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoldModal = false }) {
                    Text("Maybe later", color = TinderTextSecondary)
                }
            },
            containerColor = TinderSurface
        )
    }
}

@Composable
private fun ProfileGridCard(
    profile: DatingProfile,
    timeBadge: String,
    isLocked: Boolean = false,
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isLocked) Modifier.blur(14.dp) else Modifier),
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

            if (isLocked) {
                // Gold lock in center
                Surface(
                    shape = CircleShape,
                    color = TinderGold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
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

@Composable
private fun BenefitBullet(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = TinderTextPrimary,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
