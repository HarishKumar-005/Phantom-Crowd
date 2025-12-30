package com.phantomcrowd.utils

/**
 * Centralized debug configuration for the app.
 * Controls verbose logging and debug features throughout the codebase.
 * 
 * Note: These defaults are set for debug builds. For release builds,
 * consider setting these to false or using BuildConfig.DEBUG after
 * the initial build succeeds.
 */
object DebugConfig {
    /**
     * Enable verbose logging throughout the app.
     * Set to false for release builds to reduce log spam.
     */
    var VERBOSE_LOGGING: Boolean = true

    /**
     * Show AR debug overlay with tracking state, accuracy, etc.
     */
    var SHOW_AR_DEBUG_OVERLAY: Boolean = true

    /**
     * Log every location update received from GPS.
     */
    var LOG_LOCATION_UPDATES: Boolean = true

    /**
     * Log anchor creation, loading, and rendering operations.
     */
    var LOG_ANCHOR_OPERATIONS: Boolean = true

    /**
     * Log Geospatial API state changes.
     */
    var LOG_GEOSPATIAL_STATE: Boolean = true

    /**
     * Initialize debug config based on build type.
     * Call this from Application.onCreate() if you want to use BuildConfig.
     */
    fun initFromBuildConfig(isDebug: Boolean) {
        VERBOSE_LOGGING = isDebug
        SHOW_AR_DEBUG_OVERLAY = isDebug
        LOG_LOCATION_UPDATES = isDebug
        LOG_ANCHOR_OPERATIONS = isDebug
        LOG_GEOSPATIAL_STATE = isDebug
    }
}
