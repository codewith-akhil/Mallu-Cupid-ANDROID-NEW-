package com.mallucupid.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val stateName: String,
    val countryName: String,
    val fullLocation: String
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests a FRESH location using FusedLocationProviderClient.
     * Uses getCurrentLocation() (not getLastLocation) for accuracy.
     * Returns null if permission denied or location unavailable.
     */
    suspend fun getCurrentLocation(context: Context): LocationResult? {
        if (!hasLocationPermission(context)) return null

        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        // Request fresh location with high accuracy
        val location = try {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) cont.resume(loc) else cont.resume(null)
                    }
                    .addOnFailureListener { cont.resume(null) }

                cont.invokeOnCancellation { cts.cancel() }
            }
        } catch (e: SecurityException) {
            null
        } ?: return null

        // Reverse geocode to city name
        val geocoder = Geocoder(context, java.util.Locale.getDefault())
        val address = try {
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

        val cityName = address?.locality ?: address?.subAdminArea ?: address?.subLocality ?: "Unknown"
        val stateName = address?.adminArea ?: ""
        val countryName = address?.countryName ?: ""
        val fullLocation = if (stateName.isNotBlank() && countryName.isNotBlank()) {
            "$cityName, $stateName, $countryName"
        } else {
            cityName
        }

        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            cityName = cityName,
            stateName = stateName,
            countryName = countryName,
            fullLocation = fullLocation
        )
    }
}
