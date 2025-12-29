package com.phantomcrowd.data

import java.util.UUID

/**
 * Data class representing an issue/message anchored at a specific geospatial location.
 */
data class AnchorData(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "general", // "general", "facility", "safety"
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f
)
