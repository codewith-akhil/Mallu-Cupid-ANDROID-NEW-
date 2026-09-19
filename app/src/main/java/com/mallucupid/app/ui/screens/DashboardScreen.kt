package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.SampleProfiles
import com.mallucupid.app.data.remote.ProfileSettingsPatch
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.location.LocationHelper
import com.mallucupid.app.notifications.MalluCupidMessagingService
import com.mallucupid.app.permissions.rememberLocationPermissionLauncher
import com.mallucupid.app.permissions.rememberNotificationPermissionLauncher
import com.mallucupid.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userDraft: OnboardingDraft,
    onOpenOnboarding: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val profiles = remember { mutableStateListOf(*SampleProfiles.list.toTypedArray()) }
    val coroutineScope = rememberCoroutineScope()
    var deckLoading by remember { mutableStateOf(false) }
    // Chat-Features — Pagination state. Initial deck load fetches limit=50 (was 20)
    // so the user sees a fuller deck without re-fetching. `isLoadingMore` guards
    // against duplicate concurrent page loads; auto-load triggers when the user
    // is within 5 profiles of the deck end.
    var isLoadingMore by remember { mutableStateOf(false) }

    // Create notification channel + request POST_NOTIFICATIONS permission on first launch
    val requestNotifications = rememberNotificationPermissionLauncher { granted ->
        if (granted) {
            // Channel must be created before any notification can be posted
            MalluCupidMessagingService.createNotificationChannel(context)
        }
    }
    LaunchedEffect(Unit) {
        MalluCupidMessagingService.createNotificationChannel(context)
        requestNotifications()
    }

    // Load the real swipe deck from Supabase on first composition.
    // Shows a loading spinner while fetching. Falls back to bundled
    // SampleProfiles if the deck is empty or the call fails.
    LaunchedEffect(Unit) {
        val session = SessionManager.current()
        if (session?.userId != null) {
            deckLoading = true
            try {
                val deck = SupabaseRepository.getSwipeDeck(limit = 50)
                if (deck.isNotEmpty()) {
                    profiles.clear()
                    profiles.addAll(deck)
                }
            } finally {
                deckLoading = false
            }
        }
    }

    // Real per-category counts for the Explore tab. Fetched once on first
    // composition by counting `lookingFor` values across a wider deck
    // (limit=100). Falls back to the hardcoded defaults baked into
    // ExploreSpaceItem if the call fails or returns nothing.
    var exploreCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val session = SessionManager.current()
        if (session?.userId != null) {
            try {
                val deck = SupabaseRepository.getSwipeDeck(limit = 100)
                if (deck.isNotEmpty()) {
                    exploreCounts = deck.groupingBy { it.lookingFor }.eachCount()
                }
            } catch (_: Exception) {
                // Keep emptyMap() — ExploreViewContent falls back to defaults.
            }
        }
    }
    val haptic = LocalHapticFeedback.current
    // Chat-Features — Real unread-chat count. Replaces the hardcoded `2` demo
    // value. Polled every 30s from the `get_unread_message_count` RPC (which
    // itself enforces auth.uid() on the server so we don't need to send user_id).
    var unreadChatCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val session = SessionManager.current()
        if (session?.userId != null) {
            val uid = session.userId
            // Initial fetch — immediate.
            unreadChatCount = runCatching { SupabaseRepository.getUnreadMessageCount(uid) }.getOrDefault(0)
            // Poll every 30s while the dashboard is alive.
            while (coroutineContext.isActive) {
                delay(30_000L)
                unreadChatCount = runCatching { SupabaseRepository.getUnreadMessageCount(uid) }.getOrDefault(unreadChatCount)
            }
        }
    }
    // Chat-Features — Daily swipe quota + Pro status. Drives the "X likes
    // remaining" pill on the swipe screen. Refreshed whenever the deck reloads.
    // Initial value of `swipeRemaining` is the default daily limit (20) so the
    // first like works even before the RPC returns; the RPC may override both
    // (e.g. the user is Pro → remaining is unlimited).
    var swipeRemaining by remember { mutableIntStateOf(20) }
    var isProUser by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val status = runCatching { SupabaseRepository.getSwipeStatus() }.getOrNull()
        if (status != null) {
            swipeRemaining = status.remaining
            isProUser = status.isPro
        }
    }
    // Chat-Features — True when the chat tab has a fullscreen conversation
    // open. The bottom nav hides (AnimatedVisibility) while this is true so
    // the conversation can use the full screen height.
    var chatConversationOpen by remember { mutableStateOf(false) }
    var currentDraft by remember { mutableStateOf(userDraft) }
    var showEditProfileScreen by remember { mutableStateOf(false) }
    var showAccountSettingsScreen by remember { mutableStateOf(false) }
    var showFaceVerificationScreen by remember { mutableStateOf(false) }
    var activeSystemScreen by remember { mutableStateOf<String?>(null) } // "LOADING", "NO_INTERNET", "ERROR"
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

    // Chat-Features — Pagination. Auto-fetch more profiles when the user is
    // within 5 cards of the deck end. Guarded by `isLoadingMore` to prevent
    // duplicate concurrent fetches (e.g. rapid swipes). New profiles are
    // deduped by id so a profile that appears in both the existing deck and
    // the next page is not inserted twice.
    LaunchedEffect(currentProfileIndex, displayProfiles.size) {
        if (isLoadingMore) return@LaunchedEffect
        if (displayProfiles.isEmpty()) return@LaunchedEffect
        if (currentProfileIndex < displayProfiles.size - 5) return@LaunchedEffect
        val uid = SessionManager.current()?.userId ?: return@LaunchedEffect
        isLoadingMore = true
        try {
            val more = SupabaseRepository.getSwipeDeck(limit = 50)
            if (more.isNotEmpty()) {
                val existingIds = profiles.map { it.id }.toHashSet()
                val fresh = more.filter { it.id !in existingIds }
                if (fresh.isNotEmpty()) profiles.addAll(fresh)
            }
        } catch (_: Exception) {
            // Silent — pagination is best-effort; user keeps swiping on the existing deck.
        } finally {
            isLoadingMore = false
        }
    }

    // Location: request permission + fetch fresh location + save to DB on every app launch.
    // Like Tinder — keeps the user's location current so the swipe deck shows nearby profiles.
    val requestLocation = rememberLocationPermissionLauncher { granted ->
        if (granted) {
            coroutineScope.launch {
                try {
                    val loc = LocationHelper.getCurrentLocation(context)
                    if (loc != null) {
                        val uid = SessionManager.current()?.userId
                        if (uid != null) {
                            val updatedDraft = currentDraft.copy(
                                city = loc.fullLocation,
                                latitude = loc.latitude,
                                longitude = loc.longitude
                            )
                            currentDraft = updatedDraft
                            SupabaseRepository.saveProfile(
                                uid,
                                updatedDraft.registeredEmail.ifBlank { SessionManager.current()?.email.orEmpty() },
                                updatedDraft
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Silent — location refresh is best-effort.
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            // Already have permission — fetch location directly.
            coroutineScope.launch {
                try {
                    val loc = LocationHelper.getCurrentLocation(context)
                    if (loc != null) {
                        val uid = SessionManager.current()?.userId
                        if (uid != null) {
                            val updatedDraft = currentDraft.copy(
                                city = loc.fullLocation,
                                latitude = loc.latitude,
                                longitude = loc.longitude
                            )
                            currentDraft = updatedDraft
                            SupabaseRepository.saveProfile(
                                uid,
                                updatedDraft.registeredEmail.ifBlank { SessionManager.current()?.email.orEmpty() },
                                updatedDraft
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Silent — best effort.
                }
            }
        } else {
            // No permission yet — request it. The launcher callback will
            // fetch + save location if the user grants it.
            requestLocation()
        }
    }

    LaunchedEffect(actionToast) {
        if (actionToast != null) {
            kotlinx.coroutines.delay(2000)
            actionToast = null
        }
    }

    // Chat-Features — System back gesture handlers. Each handler is enabled
    // only when its corresponding overlay/sheet/screen is open, so the back
    // gesture dismisses the topmost overlay instead of navigating out of the
    // dashboard. Priority order is source order: the LAST composed (i.e. the
    // last one in this list) wins when multiple are simultaneously enabled.
    // The full-screen child composables (EditProfileScreen, AccountSettingsScreen,
    // FaceVerificationScreen, system-state screens) already wire their own
    // onBack callbacks to the same state vars, so these BackHandlers cover the
    // SYSTEM back gesture / predictive-back swipe for them too.
    BackHandler(enabled = chatConversationOpen) { chatConversationOpen = false }
    BackHandler(enabled = activeSystemScreen != null) { activeSystemScreen = null }
    BackHandler(enabled = showFaceVerificationScreen) { showFaceVerificationScreen = false }
    BackHandler(enabled = showAccountSettingsScreen) { showAccountSettingsScreen = false }
    BackHandler(enabled = showEditProfileScreen) { showEditProfileScreen = false }
    BackHandler(enabled = firstImpressionProfile != null) { firstImpressionProfile = null }
    BackHandler(enabled = expandedProfile != null) { expandedProfile = null }
    BackHandler(enabled = showMatchModal) { showMatchModal = false }
    BackHandler(enabled = showFilterSheet) { showFilterSheet = false }

    if (showEditProfileScreen) {
        EditProfileScreen(
            initialDraft = currentDraft,
            onSaveAndClose = { updatedDraft ->
                coroutineScope.launch {
                    val uid = SessionManager.current()?.userId
                    if (uid != null) {
                        val ok = SupabaseRepository.saveProfile(uid, updatedDraft.registeredEmail.ifBlank { SessionManager.current()?.email.orEmpty() }, updatedDraft)
                        actionToast = if (ok) "Profile saved" else "Profile saved locally"
                    } else {
                        actionToast = "Profile saved locally"
                    }
                    currentDraft = updatedDraft
                    showEditProfileScreen = false
                }
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

    if (showFaceVerificationScreen) {
        FaceVerificationScreen(
            draft = currentDraft,
            onVerificationComplete = { verifiedDraft ->
                currentDraft = verifiedDraft
                showFaceVerificationScreen = false
                actionToast = "Face Verified! Blue checkmark badge applied"
                // Persist is_verified=true to Supabase
                coroutineScope.launch {
                    val uid = SessionManager.current()?.userId
                    if (uid != null) {
                        SupabaseRepository.saveProfile(uid, verifiedDraft.registeredEmail.ifBlank { SessionManager.current()?.email.orEmpty() }, verifiedDraft)
                    }
                }
            },
            onBack = { showFaceVerificationScreen = false }
        )
        return
    }

    if (activeSystemScreen != null) {
        when (activeSystemScreen) {
            "LOADING" -> {
                LoadingStateScreen(
                    message = "Connecting to singles nearby...",
                    onCancel = { activeSystemScreen = null }
                )
                return
            }
            "NO_INTERNET" -> {
                NoInternetScreen(
                    onRetry = {
                        activeSystemScreen = null
                        actionToast = "Connected to Mallu Cupid!"
                    },
                    onOfflineMode = { activeSystemScreen = null }
                )
                return
            }
            "ERROR" -> {
                ErrorStateScreen(
                    title = "Connection Interrupted",
                    message = "Could not sync your matches. Please check connection and try again.",
                    onRetry = {
                        activeSystemScreen = null
                        actionToast = "Retried successfully"
                    },
                    onBack = { activeSystemScreen = null }
                )
                return
            }
        }
    }

    if (firstImpressionProfile != null) {
        FirstImpressionScreen(
            profile = firstImpressionProfile!!,
            onDismiss = { firstImpressionProfile = null },
            onSendMessage = { targetProfile, message ->
                firstImpressionProfile = null
                // Note: persisting first impressions requires a dedicated
                // `first_impressions` table in the backend (out of scope for
                // this fix). SupabaseRepository.sendMessage() cannot be used
                // because it requires a match_id, which does not exist for
                // non-mutual first impressions. Do NOT add a half-working
                // persistence call here.
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
                // Chat-Features — Daily swipe quota. Non-Pro users are capped at
                // `swipeRemaining` likes per day; once they hit 0 we surface an
                // upgrade toast and skip the swipe (no recordSwipe call, no
                // index advance) so the limit is actually enforced client-side.
                // Pro users (`isProUser == true`) bypass the check entirely.
                if (!isProUser && swipeRemaining <= 0) {
                    actionToast = "Daily limit reached — upgrade to Pro for unlimited swipes"
                    return@ExpandedProfileSheet
                }
                expandedProfile = null
                actionToast = "Liked ${p.name} ♥"
                if (!isProUser) swipeRemaining = (swipeRemaining - 1).coerceAtLeast(0)
                coroutineScope.launch {
                    val uid = SessionManager.current()?.userId ?: return@launch
                    SupabaseRepository.recordSwipe(uid, p.id, "like")
                    // Only show match modal if a mutual match actually exists.
                    val matchId = SupabaseRepository.getMatchId(uid, p.id)
                    if (matchId != null) {
                        matchedProfile = p
                        showMatchModal = true
                    }
                }
                if (displayProfiles.isNotEmpty()) {
                    currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                }
            },
            onDislike = {
                val p = activeExp
                expandedProfile = null
                actionToast = "Passed on ${p.name}"
                coroutineScope.launch {
                    val uid = SessionManager.current()?.userId ?: return@launch
                    SupabaseRepository.recordSwipe(uid, p.id, "pass")
                }
                if (displayProfiles.isNotEmpty()) {
                    currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                }
            },
            onSuperLike = {
                actionToast = "Super Liked ${activeExp.name}! ⭐"
                coroutineScope.launch {
                    val uid = SessionManager.current()?.userId ?: return@launch
                    SupabaseRepository.recordSwipe(uid, activeExp.id, "superlike")
                    val matchId = SupabaseRepository.getMatchId(uid, activeExp.id)
                    if (matchId != null) {
                        matchedProfile = activeExp
                        showMatchModal = true
                    }
                }
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

    // Chat-Features — Full-screen loading state while the swipe deck is being
    // fetched from Supabase. Replaces the previous inline overlay Box so the
    // user sees the branded pulsing-cupid LoadingStateScreen (same component
    // used by the system-state screens) instead of a bare spinner. Once the
    // deck arrives, `deckLoading` flips to false and we fall through to the
    // main Box below.
    if (deckLoading) {
        LoadingStateScreen(
            message = "Finding singles nearby...",
            onCancel = null
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
                        // Chat-Features — Daily swipe quota. Same gate as the
                        // expanded-profile like handler: non-Pro users hit a
                        // hard cap at swipeRemaining=0; Pro users bypass.
                        if (!isProUser && swipeRemaining <= 0) {
                            actionToast = "Daily limit reached — upgrade to Pro for unlimited swipes"
                            return@TinderSwipeableCardStack
                        }
                        actionToast = "Liked ${likedProfile.name} ♥"
                        if (!isProUser) swipeRemaining = (swipeRemaining - 1).coerceAtLeast(0)
                        coroutineScope.launch {
                            val uid = SessionManager.current()?.userId ?: return@launch
                            SupabaseRepository.recordSwipe(uid, likedProfile.id, "like")
                            // Only show match modal if a mutual match actually exists.
                            val matchId = SupabaseRepository.getMatchId(uid, likedProfile.id)
                            if (matchId != null) {
                                matchedProfile = likedProfile
                                showMatchModal = true
                            }
                        }
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                        }
                    },
                    onDislike = { dislikedProfile ->
                        actionToast = "Passed on ${dislikedProfile.name}"
                        coroutineScope.launch {
                            val uid = SessionManager.current()?.userId ?: return@launch
                            SupabaseRepository.recordSwipe(uid, dislikedProfile.id, "pass")
                        }
                        if (displayProfiles.isNotEmpty()) {
                            currentProfileIndex = (currentProfileIndex + 1) % displayProfiles.size
                        }
                    },
                    onRewind = {
                        if (displayProfiles.isNotEmpty()) {
                            val prevIndex = (currentProfileIndex - 1 + displayProfiles.size) % displayProfiles.size
                            val prevProfile = displayProfiles[prevIndex]
                            // Delete the previous swipe from DB so the profile
                            // reappears in future decks.
                            coroutineScope.launch {
                                val uid = SessionManager.current()?.userId ?: return@launch
                                SupabaseRepository.deleteSwipe(uid, prevProfile.id)
                            }
                            currentProfileIndex = prevIndex
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
                        coroutineScope.launch {
                            val uid = SessionManager.current()?.userId ?: return@launch
                            SupabaseRepository.recordSwipe(uid, superLikedProfile.id, "superlike")
                            // Check if this super-like created a mutual match.
                            val matchId = SupabaseRepository.getMatchId(uid, superLikedProfile.id)
                            if (matchId != null) {
                                matchedProfile = superLikedProfile
                                showMatchModal = true
                            }
                        }
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
                        actionToast = "Showing $cat nearby"
                    },
                    categoryCounts = exploreCounts
                )
            }

            "Likes" -> {
                LikesViewContent(
                    userId = SessionManager.current()?.userId,
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
                    },
                    onConversationStateChanged = { isOpen -> chatConversationOpen = isOpen }
                )
            }

            "Profile" -> {
                ProfileViewContent(
                    userDraft = currentDraft,
                    onEditProfile = { showEditProfileScreen = true },
                    onOpenSettings = { showAccountSettingsScreen = true },
                    onSignOut = onSignOut,
                    onOpenFaceVerification = { showFaceVerificationScreen = true },
                    onShowSystemScreen = { screenType -> activeSystemScreen = screenType }
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
        // Chat-Features — Hide the nav bar entirely when a chat conversation is
        // open so the conversation gets full screen height. AnimatedVisibility
        // (slide + fade) keeps the existing nav padding rhythm when collapsed.
        androidx.compose.animation.AnimatedVisibility(
            visible = !chatConversationOpen,
            enter = androidx.compose.animation.slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(220),
                initialOffsetY = { it }
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(
                animationSpec = androidx.compose.animation.core.tween(180),
                targetOffsetY = { it }
            ) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                    // Sliding pill background behind active tab — color animates via spring when isActive flips.
                    val pillColor by animateColorAsState(
                        targetValue = if (isActive) DashboardTerracotta.copy(alpha = 0.18f) else Color.Transparent,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "navPillColor"
                    )
                    // Smoothly interpolated icon & label colors instead of an if/else snap.
                    val iconColor by animateColorAsState(
                        targetValue = if (isActive) TinderCoral else DashboardNavMuted,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "navIconColor"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else DashboardNavMuted,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "navLabelColor"
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillColor,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                // Haptic tick fires BEFORE the active nav state mutates.
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeNav = item.label
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Text(
                                    text = item.icon,
                                    fontSize = 21.sp,
                                    color = iconColor
                                )
                                // Unread badge for the Chat tab — kept inside the icon's Box so it
                                // can never clip outside the 68.dp nav height or system nav bar.
                                if (item.label == "Chat" && unreadChatCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = TinderCoral,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .border(1.2.dp, DashboardBg, CircleShape)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = unreadChatCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = labelColor
                            )
                        }
                    }
                }
            }
        }
        }  // end AnimatedVisibility (bottom nav)

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = DashboardCard,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
            ) {
                FilterSheetContent(
                    userDraft = currentDraft,
                    onApply = { distance, ageMin, ageMax, interestedIn ->
                        coroutineScope.launch {
                            val uid = SessionManager.current()?.userId
                            if (uid == null) {
                                actionToast = "Could not apply filters. Please try again."
                                return@launch
                            }
                            // Save filters to DB via ProfileSettingsPatch.
                            val ok = SupabaseRepository.saveProfileSettings(
                                ProfileSettingsPatch(
                                    maxDistanceKm = distance,
                                    ageMin = ageMin,
                                    ageMax = ageMax,
                                    interestedIn = interestedIn
                                ),
                                uid
                            )
                            if (!ok) {
                                actionToast = "Could not apply filters. Please try again."
                                return@launch
                            }
                            // Reflect the saved filters in the in-memory draft so a
                            // subsequent sheet open starts from the persisted state.
                            currentDraft = currentDraft.copy(
                                maxDistanceKm = distance,
                                ageMin = ageMin,
                                ageMax = ageMax,
                                interestedIn = interestedIn
                            )
                            // Reload the swipe deck with the new filters applied.
                            deckLoading = true
                            isLoadingMore = true
                            val deck = SupabaseRepository.getSwipeDeck(limit = 50)
                            isLoadingMore = false
                            if (deck.isNotEmpty()) {
                                profiles.clear()
                                profiles.addAll(deck)
                            }
                            deckLoading = false
                            actionToast = "Filters applied"
                            showFilterSheet = false
                        }
                    },
                    onDismiss = { showFilterSheet = false }
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
            text = "People nearby who swiped right on your profile",
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
            "Thank you! Which part of town are you staying in?",
            "Downtown! Love the cafes around here.",
            "Awesome! Have you checked out that new cafe on the corner?"
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                        imageVector = Icons.AutoMirrored.Filled.Send,
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
// FILTER BOTTOM SHEET CONTENT
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheetContent(
    userDraft: OnboardingDraft,
    onApply: (Int, Int, Int, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Initial state read from userDraft.maxDistanceKm (NOT the legacy `distance`
    // field) and from ageMin / ageMax / interestedIn, so the sheet always
    // reflects the latest profile state.
    var distance by remember {
        mutableFloatStateOf(userDraft.maxDistanceKm.toFloat().coerceIn(5f, 200f))
    }
    var ageMin by remember {
        mutableFloatStateOf(userDraft.ageMin.toFloat().coerceIn(18f, 80f))
    }
    var ageMax by remember {
        // Defensive: ensure max >= min on init in case the persisted row is stale.
        mutableFloatStateOf(
            maxOf(userDraft.ageMax.toFloat(), userDraft.ageMin.toFloat()).coerceIn(18f, 99f)
        )
    }
    var interestedIn by remember { mutableStateOf(userDraft.interestedIn) }
    var isApplying by remember { mutableStateOf(false) }

    // Safety net: if the parent doesn't dismiss the sheet (e.g. save failed or
    // there was no session), re-enable the Apply button after a brief window so
    // the user can retry instead of staring at a stuck spinner.
    LaunchedEffect(isApplying) {
        if (isApplying) {
            kotlinx.coroutines.delay(10_000)
            isApplying = false
        }
    }

    val interestChoices = listOf(
        "Women", "Men", "Transmen", "Transwomen", "Couples", "Anyone"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Discovery Filters",
            color = DashboardCream,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "Filter matches near you",
            color = DashboardCream.copy(alpha = 0.65f),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- Show Me -------------------------------------------------------
        Text(
            text = "Show Me",
            color = DashboardCream,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Multi-select chips, two per row. "Anyone" is mutually exclusive with
        // every other option — selecting it clears the rest, and selecting any
        // specific option clears "Anyone". Matches AccountSettingsScreen.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            interestChoices.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { choice ->
                        val isSelected = interestedIn.contains(choice)
                        Surface(
                            onClick = {
                                interestedIn = if (choice == "Anyone") {
                                    if (isSelected) listOf("Women") else listOf("Anyone")
                                } else {
                                    val base = interestedIn
                                        .filter { it != "Anyone" }
                                        .toMutableList()
                                    if (isSelected) {
                                        base.remove(choice)
                                        if (base.isEmpty()) base.add("Women")
                                    } else {
                                        base.add(choice)
                                    }
                                    base
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DashboardTerracotta else Color(0xFF261E1A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DashboardPeach else Color(0xFF42342D)
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

        Spacer(modifier = Modifier.height(20.dp))

        // --- Maximum Distance ---------------------------------------------
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
            colors = SliderDefaults.colors(
                thumbColor = DashboardTerracotta,
                activeTrackColor = DashboardTerracotta
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Age Range (two sliders) --------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Age Range", color = DashboardCream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${ageMin.toInt()} – ${ageMax.toInt()}", color = DashboardPeach, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        // Min age — clamped so it can never exceed max.
        Slider(
            value = ageMin,
            onValueChange = { ageMin = it.coerceAtMost(ageMax) },
            valueRange = 18f..80f,
            colors = SliderDefaults.colors(
                thumbColor = DashboardTerracotta,
                activeTrackColor = DashboardTerracotta
            )
        )
        // Max age — clamped so it can never drop below min.
        Slider(
            value = ageMax,
            onValueChange = { ageMax = it.coerceAtLeast(ageMin) },
            valueRange = 18f..99f,
            colors = SliderDefaults.colors(
                thumbColor = DashboardTerracotta,
                activeTrackColor = DashboardTerracotta
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Apply button --------------------------------------------------
        Button(
            onClick = {
                if (!isApplying) {
                    isApplying = true
                    onApply(distance.toInt(), ageMin.toInt(), ageMax.toInt(), interestedIn)
                }
            },
            enabled = !isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
        ) {
            if (isApplying) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Apply Filters", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isApplying
        ) {
            Text("Cancel", color = DashboardNavMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                // Match celebration — "You" avatar. Falls back to a neutral
                // person-silhouette Box+Icon when the user has no profile photo
                // yet (e.g. fresh signup before first photo upload). No Unsplash
                // placeholder URL — the previous external-image fallback leaked
                // a hard-coded stock photo into the user's match modal.
                if (userPhoto.isNotBlank()) {
                    AsyncImage(
                        model = userPhoto,
                        contentDescription = "You",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(3.dp, DashboardTerracotta, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE7E1))
                            .border(3.dp, DashboardTerracotta, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "You",
                            tint = Color(0xFFB9AFA6),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
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
