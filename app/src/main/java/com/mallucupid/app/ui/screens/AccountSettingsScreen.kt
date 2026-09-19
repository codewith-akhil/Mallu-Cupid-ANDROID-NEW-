package com.mallucupid.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.BlockedUser
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.remote.ProfileSettingsPatch
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    initialDraft: OnboardingDraft,
    onSaveAndClose: (OnboardingDraft) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initialDraft) }

    // Sub-screen navigation states
    var currentSubView by remember { mutableStateOf("MAIN") } // "MAIN", "BLOCKED_USERS", "DELETE_ACCOUNT", "ACTIVE_STATUS", "FACE_VERIFICATION"
    var userToUnblock by remember { mutableStateOf<BlockedUser?>(null) }

    // ---- Action loading states ----
    var unblockLoading by remember { mutableStateOf(false) }
    var deleteLoading by remember { mutableStateOf(false) }
    // Sign-out confirmation dialog state
    var showSignOutDialog by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    // ---- Settings persistence state (MAIN view only) ----
    val sessionUserId = remember { SessionManager.current()?.userId }
    var settingsLoading by remember { mutableStateOf(true) }
    var settingsSaving by remember { mutableStateOf(false) }

    /**
     * Hydrate discovery / chat-privacy toggles from `profiles` + `user_settings`
     * on first composition of the MAIN view. Sub-screens (ActiveStatus,
     * EmailSettings, PushNotifications) load their own slices lazily.
     */
    LaunchedEffect(Unit) {
        val uid = sessionUserId
        if (uid == null) {
            settingsLoading = false
            return@LaunchedEffect
        }
        val (profile, settings) = withContext(Dispatchers.IO) {
            SupabaseRepository.loadProfileSettings(uid) to SupabaseRepository.loadUserSettings(uid)
        }
        if (profile != null || settings != null) {
            draft = draft.copy(
                isOnline = profile?.isOnline ?: draft.isOnline,
                photoVerifiedOnlyChat = profile?.photoVerifiedOnlyChat ?: draft.photoVerifiedOnlyChat,
                maxDistanceKm = profile?.maxDistanceKm ?: draft.maxDistanceKm,
                ageMin = profile?.ageMin ?: draft.ageMin,
                ageMax = profile?.ageMax ?: draft.ageMax,
                interestedIn = profile?.interestedIn?.takeIf { it.isNotEmpty() } ?: draft.interestedIn,
                showActiveStatus = settings?.showActiveStatus ?: draft.showActiveStatus,
                showRecentlyActiveStatus = settings?.showRecentlyActiveStatus ?: draft.showRecentlyActiveStatus,
            )
        }
        settingsLoading = false
    }

    /** Persist a partial profile patch to the `profiles` table. */
    fun saveProfilePatch(patch: ProfileSettingsPatch) {
        val uid = sessionUserId ?: return
        coroutineScope.launch {
            settingsSaving = true
            withContext(Dispatchers.IO) { SupabaseRepository.saveProfileSettings(patch, uid) }
            settingsSaving = false
        }
    }

    when (currentSubView) {
        "FACE_VERIFICATION" -> {
            FaceVerificationScreen(
                draft = draft,
                onVerificationComplete = { updated ->
                    draft = updated
                    // Persist is_verified=true to Supabase
                    coroutineScope.launch {
                        val uid = SessionManager.current()?.userId
                        if (uid != null) {
                            withContext(Dispatchers.IO) {
                                SupabaseRepository.saveProfile(uid, updated.registeredEmail.ifBlank { SessionManager.current()?.email.orEmpty() }, updated)
                            }
                        }
                    }
                    currentSubView = "MAIN"
                    Toast.makeText(context, "Face Verified! Blue checkmark applied", Toast.LENGTH_SHORT).show()
                },
                onBack = { currentSubView = "MAIN" }
            )
        }

        "ACTIVE_STATUS" -> {
            ActiveStatusScreen(
                draft = draft,
                onUpdateDraft = { draft = it },
                onBack = { currentSubView = "MAIN" }
            )
        }

        
        "BLOCKED_USERS" -> {
            BlockedUsersScreen(
                blockedUsers = draft.blockedUsers,
                onUnblockUser = { user ->
                    userToUnblock = user
                },
                onBack = { currentSubView = "MAIN" }
            )

            if (userToUnblock != null) {
                val targetUser = userToUnblock!!
                AlertDialog(
                    onDismissRequest = { userToUnblock = null },
                    containerColor = DashboardCard,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = DashboardPeach,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Unblock ${targetUser.name}?",
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Text(
                            text = "They will once again be able to see your profile on Mallu Cupid and match with you.",
                            color = DashboardMutedBeige,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (!unblockLoading) {
                                    unblockLoading = true
                                    coroutineScope.launch {
                                        val uid = SessionManager.current()?.userId
                                        val err = if (uid != null) {
                                            SupabaseRepository.unblockUser(uid, targetUser.id)
                                        } else "Not signed in"
                                        unblockLoading = false
                                        if (err != null) {
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val updatedBlocked = draft.blockedUsers.filter { it.id != targetUser.id }
                                            draft = draft.copy(blockedUsers = updatedBlocked)
                                            Toast.makeText(context, "${targetUser.name} has been unblocked", Toast.LENGTH_SHORT).show()
                                        }
                                        userToUnblock = null
                                    }
                                }
                            },
                            enabled = !unblockLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DashboardTerracotta,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (unblockLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Unblock", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { userToUnblock = null }) {
                            Text("Cancel", color = DashboardNavMuted)
                        }
                    }
                )
            }
        }

        "DELETE_ACCOUNT" -> {
            DeleteAccountScreen(
                onConfirmDelete = { reason ->
                    if (!deleteLoading) {
                        deleteLoading = true
                        coroutineScope.launch {
                            val uid = SessionManager.current()?.userId
                            val err = if (uid != null) {
                                SupabaseRepository.deleteAccount(uid)
                            } else "Not signed in"
                            deleteLoading = false
                            if (err != null) {
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Your account has been deleted.", Toast.LENGTH_LONG).show()
                                SessionManager.signOut()
                                onAccountDeleted()
                            }
                        }
                    }
                },
                deleteLoading = deleteLoading,
                onBack = { currentSubView = "MAIN" }
            )
        }

        else -> {
            // Main Account Settings Page
            val scrollState = rememberScrollState()

            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DashboardBg)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSaveAndClose(draft) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DashboardCard)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DashboardCream,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Account Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream,
                            modifier = Modifier.weight(1f)
                        )

                        if (settingsLoading || settingsSaving) {
                            CircularProgressIndicator(
                                color = DashboardPeach,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Button(
                            onClick = { onSaveAndClose(draft) },
                            enabled = !settingsLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DashboardTerracotta,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                containerColor = DashboardBg
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {

                    // ==========================================
                    // 1. DISCOVERY MANAGEMENT
                    // ==========================================
                    SettingsSectionCard(
                        title = "Discovery Management",
                        subtitle = "Control who you see and how far Cupid searches nearby",
                        icon = Icons.Default.TravelExplore
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            // Maximum Distance Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Maximum Distance",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DashboardCream
                                    )
                                    Text(
                                        text = "${draft.maxDistanceKm} km",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardPeach
                                    )
                                }

                                Slider(
                                    value = draft.maxDistanceKm.toFloat(),
                                    onValueChange = { draft = draft.copy(maxDistanceKm = it.toInt()) },
                                    onValueChangeFinished = {
                                        saveProfilePatch(ProfileSettingsPatch(maxDistanceKm = draft.maxDistanceKm))
                                    },
                                    enabled = !settingsLoading && !settingsSaving,
                                    valueRange = 2f..160f,
                                    steps = 78,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DashboardPeach,
                                        activeTrackColor = DashboardTerracotta,
                                        inactiveTrackColor = Color(0xFFE0E0E0)
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "Profiles within ${draft.maxDistanceKm} km will be shown first in your deck.",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted
                                )
                            }

                            HorizontalDivider(color = Color(0xFFE0E0E0))

                            // Interested In Selection:
                            // Women, Men, Transmen, Transwomen, Couples, Anyone
                            Column {
                                Text(
                                    text = "Interested In",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DashboardCream
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val interestChoices = listOf(
                                    "Women",
                                    "Men",
                                    "Transmen",
                                    "Transwomen",
                                    "Couples",
                                    "Anyone"
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    interestChoices.chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            pair.forEach { choice ->
                                                val isSelected = draft.interestedIn.contains(choice)
                                                Surface(
                                                    onClick = {
                                                        val updatedList = if (choice == "Anyone") {
                                                            if (isSelected) listOf("Women") else listOf("Anyone")
                                                        } else {
                                                            val base = draft.interestedIn.filter { it != "Anyone" }.toMutableList()
                                                            if (isSelected) {
                                                                base.remove(choice)
                                                                if (base.isEmpty()) base.add("Women")
                                                            } else {
                                                                base.add(choice)
                                                            }
                                                            base
                                                        }
                                                        draft = draft.copy(interestedIn = updatedList)
                                                        saveProfilePatch(ProfileSettingsPatch(interestedIn = updatedList))
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) DashboardTerracotta else Color.White,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) DashboardPeach else Color(0xFFCCCCCC)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                        }
                                                        Text(
                                                            text = choice,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else DashboardMutedBeige
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE0E0E0))

                            // Age Group Range
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Age Group Range",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DashboardCream
                                    )
                                    Text(
                                        text = "${draft.ageMin} - ${draft.ageMax} yrs",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardPeach
                                    )
                                }

                                @OptIn(ExperimentalMaterial3Api::class)
                                RangeSlider(
                                    value = draft.ageMin.toFloat()..draft.ageMax.toFloat(),
                                    onValueChange = { range ->
                                        draft = draft.copy(
                                            ageMin = range.start.toInt(),
                                            ageMax = range.endInclusive.toInt()
                                        )
                                    },
                                    onValueChangeFinished = {
                                        saveProfilePatch(
                                            ProfileSettingsPatch(
                                                ageMin = draft.ageMin,
                                                ageMax = draft.ageMax
                                            )
                                        )
                                    },
                                    enabled = !settingsLoading && !settingsSaving,
                                    valueRange = 18f..65f,
                                    steps = 46,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DashboardPeach,
                                        activeTrackColor = DashboardTerracotta,
                                        inactiveTrackColor = Color(0xFFE0E0E0)
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "Looking for matches aged between ${draft.ageMin} and ${draft.ageMax}.",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 2. CONTROL WHO CAN MESSAGE YOU
                    // ==========================================
                    SettingsSectionCard(
                        title = "Chat & Message Privacy",
                        subtitle = "Protect your inbox from spam and fake profiles",
                        icon = Icons.Default.ChatBubble
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !settingsLoading && !settingsSaving) {
                                        val next = !draft.photoVerifiedOnlyChat
                                        draft = draft.copy(photoVerifiedOnlyChat = next)
                                        saveProfilePatch(ProfileSettingsPatch(photoVerifiedOnlyChat = next))
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = draft.photoVerifiedOnlyChat,
                                    onCheckedChange = { isChecked ->
                                        draft = draft.copy(photoVerifiedOnlyChat = isChecked)
                                        saveProfilePatch(ProfileSettingsPatch(photoVerifiedOnlyChat = isChecked))
                                    },
                                    enabled = !settingsLoading && !settingsSaving,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DashboardTerracotta,
                                        checkmarkColor = Color.White,
                                        uncheckedColor = Color(0xFF999999)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Photo Verified Chat Only",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DashboardCream
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = SuperBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Only let matches with verified selfies and photo badges initiate chats with you.",
                                        fontSize = 12.sp,
                                        color = DashboardNavMuted,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 3. ACTIVITY STATUS (ONLINE & OFFLINE)
                    // ==========================================
                    SettingsSectionCard(
                        title = "Activity Status",
                        subtitle = "Let your matches know when you are active on Mallu Cupid",
                        icon = Icons.Default.Sensors
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (draft.isOnline) Color(0xFF4CAF50) else Color(0xFF757575))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (draft.isOnline) "Status: Online" else "Status: Offline",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DashboardCream
                                        )
                                        Text(
                                            text = if (draft.isOnline) "Active now in the nearby singles feed" else "Appear offline to all matches",
                                            fontSize = 11.sp,
                                            color = DashboardNavMuted
                                        )
                                    }
                                }

                                Switch(
                                    checked = draft.isOnline,
                                    onCheckedChange = { isChecked ->
                                        draft = draft.copy(isOnline = isChecked)
                                        saveProfilePatch(ProfileSettingsPatch(isOnline = isChecked))
                                    },
                                    enabled = !settingsLoading && !settingsSaving,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4CAF50),
                                        uncheckedThumbColor = DashboardNavMuted,
                                        uncheckedTrackColor = Color(0xFFE0E0E0)
                                    )
                                )
                            }

                            // Active Status sub-screen navigator (Screenshot 1)
                            Surface(
                                onClick = { currentSubView = "ACTIVE_STATUS" },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = DashboardPeach,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Active & Recently Active Settings",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DashboardCream
                                        )
                                        Text(
                                            text = "Configure 2h and 24h activity indicators",
                                            fontSize = 11.sp,
                                            color = DashboardNavMuted
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = DashboardNavMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // NOTIFICATIONS & EMAIL SETTINGS
                    // ==========================================
                    SettingsSectionCard(
                        title = "Blocked Contacts",
                        subtitle = "Manage contacts and users you've blocked from finding you",
                        icon = Icons.Default.Block
                    ) {
                        Surface(
                            onClick = { currentSubView = "BLOCKED_USERS" },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE0E0E0),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PersonOff,
                                            contentDescription = null,
                                            tint = NopeCoral,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "View Blocked Users (${draft.blockedUsers.size})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardCream
                                    )
                                    Text(
                                        text = "Search and unblock users with instant confirmation",
                                        fontSize = 11.sp,
                                        color = DashboardNavMuted
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = DashboardNavMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 5. ACCOUNT DATA
                    // Reg Email, Age and DOB, Gender, Verification Status
                    // ==========================================
                    SettingsSectionCard(
                        title = "Account Data",
                        subtitle = "Registered profile credentials & security records",
                        icon = Icons.Default.ManageAccounts
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            // Registered Email
                            AccountDataField(
                                label = "Registered Email",
                                value = draft.registeredEmail,
                                icon = Icons.Default.Email
                            )

                            // Age and Date of Birth
                            val dobFormatted = "${draft.birthDay}/${draft.birthMonth}/${draft.birthYear}"
                            AccountDataField(
                                label = "Age & Date of Birth",
                                value = "${draft.calculatedAge} years old (DOB: $dobFormatted)",
                                icon = Icons.Default.Cake
                            )

                            // Gender
                            AccountDataField(
                                label = "Registered Gender",
                                value = draft.gender,
                                icon = Icons.Default.Wc
                            )

                            // Verification Status: Verified or Unverified
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (draft.isVerified) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                                            contentDescription = null,
                                            tint = if (draft.isVerified) SuperBlue else NopeCoral,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Verification Status",
                                                fontSize = 11.sp,
                                                color = DashboardNavMuted
                                            )
                                            Text(
                                                text = if (draft.isVerified) "VERIFIED" else "UNVERIFIED",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (draft.isVerified) SuperBlue else NopeCoral
                                            )
                                        }
                                    }

                                    if (!draft.isVerified) {
                                        Button(
                                            onClick = { currentSubView = "FACE_VERIFICATION" },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuperBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Verify Now",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { currentSubView = "FACE_VERIFICATION" },
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = "Re-verify Face",
                                                fontSize = 11.sp,
                                                color = DashboardPeach
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ==========================================
                    // 6. DELETE MY ACCOUNT BUTTON
                    // Navigates to Account Deletion Page
                    // ==========================================
                    Button(
                        onClick = { currentSubView = "DELETE_ACCOUNT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF381F1D),
                            contentColor = NopeCoral
                        ),
                        border = BorderStroke(1.dp, NopeCoral.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete My Account",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Log out option
                    TextButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Log Out from Mallu Cupid",
                            fontSize = 14.sp,
                            color = DashboardNavMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                    }
                    // ---- Initial-load overlay (child of the existing root, no inset change) ----
                    if (settingsLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DashboardBg.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = DashboardTerracotta,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Sign Out confirmation dialog (child of the existing root — no inset change)
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
            }
        }
    }
}

