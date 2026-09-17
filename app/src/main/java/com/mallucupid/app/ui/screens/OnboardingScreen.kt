package com.mallucupid.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.PromptItem
import com.mallucupid.app.data.SampleProfiles
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.location.LocationHelper
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TOTAL_STEPS = 9

private val interestOptions = listOf(
    "Foodie", "Travel", "Movies", "Music", "Fitness", "Reading",
    "Cooking", "Beach days", "Family time"
)

private val goalOptions = listOf(
    "Something serious", "Marriage-minded", "Open to seeing where it goes", "New connections"
)

private val promptOptions = listOf(
    "A perfect Sunday looks like...",
    "The quickest way to my heart is...",
    "I will never say no to...",
    "A non-negotiable for me is..."
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialDraft: OnboardingDraft = OnboardingDraft(),
    onComplete: (OnboardingDraft) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var draft by remember { mutableStateOf(initialDraft) }
    var error by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()

    // Smooth animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = step.toFloat() / TOTAL_STEPS.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    fun validate(): String {
        return when (step) {
            1 -> if (draft.gender.isBlank() || draft.lookingFor.isBlank()) {
                "Choose your identity and who you would like to meet."
            } else ""
            2 -> if (draft.city.trim().isBlank()) {
                "Add your current city to continue."
            } else ""
            3 -> {
                val age = draft.calculatedAge
                val minAge = draft.countryMinAge
                if (draft.birthDay.isBlank() || draft.birthMonth.isBlank() || draft.birthYear.isBlank() || age < minAge || age > 100) {
                    "You must be $minAge or older to join."
                } else ""
            }
            4 -> if (draft.photos.size < 3) {
                "Add at least 3 photos. Your first impression matters."
            } else ""
            5 -> if (draft.bio.trim().length < 10) {
                "Please add a short introduction about yourself (at least 10 characters)."
            } else ""
            6 -> if (draft.interests.size < 3) {
                "Choose at least 3 interests."
            } else ""
            7 -> if (draft.goal.isBlank()) {
                "Choose what you are looking for."
            } else ""
            8 -> if (draft.prompts.any { it.answer.trim().length < 3 }) {
                "Answer both prompts so people can start a conversation."
            } else ""
            else -> ""
        }
    }

    fun handleNext() {
        val err = validate()
        if (err.isNotBlank()) {
            error = err
            return
        }
        error = ""
        if (step < TOTAL_STEPS) {
            step++
        } else {
            isSaving = true
            onComplete(draft)
        }
    }

    fun handleBack() {
        error = ""
        if (step > 1) {
            step--
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryRed, DarkMaroon)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { handleBack() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }

                Text(
                    text = "mc",
                    color = AccentPink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$step / $TOTAL_STEPS",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentPink, SoftPink)
                            )
                        )
                )
            }

            // Scrollable Content — no live preview (removed per design request)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Let's make your profile feel like you",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OnboardingStepBody(
                        step = step,
                        draft = draft,
                        onDraftChange = { draft = it },
                        error = error
                    )
                }
            }

            // Bottom Actions Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { handleNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    enabled = !isSaving && validate().isBlank()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (step < TOTAL_STEPS) "Continue" else if (isSaving) "Saving profile..." else "Finish my profile",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (step == 9) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onComplete(draft) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Skip for now",
                            color = SoftPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP BODY DISPATCHER + LIVE PREVIEW CARD
