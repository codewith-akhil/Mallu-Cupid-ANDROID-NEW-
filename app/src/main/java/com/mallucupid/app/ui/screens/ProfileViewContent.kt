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
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.ui.theme.*

@Composable
fun ProfileViewContent(
    userDraft: OnboardingDraft,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val scrollState = rememberScrollState()
    var subscriptionCardIndex by remember { mutableIntStateOf(0) } // 0: Plus, 1: Gold, 2: Platinum
    var showFeaturesModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
            .padding(bottom = 76.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar with Settings icon (Matches screenshot 12)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showSettingsModal = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TinderTextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // User Avatar & Verified Name (Matches screenshot 12: Akhil P ✔)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
            ) {
                val photoUrl = userDraft.photos.firstOrNull()
                    ?: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=800&q=80"
                AsyncImage(
                    model = photoUrl,
                    contentDescription = userDraft.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Verified Badge at bottom right of avatar
                if (userDraft.isVerified) {
                    Surface(
                        shape = CircleShape,
                        color = TinderBlue,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Name, Age and Verified Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${userDraft.name}, ${userDraft.calculatedAge}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                if (userDraft.isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = TinderBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Edit Profile Button (Matches screenshot 12: rounded black pill)
            Button(
                onClick = onEditProfile,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit profile",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Progress Bar Banner: 100% You're all set! (Matches screenshot 12)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TinderSurface,
            border = BorderStroke(1.dp, TinderBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "100%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderCoral
                    )
                    Text(
                        text = "⭐ You're all set! See who's out there 🪅",
                        fontSize = 13.sp,
                        color = TinderTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = TinderCoral,
                    trackColor = Color(0xFFE4E4E7)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3 Quick utility cards in a row (Matches screenshot 12: Super Likes, My Boosts, Subscriptions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Super Likes Card
            QuickUtilityCard(
                iconText = "⭐",
                title = "0 Super Likes",
                actionText = "Get more",
                modifier = Modifier.weight(1f),
                onClick = { showFeaturesModal = true }
            )

            // My Boosts Card
            QuickUtilityCard(
                iconText = "⚡",
                title = "My Boosts",
                actionText = "Get more",
                modifier = Modifier.weight(1f),
                onClick = { showFeaturesModal = true }
            )

            // Subscriptions Card
            QuickUtilityCard(
                iconText = "🔥",
                title = "Subscriptions",
                actionText = "",
                modifier = Modifier.weight(1f),
                onClick = { showFeaturesModal = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Interactive Subscription Carousel Cards (Matches screenshots 12, 13, and 14)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (subscriptionCardIndex) {
                0 -> {
                    // 1. Cupid Plus Card (Matches screenshot 12)
                    SubscriptionBannerCard(
                        tierName = "CUPID PLUS",
                        flameColor = TinderCoral,
                        gradientColors = listOf(Color(0xFF2C2E3B), Color(0xFF1E202B)),
                        features = listOf("Unlimited Likes", "Unlimited Rewinds", "Passport to any location"),
                        onUpgrade = { showFeaturesModal = true }
                    )
                }

                1 -> {
                    // 2. Cupid Gold Card (Matches screenshot 13)
                    SubscriptionBannerCard(
                        tierName = "CUPID GOLD",
                        flameColor = TinderGold,
                        gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
                        features = listOf("See Who Likes You", "Top Picks daily", "Free Super Likes"),
                        onUpgrade = { showFeaturesModal = true }
                    )
                }

                2 -> {
                    // 3. Cupid Platinum Card (Matches screenshot 14)
                    SubscriptionBannerCard(
                        tierName = "CUPID PLATINUM",
                        flameColor = Color(0xFF60A5FA),
                        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                        features = listOf("Priority Likes", "Message Before Matching", "See Who Likes You"),
                        onUpgrade = { showFeaturesModal = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Carousel 3-dots indicator (Matches screenshot 12-14)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..2) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (subscriptionCardIndex == i) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (subscriptionCardIndex == i) TinderTextPrimary
                            else TinderTextMuted.copy(alpha = 0.5f)
                        )
                        .clickable { subscriptionCardIndex = i }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // "See all Features" link
        Text(
            text = "See all Features",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TinderTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showFeaturesModal = true }
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Sign Out Button
        Surface(
            onClick = onSignOut,
            shape = RoundedCornerShape(16.dp),
            color = TinderSurface,
            border = BorderStroke(1.dp, TinderBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Sign Out",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TinderCoral
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Settings Modal Dialog
    if (showSettingsModal) {
        AlertDialog(
            onDismissRequest = { showSettingsModal = false },
            title = { Text("Settings", fontWeight = FontWeight.Bold, color = TinderTextPrimary) },
            text = {
                Column {
                    Text("• App: Mallu Cupid v2.4", color = TinderTextPrimary)
                    Text("• Account: ${userDraft.name}", color = TinderTextPrimary)
                    Text("• Discovery Location: ${userDraft.city}", color = TinderTextPrimary)
                    Text("• Maximum Distance: ${userDraft.distance} km", color = TinderTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Kerala's dedicated dating community.", color = TinderTextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = TinderSurface
        )
    }

    // Full Features Comparison Modal
    if (showFeaturesModal) {
        AlertDialog(
            onDismissRequest = { showFeaturesModal = false },
            title = {
                Text("Subscription Plans", fontWeight = FontWeight.Bold, color = TinderTextPrimary)
            },
            text = {
                Column {
                    Text("🔥 Cupid Plus: Unlimited likes & rewinds", fontSize = 13.sp, color = TinderTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("👑 Cupid Gold: See who likes you + top picks", fontSize = 13.sp, color = TinderTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("💎 Cupid Platinum: Priority likes & attach notes", fontSize = 13.sp, color = TinderTextPrimary)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFeaturesModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TinderCoral)
                ) {
                    Text("Got it", color = Color.White)
                }
            },
            containerColor = TinderSurface
        )
    }
}

@Composable
private fun QuickUtilityCard(
    iconText: String,
    title: String,
    actionText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = TinderSurface,
        border = BorderStroke(1.dp, TinderBorder),
        shadowElevation = 1.dp,
        modifier = modifier.height(105.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row with emoji and + badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = iconText, fontSize = 22.sp)
                    Surface(
                        shape = CircleShape,
                        color = TinderSurface,
                        border = BorderStroke(1.dp, TinderBorder),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "+", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TinderTextPrimary)
                        }
                    }
                }

                // Bottom title and action
                Column {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                    if (actionText.isNotBlank()) {
                        Text(
                            text = actionText,
                            fontSize = 11.sp,
                            color = TinderTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionBannerCard(
    tierName: String,
    flameColor: Color,
    gradientColors: List<Color>,
    features: List<String>,
    onUpgrade: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Tier Name & Upgrade Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔥", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tierName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        onClick = onUpgrade,
                        shape = RoundedCornerShape(50),
                        color = Color.White
                    ) {
                        Text(
                            text = "UPGRADE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Features
                Column {
                    Text(
                        text = "What's Included:",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = features.joinToString("  •  "),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
