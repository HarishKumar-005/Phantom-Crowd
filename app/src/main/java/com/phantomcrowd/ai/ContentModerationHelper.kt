package com.phantomcrowd.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device content moderation using MediaPipe Text Classifier.
 * 
 * Model: average_word_classifier.tflite in assets folder
 * 
 * Provides three-tier moderation:
 * - Safe (green): Content is appropriate
 * - Warning (yellow): Content may need review
 * - Blocked (red): Content violates guidelines
 */
class ContentModerationHelper(context: Context) {
    
    companion object {
        private const val TAG = "ContentModerationHelper"
        private const val MODEL_PATH = "average_word_classifier.tflite"
        
        // Thresholds for classification
        private const val BLOCKED_THRESHOLD = 0.7f  // High confidence negative
        private const val WARNING_THRESHOLD = 0.4f  // Moderate confidence negative
    }
    
    private var textClassifier: TextClassifier? = null
    private var initError: String? = null
    
    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()
            
            val options = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            
            textClassifier = TextClassifier.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe TextClassifier initialized successfully")
        } catch (e: Exception) {
            initError = e.message
            Log.e(TAG, "Failed to initialize TextClassifier: ${e.message}", e)
        }
    }
    
    /**
     * Classify text content and return moderation result.
     * Runs on IO dispatcher for background processing.
     * 
     * @param text The text to analyze
     * @return ModerationResult indicating safety level
     */
    suspend fun moderateContent(text: String): ModerationResult = withContext(Dispatchers.IO) {
        // Skip if text is too short
        if (text.isBlank() || text.length < 5) {
            return@withContext ModerationResult.Empty
        }
        
        // Handle initialization failure gracefully
        if (textClassifier == null) {
            Log.w(TAG, "TextClassifier not available: $initError")
            return@withContext ModerationResult.Error(initError ?: "Classifier not initialized")
        }
        
        try {
            val startTime = System.currentTimeMillis()
            val result = textClassifier!!.classify(text)
            val inferenceTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Classification completed in ${inferenceTime}ms")
            parseModerationResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed: ${e.message}", e)
            ModerationResult.Error(e.message ?: "Classification failed")
        }
    }
    
    /**
     * Parse MediaPipe result into moderation decision.
     */
    private fun parseModerationResult(result: TextClassifierResult): ModerationResult {
        val classifications = result.classificationResult().classifications()
        if (classifications.isEmpty()) {
            Log.w(TAG, "No classifications returned")
            return ModerationResult.Safe(0f)
        }
        
        val categories = classifications[0].categories()
        var negativeScore = 0f
        var positiveScore = 0f
        
        categories.forEach { category ->
            val name = category.categoryName().lowercase()
            val score = category.score()
            
            Log.d(TAG, "Category: $name, Score: $score")
            
            when (name) {
                "negative", "toxic", "harmful", "offensive" -> {
                    if (score > negativeScore) negativeScore = score
                }
                "positive", "safe", "neutral" -> {
                    if (score > positiveScore) positiveScore = score
                }
            }
        }
        
        Log.d(TAG, "Final scores - Negative: $negativeScore, Positive: $positiveScore")
        
        return when {
            negativeScore > BLOCKED_THRESHOLD -> {
                Log.w(TAG, "Content BLOCKED (negative score: $negativeScore)")
                ModerationResult.Blocked(negativeScore)
            }
            negativeScore > WARNING_THRESHOLD -> {
                Log.w(TAG, "Content WARNING (negative score: $negativeScore)")
                ModerationResult.Warning(negativeScore)
            }
            else -> {
                Log.d(TAG, "Content SAFE (positive score: $positiveScore)")
                ModerationResult.Safe(positiveScore)
            }
        }
    }
    
    /**
     * Check if the classifier is ready to use.
     */
    fun isReady(): Boolean = textClassifier != null
    
    /**
     * Release native resources when done.
     */
    fun close() {
        try {
            textClassifier?.close()
            textClassifier = null
            Log.d(TAG, "TextClassifier closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TextClassifier: ${e.message}", e)
        }
    }
}

/**
 * Result of content moderation analysis.
 */
sealed class ModerationResult {
    /** No analysis performed (text too short) */
    object Empty : ModerationResult()
    
    /** Content is appropriate for posting */
    data class Safe(val score: Float) : ModerationResult()
    
    /** Content may need review but is allowed */
    data class Warning(val score: Float) : ModerationResult()
    
    /** Content violates guidelines and should not be posted */
    data class Blocked(val score: Float) : ModerationResult()
    
    /** Error during analysis (still allow posting) */
    data class Error(val message: String) : ModerationResult()
}
