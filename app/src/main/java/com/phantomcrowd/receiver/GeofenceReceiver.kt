package com.phantomcrowd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import com.phantomcrowd.R

class GeofenceReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val CHANNEL_ID = "phantom_crowd_geofence"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Geofence event received")
        
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent != null && !geofencingEvent.hasError()) {
            val geofenceTransition = geofencingEvent.geofenceTransition
            
            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {
                
                val triggeringGeofences = geofencingEvent.triggeringGeofences
                triggeringGeofences?.forEach { geofence ->
                    val requestId = geofence.requestId
                    Log.d(TAG, "Geofence triggered for: $requestId")
                    sendNotification(context, requestId)
                }
            }
        } else {
            val errorMessage = geofencingEvent?.errorCode ?: "Unknown error"
            Log.e(TAG, "Geofence error: $errorMessage")
        }
    }
    
    /**
     * Send notification to user
     */
    private fun sendNotification(context: Context, issueId: String) {
        // Correctly handle missing permission for notifications if needed, 
        // though usually this is checked at runtime before creating this situation.
        // For simplicity in this demo, we assume permission is granted or this will fail silently/log.
        
        // Use R.drawable.ic_launcher_foreground if ic_dialog_info is not suitable, 
        // but specs asked for android.R.drawable.ic_dialog_info.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 Issue Nearby!")
            .setContentText("There's an issue $issueId nearby. Tap to navigate.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        try {
            // Note: In real app, check ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            with(NotificationManagerCompat.from(context)) {
                 notify(issueId.hashCode(), notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing: ${e.message}")
        }
        
        Log.d(TAG, "Notification sent for $issueId")
    }
}
