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
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onOpenFaceVerification: () -> Unit = {},
    onShowSystemScreen: ((String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    // Sign-out confirmation dialog state
    var showSignOutDialog by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
            .padding(bottom = 76.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar with Settings icon (Navigates to Account Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Account Settings",
                    tint = TinderTextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // User Profile Photo — 4:5 portrait card (user requirement: NOT a round card)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .aspectRatio(0.8f) // 4:5 like Tinder profile cards
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val photoUrl = userDraft.photos.firstOrNull()
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = userDraft.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // No photo yet — neutral placeholder instead of a hardcoded fake avatar.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEDE7E1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "No profile photo",
                            tint = Color(0xFFB9AFA6),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Verified Badge at bottom right of photo
                if (userDraft.isVerified) {
                    Surface(
                        shape = CircleShape,
                        color = TinderBlue,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(30.dp)
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

            // Action Buttons Row: Edit Profile + Verify Now
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Profile Button (Matches screenshot 12: rounded black pill)
                Button(
                    onClick = onEditProfile,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit profile",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Verify Now Button (Tinder style)
                if (!userDraft.isVerified) {
                    Button(
                        onClick = onOpenFaceVerification,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = SuperBlue),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Verify Now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
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

        Spacer(modifier = Modifier.height(20.dp))

        // System UI States Showcase (Loading, No Internet, Error)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TinderSurface,
            border = BorderStroke(1.dp, TinderBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = DashboardPeach, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System UI States Preview",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onShowSystemScreen?.invoke("LOADING") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text("Loading", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onShowSystemScreen?.invoke("NO_INTERNET") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text("No Net", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onShowSystemScreen?.invoke("ERROR") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text("Error", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Sign Out Button
        Surface(
            onClick = { showSignOutDialog = true },
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

    // Sign Out confirmation dialog (child of the existing root container — no inset change)
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!signingOut) showSignOutDialog = false
            },
            title = {
                Text(
                    text = "Sign Out?",
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out? You'll need your email and password to sign back in.",
                    color = DashboardMutedBeige,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        signingOut = true
                        onSignOut()
                    },
                    enabled = !signingOut,
                    colors = ButtonDefaults.buttonColors(containerColor = NopeCoral)
                ) {
                    if (signingOut) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutDialog = false },
                    enabled = !signingOut
                ) {
                    Text("Cancel", color = DashboardNavMuted)
                }
            },
            containerColor = DashboardCard
        )
    }
}
