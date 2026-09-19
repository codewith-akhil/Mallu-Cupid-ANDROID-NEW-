package com.mallucupid.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*

/**
 * Real dating-app profile page.
 *
 * Top-to-bottom:
 *   a) Settings gear (top bar)
 *   b) 4:5 portrait photo card with verified badge
 *   c) Horizontal LazyRow thumbnail strip of secondary photos
 *   d) Name + age + verified badge
 *   e) City + country line
 *   f) Job title + company line
 *   g) College + course line
 *   h) Edit Profile + Verify Now buttons
 *   i) Profile-strength banner (REAL 8-field computation, 4-tier messaging)
 *   j) Bio section
 *   k) Interests chips
 *   l) Languages chips
 *   m) Prompts (only answered)
 *   n) Essentials 2-col grid
 *   o) Premium status / Go Premium CTA (4-state)
 *   p) Sign Out button + dialog
 */
@OptIn(ExperimentalLayoutApi::class)
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

    // ---- Profile-strength (REAL computation from 8 fields, 12.5% each) ----
    val strengthState = remember(userDraft) {
        derivedStateOf {
            var score = 0
            if (userDraft.photos.isNotEmpty()) score += 1
            if (userDraft.bio.length >= 10) score += 1
            if (userDraft.interests.size >= 3) score += 1
            if (userDraft.jobTitle.isNotBlank() || userDraft.college.isNotBlank()) score += 1
            if (userDraft.city.isNotBlank()) score += 1
            if (userDraft.prompts.any { it.answer.isNotBlank() }) score += 1
            if (userDraft.languages.isNotEmpty()) score += 1
            if (userDraft.gender.isNotBlank()) score += 1
            score
        }
    }
    val strengthPct = strengthState.value / 8f
    val strengthMsg = when {
        strengthPct >= 1f -> "⭐ You're all set! See who's out there 🪅"
        strengthPct >= 0.8f -> "Almost there — finish strong"
        strengthPct >= 0.41f -> "Your profile is taking shape — keep going"
        else -> "Add more to your profile to get better matches"
    }

    // ---- Premium subscription state (4-state: Loading / Active / NoSub / Error) ----
    var premiumState by remember { mutableStateOf<PremiumState>(PremiumState.Loading) }
    var showPremiumFlow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = SessionManager.current()?.userId
        if (uid.isNullOrBlank()) {
            premiumState = PremiumState.Error
            return@LaunchedEffect
        }
        premiumState = runCatching {
            SupabaseRepository.getActiveSubscription(uid)
                ?.takeIf { sub ->
                    // Filter out expired subs locally so we don't badge a stale row.
                    sub.expiresAt?.let {
                        runCatching {
                            java.time.OffsetDateTime.parse(it)
                                .isAfter(java.time.OffsetDateTime.now())
                        }.getOrDefault(true)
                    } ?: true
                }?.let { PremiumState.Active(it) } ?: PremiumState.NoSub
        }.getOrDefault(PremiumState.Error)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
            .padding(bottom = 76.dp)
            .verticalScroll(scrollState)
    ) {
        // a) Top Bar with Settings gear (existing)
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

        // b) User Profile Photo — 4:5 portrait card (existing)
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

            // c) Horizontal LazyRow thumbnail strip of secondary photos
            val secondaryPhotos = userDraft.photos.drop(1)
            if (secondaryPhotos.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp)
                ) {
                    items(secondaryPhotos) { photo ->
                        AsyncImage(
                            model = photo,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // d) Name, Age and Verified Icon (existing)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameAge = buildString {
                    if (userDraft.name.isNotBlank()) {
                        append(userDraft.name)
                        val age = userDraft.calculatedAge
                        if (age > 0) {
                            append(", ")
                            append(age)
                        }
                    } else if (userDraft.calculatedAge > 0) {
                        append(userDraft.calculatedAge)
                    }
                }
                if (nameAge.isNotBlank()) {
                    Text(
                        text = nameAge,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary
                    )
                }
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

            // e) City + country line (hide if both blank)
            val locationLine = listOf(userDraft.city, userDraft.country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (locationLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TinderTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = locationLine,
                        fontSize = 13.sp,
                        color = TinderTextSecondary
                    )
                }
            }

            // f) Job title + company line (hide if both blank)
            val jobLine = listOf(userDraft.jobTitle, userDraft.company)
                .filter { it.isNotBlank() }
                .joinToString(" at ")
            if (jobLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = null,
                        tint = TinderTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = jobLine,
                        fontSize = 13.sp,
                        color = TinderTextSecondary
                    )
                }
            }

            // g) College + course line (hide if both blank)
            val collegeLine = listOf(userDraft.college, userDraft.courseName)
                .filter { it.isNotBlank() }
                .joinToString(" — ")
            if (collegeLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = TinderTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = collegeLine,
                        fontSize = 13.sp,
                        color = TinderTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // h) Action Buttons Row: Edit Profile + Verify Now (existing)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Profile Button (rounded black pill)
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

        // i) Profile-strength banner — REAL computation, 4-tier messaging
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
                        text = "${(strengthPct * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderCoral
                    )
                    Text(
                        text = strengthMsg,
                        fontSize = 13.sp,
                        color = TinderTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { strengthPct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = TinderCoral,
                    trackColor = TinderBorder
                )
            }
        }

        // j) Bio section (hide if blank)
        if (userDraft.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            ProfileSection(title = "About me") {
                Text(
                    text = userDraft.bio,
                    fontSize = 14.sp,
                    color = TinderTextPrimary,
                    lineHeight = 20.sp
                )
            }
        }

        // k) Interests FlowRow chips (hide if empty)
        if (userDraft.interests.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            ProfileSection(title = "Interests") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    userDraft.interests.forEach { interest ->
                        ChipPill(text = interest, icon = Icons.Default.Favorite, tint = NopeCoral)
                    }
                }
            }
        }

        // l) Languages FlowRow chips (hide if empty)
        if (userDraft.languages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            ProfileSection(title = "Languages") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    userDraft.languages.forEach { language ->
                        ChipPill(text = language, icon = Icons.Default.Translate, tint = SuperBlue)
                    }
                }
            }
        }

        // m) Prompts section (only answered prompts)
        val answeredPrompts = userDraft.prompts.filter { it.answer.isNotBlank() }
        if (answeredPrompts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            ProfileSection(title = "Prompts") {
                answeredPrompts.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TinderSurface,
                        border = BorderStroke(1.dp, TinderBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = prompt.question,
                                fontSize = 12.sp,
                                color = TinderTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = prompt.answer,
                                fontSize = 14.sp,
                                color = TinderTextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        // n) Essentials 2-col grid (non-blank only)
        val essentials = buildList {
            if (userDraft.zodiac.isNotBlank()) add("Zodiac" to userDraft.zodiac)
            if (userDraft.drinking.isNotBlank()) add("Drinking" to userDraft.drinking)
            if (userDraft.smoking.isNotBlank()) add("Smoking" to userDraft.smoking)
            if (userDraft.workout.isNotBlank()) add("Workout" to userDraft.workout)
            if (userDraft.pets.isNotBlank()) add("Pets" to userDraft.pets)
            if (userDraft.height.isNotBlank()) add("Height" to userDraft.height)
            if (userDraft.communicationStyle.isNotBlank()) add("Communication" to userDraft.communicationStyle)
            if (userDraft.loveStyle.isNotBlank()) add("Love style" to userDraft.loveStyle)
            if (userDraft.lookingFor.isNotBlank()) add("Looking for" to userDraft.lookingFor)
            if (userDraft.goal.isNotBlank()) add("Goal" to userDraft.goal)
        }
        if (essentials.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            ProfileSection(title = "Essentials") {
                // 2-column grid via paired chunking
                val rows = essentials.chunked(2)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, value) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TinderSurface,
                                border = BorderStroke(1.dp, TinderBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = TinderTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = value,
                                        fontSize = 14.sp,
                                        color = TinderTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        // Pad the trailing column if odd count
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // o) Premium status / Go Premium CTA (hide on Loading/Error)
        when (val state = premiumState) {
            is PremiumState.Active -> {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = TinderGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MalluCupid Pro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val planLabel = state.sub.planName ?: state.sub.plan ?: "Pro"
                        Text(
                            text = "Plan: $planLabel",
                            fontSize = 13.sp,
                            color = TinderTextSecondary
                        )
                        state.sub.expiresAt?.let { expiry ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Renews: $expiry",
                                fontSize = 13.sp,
                                color = TinderTextSecondary
                            )
                        }
                    }
                }
            }
            PremiumState.NoSub -> {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderCoral),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = TinderCoral,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Go Premium",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Unlimited likes, see who likes you, unlimited chat & rewinds.",
                            fontSize = 13.sp,
                            color = TinderTextSecondary,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // 3 plans listed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlanPill(label = "Weekly", price = "₹49", modifier = Modifier.weight(1f))
                            PlanPill(label = "Monthly", price = "₹99", modifier = Modifier.weight(1f))
                            PlanPill(label = "Yearly", price = "₹799", modifier = Modifier.weight(1f), highlight = true)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showPremiumFlow = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = TinderCoral),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text = "Upgrade Now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            else -> { /* Loading / Error — hide card */ }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // p) Sign Out Button (existing)
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

    // Full-screen overlay — Premium subscription flow
    if (showPremiumFlow) {
        PremiumSubscriptionFlow(
            onSuccess = { showPremiumFlow = false },
            onBack = { showPremiumFlow = false }
        )
    }

    // Sign Out confirmation dialog (existing — unchanged)
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

/** Internal state for the premium CTA card. */
private sealed interface PremiumState {
    data object Loading : PremiumState
    data class Active(val sub: com.mallucupid.app.data.remote.SubscriptionDto) : PremiumState
    data object NoSub : PremiumState
    data object Error : PremiumState
}

/** Reusable titled section card. */
@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TinderSurface,
        border = BorderStroke(1.dp, TinderBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TinderTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/** Small pill chip used for interests / languages. */
@Composable
private fun ChipPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                color = TinderTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Small plan pill on the Go Premium CTA. */
@Composable
private fun PlanPill(
    label: String,
    price: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) TinderGold.copy(alpha = 0.15f) else TinderSurface,
        border = BorderStroke(1.dp, if (highlight) TinderGold else TinderBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (highlight) {
                Text(
                    text = "Best value",
                    fontSize = 9.sp,
                    color = TinderGold,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = TinderTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = price,
                fontSize = 14.sp,
                color = TinderTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
