package com.phantomcrowd.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages reading and writing AnchorData list to a local JSON file.
 */
class LocalStorageManager(private val context: Context) {

    private val gson = Gson()
    private val fileName = "anchors.json"

    // Wrapper class for JSON structure
    private data class AnchorListWrapper(val anchors: List<AnchorData>)

    private fun getFile(): File {
        return File(context.filesDir, fileName)
    }

    suspend fun saveAnchor(anchor: AnchorData) {
        withContext(Dispatchers.IO) {
            val currentList = loadAnchors().toMutableList()
            currentList.add(anchor)
            saveList(currentList)
        }
    }
    
    suspend fun saveAnchors(anchors: List<AnchorData>) {
         withContext(Dispatchers.IO) {
            saveList(anchors)
        }
    }

    suspend fun loadAnchors(): List<AnchorData> {
        return withContext(Dispatchers.IO) {
            val file = getFile()
            if (!file.exists()) {
                return@withContext emptyList()
            }
            try {
                FileReader(file).use { reader ->
                    val type = object : TypeToken<AnchorListWrapper>() {}.type
                    val wrapper: AnchorListWrapper? = gson.fromJson(reader, type)
                    wrapper?.anchors ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun saveList(list: List<AnchorData>) {
        try {
            val file = getFile()
            val wrapper = AnchorListWrapper(list)
            FileWriter(file).use { writer ->
                gson.toJson(wrapper, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
