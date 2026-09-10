package com.mallucupid.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.delay

enum class PremiumFlowStep {
    OFFER,
    PAYMENT_GATEWAY,
    PAYMENT_VERIFICATION,
    PAYMENT_SUCCESS
}

@Composable
fun PremiumSubscriptionFlow(
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(PremiumFlowStep.OFFER) }
    var selectedPaymentMethod by remember { mutableStateOf("GPay") } // GPay, PhonePe, Paytm, Cards, NetBanking
    var upiIdInput by remember { mutableStateOf("user@okhdfcbank") }

    // Verification progress states
    var verificationStatus by remember { mutableStateOf("Connecting to UPI Network...") }
    var verificationProgress by remember { mutableFloatStateOf(0.15f) }

    LaunchedEffect(currentStep) {
        if (currentStep == PremiumFlowStep.PAYMENT_VERIFICATION) {
            verificationProgress = 0.25f
            verificationStatus = "Requesting bank authorization..."
            delay(1000)

            verificationProgress = 0.65f
            verificationStatus = "Verifying 256-bit token with NPCI..."
            delay(1200)

            verificationProgress = 0.95f
            verificationStatus = "Authorizing ₹49.00 debit from account..."
            delay(800)

            verificationProgress = 1.0f
            verificationStatus = "Payment verified successfully!"
            delay(400)
            currentStep = PremiumFlowStep.PAYMENT_SUCCESS
        }
    }

    Scaffold(
        containerColor = DashboardBg,
        topBar = {
            if (currentStep != PremiumFlowStep.PAYMENT_VERIFICATION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentStep == PremiumFlowStep.OFFER) {
                            onBack()
                        } else if (currentStep == PremiumFlowStep.PAYMENT_GATEWAY) {
                            currentStep = PremiumFlowStep.OFFER
                        } else if (currentStep == PremiumFlowStep.PAYMENT_SUCCESS) {
                            onSuccess()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DashboardCream
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentStep) {
                            PremiumFlowStep.OFFER -> "Mallu Cupid Premium"
                            PremiumFlowStep.PAYMENT_GATEWAY -> "Secure Payment Gateway"
                            PremiumFlowStep.PAYMENT_VERIFICATION -> "Processing Payment"
                            PremiumFlowStep.PAYMENT_SUCCESS -> "Subscription Activated"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentStep) {
                // ==========================================
                // 1. OFFER SCREEN (₹49 INR PER WEEK)
                // ==========================================
                PremiumFlowStep.OFFER -> {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Premium Golden / Terracotta Card Banner
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF2E221B),
                            border = BorderStroke(1.5.dp, DashboardTerracotta),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = DashboardTerracotta,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "MALLU CUPID PREMIUM",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = DashboardCream
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "#1 Dating Experience",
                                    fontSize = 13.sp,
                                    color = DashboardPeach
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Price tag: 49 INR PER WEEK
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = DashboardTerracotta.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, DashboardTerracotta)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "₹49",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Black,
                                            color = DashboardCream
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "/ WEEK",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DashboardPeach
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Special introductory pricing · Cancel anytime",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Features requested: UNLIMITED LIKE, SEE WHO LIKES U, UNLIMITED CHAT, UNLIMITED REWIND
                        Text(
                            text = "PREMIUM PERKS INCLUDED",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardNavMuted,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PremiumPerkCard(
                                icon = Icons.Default.Favorite,
                                iconColor = NopeCoral,
                                title = "Unlimited Likes",
                                description = "Swipe on as many nearby profiles as your heart desires with zero daily limits."
                            )
                            PremiumPerkCard(
                                icon = Icons.Default.Visibility,
                                iconColor = RewindGold,
                                title = "See Who Likes You",
                                description = "Unblur all incoming likes and match instantly without having to wait in the feed."
                            )
                            PremiumPerkCard(
                                icon = Icons.Default.ChatBubble,
                                iconColor = SuperBlue,
                                title = "Unlimited Chat",
                                description = "Message and video/photo share freely with any match nearby anytime."
                            )
                            PremiumPerkCard(
                                icon = Icons.Default.Replay,
                                iconColor = DashboardPeach,
                                title = "Unlimited Rewind",
                                description = "Accidentally swiped left on someone special? Undo your swipe with 1 tap."
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ACTIVATE BUTTON -> PAYMENT GATEWAY
                        Button(
                            onClick = {
                                currentStep = PremiumFlowStep.PAYMENT_GATEWAY
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DashboardTerracotta,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = "Activate Now · ₹49 / Week",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ==========================================
                // 2. PAYMENT GATEWAY (UPI, GPay, Cards)
                // ==========================================
                PremiumFlowStep.PAYMENT_GATEWAY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // Order Summary Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DashboardCard,
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Mallu Cupid Premium (1 Week)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DashboardCream
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Order ID: MC_PRM_9082",
                                        fontSize = 12.sp,
                                        color = DashboardNavMuted
                                    )
                                }
                                Text(
                                    text = "₹49.00",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = DashboardCream
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "SELECT PAYMENT OPTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardNavMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Payment Methods
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaymentOptionTile(
                                title = "Google Pay (UPI)",
                                subtitle = "Instant checkout with GPay app",
                                isSelected = selectedPaymentMethod == "GPay",
                                onClick = { selectedPaymentMethod = "GPay" }
                            )
                            PaymentOptionTile(
                                title = "PhonePe",
                                subtitle = "Pay via UPI PIN on PhonePe",
                                isSelected = selectedPaymentMethod == "PhonePe",
                                onClick = { selectedPaymentMethod = "PhonePe" }
                            )
                            PaymentOptionTile(
                                title = "Paytm / Any UPI ID",
                                subtitle = "BHIM UPI, Cred, or custom VPA",
                                isSelected = selectedPaymentMethod == "Paytm",
                                onClick = { selectedPaymentMethod = "Paytm" }
                            )
                            PaymentOptionTile(
                                title = "Debit / Credit Card",
                                subtitle = "Visa, MasterCard, RuPay",
                                isSelected = selectedPaymentMethod == "Cards",
                                onClick = { selectedPaymentMethod = "Cards" }
                            )
                        }

                        if (selectedPaymentMethod == "Paytm") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = upiIdInput,
                                onValueChange = { upiIdInput = it },
                                label = { Text("Enter UPI ID / VPA") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DashboardTerracotta,
                                    unfocusedBorderColor = Color(0xFF42342D),
                                    focusedTextColor = DashboardCream,
                                    unfocusedTextColor = DashboardCream
                                )
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Security Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = SuperBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "256-Bit SSL Encrypted Indian Payment Gateway",
                                fontSize = 11.sp,
                                color = DashboardNavMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Proceed to Payment Verification
                        Button(
                            onClick = {
                                currentStep = PremiumFlowStep.PAYMENT_VERIFICATION
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Pay ₹49.00 Securely",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ==========================================
                // 3. PAYMENT VERIFICATION (Processing Gateway)
                // ==========================================
                PremiumFlowStep.PAYMENT_VERIFICATION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF261E1A),
                            border = BorderStroke(2.dp, DashboardTerracotta),
                            modifier = Modifier.size(110.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { verificationProgress },
                                    modifier = Modifier.size(90.dp),
                                    color = DashboardTerracotta,
                                    trackColor = Color(0xFF42342D),
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.CurrencyRupee,
                                    contentDescription = null,
                                    tint = DashboardCream,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        Text(
                            text = "Verifying ₹49.00 Payment",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = verificationStatus,
                            fontSize = 14.sp,
                            color = DashboardPeach,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        LinearProgressIndicator(
                            progress = { verificationProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = DashboardTerracotta,
                            trackColor = Color(0xFF382D27)
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        Text(
                            text = "Please do not press Back or switch apps while we verify your transaction with the bank.",
                            fontSize = 12.sp,
                            color = DashboardNavMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                // ==========================================
                // 4. SUCCESS SCREEN
                // ==========================================
                PremiumFlowStep.PAYMENT_SUCCESS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(30.dp))

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

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Payment Successful!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DashboardCream
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "₹49.00 paid · Transaction ID: MC_UPI_839210",
                            fontSize = 13.sp,
                            color = DashboardPeach
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DashboardCard,
                            border = BorderStroke(1.dp, Color(0xFF42342D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "MALLU CUPID PREMIUM ACTIVATED",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardCream
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlimited Likes activated", fontSize = 13.sp, color = DashboardMutedBeige)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("See Who Likes You unlocked", fontSize = 13.sp, color = DashboardMutedBeige)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlimited Chat & Media Sharing active", fontSize = 13.sp, color = DashboardMutedBeige)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlimited Rewinds ready to use", fontSize = 13.sp, color = DashboardMutedBeige)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = onSuccess,
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Start Exploring with Premium",
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
}

@Composable
private fun PremiumPerkCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color(0xFF42342D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = DashboardNavMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PaymentOptionTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF382A24) else Color(0xFF261E1A),
        border = BorderStroke(1.dp, if (isSelected) DashboardTerracotta else Color(0xFF42342D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = DashboardNavMuted
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = DashboardTerracotta,
                    unselectedColor = DashboardNavMuted
                )
            )
        }
    }
}
