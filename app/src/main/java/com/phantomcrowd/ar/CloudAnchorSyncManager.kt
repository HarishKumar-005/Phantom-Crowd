package com.phantomcrowd.ar

import com.google.ar.core.Anchor
import com.google.ar.core.Session
import com.google.firebase.firestore.FirebaseFirestore
import com.phantomcrowd.data.AnchorData
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import android.util.Log

class CloudAnchorSyncManager(
    private val firestore: FirebaseFirestore,
    private val session: Session
) {
    
    companion object {
        private const val TAG = "CloudAnchorSyncManager"
    }
    
    /**
     * Host cloud anchor at user's location.
     * Uses polling to wait for ARCore async operation.
     */
    suspend fun hostCloudAnchor(
        localAnchor: Anchor,
        anchorData: AnchorData
    ): Result<String> {
        Log.d(TAG, "Hosting cloud anchor for ${anchorData.id}...")
        
        // Host the anchor
        // Note: hostCloudAnchor returns a NEW anchor that is being hosted.
        // The original localAnchor is still valid but the new one carries the cloud state.
        val cloudAnchor = session.hostCloudAnchor(localAnchor)
        
        // Poll for result
        val resultAnchor = waitForAnchorResult(cloudAnchor)
        
        if (resultAnchor.cloudAnchorState == Anchor.CloudAnchorState.SUCCESS) {
             val cloudId = resultAnchor.cloudAnchorId
             Log.d(TAG, "✅ Cloud anchor hosted successfully: $cloudId")
             
             // Update Firestore
             return try {
                 firestore.collection("issues")
                    .document(anchorData.id)
                    .update("cloudAnchorId", cloudId)
                    .await()
                 Result.success(cloudId)
             } catch (e: Exception) {
                 Log.e(TAG, "Firestore update failed: ${e.message}")
                 Result.failure(e)
             }
        } else {
             Log.e(TAG, "❌ Cloud anchor hosting failed: ${resultAnchor.cloudAnchorState}")
             return Result.failure(Exception("Hosting failed: ${resultAnchor.cloudAnchorState}"))
        }
    }
    
    /**
     * Resolve cloud anchor by ID
     */
    suspend fun resolveCloudAnchor(cloudAnchorId: String): Result<Anchor?> {
        Log.d(TAG, "Resolving cloud anchor: $cloudAnchorId")
        
        val cloudAnchor = session.resolveCloudAnchor(cloudAnchorId)
        val resultAnchor = waitForAnchorResult(cloudAnchor)
        
        if (resultAnchor.cloudAnchorState == Anchor.CloudAnchorState.SUCCESS) {
            Log.d(TAG, "✅ Cloud anchor resolved")
            return Result.success(resultAnchor)
        } else {
            Log.w(TAG, "Cloud anchor resolution failed: ${resultAnchor.cloudAnchorState}")
            return Result.success(null) 
        }
    }
    
    /**
     * Polls the anchor state until it finishes or times out.
     */
    private suspend fun waitForAnchorResult(anchor: Anchor): Anchor {
        var timeout = 0
        // Wait up to 30 seconds (60 * 500ms)
        while (anchor.cloudAnchorState == Anchor.CloudAnchorState.TASK_IN_PROGRESS && timeout < 60) {
            delay(500)
            timeout++
        }
        return anchor
    }
}
