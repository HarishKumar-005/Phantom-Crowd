package com.phantomcrowd.utils

import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculator for bearing (direction) between GPS coordinates.
 * Uses Haversine formula for accurate bearing calculation.
 */
object BearingCalculator {
    
    private const val TAG = "BearingCalculator"
    
    /**
     * Calculate the bearing (direction) from user to target location.
     * 
     * @param userLat User's current latitude
     * @param userLon User's current longitude
     * @param targetLat Target location latitude
     * @param targetLon Target location longitude
     * @return Bearing in degrees (0-360), where 0=North, 90=East, 180=South, 270=West
     */
    fun calculateBearing(
        userLat: Double,
        userLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Double {
        val userLatRad = Math.toRadians(userLat)
        val targetLatRad = Math.toRadians(targetLat)
        val dLonRad = Math.toRadians(targetLon - userLon)
        
        val y = sin(dLonRad) * cos(targetLatRad)
        val x = cos(userLatRad) * sin(targetLatRad) - 
                sin(userLatRad) * cos(targetLatRad) * cos(dLonRad)
        
        var bearing = Math.toDegrees(atan2(y, x))
        
        // Normalize to 0-360 range
        bearing = (bearing + 360) % 360
        
        Log.d(TAG, "Bearing from ($userLat, $userLon) to ($targetLat, $targetLon) = ${String.format("%.1f", bearing)}°")
        
        return bearing
    }
    
    /**
     * Convert bearing angle to cardinal direction string.
     * 
     * @param bearing Bearing in degrees (0-360)
     * @return Cardinal direction (N, NE, E, SE, S, SW, W, NW)
     */
    fun bearingToCardinal(bearing: Double): String {
        val normalizedBearing = (bearing + 360) % 360
        
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "North"
            normalizedBearing < 67.5 -> "North-East"
            normalizedBearing < 112.5 -> "East"
            normalizedBearing < 157.5 -> "South-East"
            normalizedBearing < 202.5 -> "South"
            normalizedBearing < 247.5 -> "South-West"
            normalizedBearing < 292.5 -> "West"
            else -> "North-West"
        }
    }
    
    /**
     * Convert bearing angle to short cardinal direction string.
     * 
     * @param bearing Bearing in degrees (0-360)
     * @return Short cardinal direction (N, NE, E, SE, S, SW, W, NW)
     */
    fun bearingToCardinalShort(bearing: Double): String {
        val normalizedBearing = (bearing + 360) % 360
        
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "N"
            normalizedBearing < 67.5 -> "NE"
            normalizedBearing < 112.5 -> "E"
            normalizedBearing < 157.5 -> "SE"
            normalizedBearing < 202.5 -> "S"
            normalizedBearing < 247.5 -> "SW"
            normalizedBearing < 292.5 -> "W"
            else -> "NW"
        }
    }
}
