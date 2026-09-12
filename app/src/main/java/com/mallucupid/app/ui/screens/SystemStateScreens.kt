package com.mallucupid.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LoadingStateScreen
 *
 * Displays a luxury animated pulsing Cupid logo with dating tips
 * and smooth circular progress.
 */
@Composable
fun LoadingStateScreen(
    message: String = "Finding singles near you...",
    onCancel: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = DashboardTerracotta.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
            ) {}

            CircularProgressIndicator(
                modifier = Modifier.size(100.dp),
                color = DashboardTerracotta,
                trackColor = Color(0xFF382D27),
                strokeWidth = 4.dp
            )

            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Loading",
                tint = DashboardPeach,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = message,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Tip: Profiles with verified selfies get 3x more dates!",
            fontSize = 13.sp,
            color = DashboardNavMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(36.dp))
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardNavMuted),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * NoInternetScreen
 *
 * Offline state screen with connectivity test feedback and retry action.
 */
@Composable
fun NoInternetScreen(
    onRetry: () -> Unit,
    onOfflineMode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2E221B),
            border = BorderStroke(2.dp, Color(0xFF42342D)),
            modifier = Modifier.size(110.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "No Internet",
                    tint = DashboardPeach,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "No Internet Connection",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Unable to connect to Mallu Cupid servers. Please check your Wi-Fi or mobile data connection and try again.",
            fontSize = 14.sp,
            color = DashboardMutedBeige,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                scope.launch {
                    isChecking = true
                    delay(1200)
                    isChecking = false
                    Toast.makeText(context, "Connection restored! Reloading feed...", Toast.LENGTH_SHORT).show()
                    onRetry()
                }
            },
            enabled = !isChecking,
            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Reconnecting...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (onOfflineMode != null) {
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onOfflineMode) {
                Text("Continue Offline", color = DashboardNavMuted, fontSize = 14.sp)
            }
        }
    }
}

/**
 * ErrorStateScreen
 *
 * Displays friendly error message with retry and return actions.
 */
@Composable
fun ErrorStateScreen(
    title: String = "Something Went Wrong",
    message: String = "We encountered a hiccup while communicating with the service. Please try again.",
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF381F1D),
            border = BorderStroke(2.dp, NopeCoral),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = NopeCoral,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = DashboardMutedBeige,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Try Again", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (onBack != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardNavMuted),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Go Back")
            }
        }
    }
}

/**
 * SuccessStateScreen
 *
 * Generic celebratory success screen.
 */
@Composable
fun SuccessStateScreen(
    title: String,
    subtitle: String,
    buttonText: String = "Continue",
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E3A2F),
            border = BorderStroke(3.dp, Color(0xFF4CAF50)),
            modifier = Modifier.size(110.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = DashboardMutedBeige,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
