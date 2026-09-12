package com.mallucupid.app.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SettingsUpsert
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Push Notifications Screen
 * Faithfully matches the user's reference screenshot 3 with Mallu Cupid luxury dark palette:
 * - New matches
 * - Messages
 * - Message likes
 * - Super Likes
 * - Offers & promotions
 * - New likes with frequency selector (Every 1, 10, 100 new likes)
 *
 * Persistence: loads `push_matches`, `push_messages`, `push_message_likes`,
 * `push_super_likes`, `push_promos`, `push_likes_frequency` from `user_settings`
 * on first composition, and PATCHes the same columns on every toggle change.
 *
 * NOTE (audit gap): the local-only `pushNewLikesEnabled` master toggle has no
 * corresponding DB column and is NOT persisted — flipping it only shows/hides
 * the frequency selector. See Settings-Wiring audit report for migration recommendation.
 */
@Composable
fun PushNotificationsScreen(
    draft: OnboardingDraft,
    onUpdateDraft: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sessionUserId = remember { SessionManager.current()?.userId }

    var pushMatches by remember { mutableStateOf(draft.pushMatches) }
    var pushMessages by remember { mutableStateOf(draft.pushMessages) }
    var pushMessageLikes by remember { mutableStateOf(draft.pushMessageLikes) }
    var pushSuperLikes by remember { mutableStateOf(draft.pushSuperLikes) }
    var pushPromos by remember { mutableStateOf(draft.pushPromos) }
    var pushNewLikesEnabled by remember { mutableStateOf(true) }
    var selectedLikesFrequency by remember { mutableStateOf(draft.pushLikesFrequency) }

    var settingsLoading by remember { mutableStateOf(true) }
    var settingsSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = sessionUserId
        if (uid == null) {
            settingsLoading = false
            return@LaunchedEffect
        }
        val settings = withContext(Dispatchers.IO) { SupabaseRepository.loadUserSettings(uid) }
        if (settings != null) {
            settings.pushMatches?.let { pushMatches = it }
            settings.pushMessages?.let { pushMessages = it }
            settings.pushMessageLikes?.let { pushMessageLikes = it }
            settings.pushSuperLikes?.let { pushSuperLikes = it }
            settings.pushPromos?.let { pushPromos = it }
            settings.pushLikesFrequency?.let { selectedLikesFrequency = it }
            onUpdateDraft(
                draft.copy(
                    pushMatches = settings.pushMatches ?: draft.pushMatches,
                    pushMessages = settings.pushMessages ?: draft.pushMessages,
                    pushMessageLikes = settings.pushMessageLikes ?: draft.pushMessageLikes,
                    pushSuperLikes = settings.pushSuperLikes ?: draft.pushSuperLikes,
                    pushPromos = settings.pushPromos ?: draft.pushPromos,
                    pushLikesFrequency = settings.pushLikesFrequency ?: draft.pushLikesFrequency,
                )
            )
        }
        settingsLoading = false
    }

    fun saveSettings(
        matches: Boolean? = null,
        messages: Boolean? = null,
        messageLikes: Boolean? = null,
        superLikes: Boolean? = null,
        promos: Boolean? = null,
        likesFrequency: String? = null,
    ) {
        val uid = sessionUserId ?: return
        coroutineScope.launch {
            settingsSaving = true
            withContext(Dispatchers.IO) {
                SupabaseRepository.saveUserSettings(
                    uid,
                    SettingsUpsert(
                        userId = uid,
                        pushMatches = matches,
                        pushMessages = messages,
                        pushMessageLikes = messageLikes,
                        pushSuperLikes = superLikes,
                        pushPromos = promos,
                        pushLikesFrequency = likesFrequency,
                    )
                )
            }
            settingsSaving = false
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardBg)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onUpdateDraft(
                            draft.copy(
                                pushMatches = pushMatches,
                                pushMessages = pushMessages,
                                pushMessageLikes = pushMessageLikes,
                                pushSuperLikes = pushSuperLikes,
                                pushPromos = pushPromos,
                                pushLikesFrequency = selectedLikesFrequency
                            )
                        )
                        onBack()
                    },
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

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Push notifications",
                    fontSize = 22.sp,
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
                }
            }
        },
        containerColor = DashboardBg
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
            // Main Notification Toggles Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // New matches
                    PushToggleRow(
                        title = "New matches",
                        subtitle = "You just got a new match",
                        isChecked = pushMatches,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = {
                            pushMatches = it
                            onUpdateDraft(draft.copy(pushMatches = it))
                            saveSettings(matches = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // Messages
                    PushToggleRow(
                        title = "Messages",
                        subtitle = "Someone sent you a new message",
                        isChecked = pushMessages,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = {
                            pushMessages = it
                            onUpdateDraft(draft.copy(pushMessages = it))
                            saveSettings(messages = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // Message likes
                    PushToggleRow(
                        title = "Message likes",
                        subtitle = "Someone liked your message",
                        isChecked = pushMessageLikes,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = {
                            pushMessageLikes = it
                            onUpdateDraft(draft.copy(pushMessageLikes = it))
                            saveSettings(messageLikes = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // Super Likes
                    PushToggleRow(
                        title = "Super Likes",
                        subtitle = "You've been Super Liked! Swipe to find out by whom.",
                        isChecked = pushSuperLikes,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = {
                            pushSuperLikes = it
                            onUpdateDraft(draft.copy(pushSuperLikes = it))
                            saveSettings(superLikes = it)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // Offers & promotions
                    PushToggleRow(
                        title = "Offers & promotions",
                        subtitle = "Receive discounts, offers, promos and other news from Mallu Cupid",
                        isChecked = pushPromos,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = {
                            pushPromos = it
                            onUpdateDraft(draft.copy(pushPromos = it))
                            saveSettings(promos = it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // New Likes Section Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PushToggleRow(
                        title = "New likes",
                        subtitle = "You have new likes. See who likes You.",
                        isChecked = pushNewLikesEnabled,
                        enabled = !settingsLoading && !settingsSaving,
                        onCheckedChange = { pushNewLikesEnabled = it }
                    )

                    if (pushNewLikesEnabled) {
                        HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                        val frequencyOptions = listOf(
                            "Every 1 new like",
                            "Every 10 new likes",
                            "Every 100 new likes"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequencyOptions.forEach { option ->
                                val isSelected = selectedLikesFrequency == option
                                Surface(
                                    onClick = {
                                        selectedLikesFrequency = option
                                        onUpdateDraft(draft.copy(pushLikesFrequency = option))
                                        saveSettings(likesFrequency = option)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF382D27) else Color(0xFF261E1A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) DashboardTerracotta else Color(0xFF382D27)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = DashboardCream
                                        )

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = DashboardPeach,
                                                modifier = Modifier.size(18.dp)
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
        }
    }
}

@Composable
private fun PushToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = DashboardCream
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = DashboardNavMuted,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DashboardTerracotta,
                uncheckedThumbColor = DashboardNavMuted,
                uncheckedTrackColor = Color(0xFF382D27)
            )
        )
    }
}
