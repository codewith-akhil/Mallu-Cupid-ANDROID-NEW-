package com.mallucupid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.PromptItem
import com.mallucupid.app.data.SampleProfiles
import com.mallucupid.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    initialDraft: OnboardingDraft,
    onSaveAndClose: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf(initialDraft) }
    var selectedTab by remember { mutableStateOf("Edit") } // "Edit" or "Preview"
    var showAddPromptDialog by remember { mutableStateOf(false) }
    var newPromptQuestion by remember { mutableStateOf("A life goal of mine is:") }
    var newPromptAnswer by remember { mutableStateOf("") }
    var showTipsDialog by remember { mutableStateOf(false) }

    val sampleAddPhotos = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=800&q=80"
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(TinderSurface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSaveAndClose(draft) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TinderTextPrimary
                        )
                    }
                    Text(
                        text = "Edit profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TinderTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSaveAndClose(draft) }) {
                        Text(
                            text = "Done",
                            color = TinderCoral,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tab Row: Edit | Preview (Matches screenshot 5)
                TabRow(
                    selectedTabIndex = if (selectedTab == "Edit") 0 else 1,
                    containerColor = TinderSurface,
                    contentColor = TinderTextPrimary
                ) {
                    Tab(
                        selected = selectedTab == "Edit",
                        onClick = { selectedTab = "Edit" },
                        text = {
                            Text(
                                "Edit",
                                fontWeight = if (selectedTab == "Edit") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == "Edit") TinderTextPrimary else TinderTextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == "Preview",
                        onClick = { selectedTab = "Preview" },
                        text = {
                            Text(
                                "Preview",
                                fontWeight = if (selectedTab == "Preview") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == "Preview") TinderTextPrimary else TinderTextSecondary
                            )
                        }
                    )
                }
            }
        },
        containerColor = TinderBg
    ) { paddingValues ->
        if (selectedTab == "Preview") {
            // Live Preview of how singles see the profile
            val previewProfile = DatingProfile(
                id = "user_preview",
                name = draft.name,
                age = draft.calculatedAge,
                isVerified = draft.isVerified,
                location = "${draft.city} · 0 km away",
                distanceKm = 0,
                bio = draft.bio,
                photos = draft.photos,
                profession = "${draft.jobTitle} at ${draft.company}",
                lookingFor = draft.lookingFor,
                essentialsGender = draft.gender,
                astrologyStar = draft.zodiac,
                musicAnthem = draft.anthem,
                communicationStyle = draft.communicationStyle,
                loveStyle = draft.loveStyle,
                education = draft.education,
                drinking = draft.drinking,
                smoking = draft.smoking,
                workout = draft.workout,
                pets = draft.pets,
                prompts = draft.prompts,
                interests = draft.interests
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ExpandedProfileSheet(
                    profile = previewProfile,
                    onDismiss = { selectedTab = "Edit" },
                    onLike = { selectedTab = "Edit" },
                    onDislike = { selectedTab = "Edit" },
                    onSuperLike = { selectedTab = "Edit" }
                )
            }
        } else {
            // Edit Tab content (Matches screenshots 5 - 11)
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Media section header
                Text(
                    text = "Media",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                Text(
                    text = "Add up to 9 photos. Use prompts to share your personality.",
                    fontSize = 13.sp,
                    color = TinderTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3x3 Photo Grid (Matches screenshot 5)
                val totalSlots = 9
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0 until 3) {
                                val slotIndex = row * 3 + col
                                val photoUrl = draft.photos.getOrNull(slotIndex)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE4E4E7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (photoUrl != null) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Photo ${slotIndex + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // ✕ Delete button on photo
                                        Surface(
                                            onClick = {
                                                val updatedList = draft.photos.toMutableList()
                                                if (slotIndex < updatedList.size) {
                                                    updatedList.removeAt(slotIndex)
                                                    draft = draft.copy(photos = updatedList)
                                                }
                                            },
                                            shape = CircleShape,
                                            color = Color.White,
                                            shadowElevation = 2.dp,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    tint = TinderCoral,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Prompt quote badge in bottom left (Matches screenshot 5)
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                                .size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "❝+",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TinderTextPrimary
                                                )
                                            }
                                        }
                                    } else {
                                        // Empty slot with + button
                                        IconButton(
                                            onClick = {
                                                val pick = sampleAddPhotos[slotIndex % sampleAddPhotos.size]
                                                draft = draft.copy(photos = draft.photos + pick)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add photo",
                                                tint = TinderCoral,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Photo options: Smart Photos (Matches screenshot 5)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Photo options",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Smart Photos",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TinderTextPrimary
                            )
                            Switch(
                                checked = draft.smartPhotos,
                                onCheckedChange = { draft = draft.copy(smartPhotos = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TinderCoral
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Smart Photos continuously tests all your profile photos to find the best one.",
                            fontSize = 12.sp,
                            color = TinderTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // About me section (Matches screenshot 6)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "About me",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                            Text(
                                text = "${(500 - draft.bio.length).coerceAtLeast(0)}",
                                fontSize = 12.sp,
                                color = TinderTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = draft.bio,
                            onValueChange = { if (it.length <= 500) draft = draft.copy(bio = it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Write a short bio...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TinderCoral,
                                unfocusedBorderColor = TinderBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTipsDialog = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick \"About me\" tips",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TinderCoral
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TinderCoral,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Prompts section (Matches screenshot 6)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Prompts",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        draft.prompts.forEachIndexed { index, prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TinderBg,
                                border = BorderStroke(1.dp, TinderBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prompt.question,
                                            fontSize = 12.sp,
                                            color = TinderTextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = prompt.answer,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TinderTextPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = draft.prompts.toMutableList()
                                            updated.removeAt(index)
                                            draft = draft.copy(prompts = updated)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove prompt",
                                            tint = TinderTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Add prompt button
                        OutlinedButton(
                            onClick = { showAddPromptDialog = true },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, TinderCoral),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TinderCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add prompt",
                                color = TinderCoral,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Profile Details Sections (Matches screenshots 7 - 10)
                Text(
                    text = "Profile Details",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        GroupedEditRow(
                            title = "Interests",
                            value = draft.interests.joinToString(", ").ifBlank { "Add interests" },
                            onClick = {
                                draft = draft.copy(
                                    interests = if (draft.interests.size > 2) draft.interests else listOf("Foodie", "Travel", "Music", "Art", "Startups")
                                )
                            }
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Relationship Goals",
                            value = "Looking for: ${draft.lookingFor}",
                            onClick = {
                                val nextGoal = when (draft.lookingFor) {
                                    "Long-term partner" -> "Short-term fun"
                                    "Short-term fun" -> "Serious commitment"
                                    else -> "Long-term partner"
                                }
                                draft = draft.copy(lookingFor = nextGoal)
                            }
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Pronouns",
                            value = draft.pronouns,
                            onClick = {
                                draft = draft.copy(pronouns = if (draft.pronouns == "He") "They" else "He")
                            }
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Height",
                            value = draft.height,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Relationship type",
                            value = draft.relationshipType,
                            onClick = {
                                draft = draft.copy(
                                    relationshipType = if (draft.relationshipType == "Monogamy") "Open to exploring" else "Monogamy"
                                )
                            }
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Languages I know",
                            value = draft.languages.joinToString(", "),
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // More about me Section (Matches screenshot 8: Zodiac, Education, Communication, Love style)
                Text(
                    text = "More about me",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        GroupedEditRow(
                            title = "Zodiac",
                            value = draft.zodiac,
                            onClick = {
                                val signs = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
                                val idx = (signs.indexOf(draft.zodiac) + 1) % signs.size
                                draft = draft.copy(zodiac = signs[idx])
                            }
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Education",
                            value = draft.education,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Family plans",
                            value = draft.familyPlans,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Communication style",
                            value = draft.communicationStyle,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Love style",
                            value = draft.loveStyle,
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Lifestyle Section (Matches screenshot 9: Pets, Drinking, Smoking, Workout, Social media)
                Text(
                    text = "Lifestyle",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        GroupedEditRow(
                            title = "Pets",
                            value = draft.pets,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Drinking",
                            value = draft.drinking,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "How often do you smoke?",
                            value = draft.smoking,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Workout",
                            value = draft.workout,
                            onClick = {}
                        )
                        HorizontalDivider(color = TinderBorder)

                        GroupedEditRow(
                            title = "Social media",
                            value = draft.socialMedia,
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Work & Education Section (Matches screenshot 9)
                Text(
                    text = "Work & Education",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TinderTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        GroupedEditRow(title = "College/uni", value = draft.college, onClick = {})
                        HorizontalDivider(color = TinderBorder)
                        GroupedEditRow(title = "Job title", value = draft.jobTitle, onClick = {})
                        HorizontalDivider(color = TinderBorder)
                        GroupedEditRow(title = "Company", value = draft.company, onClick = {})
                        HorizontalDivider(color = TinderBorder)
                        GroupedEditRow(title = "Living in", value = draft.city, onClick = {})
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Music & Spotify Section (Matches screenshot 10)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "My Anthem",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = draft.anthem,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TinderTextPrimary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "My Top Spotify Artists",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TinderTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Spotify to Your Profile", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Control Your Profile (Matches screenshot 11: Cupid Plus badge)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Control Your Profile",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TinderTextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TinderGold,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PLUS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Don't show my age",
                                fontSize = 15.sp,
                                color = TinderTextPrimary
                            )
                            Switch(
                                checked = draft.dontShowAge,
                                onCheckedChange = { draft = draft.copy(dontShowAge = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TinderCoral
                                )
                            )
                        }

                        HorizontalDivider(color = TinderBorder, modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Don't show my distance",
                                fontSize = 15.sp,
                                color = TinderTextPrimary
                            )
                            Switch(
                                checked = draft.dontShowDistance,
                                onCheckedChange = { draft = draft.copy(dontShowDistance = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TinderCoral
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Add Prompt Dialog
    if (showAddPromptDialog) {
        val promptOptions = listOf(
            "A life goal of mine is:",
            "My ideal weekend in Kerala:",
            "The quickest way to my heart is:",
            "A non-negotiable for me is:",
            "My favourite spot for sulaimani:"
        )

        AlertDialog(
            onDismissRequest = { showAddPromptDialog = false },
            title = {
                Text("Select a Prompt", fontWeight = FontWeight.Bold, color = TinderTextPrimary)
            },
            text = {
                Column {
                    promptOptions.forEach { q ->
                        Surface(
                            onClick = { newPromptQuestion = q },
                            shape = RoundedCornerShape(8.dp),
                            color = if (newPromptQuestion == q) TinderBg else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = q,
                                fontSize = 13.sp,
                                fontWeight = if (newPromptQuestion == q) FontWeight.Bold else FontWeight.Normal,
                                color = TinderTextPrimary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPromptAnswer,
                        onValueChange = { newPromptAnswer = it },
                        placeholder = { Text("Your answer...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPromptAnswer.isNotBlank()) {
                            draft = draft.copy(
                                prompts = draft.prompts + PromptItem(newPromptQuestion, newPromptAnswer)
                            )
                            newPromptAnswer = ""
                            showAddPromptDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TinderCoral)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPromptDialog = false }) {
                    Text("Cancel", color = TinderTextSecondary)
                }
            },
            containerColor = TinderSurface
        )
    }

    // Tips Dialog
    if (showTipsDialog) {
        AlertDialog(
            onDismissRequest = { showTipsDialog = false },
            title = { Text("Profile Bio Tips", fontWeight = FontWeight.Bold, color = TinderTextPrimary) },
            text = {
                Text(
                    text = "• Keep it authentic and concise.\n" +
                            "• Mention your favorite local hobbies or places in Kerala.\n" +
                            "• Avoid overly generic phrases.\n" +
                            "• Mention what kind of connection you are seeking.",
                    color = TinderTextPrimary,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTipsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TinderCoral)
                ) {
                    Text("Got it", color = Color.White)
                }
            },
            containerColor = TinderSurface
        )
    }
}

@Composable
private fun GroupedEditRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = TinderTextPrimary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = TinderTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TinderTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
