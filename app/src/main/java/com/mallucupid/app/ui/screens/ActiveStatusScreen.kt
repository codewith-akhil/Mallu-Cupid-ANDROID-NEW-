package com.mallucupid.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
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
import com.mallucupid.app.ui.theme.*

/**
 * Active Status Screen
 * Faithfully matches the user's reference screenshot 1 with Mallu Cupid luxury dark palette:
 * - "Show active status" (displays if active in Mallu Cupid within 2 hours)
 * - "Show recently active status" (displays if active in Mallu Cupid within 24 hours)
 */
@Composable
fun ActiveStatusScreen(
    draft: OnboardingDraft,
    onUpdateDraft: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    var showActive by remember { mutableStateOf(draft.showActiveStatus) }
    var showRecentlyActive by remember { mutableStateOf(draft.showRecentlyActiveStatus) }

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
                                showActiveStatus = showActive,
                                showRecentlyActiveStatus = showRecentlyActive
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
                    text = "Active",
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
            // Card 1: Show active status
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show active status",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )

                    Switch(
                        checked = showActive,
                        onCheckedChange = { isChecked ->
                            showActive = isChecked
                            onUpdateDraft(draft.copy(showActiveStatus = isChecked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DashboardTerracotta,
                            uncheckedThumbColor = DashboardNavMuted,
                            uncheckedTrackColor = Color(0xFF382D27)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Active status is displayed on your profile if you were active in the Mallu Cupid app within the last 2 hours",
                fontSize = 13.sp,
                color = DashboardNavMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Card 2: Show recently active status
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show recently active status",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )

                    Switch(
                        checked = showRecentlyActive,
                        onCheckedChange = { isChecked ->
                            showRecentlyActive = isChecked
                            onUpdateDraft(draft.copy(showRecentlyActiveStatus = isChecked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DashboardTerracotta,
                            uncheckedThumbColor = DashboardNavMuted,
                            uncheckedTrackColor = Color(0xFF382D27)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recently active status is displayed on your profile if you were active in the Mallu Cupid app within the last 24 hours",
                fontSize = 13.sp,
                color = DashboardNavMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
