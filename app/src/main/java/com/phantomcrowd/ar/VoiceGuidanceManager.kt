package com.phantomcrowd.ar

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * VoiceGuidanceManager - Text-to-Speech for AR Navigation
 * 
 * Provides voice feedback during navigation:
 * - Initial distance announcement
 * - Milestone updates (200m, 100m, 50m)
 * - Arrival confirmation
 */
class VoiceGuidanceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceGuidance"
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastMilestone: Int = Int.MAX_VALUE
    private val milestones = listOf(200, 100, 50, 20)
    
    /**
     * Initialize TextToSpeech engine
     */
    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported, trying default")
                    tts?.setLanguage(Locale.getDefault())
                }
                
                // Set speech rate (slightly faster for navigation)
                tts?.setSpeechRate(1.1f)
                
                // Set up progress listener for queue management
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech done: $utteranceId")
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error: $utteranceId")
                    }
                })
                
                isInitialized = true
                Log.d(TAG, "TextToSpeech initialized successfully")
                onReady()
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
    }
    
    /**
     * Speak initial navigation distance
     */
    fun speakNavigationStart(distanceMeters: Int) {
        val text = if (distanceMeters >= 1000) {
            val km = distanceMeters / 1000f
            "Navigate ${String.format("%.1f", km)} kilometers to destination"
        } else {
            "Navigate $distanceMeters meters to destination"
        }
        speak(text)
        lastMilestone = Int.MAX_VALUE
    }
    
    /**
     * Check and speak milestone updates (200m, 100m, 50m, 20m)
     * Only speaks when crossing a milestone threshold
     */
    fun checkAndSpeakMilestone(distanceMeters: Int) {
        for (milestone in milestones) {
            if (distanceMeters <= milestone && lastMilestone > milestone) {
                lastMilestone = milestone
                speak("Now $milestone meters away")
                return
            }
        }
    }
    
    /**
     * Speak arrival confirmation
     */
    fun speakArrival() {
        speak("You've arrived at your destination")
        lastMilestone = 0
    }
    
    /**
     * Speak distance update (called periodically if desired)
     */
    fun speakDistanceUpdate(distanceMeters: Int) {
        val text = if (distanceMeters >= 1000) {
            val km = distanceMeters / 1000f
            "${String.format("%.1f", km)} kilometers remaining"
        } else {
            "$distanceMeters meters remaining"
        }
        speak(text)
    }
    
    /**
     * Speak direction guidance
     */
    fun speakDirection(direction: String) {
        speak("Head $direction")
    }
    
    /**
     * Core speak function - queues speech without interrupting
     */
    private fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, skipping: $text")
            return
        }
        
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        Log.d(TAG, "Queued speech: $text")
    }
    
    /**
     * Stop all speech immediately
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * Reset milestone tracking (for new navigation)
     */
    fun resetMilestones() {
        lastMilestone = Int.MAX_VALUE
    }
    
    /**
     * Cleanup - must be called when done
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "VoiceGuidanceManager shutdown")
    }
}
