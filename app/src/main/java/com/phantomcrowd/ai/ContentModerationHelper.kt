package com.phantomcrowd.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Content moderation using keyword-based toxicity detection.
 * 
 * Based on Jigsaw Toxic Comment Dataset categories:
 * - Toxic: Rude, disrespectful, or unreasonable
 * - Severe Toxic: Very hateful, aggressive, disrespectful
 * - Obscene: Swear words, profanity
 * - Threat: Intent to harm or violence
 * - Insult: Inflammatory comments towards person/group
 * - Identity Hate: Targeting based on identity
 * 
 * Hybrid approach: Keyword check first (fast), MediaPipe fallback (if available)
 */
class ContentModerationHelper(context: Context) {
    
    companion object {
        private const val TAG = "ContentModerationHelper"
        private const val MODEL_PATH = "average_word_classifier.tflite"
        
        // ========== JIGSAW-BASED KEYWORD PATTERNS ==========
        
        // Category 1: THREATS (Jigsaw: threat)
        // Intent to inflict pain, injury, or violence
        private val THREAT_PATTERNS = listOf(
            "kill you", "kill them", "kill everyone", "kill myself",
            "murder you", "murder them",
            "should die", "must die", "gonna die", "will die", "deserve to die",
            "beat you up", "beat them up",
            "shoot you", "stab you", "bomb",
            "i will find you", "watch your back",
            "going to hurt", "going to kill"
        )
        
        // Category 2: SEVERE TOXIC (Jigsaw: severe_toxic)
        // Very hateful, aggressive, or disrespectful
        private val SEVERE_TOXIC_PATTERNS = listOf(
            "worthless", "pathetic", "disgusting", "vile",
            "scum", "garbage", "trash", "filth",
            "subhuman", "vermin", "animal", "beast", "pig",
            "cockroach", "parasite", "pest",
            "waste of", "piece of"
        )
        
        // Category 3: INSULTS (Jigsaw: insult)
        // Insulting, inflammatory comments
        private val INSULT_PATTERNS = listOf(
            "idiot", "moron", "stupid", "dumb", "retard",
            "loser", "fool", "jerk", "creep",
            "ugly", "fat", "disgusting",
            "shut up", "get lost", "go away",
            "nobody cares", "no one cares",
            "you suck", "you're pathetic"
        )
        
        // Category 4: IDENTITY HATE (Jigsaw: identity_hate)
        // Negative comments targeting identity
        private val IDENTITY_HATE_PHRASES = listOf(
            "all women", "all men", "all blacks", "all whites",
            "those people", "you people", "your kind",
            "go back to", "don't belong"
        )
        
        // Category 5: OBSCENITY REGEX (Jigsaw: obscene)
        // Swear words with common obfuscation patterns
        private val OBSCENITY_REGEX = listOf(
            Regex("f+[u\\*@]+c+k+", RegexOption.IGNORE_CASE),
            Regex("s+h+[i1!]+t+", RegexOption.IGNORE_CASE),
            Regex("a+s+s+h+o+l+e+", RegexOption.IGNORE_CASE),
            Regex("b+[i1!]+t+c+h+", RegexOption.IGNORE_CASE),
            Regex("d+[i1!]+c+k+", RegexOption.IGNORE_CASE),
            Regex("c+u+n+t+", RegexOption.IGNORE_CASE),
            Regex("wh+o+r+e+", RegexOption.IGNORE_CASE),
            Regex("sl+u+t+", RegexOption.IGNORE_CASE),
            Regex("bastard", RegexOption.IGNORE_CASE),
            Regex("damn", RegexOption.IGNORE_CASE)
        )
        
        // Category 6: HATE SPEECH PATTERNS
        private val HATE_PATTERNS = listOf(
            "i hate", "we hate", "everyone hates",
            "are animals", "are trash", "are garbage", 
            "are worthless", "are scum", "are vermin",
            "should be killed", "should all die", "need to die",
            "don't deserve", "doesn't deserve"
        )
        
        // Severity weights for scoring
        private const val THREAT_WEIGHT = 1.0f      // Highest - immediate block
        private const val SEVERE_TOXIC_WEIGHT = 0.8f
        private const val HATE_WEIGHT = 0.9f
        private const val IDENTITY_HATE_WEIGHT = 0.85f
        private const val INSULT_WEIGHT = 0.5f      // Warning level
        private const val OBSCENITY_WEIGHT = 0.4f   // Warning level
        
        private const val BLOCKED_THRESHOLD = 0.7f
        private const val WARNING_THRESHOLD = 0.35f
    }
    
    private var textClassifier: TextClassifier? = null
    
