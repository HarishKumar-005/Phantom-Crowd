package com.phantomcrowd.ar

import com.google.ar.core.Anchor
import com.google.ar.core.Earth
import com.google.ar.core.GeospatialPose
import com.google.ar.core.Session
import com.phantomcrowd.data.AnchorData
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GeospatialAnchorManager {

    fun createAnchor(session: Session, latitude: Double, longitude: Double, altitude: Double, rotationY: Double = 0.0): Anchor? {
        val earth = session.earth
        if (earth == null || earth.trackingState != com.google.ar.core.TrackingState.TRACKING) {
            return null
        }
        
        // Phase 1+: Check GPS accuracy threshold
        val pose = earth.cameraGeospatialPose
        if (pose.horizontalAccuracy > 10.0) {
            com.phantomcrowd.utils.Logger.w(
                com.phantomcrowd.utils.Logger.Category.AR,
                "Anchor skipped: GPS accuracy ${String.format("%.1f", pose.horizontalAccuracy)}m exceeds 10m threshold"
            )
            return null
        }
        com.phantomcrowd.utils.Logger.d(
            com.phantomcrowd.utils.Logger.Category.AR,
            "Anchor OK: GPS accuracy ${String.format("%.1f", pose.horizontalAccuracy)}m within threshold"
        )

        // Create a quaternion for rotation around Y axis (up)
        // Y-axis rotation (heading)
        val qx = 0f
        val qy = sin(Math.toRadians(rotationY) / 2).toFloat()
        val qz = 0f
        val qw = cos(Math.toRadians(rotationY) / 2).toFloat()

        return try {
            earth.createAnchor(latitude, longitude, altitude, qx, qy, qz, qw)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getEarthTrackingState(session: Session): String {
        val earth = session.earth ?: return "Earth is null"
        return "Tracking State: ${earth.trackingState}, Earth State: ${earth.earthState}"
    }
    
    fun getCameraGeospatialPose(session: Session): GeospatialPose? {
        val earth = session.earth
        if (earth?.trackingState == com.google.ar.core.TrackingState.TRACKING) {
             return earth.cameraGeospatialPose
        }
        return null
    }
}
