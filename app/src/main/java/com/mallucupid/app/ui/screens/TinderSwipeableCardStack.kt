package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Tinder-style Swipeable Card Stack:
 * - Swipe Right: LIKE
 * - Swipe Left: DISLIKE
 * - Smooth physics-based spring animations, 2D momentum, tilt rotation
 * - 3D Card Deck depth (renders top card, underneath card with dynamic scaling, and 3rd card)
 * - Authentic slanted "LIKE" and "NOPE" stamp overlays
 * - Photo switching on tap without gesture interference
 * - Dynamic action buttons (↺ Rewind, ✕ Dislike, ♥ Like) synchronized with gestures
 */
@Composable
fun TinderSwipeableCardStack(
    profiles: List<DatingProfile>,
    currentIndex: Int,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onOpenFilter: () -> Unit,
    onLike: (DatingProfile) -> Unit,
    onDislike: (DatingProfile) -> Unit,
    onRewind: () -> Unit,
    onExpandProfile: (DatingProfile) -> Unit,
    onResetStack: () -> Unit,
    modifier: Modifier = Modifier,
    activeCategoryFilter: String? = null,
    onClearFilter: (() -> Unit)? = null,
    onSuperLike: ((DatingProfile) -> Unit)? = null,
    onFirstImpression: ((DatingProfile) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    val totalProfiles = profiles.size
    val activeProfile = if (totalProfiles > 0) profiles[currentIndex % totalProfiles] else null
    val nextProfile = if (totalProfiles > 1) profiles[(currentIndex + 1) % totalProfiles] else null
    val thirdProfile = if (totalProfiles > 2) profiles[(currentIndex + 2) % totalProfiles] else null

    // 2D Card translation animatables
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isSwipingOut by remember { mutableStateOf(false) }

    // Active photo index inside active card
    var currentPhotoIndex by remember { mutableIntStateOf(0) }

    // Reset photo index and offsets on card switch
    LaunchedEffect(currentIndex) {
        currentPhotoIndex = 0
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        isSwipingOut = false
    }

    // Dynamic swipe functions
    fun triggerSwipe(isLike: Boolean) {
        if (isSwipingOut || activeProfile == null) return
        isSwipingOut = true
        coroutineScope.launch {
            val targetX = if (isLike) 1500f else -1500f
            val targetY = offsetY.value + (if (offsetY.value != 0f) offsetY.value * 1.5f else 0f)

            launch {
                offsetX.animateTo(
                    targetValue = targetX,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            }
            launch {
                offsetY.animateTo(
                    targetValue = targetY,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            }
            delay(230)
            if (isLike) {
                onLike(activeProfile)
            } else {
                onDislike(activeProfile)
            }
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            isSwipingOut = false
        }
    }

    fun triggerRewind() {
        if (isSwipingOut) return
        isSwipingOut = true
        coroutineScope.launch {
            onRewind()
            offsetX.snapTo(-1150f)
            offsetY.snapTo(0f)
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            isSwipingOut = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 72.dp)
    ) {
        if (activeProfile == null) {
            // Empty state when stack is exhausted
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF28231F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✨", fontSize = 42.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "You've caught up!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "There are no more new profiles in Kerala matching your current filters.",
                    fontSize = 14.sp,
                    color = DashboardMutedBeige,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onResetStack,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
                ) {
                    Text(
                        text = "Start Over",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            // Drag progress from 0f to 1f
            val dragProgress = (abs(offsetX.value) / 340f).coerceIn(0f, 1f)

            // -------------------------------------------------------------
            // LAYER 3: Third Card in background (Deals depth)
            // -------------------------------------------------------------
            if (thirdProfile != null) {
                val thirdScale = 0.88f + 0.05f * dragProgress
                val thirdOffsetY = 24.dp * (1f - dragProgress)
                val thirdAlpha = 0.45f + 0.25f * dragProgress

                CardLayer(
                    profile = thirdProfile,
                    photoIndex = 0,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .graphicsLayer {
                            scaleX = thirdScale
                            scaleY = thirdScale
                            translationY = thirdOffsetY.toPx()
                            alpha = thirdAlpha
                        }
                )
            }

            // -------------------------------------------------------------
            // LAYER 2: Underneath Card in Stack (Next Profile)
            // -------------------------------------------------------------
            if (nextProfile != null) {
                val nextScale = 0.94f + 0.06f * dragProgress
                val nextOffsetY = 12.dp * (1f - dragProgress)
                val nextAlpha = 0.75f + 0.25f * dragProgress

                CardLayer(
                    profile = nextProfile,
                    photoIndex = 0,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            scaleX = nextScale
                            scaleY = nextScale
                            translationY = nextOffsetY.toPx()
                            alpha = nextAlpha
                        }
                )
            }

            // -------------------------------------------------------------
            // LAYER 1: Top Active Card (Fully interactive gesture stack)
            // -------------------------------------------------------------
            var startTouch = remember { Offset.Zero }
            var totalMoved = remember { 0f }
            var touchTime = remember { 0L }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        // Natural tilting rotation based on horizontal displacement
                        rotationZ = (offsetX.value / 18f).coerceIn(-22f, 22f)
                    }
                    .pointerInput(activeProfile.id) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startTouch = offset
                                totalMoved = 0f
                                touchTime = System.currentTimeMillis()
                            },
                            onDragEnd = {
                                if (isSwipingOut) return@detectDragGestures
                                val duration = System.currentTimeMillis() - touchTime
                                val velocityX = (offsetX.value) / (duration.coerceAtLeast(1) / 1000f)

                                // Check if user simply tapped to change photo
                                if (totalMoved < 22f && duration < 320) {
                                    val cardWidth = size.width
                                    val isLeftTap = startTouch.x < cardWidth * 0.45f

                                    if (isLeftTap) {
                                        if (currentPhotoIndex > 0) currentPhotoIndex--
                                    } else {
                                        if (currentPhotoIndex < activeProfile.photos.size - 1) {
                                            currentPhotoIndex++
                                        }
                                    }
                                    // Reset offsets just in case
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f)
                                        offsetY.animateTo(0f)
                                    }
                                } else {
                                    // Fling velocity or distance threshold exceeded
                                    val isRight = offsetX.value > 220f || velocityX > 1100f
                                    val isLeft = offsetX.value < -220f || velocityX < -1100f

                                    if (isRight) {
                                        triggerSwipe(isLike = true)
                                    } else if (isLeft) {
                                        triggerSwipe(isLike = false)
                                    } else {
                                        // Snap back smoothly to center
                                        coroutineScope.launch {
                                            launch {
                                                offsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                            launch {
                                                offsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    launch {
                                        offsetX.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                        )
                                    }
                                    launch {
                                        offsetY.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                        )
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (!isSwipingOut) {
                                    change.consume()
                                    totalMoved += hypot(dragAmount.x, dragAmount.y)
                                    coroutineScope.launch {
                                        offsetX.snapTo(offsetX.value + dragAmount.x)
                                        // Subtle vertical tracking for organic hand arc
                                        offsetY.snapTo(offsetY.value + dragAmount.y * 0.4f)
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Card Body
                CardLayer(
                    profile = activeProfile,
                    photoIndex = currentPhotoIndex,
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    onOpenFilter = onOpenFilter,
                    onExpand = { onExpandProfile(activeProfile) },
                    activeCategoryFilter = activeCategoryFilter,
                    onClearFilter = onClearFilter,
                    modifier = Modifier.fillMaxSize()
                )

                // -------------------------------------------------------------
                // STAMP: "LIKE" (Appears on right drag)
                // -------------------------------------------------------------
                val likeAlpha = (offsetX.value / 120f).coerceIn(0f, 1f)
                if (likeAlpha > 0f) {
                    val likeScale = 0.8f + 0.25f * likeAlpha
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x2820D5A3),
                        border = BorderStroke(3.5.dp, Color(0xFF20D5A3)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 28.dp, top = 94.dp)
                            .graphicsLayer {
                                rotationZ = -15f
                                alpha = likeAlpha
                                scaleX = likeScale
                                scaleY = likeScale
                            }
                    ) {
                        Text(
                            text = "LIKE",
                            color = Color(0xFF20D5A3),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // -------------------------------------------------------------
                // STAMP: "NOPE" / DISLIKE (Appears on left drag)
                // -------------------------------------------------------------
                val nopeAlpha = (-offsetX.value / 120f).coerceIn(0f, 1f)
                if (nopeAlpha > 0f) {
                    val nopeScale = 0.8f + 0.25f * nopeAlpha
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x28FF4458),
                        border = BorderStroke(3.5.dp, Color(0xFFFF4458)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 28.dp, top = 94.dp)
                            .graphicsLayer {
                                rotationZ = 15f
                                alpha = nopeAlpha
                                scaleX = nopeScale
                                scaleY = nopeScale
                            }
                    ) {
                        Text(
                            text = "NOPE",
                            color = Color(0xFFFF4458),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // Action Buttons: Rewind (↺), Dislike (✕), Super Like (★), Like (♥), First Impression (✈)
            // (Dynamically synchronized with the drag state!)
            // -------------------------------------------------------------
            val dislikeScale = 1f + ((-offsetX.value / 360f).coerceIn(0f, 0.22f))
            val likeScale = 1f + ((offsetX.value / 360f).coerceIn(0f, 0.22f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Rewind Button (↺)
                InteractiveTinderButton(
                    symbol = "↺",
                    tint = Color(0xFFF5B800),
                    baseSize = 48.dp,
                    fontSize = 22.sp,
                    elevation = 6.dp,
                    scaleFactor = 1f,
                    onClick = { triggerRewind() }
                )

                Spacer(modifier = Modifier.width(20.dp))

                // 2. Dislike Button (✕)
                InteractiveTinderButton(
                    symbol = "✕",
                    tint = Color(0xFFFF4458),
                    baseSize = 64.dp,
                    fontSize = 28.sp,
                    elevation = 8.dp,
                    scaleFactor = dislikeScale,
                    onClick = { triggerSwipe(isLike = false) }
                )

                Spacer(modifier = Modifier.width(20.dp))

                // 3. Like Button (♥)
                InteractiveTinderButton(
                    symbol = "♥",
                    tint = Color(0xFFFF4458), // Authentic Tinder coral-red heart
                    baseSize = 64.dp,
                    fontSize = 32.sp,
                    elevation = 8.dp,
                    scaleFactor = likeScale,
                    onClick = { triggerSwipe(isLike = true) }
                )

                Spacer(modifier = Modifier.width(20.dp))

                // 4. First Impression Button (✈ / Paper Plane)
                InteractiveTinderButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    tint = Color(0xFF5AB9F8), // Sky cyan blue matching screenshot
                    baseSize = 48.dp,
                    elevation = 6.dp,
                    scaleFactor = 1f,
                    onClick = {
                        if (activeProfile != null) {
                            onFirstImpression?.invoke(activeProfile)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Reusable visual Card Layer rendering photo, gradients, header, and profile info
 */
@Composable
private fun CardLayer(
    profile: DatingProfile,
    photoIndex: Int,
    activeTab: String = "For you",
    onTabSelected: ((String) -> Unit)? = null,
    onOpenFilter: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    activeCategoryFilter: String? = null,
    onClearFilter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val photo = profile.photos.getOrElse(photoIndex) {
        profile.photos.firstOrNull() ?: ""
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DashboardCard)
    ) {
        // Full Photo
        AsyncImage(
            model = photo,
            contentDescription = "${profile.name}'s photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Photo Wash Gradient (exact matching screenshot_1.png)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xAD231914),
                            0.22f to Color.Transparent,
                            0.48f to Color.Transparent,
                            0.64f to Color(0x18231914),
                            0.82f to Color(0xEB231914),
                            1.0f to DashboardBg
                        )
                    )
                )
        )

        // Top Header: Filter circle button & Tabs (For you, Astrology, Music)
        if (onTabSelected != null && onOpenFilter != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Filter Circle button
                    Surface(
                        onClick = onOpenFilter,
                        shape = CircleShape,
                        color = Color(0x6B201B18),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "☰",
                                color = DashboardCream,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tabs: For you, Astrology, Music
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x3B201B18))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("For you", "Astrology", "Music").forEach { tab ->
                            val isActive = activeTab == tab
                            Surface(
                                onClick = { onTabSelected(tab) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isActive) DashboardCream else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = tab,
                                    color = if (isActive) Color(0xFF28231F) else DashboardCream.copy(alpha = 0.74f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Segmented Photo progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    profile.photos.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (index == photoIndex) DashboardCream
                                    else DashboardCream.copy(alpha = 0.35f)
                                )
                        )
                    }
                }

                if (activeCategoryFilter != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xD9201B18),
                        border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.6f)),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✨ Space: $activeCategoryFilter",
                                color = DashboardPeach,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (onClearFilter != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onClearFilter() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Profile Info Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, end = 22.dp, bottom = 100.dp)
        ) {
            // Recently Active Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x85201B18),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(DashboardTerracotta)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Recently active",
                        color = DashboardCream,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Name & Age (Georgia / Serif style) with Expand Arrow (Matches screenshot_1.png)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable { onExpand?.invoke() }
                ) {
                    Text(
                        text = profile.name,
                        color = DashboardCream,
                        fontSize = 38.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "${profile.age}",
                        color = DashboardPeach,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal
                    )
                }

                // Up arrow button to open expanded profile
                if (onExpand != null) {
                    Surface(
                        onClick = onExpand,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand profile",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Location
            Text(
                text = "⌖  ${profile.location}",
                color = DashboardMutedBeige,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bio
            Text(
                text = profile.bio,
                color = DashboardCream.copy(alpha = 0.92f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            // Dynamic Tab Pills
            when (activeTab) {
                "Astrology" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x60B24B39),
                        border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Nakshatra: ${profile.astrologyStar} · Highly Compatible",
                            color = DashboardCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                "Music" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x60342823),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "🎵 Anthem: ${profile.musicAnthem}",
                            color = DashboardCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive button with spring bounce and dynamic drag scale factor
 */
@Composable
private fun InteractiveTinderButton(
    symbol: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color,
    baseSize: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
    elevation: androidx.compose.ui.unit.Dp,
    scaleFactor: Float = 1f,
    onClick: () -> Unit
) {
    val clickScale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        onClick = {
            coroutineScope.launch {
                clickScale.animateTo(0.82f, tween(60))
                clickScale.animateTo(
                    1.0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            onClick()
        },
        shape = CircleShape,
        color = Color(0xF22A211D),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = elevation,
        modifier = Modifier
            .size(baseSize)
            .graphicsLayer {
                val effectiveScale = scaleFactor * clickScale.value
                scaleX = effectiveScale
                scaleY = effectiveScale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(baseSize * 0.44f)
                )
            } else {
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
}
