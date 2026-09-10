package com.mallucupid.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.ui.theme.*

/**
 * Email Settings Screen
 * Faithfully matches the user's reference screenshot 2 with Mallu Cupid luxury dark palette:
 * - Email address card with description
 * - "Verified email address" status
 * - "Send verification email" button
 * - Email subscriptions card: New matches, New messages, Promotions
 * - "Unsubscribe from all" button
 */
@Composable
fun EmailSettingsScreen(
    draft: OnboardingDraft,
    onUpdateDraft: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var subMatches by remember { mutableStateOf(draft.emailSubMatches) }
    var subMessages by remember { mutableStateOf(draft.emailSubMessages) }
    var subPromos by remember { mutableStateOf(draft.emailSubPromos) }

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
                                emailSubMatches = subMatches,
                                emailSubMessages = subMessages,
                                emailSubPromos = subPromos
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
                    text = "Email",
                    fontSize = 22.sp,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Section 1: Email address
            Text(
                text = "Email address",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardCream,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = draft.registeredEmail,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DashboardCream
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Control the emails you want to get – all of them, just the important stuff or the bare minimum. You can always unsubscribe at the bottom of any email.",
                        fontSize = 13.sp,
                        color = DashboardNavMuted,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Verified email address",
                fontSize = 13.sp,
                color = SuperBlue,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "Verification link sent to ${draft.registeredEmail}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF382D27),
                    contentColor = DashboardCream
                )
            ) {
                Text(
                    text = "Send verification email",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 2: Email subscriptions
            Text(
                text = "Email subscriptions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardCream,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // New matches
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New matches",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = DashboardCream
                        )
                        Switch(
                            checked = subMatches,
                            onCheckedChange = {
                                subMatches = it
                                onUpdateDraft(draft.copy(emailSubMatches = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DashboardTerracotta,
                                uncheckedThumbColor = DashboardNavMuted,
                                uncheckedTrackColor = Color(0xFF382D27)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // New messages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New messages",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = DashboardCream
                        )
                        Switch(
                            checked = subMessages,
                            onCheckedChange = {
                                subMessages = it
                                onUpdateDraft(draft.copy(emailSubMessages = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DashboardTerracotta,
                                uncheckedThumbColor = DashboardNavMuted,
                                uncheckedTrackColor = Color(0xFF382D27)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF42342D), thickness = 0.8.dp)

                    // Promotions
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Promotions",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = DashboardCream
                            )
                            Switch(
                                checked = subPromos,
                                onCheckedChange = {
                                    subPromos = it
                                    onUpdateDraft(draft.copy(emailSubPromos = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = DashboardTerracotta,
                                    uncheckedThumbColor = DashboardNavMuted,
                                    uncheckedTrackColor = Color(0xFF382D27)
                                )
                            )
                        }
                        Text(
                            text = "I want to receive news, updates and offers from Mallu Cupid",
                            fontSize = 12.sp,
                            color = DashboardNavMuted,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp, end = 48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Unsubscribe from all
            Button(
                onClick = {
                    subMatches = false
                    subMessages = false
                    subPromos = false
                    onUpdateDraft(
                        draft.copy(
                            emailSubMatches = false,
                            emailSubMessages = false,
                            emailSubPromos = false
                        )
                    )
                    Toast.makeText(context, "Unsubscribed from all email notifications", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF191412),
                    contentColor = DashboardCream
                ),
                border = BorderStroke(1.dp, Color(0xFF42342D))
            ) {
                Text(
                    text = "Unsubscribe from all",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
