package com.avalemon.spotcheck.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await

/** One-shot foreground location via the fused provider. Never throws; null means "no fix". */
class LocationRepository(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission") // Checked by hasPermission(); SecurityException is caught anyway.
    suspend fun currentLocation(): LatLng? {
        if (!hasPermission()) return null
        return runCatching {
            val cts = CancellationTokenSource()
            val fix = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
            fix?.let { LatLng(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
