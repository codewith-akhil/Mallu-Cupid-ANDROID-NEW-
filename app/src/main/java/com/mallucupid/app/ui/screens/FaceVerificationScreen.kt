package com.mallucupid.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.delay

enum class VerificationStage {
    INTRO,
    CAMERA_POSE_1, // Look straight & smile
    CAMERA_POSE_2, // Tilt head right
    ANALYZING,
    SUCCESS
}

@Composable
fun FaceVerificationScreen(
    draft: OnboardingDraft,
    onVerificationComplete: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(VerificationStage.INTRO) }
    var capturedPhoto1 by remember { mutableStateOf<Bitmap?>(null) }
    var capturedPhoto2 by remember { mutableStateOf<Bitmap?>(null) }
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var analysisStatusText by remember { mutableStateOf("Initializing scanner...") }

    // Camera Capture Launcher for live selfies
    val takeSelfieLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            if (stage == VerificationStage.CAMERA_POSE_1) {
                capturedPhoto1 = bitmap
                stage = VerificationStage.CAMERA_POSE_2
                Toast.makeText(context, "Pose 1 recorded! Now complete Pose 2", Toast.LENGTH_SHORT).show()
            } else if (stage == VerificationStage.CAMERA_POSE_2) {
                capturedPhoto2 = bitmap
                stage = VerificationStage.ANALYZING
            }
        }
    }

    // Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takeSelfieLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed for face verification", Toast.LENGTH_SHORT).show()
        }
    }

    // Simulated multi-step AI liveness check
    LaunchedEffect(stage) {
        if (stage == VerificationStage.ANALYZING) {
            analysisStatusText = "Scanning 3D facial geometry..."
            analysisProgress = 0.25f
            delay(900)

            analysisStatusText = "Validating liveness & anti-spoofing..."
            analysisProgress = 0.60f
            delay(1000)

            analysisStatusText = "Matching with your Kerala profile photos..."
            analysisProgress = 0.88f
            delay(1100)

            analysisProgress = 1.0f
            analysisStatusText = "Verification complete! 100% Match."
            delay(600)
            stage = VerificationStage.SUCCESS
        }
    }

    Scaffold(
        containerColor = DashboardBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DashboardCream
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Photo Verification",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (stage) {
                // ==========================================
                // 1. INTRO STAGE (Explains Tinder-style verification)
                // ==========================================
                VerificationStage.INTRO -> {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Shield & Blue Badge Icon
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF261E1A),
                        border = BorderStroke(2.dp, SuperBlue),
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = SuperBlue,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Get Verified on Mallu Cupid",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Show Kerala singles you're really the person in your photos by taking 2 quick selfie poses.",
                        fontSize = 14.sp,
                        color = DashboardMutedBeige,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // 3 Feature bullet cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VerificationBenefitItem(
                            icon = Icons.Default.CheckCircle,
                            iconTint = SuperBlue,
                            title = "Earn a Blue Verified Checkmark",
                            desc = "The official badge displays on your profile and in chat."
                        )
                        VerificationBenefitItem(
                            icon = Icons.Default.Bolt,
                            iconTint = RewindGold,
                            title = "Receive 2x More Matches",
                            desc = "Kerala singles trust and prefer chatting with photo-verified profiles."
                        )
                        VerificationBenefitItem(
                            icon = Icons.Default.Lock,
                            iconTint = DashboardPeach,
                            title = "Strict Privacy Guaranteed",
                            desc = "Selfies are only used to verify your identity and never shown on your feed."
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Verify Now Action Button
                    Button(
                        onClick = {
                            stage = VerificationStage.CAMERA_POSE_1
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DashboardTerracotta,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verify Now",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ==========================================
                // 2. LIVE CAMERA POSE 1 & 2
                // ==========================================
                VerificationStage.CAMERA_POSE_1, VerificationStage.CAMERA_POSE_2 -> {
                    val isPose1 = stage == VerificationStage.CAMERA_POSE_1

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress indicators for Pose 1 & 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepPill(title = "Pose 1", isActive = isPose1, isDone = !isPose1)
                        Spacer(modifier = Modifier.width(12.dp))
                        StepPill(title = "Pose 2", isActive = !isPose1, isDone = false)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (isPose1) "Pose 1: Look straight and smile 😊" else "Pose 2: Tilt head to the right 👉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isPose1)
                            "Position your face inside the oval frame and look directly into the camera."
                        else
                            "Keep your shoulders steady and tilt your head slightly to your right side.",
                        fontSize = 13.sp,
                        color = DashboardNavMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Live Camera Oval Guide Reticle
                    Box(
                        modifier = Modifier
                            .size(width = 240.dp, height = 300.dp)
                            .clip(RoundedCornerShape(120.dp))
                            .background(Color(0xFF261E1A))
                            .border(3.dp, if (isPose1) SuperBlue else DashboardPeach, RoundedCornerShape(120.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPose1 && capturedPhoto1 != null) {
                            Image(
                                bitmap = capturedPhoto1!!.asImageBitmap(),
                                contentDescription = "Pose 1 captured",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!isPose1 && capturedPhoto2 != null) {
                            Image(
                                bitmap = capturedPhoto2!!.asImageBitmap(),
                                contentDescription = "Pose 2 captured",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Scanner Reticle Animation
                            val infiniteTransition = rememberInfiniteTransition(label = "scan")
                            val scanOffset by infiniteTransition.animateFloat(
                                initialValue = -80f,
                                targetValue = 80f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scan_offset"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isPose1) Icons.Default.Face else Icons.Default.FaceRetouchingNatural,
                                    contentDescription = null,
                                    tint = DashboardNavMuted,
                                    modifier = Modifier.size(90.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isPose1) "Hold face here" else "Turn head slightly",
                                    fontSize = 13.sp,
                                    color = DashboardNavMuted
                                )
                            }

                            // Moving scan line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .offset(y = scanOffset.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, SuperBlue, Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Shutter / Capture Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Demo quick simulation button for testing in emulators without camera
                        OutlinedButton(
                            onClick = {
                                if (isPose1) {
                                    stage = VerificationStage.CAMERA_POSE_2
                                    Toast.makeText(context, "Pose 1 recorded (Simulated)", Toast.LENGTH_SHORT).show()
                                } else {
                                    stage = VerificationStage.ANALYZING
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardPeach),
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulate Pose", fontSize = 13.sp)
                        }

                        // Real Camera Shutter
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Capture Selfie",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ==========================================
                // 3. ANALYZING / LIVENESS VALIDATION
                // ==========================================
                VerificationStage.ANALYZING -> {
                    Spacer(modifier = Modifier.height(60.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { analysisProgress },
                            modifier = Modifier.size(130.dp),
                            color = SuperBlue,
                            trackColor = Color(0xFF382D27),
                            strokeWidth = 6.dp
                        )
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = SuperBlue,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = "Analyzing Verification",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = analysisStatusText,
                        fontSize = 14.sp,
                        color = DashboardPeach,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    LinearProgressIndicator(
                        progress = { analysisProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = DashboardTerracotta,
                        trackColor = Color(0xFF382D27)
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = "Checking that your live selfie matches the 6 profile photos uploaded on your Mallu Cupid account.",
                        fontSize = 12.sp,
                        color = DashboardNavMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }

                // ==========================================
                // 4. VERIFICATION SUCCESS
                // ==========================================
                VerificationStage.SUCCESS -> {
                    Spacer(modifier = Modifier.height(40.dp))

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E3A5F),
                        border = BorderStroke(3.dp, SuperBlue),
                        modifier = Modifier.size(110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = SuperBlue,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "You're Verified!",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Congratulations! Your selfie matched your profile pictures. The blue verified badge has now been applied to your Mallu Cupid account.",
                        fontSize = 14.sp,
                        color = DashboardMutedBeige,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DashboardCard,
                        border = BorderStroke(1.dp, Color(0xFF42342D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SuperBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Photo Verified Status: Active",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardCream
                                )
                                Text(
                                    text = "Your profile is boosted in Kerala discovery feeds.",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            val updatedDraft = draft.copy(isVerified = true)
                            onVerificationComplete(updatedDraft)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Done & Return to Profile",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StepPill(title: String, isActive: Boolean, isDone: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            isDone -> SuperBlue
            isActive -> DashboardTerracotta
            else -> Color(0xFF261E1A)
        },
        border = BorderStroke(1.dp, if (isActive || isDone) Color.Transparent else Color(0xFF42342D))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive || isDone) Color.White else DashboardNavMuted
            )
        }
    }
}

@Composable
private fun VerificationBenefitItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color(0xFF42342D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = DashboardNavMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
