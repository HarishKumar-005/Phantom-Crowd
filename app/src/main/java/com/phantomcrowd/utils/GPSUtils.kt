package com.phantomcrowd.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class GPSUtils(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private val _locationFlow = kotlinx.coroutines.flow.MutableStateFlow<Location?>(null)
    val locationFlow: kotlinx.coroutines.flow.StateFlow<Location?> = _locationFlow.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            android.util.Log.e(Constants.TAG_PERMISSION, "Location permission missing in GPSUtils")
            return
        }

        try {
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000 // Update every 2 seconds
            ).build()

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    for (location in result.locations) {
                         android.util.Log.d(Constants.TAG_GPS, "Location update: ${location.latitude}, ${location.longitude}")
                         _locationFlow.value = location
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
            android.util.Log.d(Constants.TAG_GPS, "Location updates requested")

        } catch (e: Exception) {
            android.util.Log.e(Constants.TAG_GPS, "Error requesting location updates", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
             android.util.Log.e(Constants.TAG_PERMISSION, "Location permission denied")
             return null
        }

        return try {
            // Try to get the current location with high accuracy
            val result = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()
            
            if (result != null) {
                android.util.Log.d(Constants.TAG_GPS, "Location: ${result.latitude}, ${result.longitude}")
            } else {
                android.util.Log.w(Constants.TAG_GPS, "Location is null")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(Constants.TAG_GPS, "GPS Error", e)
            e.printStackTrace()
            // Fallback to last known location
            try {
                fusedLocationClient.lastLocation.await()
            } catch (e2: Exception) {
                null
            }
        }
    }

    @Suppress("unused")
    fun getDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
