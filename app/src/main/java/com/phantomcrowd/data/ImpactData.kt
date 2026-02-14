package com.phantomcrowd.data

import com.google.firebase.Timestamp

/**
 * Data models for the Impact Dashboard.
 * These represent aggregated statistics computed from live Firestore data
 * across the issues, surface_anchors, and authority_actions collections.
 */

/**
 * A single authority/admin action taken on an issue.
 * Mirrors the Firestore `authority_actions/{actionId}` document.
 */
data class AuthorityAction(
    val id: String = "",
    val issueId: String = "",
    val actionType: String = "",   // "IN_PROGRESS", "RESOLVED", "REJECTED"
    val adminEmail: String = "",
    val adminUid: String = "",
    val notes: String = "",
    val timestamp: Long = 0L
)

/**
 * Breakdown of statistics for a single category (UseCase).
 */
data class CategoryBreakdown(
    val category: String = "",        // UseCase enum name or surface_anchor category
    val displayName: String = "",     // Human-readable label
    val icon: String = "",            // Emoji icon
    val total: Int = 0,
    val fixed: Int = 0,
    val pending: Int = 0,
    val inProgress: Int = 0,
    val rejected: Int = 0,
    val resolutionRate: Float = 0f,
    val topHotspots: List<String> = emptyList(),       // Top 3 location names
    val recentActions: List<AuthorityAction> = emptyList()  // Real admin actions
)

/**
 * A resolved issue shown in the "Success Stories" section.
 */
data class SuccessStory(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val resolvedAt: Long = 0L
)

/**
 * Aggregated impact statistics for the entire dashboard.
 * Computed from live Firestore data.
 */
data class ImpactStats(
    val totalReports: Int = 0,
    val issuesFixed: Int = 0,
    val issuesInProgress: Int = 0,
    val redZones: Int = 0,
    val estimatedReach: Int = 0,
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val successStories: List<SuccessStory> = emptyList(),
    val allActions: List<AuthorityAction> = emptyList(),
    val lastSyncedMs: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════════
// Display name mapping for categories that may appear in
// surface_anchors but are not part of the UseCase enum.
// ═══════════════════════════════════════════════════════════════════

val categoryDisplayNames: Map<String, Pair<String, String>> = mapOf(
    // The 6 parent UseCase categories only
    "WOMENS_SAFETY"    to Pair("Women's Safety", "👩"),
    "ACCESSIBILITY"    to Pair("Accessibility", "\u267F"),
    "LABOR_RIGHTS"     to Pair("Labor Rights", "👷"),
    "FACILITIES"       to Pair("Facilities", "🏢"),
    "ENVIRONMENTAL"    to Pair("Environmental", "🌍"),
    "CIVIL_RESISTANCE" to Pair("Civil Resistance", "🎙\uFE0F"),
    // Fallback
    "GENERAL"          to Pair("General", "📋")
)

/**
 * Reverse lookup: maps every subcategory ID to its parent UseCase.
 * Used by [computeImpactStats] to roll sub-categories up into 6 groups.
 */
val subcategoryToUseCase: Map<String, String> = mapOf(
    // Women's Safety
    "ASSAULT" to "WOMENS_SAFETY",
    "HARASSMENT" to "WOMENS_SAFETY",
    "UNSAFE_AREA" to "WOMENS_SAFETY",
    "NO_EMERGENCY_HELP" to "WOMENS_SAFETY",
    "STALKING" to "WOMENS_SAFETY",
    // Accessibility
    "BROKEN_RAMP" to "ACCESSIBILITY",
    "NO_TOILET" to "ACCESSIBILITY",
    "NO_ELEVATOR" to "ACCESSIBILITY",
    "INACCESSIBLE_DOOR" to "ACCESSIBILITY",
    "NO_CAPTIONS" to "ACCESSIBILITY",
    "BLOCKED_PATH" to "ACCESSIBILITY",
    // Labor Rights
    "WAGE_THEFT" to "LABOR_RIGHTS",
    "SAFETY_VIOLATION" to "LABOR_RIGHTS",
    "EXCESSIVE_HOURS" to "LABOR_RIGHTS",
    "NO_BENEFITS" to "LABOR_RIGHTS",
    "CHILD_LABOR" to "LABOR_RIGHTS",
    // Facilities
    "BROKEN_EQUIPMENT" to "FACILITIES",
    "WATER_LEAK" to "FACILITIES",
    "ELECTRICAL" to "FACILITIES",
    "DIRTY" to "FACILITIES",
    "PEST" to "FACILITIES",
    "STRUCTURAL" to "FACILITIES",
    // Environmental
    "OVERFLOWING_TRASH" to "ENVIRONMENTAL",
    "SPILL" to "ENVIRONMENTAL",
    "POLLUTION" to "ENVIRONMENTAL",
    "BAD_ODOR" to "ENVIRONMENTAL",
    "NOISE" to "ENVIRONMENTAL",
    // Civil Resistance
    "POLICE_VIOLENCE" to "CIVIL_RESISTANCE",
    "DETENTION" to "CIVIL_RESISTANCE",
    "SUPPRESSION" to "CIVIL_RESISTANCE",
    "CONFISCATION" to "CIVIL_RESISTANCE",
    "INTIMIDATION" to "CIVIL_RESISTANCE",
    "CENSORSHIP" to "CIVIL_RESISTANCE",
    // Common
    "OTHER" to "GENERAL",
    "GENERAL" to "GENERAL",
    "SAFETY" to "WOMENS_SAFETY",
    "FACILITY" to "FACILITIES"
)

// ═══════════════════════════════════════════════════════════════════
// Utility: safely extract a millisecond timestamp from a Firestore
// field that might be a Long, Double, or Firestore Timestamp.
// ═══════════════════════════════════════════════════════════════════

fun getTimestampMs(value: Any?): Long = when (value) {
    is Long      -> value
    is Double    -> value.toLong()
    is Timestamp -> value.toDate().time
    is Int       -> value.toLong()
    else         -> 0L
}
