package com.mallucupid.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.DatingProfile
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.location.GpsCheck
import com.mallucupid.app.location.LocationHelper
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    initialDraft: OnboardingDraft,
    onSaveAndClose: (OnboardingDraft) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(initialDraft) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhotos by remember { mutableStateOf(false) }
    var dbGenderOptions by remember { mutableStateOf(listOf("Man", "Woman", "Transman", "Transwoman", "Non-binary")) }
    var dbLookingForOptions by remember { mutableStateOf(listOf("Serious relationship", "Casual relationship", "Dating", "New friends")) }
    var dbMaritalStatusOptions by remember { mutableStateOf(listOf("Single", "Never Married", "Divorced", "Separated", "Widowed")) }
    var dbFamilyPlansOptions by remember { mutableStateOf(listOf("Want children", "Don't want children", "Not sure yet")) }
    var dbPetsOptions by remember { mutableStateOf(listOf("Dog lover", "Cat lover", "Pet-free")) }
    var dbDrinkingOptions by remember { mutableStateOf(listOf("Not for me", "Socially", "Sober")) }
    var dbSmokingOptions by remember { mutableStateOf(listOf("Non-smoker", "Smoker", "Trying to quit")) }

    // Fetch dropdown options from DB on load
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            dbGenderOptions = SupabaseRepository.getProfileOptions("gender").ifEmpty { dbGenderOptions }
            dbLookingForOptions = SupabaseRepository.getProfileOptions("looking_for").ifEmpty { dbLookingForOptions }
            dbMaritalStatusOptions = SupabaseRepository.getProfileOptions("marital_status").ifEmpty { dbMaritalStatusOptions }
            dbFamilyPlansOptions = SupabaseRepository.getProfileOptions("family_plans").ifEmpty { dbFamilyPlansOptions }
            dbPetsOptions = SupabaseRepository.getProfileOptions("pets").ifEmpty { dbPetsOptions }
            dbDrinkingOptions = SupabaseRepository.getProfileOptions("drinking").ifEmpty { dbDrinkingOptions }
            dbSmokingOptions = SupabaseRepository.getProfileOptions("smoking").ifEmpty { dbSmokingOptions }
        }
    }

    // Tinder-level option sets: DB list FIRST (keeps admin ordering), then any
    // missing Tinder staples appended, de-duplicated. Guarantees a rich picker
    // even if the DB table is thin.
    val genderOptionsAll = (dbGenderOptions + listOf(
        "Man", "Woman", "Transman", "Transwoman", "Non-binary"
    )).distinct()
    val lookingForOptionsAll = (dbLookingForOptions + listOf(
        "Serious relationship", "Long-term partner", "Long-term, open to short",
        "Short-term fun", "Casual relationship", "Dating", "New friends",
        "Friends with benefits", "Marriage-minded", "Still figuring it out"
    )).distinct()
    val maritalStatusOptionsAll = (dbMaritalStatusOptions + listOf(
        "Single", "Never Married", "In a Relationship", "Divorced", "Separated", "Widowed"
    )).distinct()
    val familyPlansOptionsAll = (dbFamilyPlansOptions + listOf(
        "Want children", "Don't want children", "Have children & want more",
        "Have children & don't want more", "Not sure yet"
    )).distinct()
    val petsOptionsAll = (dbPetsOptions + listOf(
        "Dog lover", "Cat lover", "Have pets", "Don't have, but love pets",
        "Pet-free", "Allergic to pets"
    )).distinct()
    val drinkingOptionsAll = (dbDrinkingOptions + listOf(
        "Not for me", "Sober", "Socially", "On special occasions", "Regularly"
    )).distinct()
    val smokingOptionsAll = (dbSmokingOptions + listOf(
        "Non-smoker", "Social smoker", "Smoker", "Trying to quit"
    )).distinct()
    var selectedTab by remember { mutableStateOf("Edit") } // "Edit" or "Preview"

    // Active target slot for single-photo replacement (null means append)
    var targetPhotoSlot by remember { mutableStateOf<Int?>(null) }

    // Feature #6: Photo drag-to-reorder state (within the 3x3 grid)
    var draggedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var draggedPhotoOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetPhotoIndex by remember { mutableStateOf<Int?>(null) }
    val photoSlotBounds = remember { mutableStateMapOf<Int, Rect>() }

    // Dialog & bottom sheet states
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var helpDialogTopic by remember { mutableStateOf("Safety & Dating Tips") }
    var showLegalDialog by remember { mutableStateOf(false) }
    var legalDialogTopic by remember { mutableStateOf("Terms of Service") }
    var showTipsDialog by remember { mutableStateOf(false) }

    // Location fetching feedback
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var locationFailed by remember { mutableStateOf(false) }

    // Real Media Pickers from Device
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPhotos = true
            coroutineScope.launch {
                val session = com.mallucupid.app.data.remote.SessionManager.current()
                val userId = session?.userId
                if (userId != null) {
                    val uploadedUrl = SupabaseRepository.uploadPhoto(userId, uri)
                    if (uploadedUrl != null) {
                        val slot = targetPhotoSlot
                        if (slot != null && slot < draft.photos.size) {
                            val updated = draft.photos.toMutableList()
                            updated[slot] = uploadedUrl
                            draft = draft.copy(photos = updated)
                        } else {
                            draft = draft.copy(photos = (draft.photos + uploadedUrl).take(9))
                        }
                    }
                }
                isUploadingPhotos = false
                targetPhotoSlot = null
            }
        }
    }

    val multiplePhotosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isUploadingPhotos = true
            coroutineScope.launch {
                val session = com.mallucupid.app.data.remote.SessionManager.current()
                val userId = session?.userId
                if (userId != null) {
                    val uploadedUrls = uris.mapNotNull { uri ->
                        SupabaseRepository.uploadPhoto(userId, uri)
                    }
                    if (uploadedUrls.isNotEmpty()) {
                        val merged = (draft.photos + uploadedUrls).distinct().take(9)
                        draft = draft.copy(photos = merged)
                    }
                }
                isUploadingPhotos = false
            }
        }
    }

    // Real Location Permission & Fetcher (FusedLocationProviderClient via LocationHelper)
    // Full flow: permission -> SYSTEM "Turn on GPS?" dialog -> fetch. On failure we
    // ALWAYS show a message and NEVER wipe a city the user already saved.
    fun performLocationFetch() {
        isFetchingLocation = true
        locationFailed = false
        locationFeedbackMessage = "Locating via GPS..."
        coroutineScope.launch(Dispatchers.IO) {
            val loc = LocationHelper.getCurrentLocation(context)
            withContext(Dispatchers.Main) {
                if (loc != null) {
                    draft = draft.copy(city = loc.fullLocation, latitude = loc.latitude, longitude = loc.longitude)
                    locationFeedbackMessage = "Location updated: ${loc.fullLocation}"
                } else {
                    locationFailed = true
                    locationFeedbackMessage = "Couldn't get a GPS fix. Turn on location and retry, or type your city manually."
                }
                isFetchingLocation = false
            }
        }
    }

    val gpsResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            performLocationFetch()
        } else {
            isFetchingLocation = false
            locationFailed = true
            locationFeedbackMessage = "GPS stays off. Turn on location to auto-detect, or type your city manually."
        }
    }

    fun startLocationFlow() {
        isFetchingLocation = true
        locationFailed = false
        locationFeedbackMessage = "Checking GPS..."
        coroutineScope.launch {
            when (val gps = LocationHelper.checkGpsSettings(context)) {
                is GpsCheck.Enabled -> performLocationFetch()
                is GpsCheck.Resolvable -> {
                    try {
                        gpsResolutionLauncher.launch(
                            IntentSenderRequest.Builder(gps.pendingIntent.intentSender).build()
                        )
                    } catch (e: Exception) {
                        isFetchingLocation = false
                        locationFailed = true
                        locationFeedbackMessage = "Couldn't open the GPS prompt. Enable location in Settings, then retry."
                    }
                }
                GpsCheck.Unresolvable -> {
                    isFetchingLocation = false
                    locationFailed = true
                    locationFeedbackMessage = "GPS is turned off on this phone. Enable location in Settings and try again."
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            startLocationFlow()
        } else {
            isFetchingLocation = false
            locationFailed = true
            locationFeedbackMessage = "Location permission needed. You can enter your city manually."
            Toast.makeText(context, "Location permission denied. Enter city manually.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Edit Profile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { isSaving = true; onSaveAndClose(draft) },
                        enabled = !isSaving && !isUploadingPhotos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DashboardTerracotta,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Edit | Preview Tabs matching Dashboard aesthetic
                TabRow(
                    selectedTabIndex = if (selectedTab == "Edit") 0 else 1,
                    containerColor = DashboardBg,
                    contentColor = DashboardCream,
                    indicator = { tabPositions ->
                        val index = if (selectedTab == "Edit") 0 else 1
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                            color = DashboardTerracotta,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                    }
                ) {
                    Tab(
                        selected = selectedTab == "Edit",
                        onClick = { selectedTab = "Edit" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = if (selectedTab == "Edit") DashboardPeach else DashboardNavMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Edit Details",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == "Edit") FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == "Edit") DashboardCream else DashboardNavMuted
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == "Preview",
                        onClick = { selectedTab = "Preview" },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (selectedTab == "Preview") DashboardPeach else DashboardNavMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Live Preview",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == "Preview") FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == "Preview") DashboardCream else DashboardNavMuted
                                )
                            }
                        }
                    )
                }
            }
        },
        containerColor = DashboardBg
    ) { paddingValues ->
        if (selectedTab == "Preview") {
            // Live Preview of how other singles nearby see the user
            val previewProfile = DatingProfile(
                id = "user_preview",
                name = draft.name.ifBlank { "" },
                age = draft.calculatedAge,
                isVerified = draft.isVerified,
                location = "${draft.city} · 0 km away",
                distanceKm = 0,
                bio = draft.bio,
                photos = draft.photos,
                profession = listOfNotNull(draft.jobTitle.ifBlank { null }, draft.company.ifBlank { null }).joinToString(" at "),
                college = draft.college,
                lookingFor = draft.lookingFor,
                essentialsGender = draft.gender,
                astrologyStar = draft.zodiac,
                musicAnthem = draft.anthem,
                communicationStyle = draft.communicationStyle,
                loveStyle = draft.loveStyle,
                education = listOfNotNull(draft.courseName.ifBlank { null }, draft.education.ifBlank { null }).joinToString(" · "),
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
            // Edit Profile Form
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {

                // ==========================================
                // 1. TOP PART: IMAGE UPLOAD SECTION WITH REAL MEDIA PICKER
                // ==========================================
                DashboardSectionCard(
                    title = "Profile Photos",
                    subtitle = "Pick real images from your device gallery. Add up to 9 photos.",
                    icon = Icons.Default.AddPhotoAlternate
                ) {
                    Column {
                        // 3x3 Grid of photo slots — Feature #6: long-press & drag filled slots to reorder
                        for (row in 0 until 3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val slotIndex = row * 3 + col
                                    val photoUrl = draft.photos.getOrNull(slotIndex)
                                    val isDragged = slotIndex == draggedPhotoIndex
                                    val isDropTarget = draggedPhotoIndex != null && slotIndex == dragTargetPhotoIndex

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.78f)
                                            .graphicsLayer(
                                                translationX = if (isDragged) draggedPhotoOffset.x else 0f,
                                                translationY = if (isDragged) draggedPhotoOffset.y else 0f,
                                                shadowElevation = if (isDragged) 16f else 0f,
                                                alpha = if (isDragged) 0.92f else 1f
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White)
                                            .border(
                                                width = 1.dp,
                                                color = when {
                                                    photoUrl != null && isDragged -> DashboardPeach
                                                    photoUrl != null && isDropTarget -> TinderGold
                                                    photoUrl != null -> DashboardPeach.copy(alpha = 0.3f)
                                                    else -> Color(0xFFCCCCCC)
                                                },
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .then(
                                                if (photoUrl != null) {
                                                    // Track each filled slot's window bounds & detect long-press drag to reorder
                                                    Modifier
                                                        .onGloballyPositioned { coords ->
                                                            photoSlotBounds[slotIndex] = coords.boundsInWindow()
                                                        }
                                                        .pointerInput(slotIndex) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    draggedPhotoIndex = slotIndex
                                                                    draggedPhotoOffset = Offset.Zero
                                                                    dragTargetPhotoIndex = slotIndex
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    draggedPhotoOffset = draggedPhotoOffset + dragAmount
                                                                    val draggedCenter = photoSlotBounds[slotIndex]?.center
                                                                    if (draggedCenter != null) {
                                                                        val pointer = draggedCenter + draggedPhotoOffset
                                                                        dragTargetPhotoIndex = photoSlotBounds.entries
                                                                            .firstOrNull { (_, rect) -> rect.contains(pointer) }
                                                                            ?.key
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    val from = draggedPhotoIndex
                                                                    val to = dragTargetPhotoIndex
                                                                    if (from != null && to != null && from != to && to < draft.photos.size) {
                                                                        val updated = draft.photos.toMutableList()
                                                                        val tmp = updated[from]
                                                                        updated[from] = updated[to]
                                                                        updated[to] = tmp
                                                                        draft = draft.copy(photos = updated)
                                                                    }
                                                                    draggedPhotoIndex = null
                                                                    draggedPhotoOffset = Offset.Zero
                                                                    dragTargetPhotoIndex = null
                                                                },
                                                                onDragCancel = {
                                                                    draggedPhotoIndex = null
                                                                    draggedPhotoOffset = Offset.Zero
                                                                    dragTargetPhotoIndex = null
                                                                }
                                                            )
                                                        }
                                                } else Modifier
                                            )
                                            .clickable {
                                                targetPhotoSlot = slotIndex
                                                singlePhotoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (photoUrl != null) {
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = "Photo ${slotIndex + 1}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            // Drag handle (top-left) for every filled photo slot — small dark circle background
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xCC201B18),
                                                border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                                    .size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.DragHandle,
                                                        contentDescription = "Drag to reorder",
                                                        tint = DashboardNavMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            // Primary star badge (top-right) for the FIRST slot — replaces the previous "MAIN" text badge
                                            if (slotIndex == 0) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xCC201B18),
                                                    border = BorderStroke(1.dp, TinderGold.copy(alpha = 0.7f)),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(22.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = "Primary photo",
                                                            tint = TinderGold,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Delete photo button (top-right corner for slots 1+; just below the star for slot 0)
                                            Surface(
                                                onClick = {
                                                    val updatedList = draft.photos.toMutableList()
                                                    if (slotIndex < updatedList.size) {
                                                        updatedList.removeAt(slotIndex)
                                                        draft = draft.copy(photos = updatedList)
                                                    }
                                                },
                                                shape = CircleShape,
                                                color = Color(0xCC201B18),
                                                border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .then(if (slotIndex == 0) Modifier.padding(top = 28.dp) else Modifier)
                                                    // Touch target enlarged to 44dp accessibility minimum (icon stays 14dp)
                                                    .size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove photo",
                                                        tint = NopeCoral,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            // Tap to replace indicator at bottom
                                            Surface(
                                                color = Color(0x99201B18),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                            ) {
                                                Text(
                                                    text = "Change",
                                                    fontSize = 9.sp,
                                                    color = DashboardCream,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            // Empty slot with + button (Add photo — non-draggable)
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add photo",
                                                    tint = DashboardPeach,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Slot ${slotIndex + 1}",
                                                    fontSize = 10.sp,
                                                    color = DashboardNavMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Helper hint for drag-to-reorder discoverability
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = DashboardNavMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Long-press a photo to drag and reorder. First slot is your primary photo.",
                                fontSize = 11.sp,
                                color = DashboardNavMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Pick multiple photos action button
                        Button(
                            onClick = {
                                multiplePhotosPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DashboardTerracotta,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Upload from Device Gallery",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Smart photos switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smart Photos",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardCream
                                )
                                Text(
                                    text = "Continuously puts your most liked photo first",
                                    fontSize = 11.sp,
                                    color = DashboardNavMuted
                                )
                            }
                            Switch(
                                checked = draft.smartPhotos,
                                onCheckedChange = { draft = draft.copy(smartPhotos = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = DashboardTerracotta,
                                    uncheckedThumbColor = DashboardNavMuted,
                                    uncheckedTrackColor = Color(0xFFE0E0E0)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 2. PROFILE NAME
                // ==========================================
                DashboardSectionCard(
                    title = "Profile Name",
                    subtitle = "This will be displayed prominently on your Cupid card.",
                    icon = Icons.Default.Badge
                ) {
                    Column {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { if (it.length <= 50) draft = draft.copy(name = it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter your full or preferred name", color = Color(0xFF9E9E9E)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = DashboardPeach
                                )
                            },
                            trailingIcon = {
                                if (draft.isVerified) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified Profile",
                                        tint = SuperBlue
                                    )
                                }
                            },
                            colors = darkFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            )
                        )
                        if (draft.name.isBlank()) {
                            Text(
                                text = "Name cannot be empty",
                                color = NopeCoral,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 3. ABOUT ME SECTION (INPUT VALIDATIONS MAX 350 CHARS)
                // ==========================================
                DashboardSectionCard(
                    title = "About Me",
                    subtitle = "Express your personality. Strict maximum 350 characters.",
                    icon = Icons.Default.Description
                ) {
                    val remainingChars = 350 - draft.bio.length
                    Column {
                        OutlinedTextField(
                            value = draft.bio,
                            onValueChange = {
                                if (it.length <= 350) {
                                    draft = draft.copy(bio = it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Tell singles nearby about your interests, vibe, favorite coffee spot or weekend plans...",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 13.sp
                                )
                            },
                            colors = darkFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 4,
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (remainingChars < 20) "$remainingChars characters remaining!" else "Max 350 chars",
                                fontSize = 11.sp,
                                color = if (remainingChars < 20) NopeCoral else DashboardNavMuted
                            )

                            Text(
                                text = "${draft.bio.length} / 350",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (draft.bio.length >= 340) NopeCoral else DashboardPeach
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .clickable { showTipsDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = RewindGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View \"About Me\" tips",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DashboardPeach
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 4. GENDER (DROPDOWN MODAL SELECTION)
                // ==========================================
                DashboardSectionCard(
                    title = "Gender",
                    subtitle = "Select your identified gender",
                    icon = Icons.Default.Wc
                ) {
                    ModalDropdownField(
                        label = "Gender",
                        options = genderOptionsAll,
                        selected = draft.gender,
                        onSelect = { draft = draft.copy(gender = it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 5. LOOKING FOR (DROPDOWN MODAL SELECTION)
                // Tinder-level options incl. long-term, short-term, friends, etc.
                // ==========================================
                DashboardSectionCard(
                    title = "Looking For",
                    subtitle = "Be open about what connection you want right now",
                    icon = Icons.Default.Favorite
                ) {
                    ModalDropdownField(
                        label = "Looking for",
                        options = lookingForOptionsAll,
                        selected = draft.lookingFor,
                        onSelect = { draft = draft.copy(lookingFor = it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 6. CURRENT CITY WITH LOCATION FETCHING
                // ==========================================
                DashboardSectionCard(
                    title = "Current City & Location",
                    subtitle = "Used to show accurate distance to nearby singles",
                    icon = Icons.Default.LocationOn
                ) {
                    Column {
                        OutlinedTextField(
                            value = draft.city,
                            onValueChange = { draft = draft.copy(city = it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. London, United Kingdom", color = Color(0xFF9E9E9E)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = DashboardPeach
                                )
                            },
                            colors = darkFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Location Fetch Button — full flow incl. system GPS dialog
                        OutlinedButton(
                            onClick = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    startLocationFlow()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            border = BorderStroke(1.2.dp, DashboardPeach),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardPeach),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isFetchingLocation
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = DashboardPeach,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detecting GPS coordinates...", fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Auto-Detect Current City (GPS)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (locationFeedbackMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = locationFeedbackMessage!!,
                                fontSize = 11.sp,
                                color = if (locationFailed) NopeCoral else DashboardPeach,
                                fontWeight = if (locationFailed) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 7. WORK AND EDUCATIONS:
                // COLLEGE NAME, COURSE NAME, DESIGNATION, COMPANY NAME
                // ==========================================
                DashboardSectionCard(
                    title = "Work & Education",
                    subtitle = "Share where you studied and where you work",
                    icon = Icons.Default.School
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // College Name
                        DarkLabeledTextField(
                            label = "College / University Name",
                            value = draft.college,
                            placeholder = "e.g. Community College, State University",
                            icon = Icons.Default.AccountBalance,
                            onValueChange = { draft = draft.copy(college = it) }
                        )

                        // Course Name
                        DarkLabeledTextField(
                            label = "Course / Degree Name",
                            value = draft.courseName,
                            placeholder = "e.g. B.Tech Computer Science, MBA, BA English",
                            icon = Icons.Default.MenuBook,
                            onValueChange = { draft = draft.copy(courseName = it) }
                        )

                        // Designation
                        DarkLabeledTextField(
                            label = "Designation / Job Title",
                            value = draft.jobTitle,
                            placeholder = "e.g. Founder, Software Engineer, Architect",
                            icon = Icons.Default.Work,
                            onValueChange = { draft = draft.copy(jobTitle = it) }
                        )

                        // Company Name
                        DarkLabeledTextField(
                            label = "Company Name",
                            value = draft.company,
                            placeholder = "e.g. Acme Solutions, Downtown Hub",
                            icon = Icons.Default.Business,
                            onValueChange = { draft = draft.copy(company = it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 8. PERSONAL DETAILS:
                // MARITAL STATUS, FAMILY PLANS, PETS, DRINKING, SMOKING
                // ==========================================
                DashboardSectionCard(
                    title = "Personal Details & Lifestyle",
                    subtitle = "Key values and everyday habits",
                    icon = Icons.Default.Diversity3
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Marital Status (dropdown modal)
                        ModalDropdownField(
                            label = "Marital Status",
                            options = maritalStatusOptionsAll,
                            selected = draft.maritalStatus,
                            onSelect = { draft = draft.copy(maritalStatus = it) }
                        )

                        // Family Plans (dropdown modal)
                        ModalDropdownField(
                            label = "Family Plans",
                            options = familyPlansOptionsAll,
                            selected = draft.familyPlans,
                            onSelect = { draft = draft.copy(familyPlans = it) }
                        )

                        // Pets (dropdown modal)
                        ModalDropdownField(
                            label = "Pets",
                            options = petsOptionsAll,
                            selected = draft.pets,
                            onSelect = { draft = draft.copy(pets = it) }
                        )

                        // Drinking (dropdown modal)
                        ModalDropdownField(
                            label = "Drinking Habits",
                            options = drinkingOptionsAll,
                            selected = draft.drinking,
                            onSelect = { draft = draft.copy(drinking = it) }
                        )

                        // Smoking (dropdown modal)
                        ModalDropdownField(
                            label = "Smoking Habits",
                            options = smokingOptionsAll,
                            selected = draft.smoking,
                            onSelect = { draft = draft.copy(smoking = it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 9. PROFILE SHARING (OPTIONAL):
                // USERNAME, SHARE MY PROFILE
                // ==========================================
                DashboardSectionCard(
                    title = "Profile Sharing (Optional)",
                    subtitle = "Claim your custom handle and share your profile link with others",
                    icon = Icons.Default.Share
                ) {
                    Column {
                        Text(
                            text = "Custom Username",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DashboardMutedBeige
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = draft.username,
                            onValueChange = { input ->
                                val clean = input.trim().replace("@", "").filter { it.isLetterOrDigit() || it == '_' }
                                if (clean.length <= 30) draft = draft.copy(username = clean)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("username_here", color = Color(0xFF9E9E9E)) },
                            prefix = {
                                Text(
                                    text = "@",
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardPeach,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            },
                            colors = darkFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Link preview box
                        val shareLink = "https://mallucupid.app/u/${draft.username.ifBlank { "user" }}"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = DashboardPeach,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = shareLink,
                                        fontSize = 12.sp,
                                        color = DashboardCream,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("MalluCupid Profile", shareLink)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Profile link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy link",
                                        tint = DashboardPeach,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Share button
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Connect with me on MalluCupid! ❤️ Check out my profile: $shareLink"
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share your MalluCupid profile via")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DashboardCard,
                                contentColor = DashboardCream
                            ),
                            border = BorderStroke(1.dp, DashboardPeach.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = DashboardPeach,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share My Profile",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DashboardCream
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 10. HELP SECTION & LEGAL LINKS
                // ==========================================
                DashboardSectionCard(
                    title = "Help & Community",
                    subtitle = "Guidelines, safety measures and support resources",
                    icon = Icons.Default.HelpOutline
                ) {
                    Column {
                        GroupedActionRow(
                            title = "Dating Safety & Tips",
                            subtitle = "Tips for safe and enjoyable dates",
                            icon = Icons.Default.Shield,
                            onClick = {
                                helpDialogTopic = "Safety & Dating Tips"
                                showHelpDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        GroupedActionRow(
                            title = "Community Guidelines",
                            subtitle = "Respect, authenticity, and mutual trust",
                            icon = Icons.Default.Groups,
                            onClick = {
                                helpDialogTopic = "Community Guidelines"
                                showHelpDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        GroupedActionRow(
                            title = "Help & Support Desk",
                            subtitle = "Contact MalluCupid care team (support@mallucupid.app)",
                            icon = Icons.Default.SupportAgent,
                            onClick = {
                                helpDialogTopic = "Help & Support Desk"
                                showHelpDialog = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Legal Links Section
                DashboardSectionCard(
                    title = "Legal Information",
                    subtitle = "Policies governing your account and personal data",
                    icon = Icons.Default.Gavel
                ) {
                    Column {
                        GroupedActionRow(
                            title = "Terms of Service",
                            subtitle = "Read user agreement and terms",
                            icon = Icons.Default.Article,
                            onClick = {
                                legalDialogTopic = "Terms of Service"
                                showLegalDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        GroupedActionRow(
                            title = "Privacy Policy",
                            subtitle = "How we protect your personal info and photos",
                            icon = Icons.Default.Lock,
                            onClick = {
                                legalDialogTopic = "Privacy Policy"
                                showLegalDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        GroupedActionRow(
                            title = "Cookie & Location Preferences",
                            subtitle = "Manage stored device preferences",
                            icon = Icons.Default.Cookie,
                            onClick = {
                                legalDialogTopic = "Cookie & Location Preferences"
                                showLegalDialog = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ==========================================
                // 11. LOGOUT BUTTON WITH CONFIRMATION DIALOGUE BOX
                // ==========================================
                Surface(
                    onClick = { showLogoutConfirmDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2C1917),
                    border = BorderStroke(1.2.dp, NopeCoral.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = NopeCoral,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Log Out of MalluCupid",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NopeCoral
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // ==========================================
    // LOGOUT CONFIRMATION DIALOGUE BOX
    // ==========================================
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!signingOut) showLogoutConfirmDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = NopeCoral,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Log Out of MalluCupid?",
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out? You will need your email/password to sign back in and chat with your matches.",
                    color = DashboardMutedBeige,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        signingOut = true
                        onSignOut()
                    },
                    enabled = !signingOut,
                    colors = ButtonDefaults.buttonColors(containerColor = NopeCoral)
                ) {
                    if (signingOut) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false },
                    enabled = !signingOut,
                    border = BorderStroke(1.dp, Color(0xFF4A3A33)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DashboardMutedBeige)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = DashboardCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ==========================================
    // HELP MODAL DIALOG
    // ==========================================
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = helpDialogTopic,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            },
            text = {
                val content = when (helpDialogTopic) {
                    "Safety & Dating Tips" -> """
                        • Always meet in crowded public places for initial dates (e.g. downtown cafes, shopping malls, the waterfront).
                        • Inform a close friend or family member of your whereabouts.
                        • Never share one-time passwords (OTP), banking details, or financial credentials.
                        • Respect personal boundaries and cultural preferences.
                        • Report and block any suspicious or inappropriate profiles immediately.
                    """.trimIndent()
                    "Community Guidelines" -> """
                        • MalluCupid is dedicated to kind, authentic connections for singles nearby.
                        • Zero tolerance for hate speech, harassment, nudity, or fake accounts.
                        • Treat every person with respect and dignity regardless of background.
                        • Keep chats friendly, respectful, and consensual.
                    """.trimIndent()
                    else -> """
                        • Need help with your account or profile verification?
                        • Email our support team at: support@mallucupid.app
                        • Response time: Within 24 hours.
                        • Available 7 days a week.
                    """.trimIndent()
                }
                Text(
                    text = content,
                    color = DashboardMutedBeige,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = DashboardCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ==========================================
    // LEGAL MODAL DIALOG
    // ==========================================
    if (showLegalDialog) {
        AlertDialog(
            onDismissRequest = { showLegalDialog = false },
            title = {
                Text(
                    text = legalDialogTopic,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            },
            text = {
                val content = when (legalDialogTopic) {
                    "Terms of Service" -> """
                        Welcome to MalluCupid. By accessing or using our application, you agree to comply with our Terms of Service.
                        • Users must be at least 18 years of age.
                        • Profile information must represent your real identity.
                        • Accounts violating safety and harassment policies will be permanently terminated.
                    """.trimIndent()
                    "Privacy Policy" -> """
                        Your privacy is our priority.
                        • Your precise location coordinates are never revealed to other users (only approximate distance in kilometers).
                        • Device media files are uploaded securely only when you select them.
                        • We do not sell personal data to third parties.
                    """.trimIndent()
                    else -> """
                        • Device cookies and local storage are used to preserve your login session and match preferences securely.
                        • You can reset local cache anytime in your device application settings.
                    """.trimIndent()
                }
                Text(
                    text = content,
                    color = DashboardMutedBeige,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLegalDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
                ) {
                    Text("Understood", color = Color.White)
                }
            },
            containerColor = DashboardCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ==========================================
    // BIO TIPS DIALOG
    // ==========================================
    if (showTipsDialog) {
        AlertDialog(
            onDismissRequest = { showTipsDialog = false },
            title = {
                Text(
                    text = "Profile Bio Tips",
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
            },
            text = {
                Text(
                    text = "• Highlight your genuine interests (e.g. favorite films, road trips to the hills, favorite foods).\n\n" +
                            "• Keep it positive, conversational, and under the 350-character limit.\n\n" +
                            "• Mention what kind of connection you are excited to find.\n\n" +
                            "• A witty one-liner makes a great conversation starter!",
                    color = DashboardMutedBeige,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTipsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardTerracotta)
                ) {
                    Text("Got It", color = Color.White)
                }
            },
            containerColor = DashboardCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ====================================================================
// SUB-COMPONENTS STYLED SPECIFICALLY IN DASHBOARD LUXURY ROMANTIC PALETTE
// ====================================================================

@Composable
private fun DashboardSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = DashboardPeach,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = DashboardNavMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DarkLabeledTextField(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DashboardMutedBeige
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF9E9E9E), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DashboardPeach,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = darkFieldColors(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Dropdown MODAL selection field (user requirement: every selection in Edit
 * Profile is a modal bottom-sheet picker). Field shows the current value on a
 * REAL WHITE background with BLACK text; the modal lists options in white with
 * black text and a terracotta check on the selected row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalDropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DashboardMutedBeige
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(
                1.dp,
                if (selected.isNotBlank()) DashboardTerracotta else Color(0xFFDDDDDD)
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.ifBlank { "Select $label" },
                    color = if (selected.isNotBlank()) Color.Black else Color(0xFF6E6E6E),
                    fontSize = 14.sp,
                    fontWeight = if (selected.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open $label picker",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = Color(0xFFEEEEEE))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                ) {
                    items(options.size) { index ->
                        val opt = options[index]
                        val isSelected = opt.equals(selected, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(opt)
                                    expanded = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = opt,
                                fontSize = 15.sp,
                                color = if (isSelected) DashboardTerracotta else Color.Black,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = DashboardTerracotta,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DashboardPeach,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DashboardCream
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = DashboardNavMuted
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = DashboardNavMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DashboardTerracotta,
    unfocusedBorderColor = Color(0xFFDDDDDD),
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = DashboardTerracotta
)


@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedOption.isNotBlank()) selectedOption else label,
                    color = if (selectedOption.isNotBlank()) Color.Black else Color.Gray,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select",
                    tint = Color.Gray
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                val isSelected = selectedOption == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) DashboardTerracotta else Color.Black,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
