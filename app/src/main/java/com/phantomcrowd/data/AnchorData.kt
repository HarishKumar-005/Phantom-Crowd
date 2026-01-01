package com.phantomcrowd.data

import java.util.UUID

import kotlinx.serialization.Serializable

/**
 * Data class representing an issue/message anchored at a specific geospatial location.
 * 
 * IMPORTANT: All fields MUST have default values for Firestore deserialization to work.
 * Firestore uses reflection and requires a no-argument constructor.
 */
@Serializable
data class AnchorData(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double = 0.0,      // DEFAULT VALUE REQUIRED for Firestore
    val longitude: Double = 0.0,     // DEFAULT VALUE REQUIRED for Firestore
    val altitude: Double = 0.0,
    val messageText: String = "",    // DEFAULT VALUE REQUIRED for Firestore
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "general", // "general", "facility", "safety"
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    // Cloud persistence fields (Phase A)
    val geohash: String = "",  // For efficient location queries in Firestore
    val cloudAnchorId: String = "",  // For cloud AR anchor persistence
    val upvotes: Int = 0,  // Community validation count
    // Phase F: Wall Overlay Posting (simplified - no photo)
    val wallAnchorId: String = "",  // Unique wall surface identifier
    
    // UI Redesign fields (Phase J) - All with defaults for backward compatibility
    val useCase: String = "",           // UseCase enum name (e.g., "WOMENS_SAFETY")
    val useCaseCategory: String = "",   // Category ID (e.g., "HARASSMENT")
    val severity: String = "MEDIUM",    // Severity enum name (URGENT, HIGH, MEDIUM, LOW)
    val locationName: String = "",      // Reverse geocoded or user entered location name
    val nearbyIssueCount: Int = 0,      // Calculated: count of nearby issues
    val status: String = ""             // Issue status (e.g., "PENDING", "RESOLVED", "IN_PROGRESS")
)

