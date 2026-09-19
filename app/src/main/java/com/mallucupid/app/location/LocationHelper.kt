package com.mallucupid.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val stateName: String,
    val countryName: String,
    val countryCode: String,
    val fullLocation: String
)

/**
 * Result of checking whether the device can provide high-accuracy location right now.
 *
 * [Enabled]        — GPS / network location is ON, we can fetch immediately.
 * [Resolvable]     — location is OFF but Android can turn it ON with the user's single
 *                    confirmation via the system dialog (the "Turn on device location?"
 *                    prompt). [pendingIntent] must be launched with
 *                    ActivityResultContracts.StartIntentSenderForResult().
 * [Unresolvable]   — cannot show the system dialog (rare: Play Services missing /
 *                    provider locked by MDM). The app must send the user to system
 *                    settings manually via [openLocationSettings].
 */
sealed class GpsCheck {
    object Enabled : GpsCheck()
    data class Resolvable(val pendingIntent: android.app.PendingIntent) : GpsCheck()
    object Unresolvable : GpsCheck()
}

object LocationHelper {

    /** Fresh-fix timeout. FusedLocationProvider rarely needs more than ~15 s. */
    private const val FIX_TIMEOUT_MS = 20_000L

    /** Reverse-geocode timeout — the Geocoder listener can hang on some OEMs. */
    private const val GEOCODER_TIMEOUT_MS = 8_000L

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Checks if location services are usable. When they are OFF, returns
     * [GpsCheck.Resolvable] so the caller can show the SYSTEM "Turn on location"
     * dialog — the same prompt Google Maps shows — which actually flips the
     * phone's GPS on when the user taps "Yes". This is what was missing before:
     * the app silently did nothing when GPS was off.
     */
    suspend fun checkGpsSettings(context: Context): GpsCheck {
        // Cheap pre-check: if a provider is already on, skip the dialog entirely.
        if (isGpsEnabled(context)) return GpsCheck.Enabled

        val settingsClient = LocationServices.getSettingsClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        return try {
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<GpsCheck> { cont ->
                    settingsClient.checkLocationSettings(settingsRequest)
                        .addOnSuccessListener { cont.resume(GpsCheck.Enabled) }
                        .addOnFailureListener { e ->
                            val outcome = if (e is ResolvableApiException) {
                                try { GpsCheck.Resolvable(e.resolution) } catch (_: Exception) { GpsCheck.Unresolvable }
                            } else {
                                GpsCheck.Unresolvable
                            }
                            cont.resume(outcome)
                        }
                }
            } ?: GpsCheck.Unresolvable
        } catch (e: Exception) {
            GpsCheck.Unresolvable
        }
    }

    /**
     * One-shot high-accuracy fix. Returns null on ANY failure (no fix within
     * [FIX_TIMEOUT_MS], no permission, provider error) — the caller decides what
     * message to show. Never throws.
     *
     * If `getCurrentLocation()` cannot acquire a fresh fix in time, we fall back
     * to `lastLocation` so we can still populate the city from the most recent
     * cached position (better than showing "Unknown").
     */
    suspend fun getCurrentLocation(context: Context): LocationResult? {
        if (!hasLocationPermission(context)) return null

        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        val location = withTimeoutOrNull(FIX_TIMEOUT_MS) {
            try {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cts = CancellationTokenSource()
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }
                        .addOnCanceledListener { cont.resume(null) }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            } catch (e: SecurityException) {
                null
            }
        } ?: run {
            // Fresh fix failed — try the cached last location before giving up.
            // 3s timeout: lastLocation is a fast in-memory cache lookup (no fresh
            // GPS fix required), so it should resolve within milliseconds; the
            // 3s ceiling is a safety net for slow Play Services bind.
            try {
                withTimeoutOrNull(3_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        fusedClient.lastLocation
                            .addOnSuccessListener { loc -> cont.resume(loc) }
                            .addOnFailureListener { cont.resume(null) }
                            .addOnCanceledListener { cont.resume(null) }
                    }
                }
            } catch (_: SecurityException) {
                null
            }
        } ?: return null

        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            cont.resume(addresses.firstOrNull())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                }
            } catch (e: Exception) {
                null
            }
        }

        val cityName = address?.locality ?: address?.subAdminArea ?: address?.subLocality ?: "Unknown"
        val stateName = address?.adminArea ?: ""
        val countryName = address?.countryName ?: ""
        val countryCode = address?.countryCode ?: ""
        val fullLocation = if (stateName.isNotBlank() && countryName.isNotBlank()) {
            "$cityName, $countryName"
        } else {
            cityName
        }

        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            cityName = cityName,
            stateName = stateName,
            countryName = countryName,
            countryCode = countryCode,
            fullLocation = fullLocation
        )
    }
}
