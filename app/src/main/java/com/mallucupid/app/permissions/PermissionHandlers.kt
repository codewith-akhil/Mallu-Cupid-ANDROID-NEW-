package com.mallucupid.app.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Permission state for each permission group.
 */
data class PermissionState(
    val locationGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val mediaGranted: Boolean = false,
)

/**
 * Checks all runtime permission states on first composition.
 * Returns the current permission state — does NOT request anything.
 * Use the specific launcher functions below to request permissions.
 */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionState()) }

    LaunchedEffect(Unit) {
        state = PermissionState(
            locationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            cameraGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true,
            mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    return state
}

/**
 * Requests notification permission (Android 13+).
 * Pre-Android 13: returns true immediately (no runtime permission needed).
 *
 * Usage:
 *   val requestNotifications = rememberNotificationPermissionLauncher { granted -> ... }
 *   Button(onClick = { requestNotifications() }) { ... }
 */
@Composable
fun rememberNotificationPermissionLauncher(
    onResult: (Boolean) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return remember { { onResult(true) } }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return remember {
        {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyGranted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(true)
            }
        }
    }
}

/**
 * Requests location permission (fine + coarse).
 *
 * Usage:
 *   val requestLocation = rememberLocationPermissionLauncher { granted -> ... }
 *   Button(onClick = { requestLocation() }) { ... }
 */
@Composable
fun rememberLocationPermissionLauncher(
    onResult: (Boolean) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onResult(granted)
    }

    return remember {
        {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyGranted) {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            } else {
                onResult(true)
            }
        }
    }
}

/**
 * Requests camera permission.
 *
 * Usage:
 *   val requestCamera = rememberCameraPermissionLauncher { granted -> ... }
 *   Button(onClick = { requestCamera() }) { ... }
 */
@Composable
fun rememberCameraPermissionLauncher(
    onResult: (Boolean) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return remember {
        {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyGranted) {
                launcher.launch(Manifest.permission.CAMERA)
            } else {
                onResult(true)
            }
        }
    }
}
