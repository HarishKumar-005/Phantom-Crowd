package com.phantomcrowd.utils

import com.phantomcrowd.data.AnchorData
import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log

class OfflineMapCache(private val context: Context) {
    
    companion object {
        private const val TAG = "OfflineMapCache"
        private const val CACHE_FILE = "offline_issues.json"
    }
    
    private val cacheFile = File(context.cacheDir, CACHE_FILE)
    
    /**
     * Cache issues locally
     */
    fun cacheIssues(issues: List<AnchorData>) {
        try {
            val json = Json.encodeToString(issues)
            cacheFile.writeText(json)
            Log.d(TAG, "Cached ${issues.size} issues")
        } catch (e: Exception) {
            Log.e(TAG, "Cache write failed: ${e.message}")
        }
    }
    
    /**
     * Get cached issues
     */
    fun getCachedIssues(): List<AnchorData> {
        return try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                Json.decodeFromString<List<AnchorData>>(json)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache read failed: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        if (cacheFile.exists()) {
            cacheFile.delete()
            Log.d(TAG, "Cache cleared")
        }
    }
}
