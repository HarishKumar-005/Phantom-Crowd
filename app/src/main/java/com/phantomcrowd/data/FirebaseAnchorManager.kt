package com.phantomcrowd.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.phantomcrowd.utils.GeohashingUtility
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for Firebase Firestore operations.
 * Handles uploading, querying, and syncing issues with cloud storage.
 */
class FirebaseAnchorManager(private val firestore: FirebaseFirestore) {
    
    companion object {
        private const val ISSUES_COLLECTION = "issues"
        private const val TAG = "FirebaseAnchorManager"
    }
    
    /**
     * Upload a new issue to Firestore
     */
    suspend fun uploadIssue(anchor: AnchorData): Result<String> = try {
        val geohash = GeohashingUtility.encode(anchor.latitude, anchor.longitude)
        
        val enhancedAnchor = anchor.copy(
            geohash = geohash,
            timestamp = System.currentTimeMillis()
        )
        
        val docRef = firestore.collection(ISSUES_COLLECTION).document(anchor.id)
        docRef.set(enhancedAnchor).await()
        
        Log.d(TAG, "Uploaded issue ${anchor.id} with geohash $geohash")
        Result.success(anchor.id)
    } catch (e: Exception) {
        Log.e(TAG, "Upload failed: ${e.message}")
        Result.failure(e)
    }
    
    /**
     * Download issues nearby (radius search using geohashing)
     */
    fun getNearbyIssues(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5
    ): Flow<List<AnchorData>> = callbackFlow {
        try {
            val nearbyGeohashes = GeohashingUtility.getNearbyGeohashes(latitude, longitude, radiusKm)
            
            Log.d(TAG, "Searching in ${nearbyGeohashes.size} geohash cells")
            
            val listener = firestore
                .collection(ISSUES_COLLECTION)
                .whereIn("geohash", nearbyGeohashes)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Listen error: ${e.message}")
                        close(e)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        try {
                            val issues = snapshot.toObjects(AnchorData::class.java)
                            Log.d(TAG, "Found ${issues.size} nearby issues")
                            trySend(issues)
                        } catch (parseError: Exception) {
                            Log.e(TAG, "Parse error: ${parseError.message}")
                            close(parseError)
                        }
                    }
                }
            
            awaitClose { listener.remove() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Query error: ${e.message}")
            close(e)
        }
    }
    
    /**
     * Get all issues (no radius limit)
     */
    fun getAllIssues(): Flow<List<AnchorData>> = callbackFlow {
        try {
            val listener = firestore
                .collection(ISSUES_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Listen error: ${e.message}")
                        close(e)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        try {
                            val issues = snapshot.toObjects(AnchorData::class.java)
                            Log.d(TAG, "Loaded ${issues.size} total issues")
                            trySend(issues)
                        } catch (parseError: Exception) {
                            Log.e(TAG, "Parse error: ${parseError.message}")
                            close(parseError)
                        }
                    }
                }
            
            awaitClose { listener.remove() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Query error: ${e.message}")
            close(e)
        }
    }
    
    /**
     * Update issue (for upvotes, new cloud anchor ID, etc)
     */
    suspend fun updateIssue(issueId: String, updates: Map<String, Any>): Result<Unit> = try {
        firestore.collection(ISSUES_COLLECTION).document(issueId).update(updates).await()
        Log.d(TAG, "Updated issue $issueId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Update failed: ${e.message}")
        Result.failure(e)
    }
    
    /**
     * Delete issue
     */
    suspend fun deleteIssue(issueId: String): Result<Unit> = try {
        firestore.collection(ISSUES_COLLECTION).document(issueId).delete().await()
        Log.d(TAG, "Deleted issue $issueId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Delete failed: ${e.message}")
        Result.failure(e)
    }
    
    /**
     * Upvote an issue
     */
    suspend fun upvoteIssue(issueId: String): Result<Unit> {
        return try {
            firestore.collection(ISSUES_COLLECTION).document(issueId)
                .update("upvotes", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            Log.d(TAG, "Upvoted issue $issueId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Upvote failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * One-shot fetch of nearby issues (suspend, not Flow)
     */
    suspend fun getIssuesNearLocation(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<AnchorData> = suspendCancellableCoroutine { continuation ->
        val radiusKm = (radiusMeters / 1000.0).coerceAtLeast(1.0).toInt()
        val nearbyGeohashes = GeohashingUtility.getNearbyGeohashes(latitude, longitude, radiusKm)
        
        Log.d(TAG, "One-shot fetch: searching ${nearbyGeohashes.size} geohash cells")
        
        if (nearbyGeohashes.isEmpty()) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        
        firestore.collection(ISSUES_COLLECTION)
            .whereIn("geohash", nearbyGeohashes)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val issues = snapshot.toObjects(AnchorData::class.java)
                    Log.d(TAG, "One-shot: Found ${issues.size} nearby issues")
                    continuation.resume(issues)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    continuation.resume(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Query failed: ${e.message}")
                continuation.resume(emptyList())
            }
    }
    
    /**
     * One-shot fetch of all issues (suspend, not Flow)
     */
    suspend fun fetchAllIssues(): List<AnchorData> = suspendCancellableCoroutine { continuation ->
        firestore.collection(ISSUES_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val issues = snapshot.toObjects(AnchorData::class.java)
                    Log.d(TAG, "One-shot: Loaded ${issues.size} total issues")
                    continuation.resume(issues)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    continuation.resume(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Query failed: ${e.message}")
                continuation.resume(emptyList())
            }
    }
}

// Extension function for Firebase Task to coroutine
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}