    init {
        // Try to initialize MediaPipe (optional fallback)
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()
            val options = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            textClassifier = TextClassifier.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe TextClassifier initialized (fallback ready)")
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe not available, using keyword-only mode: ${e.message}")
        }
    }
    
    /**
     * Analyze text for toxicity using keyword matching.
     * Primary detection method based on Jigsaw dataset patterns.
     */
    suspend fun moderateContent(text: String): ModerationResult = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.length < 5) {
            return@withContext ModerationResult.Empty
        }
        
        val lowerText = text.lowercase()
        val startTime = System.currentTimeMillis()
        
        // Calculate toxicity score based on keyword matches
        var totalScore = 0f
        val matchedCategories = mutableListOf<String>()
        
        // Check THREATS (highest priority)
        val threatMatches = THREAT_PATTERNS.filter { lowerText.contains(it) }
        if (threatMatches.isNotEmpty()) {
            totalScore += THREAT_WEIGHT
            matchedCategories.add("THREAT: ${threatMatches.first()}")
            Log.w(TAG, "Threat detected: $threatMatches")
        }
        
        // Check HATE SPEECH
        val hateMatches = HATE_PATTERNS.filter { lowerText.contains(it) }
        if (hateMatches.isNotEmpty()) {
            totalScore += HATE_WEIGHT
            matchedCategories.add("HATE: ${hateMatches.first()}")
            Log.w(TAG, "Hate speech detected: $hateMatches")
        }
        
        // Check SEVERE TOXIC
        val severeToxicMatches = SEVERE_TOXIC_PATTERNS.filter { lowerText.contains(it) }
        if (severeToxicMatches.isNotEmpty()) {
            totalScore += SEVERE_TOXIC_WEIGHT
            matchedCategories.add("SEVERE: ${severeToxicMatches.first()}")
            Log.w(TAG, "Severe toxic detected: $severeToxicMatches")
        }
        
        // Check IDENTITY HATE
        val identityMatches = IDENTITY_HATE_PHRASES.filter { lowerText.contains(it) }
        if (identityMatches.isNotEmpty()) {
            totalScore += IDENTITY_HATE_WEIGHT
            matchedCategories.add("IDENTITY: ${identityMatches.first()}")
            Log.w(TAG, "Identity hate detected: $identityMatches")
        }
        
        // Check INSULTS
        val insultMatches = INSULT_PATTERNS.filter { lowerText.contains(it) }
        if (insultMatches.isNotEmpty()) {
            totalScore += INSULT_WEIGHT
            matchedCategories.add("INSULT: ${insultMatches.first()}")
            Log.d(TAG, "Insult detected: $insultMatches")
        }
        
        // Check OBSCENITY (regex)
        val obsceneMatches = OBSCENITY_REGEX.filter { it.containsMatchIn(lowerText) }
        if (obsceneMatches.isNotEmpty()) {
            totalScore += OBSCENITY_WEIGHT
            matchedCategories.add("OBSCENE")
            Log.d(TAG, "Obscenity detected")
        }
        
        val inferenceTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Keyword analysis completed in ${inferenceTime}ms, score: $totalScore")
        
        // Normalize score (cap at 1.0)
        val normalizedScore = minOf(totalScore, 1.0f)
        
        // Return result based on score
        when {
            normalizedScore >= BLOCKED_THRESHOLD -> {
                Log.w(TAG, "BLOCKED - Score: $normalizedScore, Categories: $matchedCategories")
                ModerationResult.Blocked(normalizedScore)
            }
            normalizedScore >= WARNING_THRESHOLD -> {
                Log.w(TAG, "WARNING - Score: $normalizedScore, Categories: $matchedCategories")
                ModerationResult.Warning(normalizedScore)
            }
            else -> {
                Log.d(TAG, "SAFE - Score: $normalizedScore")
                ModerationResult.Safe(1.0f - normalizedScore)
            }
        }
    }
    
    /**
     * Quick synchronous check for obvious toxic content.
     * Use this for instant feedback while typing.
     */
    fun quickCheck(text: String): ToxicityLevel {
        if (text.length < 5) return ToxicityLevel.NONE
        
        val lowerText = text.lowercase()
        
        // Check for immediate blocks (threats)
        if (THREAT_PATTERNS.any { lowerText.contains(it) }) {
            return ToxicityLevel.BLOCKED
        }
        
        // Check for hate speech
        if (HATE_PATTERNS.any { lowerText.contains(it) }) {
            return ToxicityLevel.BLOCKED
        }
        
        // Check for severe toxic
        if (SEVERE_TOXIC_PATTERNS.any { lowerText.contains(it) }) {
            return ToxicityLevel.WARNING
        }
        
        // Check for obscenity
        if (OBSCENITY_REGEX.any { it.containsMatchIn(lowerText) }) {
            return ToxicityLevel.WARNING
        }
        
        return ToxicityLevel.SAFE
    }
    
    fun isReady(): Boolean = true  // Keyword-based is always ready
    
    fun close() {
        try {
            textClassifier?.close()
            textClassifier = null
            Log.d(TAG, "ContentModerationHelper closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing: ${e.message}")
        }
    }
}

/** Quick toxicity check levels */
enum class ToxicityLevel {
    NONE,
    SAFE,
    WARNING,
    BLOCKED
}

/**
 * Result of content moderation analysis.
 */
sealed class ModerationResult {
    object Empty : ModerationResult()
    data class Safe(val score: Float) : ModerationResult()
    data class Warning(val score: Float) : ModerationResult()
    data class Blocked(val score: Float) : ModerationResult()
    data class Error(val message: String) : ModerationResult()
}
