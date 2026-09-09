package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstImpressionScreen(
    profile: DatingProfile,
    onDismiss: () -> Unit,
    onSendMessage: (DatingProfile, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentPhotoIndex by remember { mutableIntStateOf(0) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showSuccessCelebration by remember { mutableStateOf(false) }

    val photos = remember(profile.photos) {
        if (profile.photos.isNotEmpty()) profile.photos
        else listOf("https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=900&q=85")
    }

    fun handleNextPhoto() {
        if (photos.isNotEmpty()) {
            currentPhotoIndex = (currentPhotoIndex + 1) % photos.size
        }
    }

    fun handlePrevPhoto() {
        if (photos.isNotEmpty()) {
            currentPhotoIndex = (currentPhotoIndex - 1 + photos.size) % photos.size
        }
    }

    fun triggerSendMessage() {
        if (messageText.isBlank() || isSending) return
        focusManager.clearFocus()
        isSending = true
        coroutineScope.launch {
            showSuccessCelebration = true
            delay(1200)
            onSendMessage(profile, messageText.trim())
            onDismiss()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DashboardBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // ---------------------------------------------------------------------
        // 1. TOP BAR: Back icon on left, "First Impression" heading in center
        // (Includes statusBarsPadding so it doesn't collide with device status bar)
        // ---------------------------------------------------------------------
        topBar = {
            Surface(
                color = DashboardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Back button on the left
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = Color(0x3BFFFFFF),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DashboardCream,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // "First Impression" heading centered
                    Text(
                        text = "First Impression",
                        color = DashboardCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Impression credit badge on top right
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x3D2B231F),
                        border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.5f)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "⚡ 1 Free",
                                color = DashboardPeach,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        // ---------------------------------------------------------------------
        // TYPE BOX WITH SEND ICON
        // (Includes navigationBarsPadding and imePadding so it sits above keyboard)
        // ---------------------------------------------------------------------
        bottomBar = {
            Surface(
                color = DashboardCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Text input pill
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0x33FFFFFF),
                        border = BorderStroke(
                            1.dp,
                            if (messageText.isNotBlank()) DashboardTerracotta else Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = "Send a message...",
                                        color = DashboardMutedBeige.copy(alpha = 0.6f),
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    textStyle = TextStyle(
                                        color = DashboardCream,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(DashboardPeach),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Circular Send Button with Send Icon
                    val canSend = messageText.isNotBlank() && !isSending
                    Surface(
                        onClick = { triggerSendMessage() },
                        shape = CircleShape,
                        color = if (canSend) DashboardTerracotta else Color(0x33FFFFFF),
                        border = BorderStroke(
                            1.dp,
                            if (canSend) DashboardPeach else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    color = DashboardCream,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send first impression",
                                    tint = if (canSend) DashboardCream else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Header Title only (removed "Up to 5x..." and "Stand out with...")
                Text(
                    text = "Send a First Impression, Breake the ice",
                    color = DashboardCream,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // -------------------------------------------------------------
                // 3. ACCOUNT IMAGES WITH LEFT/RIGHT SWIPE & ICONS
                // -------------------------------------------------------------
                val currentPhoto = photos.getOrElse(currentPhotoIndex) { photos[0] }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardCard),
                    border = BorderStroke(1.5.dp, Color(0xFF5AB9F8).copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .pointerInput(currentPhotoIndex) {
                            var dragTotal = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragTotal > 40f) {
                                        handlePrevPhoto()
                                    } else if (dragTotal < -40f) {
                                        handleNextPhoto()
                                    }
                                    dragTotal = 0f
                                },
                                onDragCancel = { dragTotal = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragTotal += dragAmount
                                }
                            )
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = currentPhoto,
                            contentDescription = "${profile.name}'s photo ${currentPhotoIndex + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top right image counter (e.g. 1/8)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xCC000000),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${currentPhotoIndex + 1}/${photos.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Top dash indicators
                        if (photos.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp, start = 12.dp, end = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                photos.indices.forEach { index ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (index == currentPhotoIndex) Color.White
                                                else Color.White.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }

                        // Left swipe/tap icon button
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .align(Alignment.CenterStart)
                                .clickable { handlePrevPhoto() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photos.size > 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x8A000000),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Previous photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right swipe/tap icon button
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .align(Alignment.CenterEnd)
                                .clickable { handleNextPhoto() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photos.size > 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x8A000000),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Next photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // -------------------------------------------------------------
                // 4. NAME & VERIFICATION BADGE (If not verified - show unverified)
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${profile.name}, ${profile.age}",
                        color = DashboardCream,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )

                    if (profile.isVerified) {
                        // Verified badge (Sky blue)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x2E2196F3),
                            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.7f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified profile",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Unverified badge (Amber warning, as explicitly requested)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x38E5A100),
                            border = BorderStroke(1.dp, Color(0xFFE5A100).copy(alpha = 0.8f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Unverified profile",
                                    tint = Color(0xFFE5A100),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Unverified",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "⌖ ${profile.location}  ·  ${profile.profession}",
                    color = DashboardMutedBeige.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success Celebration Overlay
            AnimatedVisibility(
                visible = showSuccessCelebration,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xDB16120F)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = DashboardCard),
                        border = BorderStroke(1.5.dp, DashboardTerracotta),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = DashboardTerracotta.copy(alpha = 0.25f),
                                border = BorderStroke(2.dp, DashboardTerracotta),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "💌",
                                        fontSize = 32.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "First Impression Sent!",
                                color = DashboardCream,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Delivered to ${profile.name}. If they like you back, you'll instantly match!",
                                color = DashboardMutedBeige,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
