package com.phantomcrowd.data

import android.location.Location
import com.phantomcrowd.utils.Logger

/**
 * Repository to handle Anchor data operations.
 * Uses FirebaseAnchorManager for cloud sync plus local caching.
 */
class AnchorRepository(
    private val localStorageManager: LocalStorageManager,
    private val firebaseAnchorManager: FirebaseAnchorManager? = null
) {

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

    /**
     * Get nearby anchors - tries cloud first, falls back to local cache
     */
    suspend fun getNearbyAnchors(
        currentLat: Double,
        currentLon: Double,
        radiusMeters: Double
    ): List<AnchorData> {
        // Try to get from cloud first
        val cloudAnchors = try {
            firebaseAnchorManager?.getIssuesNearLocation(currentLat, currentLon, radiusMeters) ?: emptyList()
        } catch (e: Exception) {
            Logger.w(Logger.Category.DATA, "Failed to fetch cloud anchors, using local cache: ${e.message}")
            emptyList()
        }
        
        // If cloud has data, use it and cache locally
        if (cloudAnchors.isNotEmpty()) {
            Logger.d(Logger.Category.DATA, "Using ${cloudAnchors.size} cloud anchors")
            return cloudAnchors.sortedBy { anchor ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLat, currentLon,
                    anchor.latitude, anchor.longitude,
                    results
                )
                results[0]
            }
        }
        
        // Fall back to local storage
        val allAnchors = localStorageManager.loadAnchors()
        Logger.d(Logger.Category.DATA, "Using ${allAnchors.size} local anchors (cloud unavailable)")
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
    
    /**
     * Get all anchors - tries cloud first, falls back to local
     */
    suspend fun getAllAnchors(): List<AnchorData> {
        // Try cloud first
        val cloudAnchors = try {
            firebaseAnchorManager?.fetchAllIssues() ?: emptyList()
        } catch (e: Exception) {
            Logger.w(Logger.Category.DATA, "Failed to fetch all cloud anchors: ${e.message}")
            emptyList()
        }
        
        if (cloudAnchors.isNotEmpty()) {
            return cloudAnchors
        }
        
        return localStorageManager.loadAnchors()
    }
}
