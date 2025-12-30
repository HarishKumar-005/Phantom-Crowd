package com.phantomcrowd.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.phantomcrowd.utils.DebugConfig
import com.phantomcrowd.utils.Logger
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages reading and writing AnchorData list to a local JSON file.
 * Includes robust error handling and backup of corrupt files.
 */
class LocalStorageManager(private val context: Context) {

    private val gson = Gson()
    private val fileName = "anchors.json"

    // Wrapper class for JSON structure
    private data class AnchorListWrapper(val anchors: List<AnchorData>)

    private fun getFile(): File {
        return File(context.filesDir, fileName)
    }

    /**
     * Save a single anchor to storage (appends to existing list).
     */
    suspend fun saveAnchor(anchor: AnchorData) {
        withContext(Dispatchers.IO) {
            try {
                val currentList = loadAnchors().toMutableList()
                currentList.add(anchor)
                saveList(currentList)
                if (DebugConfig.LOG_ANCHOR_OPERATIONS) {
                    Logger.d(Logger.Category.DATA, "Saved anchor: ${anchor.id} at (${anchor.latitude}, ${anchor.longitude})")
                }
            } catch (e: Exception) {
                Logger.e(Logger.Category.DATA, "Failed to save anchor: ${anchor.id}", e)
                throw e
            }
        }
    }

    /**
     * Save a list of anchors to storage (replaces existing list).
     */
    suspend fun saveAnchors(anchors: List<AnchorData>) {
        withContext(Dispatchers.IO) {
            try {
                saveList(anchors)
                if (DebugConfig.LOG_ANCHOR_OPERATIONS) {
                    Logger.d(Logger.Category.DATA, "Saved ${anchors.size} anchors to storage")
                }
            } catch (e: Exception) {
                Logger.e(Logger.Category.DATA, "Failed to save anchors list", e)
                throw e
            }
        }
    }

    /**
     * Load all anchors from storage.
     * Returns empty list if file doesn't exist or is corrupt.
     */
    suspend fun loadAnchors(): List<AnchorData> {
        return withContext(Dispatchers.IO) {
            val file = getFile()
            
            if (!file.exists()) {
                if (DebugConfig.LOG_ANCHOR_OPERATIONS) {
                    Logger.d(Logger.Category.DATA, "Anchors file doesn't exist, returning empty list")
                }
                return@withContext emptyList()
            }
            
            try {
                FileReader(file).use { reader ->
                    val type = object : TypeToken<AnchorListWrapper>() {}.type
                    val wrapper: AnchorListWrapper? = gson.fromJson(reader, type)
                    val anchors = wrapper?.anchors ?: emptyList()
                    
                    if (DebugConfig.LOG_ANCHOR_OPERATIONS) {
                        Logger.d(Logger.Category.DATA, "Loaded ${anchors.size} anchors from storage")
                    }
                    anchors
                }
            } catch (e: JsonSyntaxException) {
                Logger.e(Logger.Category.DATA, "Invalid JSON in anchors file - backing up and resetting", e)
                backupCorruptFile(file)
                emptyList()
            } catch (e: Exception) {
                Logger.e(Logger.Category.DATA, "Error loading anchors", e)
                emptyList()
            }
        }
    }

    /**
     * Delete all anchors from storage.
     */
    suspend fun clearAllAnchors() {
        withContext(Dispatchers.IO) {
            try {
                val file = getFile()
                if (file.exists()) {
                    file.delete()
                    Logger.d(Logger.Category.DATA, "Cleared all anchors from storage")
                }
            } catch (e: Exception) {
                Logger.e(Logger.Category.DATA, "Failed to clear anchors", e)
            }
        }
    }

    /**
     * Get the count of stored anchors without loading full data.
     */
    suspend fun getAnchorCount(): Int {
        return loadAnchors().size
    }

    private fun saveList(list: List<AnchorData>) {
        try {
            val file = getFile()
            val wrapper = AnchorListWrapper(list)
            FileWriter(file).use { writer ->
                gson.toJson(wrapper, writer)
            }
        } catch (e: Exception) {
            Logger.e(Logger.Category.DATA, "Error writing anchors file", e)
            throw e
        }
    }

    /**
     * Backup a corrupt file before deleting it.
     * This preserves data for debugging while allowing the app to continue.
     */
    private fun backupCorruptFile(file: File) {
        try {
            val timestamp = System.currentTimeMillis()
            val backupFile = File(file.parent, "anchors_backup_$timestamp.json")
            file.copyTo(backupFile, overwrite = true)
            file.delete()
            Logger.w(Logger.Category.DATA, "Corrupt file backed up to: ${backupFile.name}")
        } catch (e: Exception) {
            Logger.e(Logger.Category.DATA, "Failed to backup corrupt file - deleting", e)
            try {
                file.delete()
            } catch (deleteError: Exception) {
                Logger.e(Logger.Category.DATA, "Failed to delete corrupt file", deleteError)
            }
        }
    }
}
