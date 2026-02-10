package com.phantomcrowd.data

import android.content.Context
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.phantomcrowd.data.AnchorData
import android.app.PendingIntent
import android.content.Intent
import com.phantomcrowd.receiver.GeofenceReceiver
import android.util.Log

class GeofenceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_RADIUS_METERS = 100f  // 100m radius
        private const val GEOFENCE_DWELL_DELAY_MS = 5000  // 5 seconds
    }
    
    private val geofencingClient = LocationServices.getGeofencingClient(context)
    
    /**
     * Create geofences for all nearby issues
     */
    fun createGeofences(anchors: List<AnchorData>) {
        try {
            val geofenceList = anchors.map { anchor ->
                Geofence.Builder()
                    .setRequestId(anchor.id)
                    .setCircularRegion(anchor.latitude, anchor.longitude, GEOFENCE_RADIUS_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or 
                        Geofence.GEOFENCE_TRANSITION_DWELL
                    )
                    .setLoiteringDelay(GEOFENCE_DWELL_DELAY_MS)
                    .build()
            }
            
            if (geofenceList.isEmpty()) {
                Log.w(TAG, "No geofences to create")
                return
            }
            
            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(0) // Don't fire when already inside — only on future ENTER
                .addGeofences(geofenceList)
                .build()
            
            val pendingIntent = getPendingIntent()
            
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully created ${geofenceList.size} geofences")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create geofences: ${e.message}")
                }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }
    
    /**
     * Remove all geofences
     */
    fun removeGeofences() {
        try {
            geofencingClient.removeGeofences(getPendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully removed all geofences")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofences: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing geofences: ${e.message}")
        }
    }
    
    /**
     * Get pending intent for geofence receiver
     */
    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