// -------------------------------------------------------------
@Composable
private fun OnboardingStepBody(
    step: Int,
    draft: OnboardingDraft,
    onDraftChange: (OnboardingDraft) -> Unit,
    error: String
) {
    when (step) {
        1 -> Step1Gender(
            gender = draft.gender,
            onGenderChange = { onDraftChange(draft.copy(gender = it)) },
            lookingFor = draft.lookingFor,
            onLookingForChange = { onDraftChange(draft.copy(lookingFor = it)) }
        )
        2 -> Step3Location(
            city = draft.city,
            onCityChange = { city, lat, lng -> onDraftChange(draft.copy(city = city, latitude = lat, longitude = lng)) },
            distance = draft.distance,
            onDistanceChange = { onDraftChange(draft.copy(distance = it)) },
            onCountryDetected = { countryName, isoCode, minAge ->
                onDraftChange(draft.copy(country = countryName, countryIsoCode = isoCode, countryMinAge = minAge))
            }
        )
        3 -> Step2Birthday(
            day = draft.birthDay,
            month = draft.birthMonth,
            year = draft.birthYear,
            onDayChange = { onDraftChange(draft.copy(birthDay = it)) },
            onMonthChange = { onDraftChange(draft.copy(birthMonth = it)) },
            onYearChange = { onDraftChange(draft.copy(birthYear = it)) },
            age = draft.calculatedAge
        )
        4 -> Step4Photos(
            photos = draft.photos,
            onPhotosChange = { onDraftChange(draft.copy(photos = it)) }
        )
        5 -> Step5Basics(
            bio = draft.bio,
            onBioChange = { onDraftChange(draft.copy(bio = it)) }
        )
        6 -> Step6Interests(
            selectedInterests = draft.interests,
            onToggle = { interest ->
                val current = draft.interests
                val updated = if (current.contains(interest)) {
                    current - interest
                } else {
                    current + interest
                }
                onDraftChange(draft.copy(interests = updated))
            }
        )
        7 -> Step7Goal(
            selectedGoal = draft.goal,
            onGoalChange = { onDraftChange(draft.copy(goal = it)) }
        )
        8 -> Step8Prompts(
            prompts = draft.prompts,
            onPromptQuestionChange = { index, question ->
                val updated = draft.prompts.toMutableList()
                if (index < updated.size) {
                    updated[index] = updated[index].copy(question = question)
                    onDraftChange(draft.copy(prompts = updated))
                }
            },
            onPromptAnswerChange = { index, answer ->
                val updated = draft.prompts.toMutableList()
                if (index < updated.size) {
                    updated[index] = updated[index].copy(answer = answer)
                    onDraftChange(draft.copy(prompts = updated))
                }
            }
        )
        9 -> Step9Preferences(
            ageMin = draft.ageMin,
            ageMax = draft.ageMax,
            onAgeChange = { min, max ->
                onDraftChange(draft.copy(ageMin = min, ageMax = max))
            },
            dealBreakers = draft.dealBreakers,
            onToggleDealBreaker = { item ->
                val current = draft.dealBreakers
                val updated = if (current.contains(item)) {
                    current - item
                } else {
                    current + item
                }
                onDraftChange(draft.copy(dealBreakers = updated))
            }
        )
    }

    if (error.isNotBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = AccentPink.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, AccentPink.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = error,
                color = SoftPink,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Compact dating-style profile preview card. Reads the live [OnboardingDraft] reactively
 * (it depends on `draft` via Compose state) so it updates as the user fills in the form.
 *
 * Tokens used: DashboardCard / DashboardCream / DashboardMutedBeige / DashboardTerracotta /
 * DashboardPeach / DashboardNavMuted / TinderGreen (online dot) / TinderGold (primary star).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveProfilePreviewCard(draft: OnboardingDraft) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header label — "LIVE PREVIEW" with a tiny online-style dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TinderGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE PREVIEW",
                    color = DashboardNavMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Photo + name/age row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val firstPhoto = draft.photos.firstOrNull()
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, DashboardTerracotta, CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (firstPhoto != null) {
                        AsyncImage(
                            model = firstPhoto,
                            contentDescription = "Profile photo preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = DashboardNavMuted,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (draft.name.isNotBlank()) draft.name else "Your name",
                        color = DashboardCream,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val locationLabel = draft.city.takeIf { it.isNotBlank() }?.let { formatPreviewLocation(it) }
                    val meta = buildString {
                        append(draft.calculatedAge)
                        if (locationLabel != null) append(" · $locationLabel")
                    }
                    Text(
                        text = meta,
                        color = DashboardMutedBeige,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bio preview (1-2 lines)
            Text(
                text = draft.bio.takeIf { it.isNotBlank() }
                    ?: "Your bio will appear here.",
                color = DashboardMutedBeige,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Up to 4 interest chips (wrapping FlowRow)
            if (draft.interests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    draft.interests.take(4).forEach { interest ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = DashboardTerracotta.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, DashboardTerracotta.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = interest,
                                color = DashboardCream,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // First prompt Q+A (only if answered)
            val firstPrompt = draft.prompts.firstOrNull()?.takeIf { it.answer.isNotBlank() }
            if (firstPrompt != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.04f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = firstPrompt.question,
                            color = DashboardPeach,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = firstPrompt.answer,
                            color = DashboardMutedBeige,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shortens a long "City, State, Country" string to just the leading locality for the preview meta.
 */
private fun formatPreviewLocation(rawCity: String): String? {
    if (rawCity.isBlank()) return null
    val first = rawCity.substringBefore(",").trim()
    return first.ifBlank { null }
}

// -------------------------------------------------------------
// STEP 1: GENDER & WHO TO MEET (DROPDOWN SELECTIONS)
// -------------------------------------------------------------
@Composable
private fun Step1Gender(
    gender: String,
    onGenderChange: (String) -> Unit,
    lookingFor: String,
    onLookingForChange: (String) -> Unit
) {
    val identityOptions = listOf("Man", "Woman", "Transman", "Transwoman", "Non-binary")
    val meetOptions = listOf("Men", "Women", "Transmen", "Transwomen", "Non-binary", "Everyone")

    var genderDropdownExpanded by remember { mutableStateOf(false) }
    var meetDropdownExpanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Who are you?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Identity Dropdown Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { genderDropdownExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = if (gender.isNotBlank()) AccentPink.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.95f),
                border = BorderStroke(
                    1.5.dp,
                    if (gender.isNotBlank()) AccentPink else Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (gender.isNotBlank()) gender else "Select your identity",
                        color = if (gender.isNotBlank()) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = if (gender.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Identity",
                        tint = if (gender.isNotBlank()) SoftPink else Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            DropdownMenu(
                expanded = genderDropdownExpanded,
                onDismissRequest = { genderDropdownExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(DarkMaroon)
            ) {
                identityOptions.forEach { option ->
                    val isSelected = gender == option
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) SoftPink else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onGenderChange(option)
                            genderDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Who do you want to meet?",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Who to Meet Dropdown Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { meetDropdownExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = if (lookingFor.isNotBlank()) AccentPink.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.95f),
                border = BorderStroke(
                    1.5.dp,
                    if (lookingFor.isNotBlank()) AccentPink else Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lookingFor.isNotBlank()) lookingFor else "Select who you want to meet",
                        color = if (lookingFor.isNotBlank()) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = if (lookingFor.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Who to Meet",
                        tint = if (lookingFor.isNotBlank()) SoftPink else Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            DropdownMenu(
                expanded = meetDropdownExpanded,
                onDismissRequest = { meetDropdownExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(DarkMaroon)
            ) {
                meetOptions.forEach { option ->
                    val isSelected = lookingFor == option
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) SoftPink else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onLookingForChange(option)
                            meetDropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AccentPink.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
        border = BorderStroke(
            2.dp,
            if (isSelected) AccentPink else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = if (isSelected) "✓" else "○",
                color = if (isSelected) SoftPink else Color.White.copy(alpha = 0.65f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 2: BIRTHDAY
// -------------------------------------------------------------
@Composable
private fun Step2Birthday(
    day: String,
    month: String,
    year: String,
    onDayChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    age: Int
) {
    Column {
        Text(
            text = "When's your birthday?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "We use this to show you age-appropriate matches.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Date of birth",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = day,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onDayChange(it) },
                placeholder = { Text("DD", color = Color.Gray, textAlign = TextAlign.Center) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp)
            )

            OutlinedTextField(
                value = month,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onMonthChange(it) },
                placeholder = { Text("MM", color = Color.Gray, textAlign = TextAlign.Center) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp)
            )

            OutlinedTextField(
                value = year,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onYearChange(it) },
                placeholder = { Text("YYYY", color = Color.Gray, textAlign = TextAlign.Center) },
                modifier = Modifier.weight(1.4f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (year.length == 4) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = if (age >= 18) "Age: $age years old" else "Age: $age (Must be 18+)",
                    color = if (age >= 18) Color.White else SoftPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 3: LOCATION & DISTANCE (MATCHING ATTACHED SCREENSHOT)
// -------------------------------------------------------------
@Composable
private fun Step3Location(
    city: String,
    onCityChange: (String, Double?, Double?) -> Unit,
    distance: Int,
    onDistanceChange: (Int) -> Unit,
    onCountryDetected: (String, String, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    fun fetchDeviceLocation() {
        isLocating = true
        coroutineScope.launch {
            if (!LocationHelper.isGpsEnabled(context)) {
                isLocating = false
                return@launch
            }
            val loc = LocationHelper.getCurrentLocation(context)
            if (loc != null) {
                onCityChange(loc.fullLocation, loc.latitude, loc.longitude)
                if (loc.countryCode.isNotBlank()) {
                    val minAge = SupabaseRepository.getMinAgeForCountry(loc.countryCode)
                    onCountryDetected(loc.countryName, loc.countryCode, minAge)
                }
            } else {
                onCityChange("", null, null)
            }
            isLocating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation()
        } else {
            isLocating = false
        }
    }

    // Auto-fetch location on step load if permission already granted
    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            fetchDeviceLocation()
        }
    }

    Column {
        Text(
            text = "Where are you based?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "We'll use your location to find people nearby.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Your location",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Location Card Container with Place icon, TextField, and MyLocation Crosshair
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Location",
                    tint = AccentPink,
                    modifier = Modifier.size(24.dp)
                )

                BasicTextField(
                    value = city,
                    onValueChange = { onCityChange(it, null, null) },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentPink),
                    decorationBox = { innerTextField ->
                        if (city.isEmpty()) {
                            Text(
                                text = "City, State, Country",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentPink,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Tap the icon to use your current location",
                            tint = AccentPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap the icon to use your current location",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Maximum Distance Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Maximum distance",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$distance km",
                color = AccentPink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = distance.toFloat(),
            onValueChange = { onDistanceChange(it.toInt()) },
            valueRange = 5f..200f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentPink,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("5 km", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            Text("200 km", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }
    }
}

// -------------------------------------------------------------
// STEP 4: PHOTOS
// -------------------------------------------------------------
@Composable
private fun Step4Photos(
    photos: List<String>,
    onPhotosChange: (List<String>) -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val stringUris = uris.map { it.toString() }
            val merged = (photos + stringUris).distinct().take(6)
            onPhotosChange(merged)
        }
    }

    // Drag-to-reorder state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Pixel size of a single photo slot — captured once laid out so we can translate
    // the user's drag distance into a (row, col) drop target.
    var slotSizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val gridSpacingPx = with(density) { 10.dp.toPx() }

    Column {
        Text(
            text = "Show your best side.",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add at least 3 photos.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Long-press to reorder. First photo is your Primary.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 3-column reorderable photo grid (2 rows × 3 cols = 6 slots)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        val photoUrl = photos.getOrNull(index)
                        val isDragged = draggedIndex == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.82f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .then(
                                    if (isDragged) Modifier
                                        .offset {
                                            IntOffset(
                                                dragOffset.x.roundToInt(),
                                                dragOffset.y.roundToInt()
                                            )
                                        }
                                        .zIndex(1f)
                                    else Modifier
                                )
                                .onGloballyPositioned { coords ->
                                    if (slotSizePx == IntSize.Zero) {
                                        slotSizePx = coords.size
                                    }
                                }
                                .pointerInput(photoUrl) {
                                    // Only attach the gesture detector on filled slots.
                                    if (photoUrl != null) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val dragged = draggedIndex
                                                if (dragged != null &&
                                                    slotSizePx.width > 0 &&
                                                    photoUrl != null
                                                ) {
                                                    val slotW = slotSizePx.width.toFloat()
                                                    val slotH = slotSizePx.height.toFloat()
                                                    val colTarget = ((dragOffset.x + slotW / 2f) /
                                                        (slotW + gridSpacingPx)).roundToInt().coerceIn(0, 2)
                                                    val rowTarget = ((dragOffset.y + slotH / 2f) /
                                                        (slotH + gridSpacingPx)).roundToInt().coerceIn(0, 1)
                                                    val targetIndex = (rowTarget * 3 + colTarget).coerceIn(0, 5)
                                                    if (targetIndex != dragged &&
                                                        targetIndex < photos.size) {
                                                        val updated = photos.toMutableList()
                                                        val tmp = updated[dragged]
                                                        updated[dragged] = updated[targetIndex]
                                                        updated[targetIndex] = tmp
                                                        onPhotosChange(updated)
                                                    }
                                                }
                                                draggedIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUrl != null) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Drag handle (top-left, small dark circle) — only on filled slots
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = DashboardNavMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Primary star badge (top-right) — only on the first photo
                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Primary photo",
                                                tint = TinderGold,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Primary",
                                                color = TinderGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    // Remove button (top-right) for non-primary filled slots
                                    IconButton(
                                        onClick = {
                                            val updated = photos.toMutableList()
                                            updated.removeAt(index)
                                            onPhotosChange(updated)
                                        },
                                        // Touch target enlarged to 44dp accessibility minimum (icon stays 16dp)
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(44.dp)
                                            .padding(4.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.6f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp).padding(2.dp)
                                            )
                                        }
                                    }
                                }

                                // Number badge (bottom-left). Index 0 is implied by the star,
                                // so we only show a number for the rest.
                                if (index > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(7.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Remove button (bottom-right) for the primary slot, since the
                                // top-right corner is reserved for the Primary star.
                                if (index == 0) {
                                    IconButton(
                                        onClick = {
                                            val updated = photos.toMutableList()
                                            updated.removeAt(index)
                                            onPhotosChange(updated)
                                        },
                                        // Touch target enlarged to 44dp accessibility minimum (icon stays 16dp)
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(44.dp)
                                            .padding(4.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.6f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp).padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (index == photos.size) {
                                // Upload tile — only show on the next slot after the last photo
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "+",
                                        color = SoftPink,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                    Text(
                                        text = "Add photo",
                                        color = SoftPink,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White.copy(alpha = 0.25f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${photos.size} of 3 minimum photos added",
                color = if (photos.size >= 3) Color.White.copy(alpha = 0.85f) else SoftPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 5: THE BASICS (BIO)
// -------------------------------------------------------------
@Composable
private fun Step5Basics(
    bio: String,
    onBioChange: (String) -> Unit
) {
    Column {
        Text(
            text = "The basics.",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "A little detail helps people find a genuine connection.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "A few words about you",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextField(
                value = bio,
                onValueChange = { if (it.length <= 180) onBioChange(it) },
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(AccentPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 125.dp)
                    .padding(16.dp),
                decorationBox = { innerTextField ->
                    if (bio.isEmpty()) {
                        Text(
                            text = "Write a few words about yourself...",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${bio.length} / 180",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

// -------------------------------------------------------------
// STEP 6: INTERESTS
// -------------------------------------------------------------
@Composable
private fun Step6Interests(
    selectedInterests: List<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = "What makes you, you?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Pick at least 3.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Chip flow layout
        val chunkedInterests = interestOptions.chunked(3)
        chunkedInterests.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { interest ->
                    val isSelected = selectedInterests.contains(interest)
                    Surface(
                        onClick = { onToggle(interest) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) AccentPink else Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) AccentPink else Color.Transparent
                        ),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = interest,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${selectedInterests.size} selected (minimum 3)",
            color = if (selectedInterests.size >= 3) Color.White.copy(alpha = 0.85f) else SoftPink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// -------------------------------------------------------------
// STEP 7: GOALS
// -------------------------------------------------------------
@Composable
private fun Step7Goal(
    selectedGoal: String,
    onGoalChange: (String) -> Unit
) {
    Column {
        Text(
            text = "What are you looking for?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Being clear helps everyone.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        goalOptions.forEach { goal ->
            ChoiceCard(
                label = goal,
                isSelected = selectedGoal == goal,
                onClick = { onGoalChange(goal) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// -------------------------------------------------------------
// STEP 8: PROMPTS
// -------------------------------------------------------------
@Composable
private fun Step8Prompts(
    prompts: List<PromptItem>,
    onPromptQuestionChange: (Int, String) -> Unit,
    onPromptAnswerChange: (Int, String) -> Unit
) {
    Column {
        Text(
            text = "Give them a way in.",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Answer two quick prompts.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        prompts.forEachIndexed { index, prompt ->
            var expandedDropdown by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Prompt ${index + 1}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    Surface(
                        onClick = { expandedDropdown = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prompt.question,
                                color = Color.Black,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .background(DarkMaroon)
                    ) {
                        promptOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    onPromptQuestionChange(index, opt)
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = prompt.answer,
                    onValueChange = { if (it.length <= 120) onPromptAnswerChange(index, it) },
                    placeholder = { Text("Write your answer...", color = Color.White.copy(alpha = 0.55f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.95f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                        focusedBorderColor = AccentPink,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = AccentPink
                    ),
                    maxLines = 3
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 9: FINE-TUNE PREFERENCES
// -------------------------------------------------------------
@Composable
private fun Step9Preferences(
    ageMin: Int,
    ageMax: Int,
    onAgeChange: (Int, Int) -> Unit,
    dealBreakers: List<String>,
    onToggleDealBreaker: (String) -> Unit
) {
    val dealBreakerOptions = listOf("Smoking", "Drinking", "Long distance", "Not family-oriented")

    Column {
        Surface(
            shape = RoundedCornerShape(50),
            color = SoftPink.copy(alpha = 0.2f),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "OPTIONAL FOR NOW",
                color = SoftPink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Text(
            text = "Fine-tune your matches.",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can adjust these later.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preferred age range",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$ageMin – $ageMax",
                color = SoftPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Age inputs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = ageMin.toString(),
                onValueChange = {
                    val num = it.toIntOrNull() ?: 18
                    onAgeChange(num.coerceIn(18, ageMax), ageMax)
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp)
            )

            Text("to", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)

            OutlinedTextField(
                value = ageMax.toString(),
                onValueChange = {
                    val num = it.toIntOrNull() ?: 50
                    onAgeChange(ageMin, num.coerceIn(ageMin, 80))
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.95f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 16.sp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Deal breakers",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dealBreakerOptions.take(2).forEach { item ->
                val isSelected = dealBreakers.contains(item)
                Surface(
                    onClick = { onToggleDealBreaker(item) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) AccentPink else Color.White.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isSelected) AccentPink else Color.Transparent)
                ) {
                    Text(
                        text = item,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dealBreakerOptions.drop(2).forEach { item ->
                val isSelected = dealBreakers.contains(item)
                Surface(
                    onClick = { onToggleDealBreaker(item) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) AccentPink else Color.White.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isSelected) AccentPink else Color.Transparent)
                ) {
                    Text(
                        text = item,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
