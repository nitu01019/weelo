package com.weelo.logistics.tutorial

import android.view.View

/**
 * TutorialStep - Defines a single step in the tutorial sequence
 * 
 * Each step can highlight a view, show text, and speak instructions
 * Modular design for easy tutorial customization
 */
data class TutorialStep(
    val stepId: String,                      // Unique identifier for this step
    val targetView: View? = null,            // View to highlight (null for no highlight)
    val spokenText: String,                  // Text to be spoken by TTS
    val durationMs: Long = 3000L,            // How long to show this step (ms)
    val highlightType: HighlightType = HighlightType.SPOTLIGHT,
    val shouldScan: Boolean = false          // If true, scan across multiple views
)

/**
 * Types of highlighting effects
 */
enum class HighlightType {
    SPOTLIGHT,          // Circle spotlight on the target
    RECTANGLE,          // Rectangle highlight around target
    NONE               // No highlight, just dim background
}
