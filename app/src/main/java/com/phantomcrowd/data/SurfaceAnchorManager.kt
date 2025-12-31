package com.phantomcrowd.data

import android.location.Location
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.phantomcrowd.utils.GeohashingUtility
import com.phantomcrowd.utils.Logger
import kotlinx.coroutines.tasks.await

/**
 * Manages surface anchors - saving to and loading from Firestore.
 * Uses GPS + relative offset approach for session persistence.
 * 
 * 100% FREE - uses local ARCore anchors only (no Cloud Anchors)
 */
object SurfaceAnchorManager {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    /**
     * Save a new surface anchor to Firestore.
     * Called when user places a message on a detected surface.
     * 
     * @param messageText The message content
     * @param category Message category (safety, facility, etc)
     * @param location User's current GPS location
     * @param anchorPose The AR anchor's pose in world space
     * @param planeType The type of surface (wall, floor, ceiling)
     * @param surfaceNormal Normal vector of the surface
     * @return Result with anchor ID on success
     */
    suspend fun saveAnchor(
        messageText: String,
        category: String,
        location: Location,
        anchorPose: Pose,
        planeType: Plane.Type,
        surfaceNormal: FloatArray
    ): Result<String> {
        return try {
            val geohash = GeohashingUtility.encode(location.latitude, location.longitude)
            
            // Calculate relative offset from GPS origin
            // This offset will be used to recreate the anchor in future sessions
            val offset = calculateOffset(anchorPose)
            
            val anchor = SurfaceAnchor(
                messageText = messageText,
                category = category,
                latitude = location.latitude,
                longitude = location.longitude,
                geohash = geohash,
                relativeOffsetX = offset.first,
                relativeOffsetY = offset.second,
                relativeOffsetZ = offset.third,
                planeType = planeType.name,
                surfaceNormalX = surfaceNormal.getOrElse(0) { 0f },
                surfaceNormalY = surfaceNormal.getOrElse(1) { 0f },
                surfaceNormalZ = surfaceNormal.getOrElse(2) { 1f },
                timestamp = System.currentTimeMillis()
            )
            
            val docRef = firestore.collection(SurfaceAnchor.COLLECTION_NAME)
                .add(anchor.toFirestoreMap())
                .await()
            
            Logger.i(Logger.Category.AR, "Surface anchor saved: ${docRef.id} at (${location.latitude}, ${location.longitude})")
            Result.success(docRef.id)
            
        } catch (e: Exception) {
            Logger.e(Logger.Category.AR, "Failed to save surface anchor", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate relative offset from anchor pose.
     * AR sessions start with camera at origin (0,0,0).
     * We store the anchor's position relative to this origin.
     */
    fun calculateOffset(anchorPose: Pose): Triple<Float, Float, Float> {
        return Triple(
            anchorPose.tx(),  // X offset (left/right)
            anchorPose.ty(),  // Y offset (up/down)
            anchorPose.tz()   // Z offset (forward/backward)
        )
    }
    
    /**
     * Calculate world position from stored anchor data.
     * Creates a Pose that can be used to position content in AR.
     */
    fun calculateAnchorWorldPosition(anchor: SurfaceAnchor): Pose {
        val translation = floatArrayOf(
            anchor.relativeOffsetX,
            anchor.relativeOffsetY,
            anchor.relativeOffsetZ
        )
        
        // Create rotation from surface normal (face toward camera)
        val rotation = createRotationFromNormal(anchor.getSurfaceNormal())
        
        return Pose(translation, rotation)
    }
    
    /**
     * Create quaternion rotation from surface normal.
     * Makes content face outward from the surface.
     */
    private fun createRotationFromNormal(normal: Triple<Float, Float, Float>): FloatArray {
        // Simple identity rotation for now - can be enhanced
        // For walls (Z normal), content faces the viewer
        return floatArrayOf(0f, 0f, 0f, 1f)
    }
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000.0 // meters
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return (earthRadius * c).toFloat()
    }
    
    /**
     * Load nearby surface anchors from Firestore (one-shot).
     * Uses geohash-based query for efficiency.
     */
    suspend fun loadNearbyAnchors(
        location: Location,
        radiusMeters: Float = 100f
    ): List<SurfaceAnchor> {
        return try {
            val nearbyHashes = GeohashingUtility.getNearbyGeohashes(
                location.latitude,
                location.longitude,
                (radiusMeters / 1000).toInt().coerceAtLeast(1)
            )
            
            Logger.d(Logger.Category.AR, "Querying ${nearbyHashes.size} geohash cells for surface anchors")
            
            val anchors = mutableListOf<SurfaceAnchor>()
            
            for (hash in nearbyHashes) {
                val docs = firestore.collection(SurfaceAnchor.COLLECTION_NAME)
                    .whereGreaterThanOrEqualTo("geohash", hash)
                    .whereLessThan("geohash", hash + "\uf8ff")
                    .get()
                    .await()
                
                for (doc in docs) {
                    try {
                        val anchor = SurfaceAnchor.fromFirestore(doc.id, doc.data)
                        val distance = anchor.distanceTo(location.latitude, location.longitude)
                        if (distance <= radiusMeters) {
                            anchors.add(anchor)
                        }
                    } catch (e: Exception) {
                        Logger.w(Logger.Category.AR, "Failed to parse anchor: ${doc.id} - ${e.message}")
                    }
                }
            }
            
            Logger.i(Logger.Category.AR, "Loaded ${anchors.size} surface anchors within ${radiusMeters}m")
            anchors.sortedBy { it.distanceTo(location.latitude, location.longitude) }.take(20)
            
        } catch (e: Exception) {
            Logger.e(Logger.Category.AR, "Failed to load surface anchors", e)
            emptyList()
        }
    }
    
    /**
     * Listen to nearby anchors in real-time.
     * Returns ListenerRegistration that should be removed when done.
     */
    fun listenToNearbyAnchors(
        location: Location,
        radiusMeters: Float = 100f,
        onAnchorsUpdated: (List<SurfaceAnchor>) -> Unit
    ): ListenerRegistration {
        val geohash = GeohashingUtility.encode(location.latitude, location.longitude)
        val prefix = geohash.take(5) // 5-char prefix for ~5km radius
        
        return firestore.collection(SurfaceAnchor.COLLECTION_NAME)
            .whereGreaterThanOrEqualTo("geohash", prefix)
            .whereLessThan("geohash", prefix + "\uf8ff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.e(Logger.Category.AR, "Listen error", error)
                    return@addSnapshotListener
                }
                
                val anchors = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val anchor = SurfaceAnchor.fromFirestore(doc.id, doc.data ?: return@mapNotNull null)
                        val distance = anchor.distanceTo(location.latitude, location.longitude)
                        if (distance <= radiusMeters) anchor else null
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedBy { it.distanceTo(location.latitude, location.longitude) }?.take(20)
                    ?: emptyList()
                
                onAnchorsUpdated(anchors)
            }
    }
    
    /**
     * Get display name for plane type.
     */
    fun getPlaneTypeDisplayName(planeType: String): String {
        return when (planeType) {
            "HORIZONTAL_UPWARD_FACING" -> "Floor/Table"
            "HORIZONTAL_DOWNWARD_FACING" -> "Ceiling"
            "VERTICAL" -> "Wall"
            else -> "Surface"
        }
    }
}
