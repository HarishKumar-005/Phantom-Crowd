package com.phantomcrowd.utils

import android.util.Log

/**
 * Enhanced logging utility with categories and levels.
 * Provides structured logging for easy filtering in Logcat.
 * 
 * Usage:
 *   Logger.d(Logger.Category.AR, "Session created")
 *   Logger.e(Logger.Category.GPS, "Failed to get location", exception)
 * 
 * Logcat filters:
 *   - "PhantomCrowd" for all app logs
 *   - "PhantomCrowd-AR" for AR-specific logs
 *   - "PhantomCrowd-GPS" for location logs
 */
object Logger {
    private const val BASE_TAG = "PhantomCrowd"

    /**
     * Log categories for filtering in Logcat.
     */
    enum class Category {
        AR,         // ARCore, Sceneform, session management
        GPS,        // Location updates, coordinates
        DATA,       // Storage, anchors, JSON parsing
        UI,         // UI events, compose state
        PERMISSION, // Permission requests and results
        NETWORK     // Network connectivity, API calls
    }

    /**
     * Debug log - only shows when DebugConfig.VERBOSE_LOGGING is true.
     */
    fun d(category: Category, message: String) {
        if (DebugConfig.VERBOSE_LOGGING) {
            Log.d(getTag(category), message)
        }
    }

    /**
     * Debug log (legacy single-tag version for compatibility).
     */
    fun d(message: String) {
        if (DebugConfig.VERBOSE_LOGGING) {
            Log.d(BASE_TAG, message)
        }
    }

    /**
     * Info log - always shows, for important non-error events.
     */
    fun i(category: Category, message: String) {
        Log.i(getTag(category), message)
    }

    /**
     * Warning log - always shows, for recoverable issues.
     */
    fun w(category: Category, message: String) {
        Log.w(getTag(category), message)
    }

    /**
     * Error log - always shows, optionally with exception stack trace.
     */
    fun e(category: Category, message: String, throwable: Throwable? = null) {
        Log.e(getTag(category), message, throwable)
        // TODO: Add Crashlytics.recordException(throwable) for non-debug
    }

    /**
     * Error log (legacy version for compatibility).
     */
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(BASE_TAG, message, throwable)
    }

    /**
     * Verbose log for very detailed debugging.
     */
    fun v(category: Category, message: String) {
        if (DebugConfig.VERBOSE_LOGGING) {
            Log.v(getTag(category), message)
        }
    }

    private fun getTag(category: Category): String {
        return "$BASE_TAG-${category.name}"
    }

    /**
     * Log with timing information for performance debugging.
     */
    inline fun <T> timed(category: Category, operation: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - startTime
                d(category, "$operation completed in ${duration}ms")
            }
        } catch (ex: Exception) {
            val duration = System.currentTimeMillis() - startTime
            e(category, "$operation failed after ${duration}ms", ex)
            throw ex
        }
    }
}