@Composable
private fun AccountDataField(
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DashboardPeach,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = DashboardNavMuted
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DashboardCream
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = DashboardPeach,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = DashboardNavMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    blockedUsers: List<BlockedUser>,
    onUnblockUser: (BlockedUser) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(searchQuery, blockedUsers) {
        if (searchQuery.isBlank()) {
            blockedUsers
        } else {
            blockedUsers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardBg)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DashboardCard)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DashboardCream,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Blocked Contacts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            }
        },
        containerColor = DashboardBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search blocked users by name...", color = DashboardNavMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = DashboardPeach
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = DashboardNavMuted
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DashboardPeach,
                    unfocusedBorderColor = Color(0xFFCCCCCC),
                    focusedContainerColor = DashboardCard,
                    unfocusedContainerColor = DashboardCard,
                    focusedTextColor = DashboardCream,
                    unfocusedTextColor = DashboardCream
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${filteredUsers.size} blocked contact(s)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DashboardNavMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = DashboardPeach,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No blocked users match '$searchQuery'" else "No blocked contacts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You haven't blocked anyone yet.",
                            fontSize = 13.sp,
                            color = DashboardNavMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredUsers, key = { it.id }) { user ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DashboardCard,
                            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = user.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardCream
                                    )
                                    Text(
                                        text = user.location,
                                        fontSize = 12.sp,
                                        color = DashboardNavMuted
                                    )
                                }

                                Button(
                                    onClick = { onUnblockUser(user) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = DashboardPeach
                                    ),
                                    border = BorderStroke(1.dp, DashboardPeach),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Unblock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onConfirmDelete: (reason: String) -> Unit,
    deleteLoading: Boolean = false,
    onBack: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("Found someone special on Mallu Cupid") }
    var additionalNotes by remember { mutableStateOf("") }
    var showFinalConfirmDialog by remember { mutableStateOf(false) }

    val reasons = listOf(
        "Found someone special on Mallu Cupid",
        "Taking a break from dating apps",
        "Not getting quality matches nearby",
        "Privacy and security concerns",
        "Starting over with a new profile",
        "Other reason"
    )

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardBg)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DashboardCard)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DashboardCream,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Delete Account",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            }
        },
        containerColor = DashboardBg
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF381F1D),
                border = BorderStroke(1.dp, NopeCoral.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = NopeCoral,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "This action is permanent",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NopeCoral
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Deleting your Mallu Cupid account will permanently remove your photos, bio, likes, chats, and subscriptions. You won't be able to recover this profile.",
                        fontSize = 13.sp,
                        color = DashboardMutedBeige,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Why are you leaving us?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardCream
            )
            Text(
                text = "Select your main reason to help us improve the experience for singles nearby:",
                fontSize = 12.sp,
                color = DashboardNavMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reason selector radio cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Surface(
                        onClick = { selectedReason = reason },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF3D2520) else DashboardCard,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) NopeCoral else Color(0xFFCCCCCC)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = NopeCoral,
                                    unselectedColor = Color(0xFF999999)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reason,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DashboardCream else DashboardMutedBeige
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reason Type Box
            Text(
                text = "Additional Feedback (Optional)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DashboardCream
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = additionalNotes,
                onValueChange = { additionalNotes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Tell us anything else about your experience...",
                        color = DashboardNavMuted,
                        fontSize = 13.sp
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DashboardPeach,
                    unfocusedBorderColor = Color(0xFFCCCCCC),
                    focusedContainerColor = DashboardCard,
                    unfocusedContainerColor = DashboardCard,
                    focusedTextColor = DashboardCream,
                    unfocusedTextColor = DashboardCream
                ),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Confirm Delete Button
            Button(
                onClick = { showFinalConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NopeCoral,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Account Deletion",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardCream),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel & Keep My Profile")
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showFinalConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFinalConfirmDialog = false },
            containerColor = DashboardCard,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = NopeCoral,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Are you absolutely sure?",
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will permanently erase your Mallu Cupid profile for '$selectedReason'. This action cannot be reversed.",
                    color = DashboardMutedBeige,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!deleteLoading) {
                            showFinalConfirmDialog = false
                            val finalReason = if (additionalNotes.isNotBlank()) "$selectedReason - $additionalNotes" else selectedReason
                            onConfirmDelete(finalReason)
                        }
                    },
                    enabled = !deleteLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NopeCoral,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (deleteLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Yes, Delete Profile", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalConfirmDialog = false }) {
                    Text("Go Back", color = DashboardNavMuted)
                }
            }
        )
    }
}
