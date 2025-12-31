package com.phantomcrowd.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.UUID

/**
 * Represents a surface-anchored message in AR space.
 * Uses GPS + relative offset for session persistence.
 * 
 * When placing (Session 1):
 * - Create local anchor on detected plane
 * - Get user GPS position
 * - Calculate offset: anchor position - GPS origin
 * - Save GPS + offset to Firestore
 * 
 * When loading (Session 2):
 * - Query Firestore by GPS radius
 * - Calculate world position: user GPS + stored offset
 * - Create new local anchor at position
 * - Message appears on same wall!
 */
data class SurfaceAnchor(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    
    @get:PropertyName("messageText")
    @set:PropertyName("messageText")
    var messageText: String = "",
    
    @get:PropertyName("category")
    @set:PropertyName("category")
    var category: String = "general",
    
    // GPS coordinates for proximity detection
    @get:PropertyName("latitude")
    @set:PropertyName("latitude")
    var latitude: Double = 0.0,
    
    @get:PropertyName("longitude")
    @set:PropertyName("longitude")
    var longitude: Double = 0.0,
    
    @get:PropertyName("geohash")
    @set:PropertyName("geohash")
    var geohash: String = "",
    
    // Relative offset from GPS origin (in meters)
    @get:PropertyName("relativeOffsetX")
    @set:PropertyName("relativeOffsetX")
    var relativeOffsetX: Float = 0f,
    
    @get:PropertyName("relativeOffsetY")
    @set:PropertyName("relativeOffsetY")
    var relativeOffsetY: Float = 0f,
    
    @get:PropertyName("relativeOffsetZ")
    @set:PropertyName("relativeOffsetZ")
    var relativeOffsetZ: Float = 0f,
    
    // Surface type: "HORIZONTAL_UPWARD_FACING", "HORIZONTAL_DOWNWARD_FACING", "VERTICAL"
    @get:PropertyName("planeType")
    @set:PropertyName("planeType")
    var planeType: String = "VERTICAL",
    
    // Surface normal direction (for orientation)
    @get:PropertyName("surfaceNormalX")
    @set:PropertyName("surfaceNormalX")
    var surfaceNormalX: Float = 0f,
    
    @get:PropertyName("surfaceNormalY")
    @set:PropertyName("surfaceNormalY")
    var surfaceNormalY: Float = 0f,
    
    @get:PropertyName("surfaceNormalZ")
    @set:PropertyName("surfaceNormalZ")
    var surfaceNormalZ: Float = 1f,
    
    // Metadata
    @get:PropertyName("timestamp")
    @set:PropertyName("timestamp")
    var timestamp: Long = System.currentTimeMillis(),
    
    @get:PropertyName("userId")
    @set:PropertyName("userId")
    var userId: String = "anonymous",
    
    @get:PropertyName("imageUrl")
    @set:PropertyName("imageUrl")
    var imageUrl: String? = null
) {
    /**
     * Convert to Firestore map for saving
     */
    fun toFirestoreMap(): Map<String, Any?> = hashMapOf(
        "messageText" to messageText,
        "category" to category,
        "latitude" to latitude,
        "longitude" to longitude,
        "geohash" to geohash,
        "relativeOffsetX" to relativeOffsetX,
        "relativeOffsetY" to relativeOffsetY,
        "relativeOffsetZ" to relativeOffsetZ,
        "planeType" to planeType,
        "surfaceNormalX" to surfaceNormalX,
        "surfaceNormalY" to surfaceNormalY,
        "surfaceNormalZ" to surfaceNormalZ,
        "timestamp" to timestamp,
        "userId" to userId,
        "imageUrl" to imageUrl
    )
    
    /**
     * Calculate distance to another location using Haversine formula
     */
    fun distanceTo(lat: Double, lng: Double): Float {
        val earthRadius = 6371000.0 // meters
        
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(lat)
        val deltaLat = Math.toRadians(lat - latitude)
        val deltaLng = Math.toRadians(lng - longitude)
        
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return (earthRadius * c).toFloat()
    }
    
    /**
     * Get offset as Triple
     */
    fun getOffset(): Triple<Float, Float, Float> = Triple(
        relativeOffsetX, relativeOffsetY, relativeOffsetZ
    )
    
    /**
     * Get surface normal as Triple
     */
    fun getSurfaceNormal(): Triple<Float, Float, Float> = Triple(
        surfaceNormalX, surfaceNormalY, surfaceNormalZ
    )
    
    companion object {
        const val COLLECTION_NAME = "surface_anchors"
        
        /**
         * Create from Firestore document
         */
        fun fromFirestore(id: String, data: Map<String, Any?>): SurfaceAnchor {
            return SurfaceAnchor(
                id = id,
                messageText = data["messageText"] as? String ?: "",
                category = data["category"] as? String ?: "general",
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                geohash = data["geohash"] as? String ?: "",
                relativeOffsetX = (data["relativeOffsetX"] as? Number)?.toFloat() ?: 0f,
                relativeOffsetY = (data["relativeOffsetY"] as? Number)?.toFloat() ?: 0f,
                relativeOffsetZ = (data["relativeOffsetZ"] as? Number)?.toFloat() ?: 0f,
                planeType = data["planeType"] as? String ?: "VERTICAL",
                surfaceNormalX = (data["surfaceNormalX"] as? Number)?.toFloat() ?: 0f,
                surfaceNormalY = (data["surfaceNormalY"] as? Number)?.toFloat() ?: 0f,
                surfaceNormalZ = (data["surfaceNormalZ"] as? Number)?.toFloat() ?: 1f,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                userId = data["userId"] as? String ?: "anonymous",
                imageUrl = data["imageUrl"] as? String
            )
        }
    }
}
