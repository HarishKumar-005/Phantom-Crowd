package com.phantomcrowd.data

import android.location.Location

/**
 * Repository to handle Anchor data operations.
 */
class AnchorRepository(private val localStorageManager: LocalStorageManager) {

    suspend fun createAnchor(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        message: String,
        category: String
    ): AnchorData {
        val newAnchor = AnchorData(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            messageText = message,
            category = category
        )
        localStorageManager.saveAnchor(newAnchor)
        return newAnchor
    }

    suspend fun getNearbyAnchors(
        currentLat: Double,
        currentLon: Double,
        radiusMeters: Double
    ): List<AnchorData> {
        val allAnchors = localStorageManager.loadAnchors()
        return allAnchors.filter { anchor ->
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLat, currentLon,
                anchor.latitude, anchor.longitude,
                results
            )
            results[0] <= radiusMeters
        }.sortedBy { anchor ->
             val results = FloatArray(1)
            Location.distanceBetween(
                currentLat, currentLon,
                anchor.latitude, anchor.longitude,
                results
            )
            results[0]
        }
    }
    
    suspend fun getAllAnchors(): List<AnchorData> {
        return localStorageManager.loadAnchors()
    }
}
