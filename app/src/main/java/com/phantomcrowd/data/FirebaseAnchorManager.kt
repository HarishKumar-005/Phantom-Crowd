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
        private const val AUTHORITY_ACTIONS_COLLECTION = "authority_actions"
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
     * Fetches from BOTH issues and surface_anchors collections and merges results
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
        
        // Track completion of both queries
        var issuesResult: List<AnchorData>? = null
        var surfaceAnchorsResult: List<AnchorData>? = null
        
        fun tryComplete() {
            val issues = issuesResult
            val surfaces = surfaceAnchorsResult
            if (issues != null && surfaces != null) {
                val merged = issues + surfaces
                Log.d(TAG, "One-shot: Merged ${issues.size} issues + ${surfaces.size} surface anchors = ${merged.size} total")
                continuation.resume(merged)
            }
        }
        
        // Query issues collection
        firestore.collection(ISSUES_COLLECTION)
            .whereIn("geohash", nearbyGeohashes)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    issuesResult = snapshot.toObjects(AnchorData::class.java)
                    Log.d(TAG, "One-shot: Found ${issuesResult?.size ?: 0} issues")
                    tryComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (issues): ${e.message}")
                    issuesResult = emptyList()
                    tryComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Query failed (issues): ${e.message}")
                issuesResult = emptyList()
                tryComplete()
            }
        
        // Query surface_anchors collection and convert to AnchorData
        firestore.collection(SurfaceAnchor.COLLECTION_NAME)
            .whereIn("geohash", nearbyGeohashes)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val surfaceAnchors = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val surfaceAnchor = SurfaceAnchor.fromFirestore(doc.id, data)
                            // Convert SurfaceAnchor to AnchorData for unified display
                            AnchorData(
                                id = "surface_${surfaceAnchor.id}",
                                messageText = surfaceAnchor.messageText,
                                category = surfaceAnchor.category,
                                latitude = surfaceAnchor.latitude,
                                longitude = surfaceAnchor.longitude,
                                altitude = 0.0,
                                geohash = surfaceAnchor.geohash,
                                timestamp = surfaceAnchor.timestamp,
                                cloudAnchorId = "",
                                wallAnchorId = "surface_${surfaceAnchor.id}",
                                upvotes = 0,
                                rotationX = 0f,
                                rotationY = 0f,
                                rotationZ = 0f
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting surface anchor: ${e.message}")
                            null
                        }
                    }
                    surfaceAnchorsResult = surfaceAnchors
                    Log.d(TAG, "One-shot: Found ${surfaceAnchors.size} surface anchors")
                    tryComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error (surface anchors): ${e.message}")
                    surfaceAnchorsResult = emptyList()
                    tryComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Query failed (surface anchors): ${e.message}")
                surfaceAnchorsResult = emptyList()
                tryComplete()
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

    // ═══════════════════════════════════════════════════════════════════════
    // Impact Dashboard: Global real-time listeners (not location-based)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe ALL issues, surface_anchors, and authority_actions globally
     * in real-time and compute [ImpactStats] for the Impact Dashboard.
     *
     * Uses addSnapshotListener (real-time stream) so the dashboard updates live.
     * limit(500) for issues/surface_anchors, limit(200) for authority_actions.
     */
    fun observeGlobalImpactData(): Flow<ImpactStats> = callbackFlow {
        // Mutable holders for the latest snapshot from each collection
        var latestIssues: List<AnchorData> = emptyList()
        var latestSurfaceAnchors: List<AnchorData> = emptyList()
        var latestActions: List<AuthorityAction> = emptyList()

        // Helper: recompute stats and emit whenever any collection changes
        fun recompute() {
            try {
                val allReports = latestIssues + latestSurfaceAnchors
                val stats = computeImpactStats(allReports, latestActions)
                trySend(stats)
            } catch (e: Exception) {
                Log.e(TAG, "Error computing impact stats: ${e.message}")
            }
        }

        // ─── Listener 1: issues ──────────────────────────────────────
        val issuesListener = firestore.collection(ISSUES_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Impact: issues listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    latestIssues = try {
                        snapshot.toObjects(AnchorData::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Impact: issues parse error: ${e.message}")
                        emptyList()
                    }
                    Log.d(TAG, "Impact: ${latestIssues.size} issues")
                    recompute()
                }
            }

        // ─── Listener 2: surface_anchors ─────────────────────────────
        val anchorsListener = firestore.collection(SurfaceAnchor.COLLECTION_NAME)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Impact: surface_anchors listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    latestSurfaceAnchors = try {
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                AnchorData(
                                    id = "surface_${doc.id}",
                                    messageText = data["messageText"] as? String ?: "",
                                    category = data["category"] as? String ?: "general",
                                    latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                                    longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                                    altitude = 0.0,
                                    geohash = data["geohash"] as? String ?: "",
                                    timestamp = getTimestampMs(data["timestamp"]),
                                    status = data["status"] as? String ?: "PENDING",
                                    severity = data["severity"] as? String ?: "MEDIUM",
                                    useCase = data["useCase"] as? String ?: data["category"] as? String ?: "general",
                                    locationName = data["locationName"] as? String ?: ""
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Impact: surface anchor parse error: ${e.message}")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Impact: surface_anchors error: ${e.message}")
                        emptyList()
                    }
                    Log.d(TAG, "Impact: ${latestSurfaceAnchors.size} surface anchors")
                    recompute()
                }
            }

        // ─── Listener 3: authority_actions ───────────────────────────
        val actionsListener = firestore.collection(AUTHORITY_ACTIONS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Impact: authority_actions listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    latestActions = try {
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                AuthorityAction(
                                    id = doc.id,
                                    issueId = data["issueId"] as? String ?: "",
                                    actionType = data["actionType"] as? String ?: "",
                                    adminEmail = data["adminEmail"] as? String ?: "",
                                    adminUid = data["adminUid"] as? String ?: "",
                                    notes = data["notes"] as? String ?: "",
                                    timestamp = getTimestampMs(data["timestamp"])
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Impact: action parse error: ${e.message}")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Impact: authority_actions error: ${e.message}")
                        emptyList()
                    }
                    Log.d(TAG, "Impact: ${latestActions.size} authority actions")
                    recompute()
                }
            }

        awaitClose {
            issuesListener.remove()
            anchorsListener.remove()
            actionsListener.remove()
            Log.d(TAG, "Impact: all listeners removed")
        }
    }

    /**
     * Compute [ImpactStats] from the merged list of all reports
     * and all authority actions.
     */
    private fun computeImpactStats(
        allReports: List<AnchorData>,
        allActions: List<AuthorityAction>
    ): ImpactStats {
        val total = allReports.size

        // Status helper: read status directly from document, default PENDING
        fun AnchorData.realStatus(): String {
            val s = status.uppercase().ifEmpty { "PENDING" }
            return if (s in listOf("PENDING", "IN_PROGRESS", "RESOLVED", "REJECTED")) s else "PENDING"
        }

        val fixed = allReports.count { it.realStatus() == "RESOLVED" }
        val inProgress = allReports.count { it.realStatus() == "IN_PROGRESS" }

        // Red zones: group by lat/lon (3 decimal places), count ≥ 5
        val locationCounts = allReports.groupBy {
            "%.3f,%.3f".format(it.latitude, it.longitude)
        }
        val redZones = locationCounts.count { it.value.size >= 5 }

        // Actions indexed by issueId for fast lookup
        val actionsByIssue = allActions.groupBy { it.issueId }

        // ─── Per-category breakdown ──────────────────────────────────
        // Normalise: always roll up to one of the 6 UseCase parent names.
        // Priority: useCase field → subcategory reverse lookup → category reverse lookup → GENERAL
        fun AnchorData.categoryKey(): String {
            // 1. If useCase is set and is a valid UseCase name, use it
            val uc = useCase.uppercase().trim()
            if (uc.isNotEmpty() && uc in categoryDisplayNames) return uc

            // 2. Try reverse-mapping the category (subcategory) to its parent UseCase
            val cat = category.uppercase().trim()
            subcategoryToUseCase[cat]?.let { return it }

            // 3. If category itself is a UseCase name, use it
            if (cat in categoryDisplayNames) return cat

            // 4. Try reverse-mapping useCase field as a subcategory
            if (uc.isNotEmpty()) subcategoryToUseCase[uc]?.let { return it }

            return "GENERAL"
        }

        val grouped = allReports.groupBy { it.categoryKey() }

        val breakdowns = grouped.map { (key, reports) ->
            val display = categoryDisplayNames[key]
            val catFixed = reports.count { it.realStatus() == "RESOLVED" }
            val catPending = reports.count { it.realStatus() == "PENDING" }
            val catInProgress = reports.count { it.realStatus() == "IN_PROGRESS" }
            val catRejected = reports.count { it.realStatus() == "REJECTED" }
            val catTotal = reports.size

            // Top hotspots
            val hotspots = reports
                .filter { it.locationName.isNotEmpty() }
                .groupBy { it.locationName }
                .entries
                .sortedByDescending { it.value.size }
                .take(3)
                .map { "${it.key} (${it.value.size})" }

            // Real admin actions for this category's issues
            val issueIds = reports.map { it.id }.toSet()
            val catActions = allActions
                .filter { it.issueId in issueIds }
                .sortedByDescending { it.timestamp }
                .take(5)

            CategoryBreakdown(
                category = key,
                displayName = display?.first ?: key.lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " "),
                icon = display?.second ?: "📋",
                total = catTotal,
                fixed = catFixed,
                pending = catPending,
                inProgress = catInProgress,
                rejected = catRejected,
                resolutionRate = if (catTotal > 0) catFixed.toFloat() / catTotal else 0f,
                topHotspots = hotspots,
                recentActions = catActions
            )
        }.sortedByDescending { it.total }

        // ─── Success stories from real resolved issues ───────────────
        val resolvedActions = allActions
            .filter { it.actionType.uppercase() == "RESOLVED" }
            .sortedByDescending { it.timestamp }
            .take(10)

        val reportMap = allReports.associateBy { it.id }

        val stories = resolvedActions.mapNotNull { action ->
            // Try to find the issue — the action.issueId might be a plain id or surface_ prefixed
            val issue = reportMap[action.issueId]
                ?: reportMap["surface_${action.issueId}"]
            if (issue != null) {
                val catKey = issue.useCase.uppercase().ifEmpty { issue.category.uppercase() }
                val display = categoryDisplayNames[catKey]
                SuccessStory(
                    title = "${display?.second ?: "✨"} ${display?.first ?: catKey} issue resolved",
                    description = action.notes.ifEmpty { issue.messageText },
                    category = catKey,
                    resolvedAt = action.timestamp
                )
            } else {
                // No matching issue found; still show the action
                SuccessStory(
                    title = "✨ Issue resolved",
                    description = action.notes.ifEmpty { "Resolved by ${action.adminEmail}" },
                    category = "",
                    resolvedAt = action.timestamp
                )
            }
        }

        return ImpactStats(
            totalReports = total,
            issuesFixed = fixed,
            issuesInProgress = inProgress,
            redZones = redZones,
            estimatedReach = total * 100,
            categoryBreakdowns = breakdowns,
            successStories = stories,
            allActions = allActions,
            lastSyncedMs = System.currentTimeMillis()
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // One-time migration: backfill status/severity on existing surface_anchors
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * One-time migration that adds "status" and "severity" fields
     * to any surface_anchors documents that are missing them.
     *
     * Safe to call multiple times — only updates docs with missing fields.
     * Uses a batch write for atomicity and efficiency.
     *
     * @return Number of documents updated.
     */
    suspend fun backfillSurfaceAnchors(): Int {
        return try {
            val snapshot = firestore.collection(SurfaceAnchor.COLLECTION_NAME)
                .get()
                .await()

            val batch = firestore.batch()
            var count = 0

            for (doc in snapshot.documents) {
                val updates = mutableMapOf<String, Any>()
                if (doc.getString("status") == null) {
                    updates["status"] = "PENDING"
                }
                if (doc.getString("severity") == null) {
                    updates["severity"] = "MEDIUM"
                }
                if (updates.isNotEmpty()) {
                    batch.update(doc.reference, updates)
                    count++
                }
            }

            if (count > 0) {
                batch.commit().await()
                Log.d(TAG, "Backfill: updated $count surface_anchors with missing status/severity")
            } else {
                Log.d(TAG, "Backfill: all surface_anchors already have status/severity")
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Backfill failed: ${e.message}")
            -1
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

