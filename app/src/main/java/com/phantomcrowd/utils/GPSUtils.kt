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
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Utility class for GPS location updates.
 * Uses FusedLocationProviderClient for accurate and battery-efficient location.
 */
class GPSUtils(private val context: Context) {

    companion object {
        private const val TAG = "GPS"
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val MIN_UPDATE_INTERVAL_MS = 1000L
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
        
        if (DebugConfig.LOG_LOCATION_UPDATES) {
            Logger.d(Logger.Category.GPS, "Permission check: fine=$fineGranted, coarse=$coarseGranted")
        }
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Logger.e(Logger.Category.GPS, "Location permission not granted!")
            return
        }

        if (isRequestingUpdates) {
            Logger.d(Logger.Category.GPS, "Already requesting location updates")
            return
        }

        Logger.i(Logger.Category.GPS, "Starting location updates...")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS
        ).setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    if (DebugConfig.LOG_LOCATION_UPDATES) {
                        Logger.d(Logger.Category.GPS, 
                            "Location: ${location.latitude}, ${location.longitude} " +
                            "(accuracy: ${location.accuracy}m)")
                    }
                    _locationFlow.value = location
                } else {
                    Logger.w(Logger.Category.GPS, "LocationResult returned null location")
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
            Logger.i(Logger.Category.GPS, "Location updates started successfully")

            // Also try to get last known location immediately
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Logger.d(Logger.Category.GPS, 
                            "Last known location: ${location.latitude}, ${location.longitude}")
                        _locationFlow.value = location
                    } else {
                        Logger.d(Logger.Category.GPS, "Last known location is null, waiting for updates...")
                    }
                }
                .addOnFailureListener { e ->
                    Logger.e(Logger.Category.GPS, "Failed to get last location", e)
                }

        } catch (e: Exception) {
            Logger.e(Logger.Category.GPS, "Error starting location updates", e)
            isRequestingUpdates = false
        }
    }

    /**
     * Stop receiving location updates.
     */
    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                Logger.i(Logger.Category.GPS, "Location updates stopped")
            } catch (e: Exception) {
                Logger.e(Logger.Category.GPS, "Error stopping location updates", e)
            }
        }
        locationCallback = null
        isRequestingUpdates = false
    }

    /**
     * Clean up all resources. Call this when the component is destroyed.
     */
    fun cleanup() {
        stopLocationUpdates()
        _locationFlow.value = null
        Logger.d(Logger.Category.GPS, "GPSUtils cleaned up")
    }

    /**
     * Check if currently receiving location updates.
     */
    fun isActive(): Boolean = isRequestingUpdates

    /**
     * Get the current location value (may be null if not yet received).
     */
    fun getCurrentLocation(): Location? = _locationFlow.value

    /**
     * Get distance between two points in meters.
     */
    fun getDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Get distance from current location to a point in meters.
     * Returns null if current location is not available.
     */
    fun getDistanceFromCurrent(lat: Double, lon: Double): Float? {
        val current = _locationFlow.value ?: return null
        return getDistanceBetween(current.latitude, current.longitude, lat, lon)
    }

    /**
     * Get location quickly using getCurrentLocation() API.
     * Much faster than requestLocationUpdates() - typically 300-1000ms.
     * 
     * Strategy:
     * 1. Try lastLocation first (instant if available and fresh)
     * 2. Fall back to getCurrentLocation() (single fresh fix)
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationFast(): Location? {
        if (!hasLocationPermission()) {
            Logger.w(Logger.Category.GPS, "No location permission for fast location")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            // First try last location (instant if available)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null && isLocationFresh(lastLoc)) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Logger.i(Logger.Category.GPS, 
                            "Fast location from cache in ${elapsed}ms: ${lastLoc.latitude}, ${lastLoc.longitude}")
                        _locationFlow.value = lastLoc
                        continuation.resume(lastLoc)
                    } else {
                        // Fall back to getCurrentLocation (faster than requestLocationUpdates)
                        Logger.d(Logger.Category.GPS, "No fresh cache, using getCurrentLocation...")
                        val cancellationTokenSource = CancellationTokenSource()
                        
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.token
                        ).addOnSuccessListener { freshLoc ->
                            val elapsed = System.currentTimeMillis() - startTime
                            if (freshLoc != null) {
                                Logger.i(Logger.Category.GPS,
                                    "Fresh location in ${elapsed}ms: ${freshLoc.latitude}, ${freshLoc.longitude}")
                                _locationFlow.value = freshLoc
                                continuation.resume(freshLoc)
                            } else {
                                Logger.w(Logger.Category.GPS, "getCurrentLocation returned null after ${elapsed}ms")
                                continuation.resume(null)
                            }
                        }.addOnFailureListener { e ->
                            val elapsed = System.currentTimeMillis() - startTime
                            Logger.e(Logger.Category.GPS, "getCurrentLocation failed after ${elapsed}ms", e)
                            continuation.resume(null)
                        }
                        
                        // Cancel if coroutine is cancelled
                        continuation.invokeOnCancellation {
                            cancellationTokenSource.cancel()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Logger.e(Logger.Category.GPS, "lastLocation check failed", e)
                    continuation.resume(null)
                }
        }
    }

    /**
     * Check if a location is fresh enough to use (less than 60 seconds old).
     */
    private fun isLocationFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        val isFresh = ageMs < 60_000 // 1 minute threshold
        if (DebugConfig.LOG_LOCATION_UPDATES) {
            Logger.d(Logger.Category.GPS, "Location age: ${ageMs/1000}s, fresh: $isFresh")
        }
        return isFresh
    }
}
