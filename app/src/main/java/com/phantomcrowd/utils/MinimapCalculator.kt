package com.phantomcrowd.utils

import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos

/**
 * Calculator for converting GPS coordinates to minimap canvas coordinates.
 * Uses Cartesian approximation which is accurate for distances < 100m.
 */
object MinimapCalculator {
    
    private const val TAG = "MinimapCalculator"
    
    // 1 degree of latitude ≈ 111 km
    private const val METERS_PER_DEGREE_LAT = 111000.0
    
    /**
     * Convert GPS coordinates to canvas pixel position.
     * 
     * @param userLat User's current latitude
     * @param userLon User's current longitude
     * @param targetLat Target anchor's latitude
     * @param targetLon Target anchor's longitude
     * @param canvasWidthPx Width of canvas in pixels
     * @param canvasHeightPx Height of canvas in pixels
     * @param radiusMeters Radius of map in meters (default 100m means 200m diameter)
     * @return Offset representing x,y position on canvas
     */
    fun gpsToCanvas(
        userLat: Double,
        userLon: Double,
        targetLat: Double,
        targetLon: Double,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        radiusMeters: Float = 100f
    ): Offset {
        // Calculate lat/lon deltas
        val dLat = targetLat - userLat
        val dLon = targetLon - userLon
        
        // Convert to meters using Cartesian approximation
        // dLon needs to be adjusted by cos(latitude) because longitude lines converge at poles
        val dLatMeters = dLat * METERS_PER_DEGREE_LAT
        val dLonMeters = dLon * METERS_PER_DEGREE_LAT * cos(Math.toRadians(userLat))
        
        // Scale to canvas pixels
        // pixelsPerMeter = canvasWidth / (2 * radius) because diameter = 2 * radius
        val pixelsPerMeter = canvasWidthPx / (2 * radiusMeters)
        
        // Calculate pixel position relative to center
        // X: positive dLon (east) = positive X (right)
        // Y: positive dLat (north) = negative Y (up, because canvas Y increases downward)
        val xPixel = canvasWidthPx / 2 + (dLonMeters * pixelsPerMeter).toFloat()
        val yPixel = canvasHeightPx / 2 - (dLatMeters * pixelsPerMeter).toFloat()
        
        // Clamp to canvas bounds (with small margin)
        val margin = 10f
        val clampedX = xPixel.coerceIn(margin, canvasWidthPx - margin)
        val clampedY = yPixel.coerceIn(margin, canvasHeightPx - margin)
        
        Log.d(TAG, "GPS (${String.format("%.6f", targetLat)}, ${String.format("%.6f", targetLon)}) → Canvas (${clampedX.toInt()}px, ${clampedY.toInt()}px)")
        
        return Offset(clampedX, clampedY)
    }
    
    /**
     * Check if a target is within the visible radius of the minimap.
     */
    fun isWithinRadius(
        userLat: Double,
        userLon: Double,
        targetLat: Double,
        targetLon: Double,
        radiusMeters: Float
    ): Boolean {
        val dLat = targetLat - userLat
        val dLon = targetLon - userLon
        val dLatMeters = dLat * METERS_PER_DEGREE_LAT
        val dLonMeters = dLon * METERS_PER_DEGREE_LAT * cos(Math.toRadians(userLat))
        val distance = kotlin.math.sqrt(dLatMeters * dLatMeters + dLonMeters * dLonMeters)
        return distance <= radiusMeters
    }
}
