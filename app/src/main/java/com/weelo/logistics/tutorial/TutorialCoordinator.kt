package com.weelo.logistics.tutorial

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import com.weelo.logistics.R

/**
 * TutorialCoordinator - Orchestrates the entire tutorial flow
 * 
 * Manages the sequence of tutorial steps
 * Coordinates overlay, TTS, and animations
 * Handles scanning effect across truck buttons
 * 
 * Modular and self-contained - can be removed easily
 */
class TutorialCoordinator(
    private val activity: Activity,
    private val onboardingManager: OnboardingManager,
    private val onComplete: () -> Unit
) {
    
    private var overlayView: TutorialOverlayView? = null
    private var ttsManager: TextToSpeechManager? = null
    private var skipButton: Button? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentStepIndex = 0
    private val steps = mutableListOf<TutorialStep>()
    
    /**
     * Start the truck selection tutorial
     * Only runs if not completed before (first install only)
     */
    fun startTruckSelectionTutorial() {
        // Check if already completed
        if (onboardingManager.isTruckSelectionTutorialCompleted()) {
            onComplete()
            return
        }
        
        // NO TTS - animation only
        // ttsManager = TextToSpeechManager(activity)
        
        // Create and add overlay
        setupOverlay()
        
        // Create skip button
        setupSkipButton()
        
        // Define tutorial steps
        setupTutorialSteps()
        
        // Start animation quickly
        handler.postDelayed({
            executeStep(0)
        }, 500)
    }
    
    /**
     * Setup the overlay view
     */
    private fun setupOverlay() {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        overlayView = TutorialOverlayView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(overlayView)
    }
    
    /**
     * Setup skip button
     */
    private fun setupSkipButton() {
        skipButton = Button(activity).apply {
            text = "Skip Tutorial"
            textSize = 14f
            setBackgroundResource(R.drawable.button_outlined)
            setTextColor(activity.getColor(R.color.black))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 32, 32, 32)
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }
            setOnClickListener {
                skipTutorial()
            }
        }
        
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(skipButton)
    }
    
    /**
     * Define tutorial steps - ANIMATION ONLY (NO VOICE)
     */
    private fun setupTutorialSteps() {
        steps.clear()
        
        // Step 1: Highlight truck section
        val trucksContainer = activity.findViewById<View>(R.id.trucksContainer)
        steps.add(TutorialStep(
            stepId = "truck_section",
            targetView = trucksContainer,
            spokenText = "",  // NO VOICE
            durationMs = 1500,
            highlightType = HighlightType.RECTANGLE
        ))
        
        // Step 2: Fast scan through truck buttons
        steps.add(TutorialStep(
            stepId = "scan_trucks",
            targetView = null,
            spokenText = "",  // NO VOICE
            durationMs = 2500,
            shouldScan = true
        ))
    }
    
    /**
     * Execute a tutorial step
     */
    private fun executeStep(index: Int) {
        if (index >= steps.size) {
            completeTutorial()
            return
        }
        
        currentStepIndex = index
        val step = steps[index]
        
        if (step.shouldScan) {
            // Perform scanning animation
            performScanAnimation()
        } else {
            // Normal step
            if (step.targetView != null) {
                overlayView?.setTarget(step.targetView, step.highlightType)
            } else {
                overlayView?.setTarget(null, step.highlightType)
            }
            
            // NO SPEECH - just animation
            handler.postDelayed({
                executeStep(index + 1)
            }, step.durationMs)
        }
    }
    
    /**
     * Perform scanning animation across all truck buttons
     */
    private fun performScanAnimation() {
        val truckCardIds = listOf(
            R.id.openCard,
            R.id.containerCard,
            R.id.lcvCard,
            R.id.miniCard,
            R.id.trailerCard,
            R.id.tipperCard,
            R.id.tankerCard,
            R.id.dumperCard,
            R.id.bulkerCard
        )
        
        var scanIndex = 0
        val scanDelay = 280L  // 280ms per truck (faster!)
        
        fun highlightNextTruck() {
            if (scanIndex >= truckCardIds.size) {
                // Scanning complete
                handler.postDelayed({
                    executeStep(currentStepIndex + 1)
                }, 200)
                return
            }
            
            val cardView = activity.findViewById<CardView>(truckCardIds[scanIndex])
            if (cardView != null) {
                overlayView?.setTarget(cardView, HighlightType.SPOTLIGHT)
            }
            
            scanIndex++
            handler.postDelayed({
                highlightNextTruck()
            }, scanDelay)
        }
        
        highlightNextTruck()
    }
    
    /**
     * Skip tutorial
     */
    private fun skipTutorial() {
        completeTutorial()
    }
    
    /**
     * Complete tutorial and clean up
     */
    private fun completeTutorial() {
        // Mark as completed
        onboardingManager.markTruckSelectionTutorialCompleted()
        
        // Clean up
        cleanup()
        
        // Callback
        onComplete()
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        
        overlayView?.clear()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.removeView(overlayView)
        rootView.removeView(skipButton)
        
        ttsManager?.release()
        
        overlayView = null
        skipButton = null
        ttsManager = null
    }
}
