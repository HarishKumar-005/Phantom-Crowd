package com.phantomcrowd.utils

import android.util.Log

/**
 * Utility for converting GPS coordinates to geohash strings.
 * Geohashing enables efficient radius queries in Firestore.
 * Precision 7 = ~150m accuracy, suitable for ~5km radius searches.
 */
object GeohashingUtility {
    
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val GEOHASH_LENGTH = 7  // ~150m accuracy
    
    /**
     * Convert latitude/longitude to geohash
     * Returns string like "9q8yy9m"
     */
    fun encode(latitude: Double, longitude: Double): String {
        var lat = latitude
        var lon = longitude
        var idx = 0
        var bit = 0
        var evenBit = true
        val geohash = StringBuilder()
        
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        
        while (geohash.length < GEOHASH_LENGTH) {
            when {
                evenBit -> {
                    // longitude
                    val lonMid = (lonMin + lonMax) / 2
                    if (lon > lonMid) {
                        idx = (idx shl 1) + 1
                        lonMin = lonMid
                    } else {
                        idx = idx shl 1
                        lonMax = lonMid
                    }
                }
                else -> {
                    // latitude
                    val latMid = (latMin + latMax) / 2
                    if (lat > latMid) {
                        idx = (idx shl 1) + 1
                        latMin = latMid
                    } else {
                        idx = idx shl 1
                        latMax = latMid
                    }
                }
            }
            
            evenBit = !evenBit
            bit++
            
            if (bit == 5) {
                geohash.append(BASE32[idx])
                bit = 0
                idx = 0
            }
        }
        
        Log.d("GeohashingUtility", "Encoded ($latitude, $longitude) → ${geohash.toString()}")
        return geohash.toString()
    }
    
    /**
     * Get nearby geohashes for radius search
     * Returns geohashes within ~150m at precision 7
     */
    fun getNearbyGeohashes(latitude: Double, longitude: Double, radiusKm: Int = 5): List<String> {
        val centerGeohash = encode(latitude, longitude)
        val nearby = mutableListOf(centerGeohash)
        
        // For 5km search, check neighboring cells
        val offsets = listOf(
            Pair(0.1, 0.0),   // North
            Pair(-0.1, 0.0),  // South
            Pair(0.0, 0.1),   // East
            Pair(0.0, -0.1),  // West
            Pair(0.1, 0.1),   // NE
            Pair(0.1, -0.1),  // NW
            Pair(-0.1, 0.1),  // SE
            Pair(-0.1, -0.1)  // SW
        )
        
        offsets.forEach { (latOffset, lonOffset) ->
            val neighborGeohash = encode(latitude + latOffset, longitude + lonOffset)
            if (!nearby.contains(neighborGeohash)) {
                nearby.add(neighborGeohash)
            }
        }
        
        Log.d("GeohashingUtility", "Found ${nearby.size} nearby geohashes for radius $radiusKm km")
        return nearby
    }
    
    /**
     * Decode geohash back to lat/lon bounds (for debugging)
     */
    fun decode(geohash: String): Pair<Double, Double> {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var evenBit = true
        
        geohash.forEach { char ->
            val idx = BASE32.indexOf(char)
            
            for (i in 4 downTo 0) {
                val bit = (idx shr i) and 1
                
                if (evenBit) {
                    val lonMid = (lonMin + lonMax) / 2
                    if (bit == 1) lonMin = lonMid else lonMax = lonMid
                } else {
                    val latMid = (latMin + latMax) / 2
                    if (bit == 1) latMin = latMid else latMax = latMid
                }
                
                evenBit = !evenBit
            }
        }
        
        val centerLat = (latMin + latMax) / 2
        val centerLon = (lonMin + lonMax) / 2
        
        Log.d("GeohashingUtility", "Decoded $geohash → ($centerLat, $centerLon)")
        return Pair(centerLat, centerLon)
    }
}
