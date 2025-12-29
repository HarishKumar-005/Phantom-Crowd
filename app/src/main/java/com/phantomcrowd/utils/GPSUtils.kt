package com.phantomcrowd.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GPSUtils(private val context: Context) {

    companion object {
        private const val TAG = "GPS"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow

    private var locationCallback: LocationCallback? = null
    private var isRequestingUpdates = false

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permission check: fine=$fineGranted, coarse=$coarseGranted")
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted!")
            return
        }

        if (isRequestingUpdates) {
            Log.d(TAG, "Already requesting location updates")
            return
        }

        Log.d(TAG, "Starting location updates...")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).setMinUpdateIntervalMillis(1000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d(TAG, "Location received: ${location.latitude}, ${location.longitude}")
                    _locationFlow.value = location
                } else {
                    Log.w(TAG, "LocationResult returned null location")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isRequestingUpdates = true
            Log.d(TAG, "Location updates started successfully")

            // Also try to get last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                    _locationFlow.value = location
                } else {
                    Log.d(TAG, "Last known location is null, waiting for updates...")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last location", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            isRequestingUpdates = false
            Log.d(TAG, "Location updates stopped")
        }
    }

    /**
     * Get distance between two points in meters.
     */
    @Suppress("unused")
    fun getDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
