package com.weelo.logistics.tutorial

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

/**
 * TextToSpeechManager - Handles spoken instructions for tutorial
 * 
 * Manages TTS initialization and speaking text
 * Provides callbacks for speech completion
 * Properly releases resources
 * 
 * Modular and can be removed easily
 */
class TextToSpeechManager(context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeechCompleteListener: (() -> Unit)? = null
    
    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                               result != TextToSpeech.LANG_NOT_SUPPORTED
                
                if (isInitialized) {
                    // Set speech rate (slightly slower for clarity)
                    textToSpeech?.setSpeechRate(0.9f)
                    
                    // Set pitch
                    textToSpeech?.setPitch(1.0f)
                }
                
                // Set up utterance listener for callbacks
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Speech started
                        android.util.Log.d("TTS", "Speech started: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        // Speech completed
                        android.util.Log.d("TTS", "Speech completed: $utteranceId")
                        onSpeechCompleteListener?.invoke()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        // Error occurred
                        android.util.Log.e("TTS", "Speech error: $utteranceId")
                        onSpeechCompleteListener?.invoke()
                    }
                })
            } else {
                android.util.Log.e("TTS", "TTS initialization failed")
            }
        }
    }
    
    /**
     * Speak the given text
     * @param text Text to speak
     * @param onComplete Callback when speech is finished
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        android.util.Log.d("TTS", "Attempting to speak: $text, initialized: $isInitialized")
        
        if (!isInitialized) {
            android.util.Log.e("TTS", "TTS not initialized, cannot speak")
            onComplete?.invoke()
            return
        }
        
        onSpeechCompleteListener = onComplete
        
        val utteranceId = "tutorial_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        
        val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        android.util.Log.d("TTS", "Speak result: $result")
    }
    
    /**
     * Stop current speech
     */
    fun stop() {
        textToSpeech?.stop()
    }
    
    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }
    
    /**
     * Release TTS resources
     * Call this when done with TTS
     */
    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
}
