package com.weelo.logistics.tutorial

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.weelo.logistics.R

/**
 * TutorialCoordinator - Orchestrates the entire tutorial flow
 * 
 * Manages the sequence of tutorial steps for different screens:
 * - Home screen tutorial (welcome, search bar, vehicle types)
 * - Location input tutorial (from/to fields, add stops, continue)
 * - Truck selection tutorial (truck types scanning)
 * 
 * Coordinates overlay, text hints, and animations
 * Handles scanning effect across buttons
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
    private var hintTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var hintContainer: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentStepIndex = 0
    private val steps = mutableListOf<TutorialStep>()
    private var currentTutorialType: TutorialType = TutorialType.TRUCK_SELECTION
    
    /**
     * Tutorial types for different screens
     */
    enum class TutorialType {
        HOME,
        LOCATION_INPUT,
        MAP_BOOKING,
        TRUCK_SELECTION
    }
    
    // ========================================
    // Home Screen Tutorial
    // ========================================
    
    /**
     * Start the home screen tutorial
     * Shows welcome message and guides through search
     */
    fun startHomeTutorial() {
        if (onboardingManager.isHomeTutorialCompleted()) {
            onComplete()
            return
        }
        
        currentTutorialType = TutorialType.HOME
        setupOverlay()
        setupSkipButton()
        setupHintViews()
        setupHomeTutorialSteps()
        
        handler.postDelayed({
            executeStep(0)
        }, 300)
    }
    
    private fun setupHomeTutorialSteps() {
        steps.clear()
        
        // Step 1: Welcome message (no highlight)
        steps.add(TutorialStep(
            stepId = "welcome",
            targetView = null,
            spokenText = "Welcome to Weelo! Your trusted logistics partner for all transportation needs.",
            durationMs = 2500,
            highlightType = HighlightType.NONE
        ))
        
        // Step 2: Highlight search bar
        val searchContainer = activity.findViewById<View>(R.id.searchContainer)
        steps.add(TutorialStep(
            stepId = "search_bar",
            targetView = searchContainer,
            spokenText = "Tap here to enter pickup and drop locations to book a vehicle",
            durationMs = 2500,
            highlightType = HighlightType.RECTANGLE
        ))
        
        // Step 3: Show vehicle types (if visible)
        steps.add(TutorialStep(
            stepId = "vehicle_types",
            targetView = null,
            spokenText = "Choose from Trucks, Tractors, Tempos and more for your logistics needs",
            durationMs = 2000,
            highlightType = HighlightType.NONE
        ))
    }
    
    // ========================================
    // Location Input Tutorial
    // ========================================
    
    /**
     * Start the location input tutorial
     * Guides through entering pickup and drop locations
     */
    fun startLocationInputTutorial() {
        if (onboardingManager.isLocationInputTutorialCompleted()) {
            onComplete()
            return
        }
        
        currentTutorialType = TutorialType.LOCATION_INPUT
        setupOverlay()
        setupSkipButton()
        setupHintViews()
        setupLocationInputSteps()
        
        handler.postDelayed({
            executeStep(0)
        }, 300)
    }
    
    private fun setupLocationInputSteps() {
        steps.clear()
        
        // Step 1: From location field
        val fromContainer = activity.findViewById<View>(R.id.fromLocationContainer)
        steps.add(TutorialStep(
            stepId = "from_location",
            targetView = fromContainer,
            spokenText = "Enter your pickup location here. Start typing for suggestions.",
            durationMs = 2500,
            highlightType = HighlightType.RECTANGLE
        ))
        
        // Step 2: To location field
        val toContainer = activity.findViewById<View>(R.id.toLocationContainer)
        steps.add(TutorialStep(
            stepId = "to_location",
            targetView = toContainer,
            spokenText = "Enter your drop-off destination here",
            durationMs = 2000,
            highlightType = HighlightType.RECTANGLE
        ))
        
        // Step 3: Add stops button
        val addStopsButton = activity.findViewById<View>(R.id.addStopsButton)
        steps.add(TutorialStep(
            stepId = "add_stops",
            targetView = addStopsButton,
            spokenText = "Need multiple stops? Tap here to add intermediate locations",
            durationMs = 2000,
            highlightType = HighlightType.SPOTLIGHT
        ))
        
        // Step 4: Select on map button
        val selectOnMapButton = activity.findViewById<View>(R.id.selectOnMapButton)
        steps.add(TutorialStep(
            stepId = "select_on_map",
            targetView = selectOnMapButton,
            spokenText = "Or select locations directly on the map",
            durationMs = 2000,
            highlightType = HighlightType.SPOTLIGHT
        ))
        
        // Step 5: Continue button
        val continueButton = activity.findViewById<View>(R.id.continueButton)
        steps.add(TutorialStep(
            stepId = "continue_button",
            targetView = continueButton,
            spokenText = "Once locations are entered, tap Continue to proceed",
            durationMs = 2000,
            highlightType = HighlightType.RECTANGLE
        ))
    }
    
    // ========================================
    // Map Booking Tutorial
    // ========================================
    
    /**
     * Start the map booking tutorial
     * Guides through route confirmation
     */
    fun startMapBookingTutorial() {
        if (onboardingManager.isMapBookingTutorialCompleted()) {
            onComplete()
            return
        }
        
        currentTutorialType = TutorialType.MAP_BOOKING
        setupOverlay()
        setupSkipButton()
        setupHintViews()
        setupMapBookingSteps()
        
        handler.postDelayed({
            executeStep(0)
        }, 500)
    }
    
    private fun setupMapBookingSteps() {
        steps.clear()
        
        // Step 1: Map overview
        steps.add(TutorialStep(
            stepId = "map_overview",
            targetView = null,
            spokenText = "Your route is shown on the map. Review the pickup and drop points.",
            durationMs = 2500,
            highlightType = HighlightType.NONE
        ))
        
        // Step 2: Proceed to truck selection - try to find proceed/continue button
        // Try different possible button IDs
        var proceedButton: View? = null
        try {
            proceedButton = activity.findViewById<View>(R.id.continueButton)
                ?: activity.findViewById<View>(R.id.confirmButton)
        } catch (e: Exception) {
            // Button not found, skip this step
        }
        
        if (proceedButton != null) {
            steps.add(TutorialStep(
                stepId = "proceed_button",
                targetView = proceedButton,
                spokenText = "Tap here to select your vehicle type",
                durationMs = 2000,
                highlightType = HighlightType.RECTANGLE
            ))
        }
    }
    
    // ========================================
    // Truck Selection Tutorial
    // ========================================
    
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
        
        currentTutorialType = TutorialType.TRUCK_SELECTION
        setupOverlay()
        setupSkipButton()
        setupHintViews()
        setupTruckSelectionSteps()
        
        // Start animation quickly
        handler.postDelayed({
            executeStep(0)
        }, 500)
    }
    
    /**
     * Define truck selection tutorial steps
     */
    private fun setupTruckSelectionSteps() {
        steps.clear()
        
        // Step 1: Introduction
        steps.add(TutorialStep(
            stepId = "truck_intro",
            targetView = null,
            spokenText = "Choose the right vehicle for your cargo",
            durationMs = 1500,
            highlightType = HighlightType.NONE
        ))
        
        // Step 2: Highlight truck section
        val trucksContainer = activity.findViewById<View>(R.id.trucksContainer)
        steps.add(TutorialStep(
            stepId = "truck_section",
            targetView = trucksContainer,
            spokenText = "Browse through different truck types available",
            durationMs = 1500,
            highlightType = HighlightType.RECTANGLE
        ))
        
        // Step 3: Fast scan through truck buttons
        steps.add(TutorialStep(
            stepId = "scan_trucks",
            targetView = null,
            spokenText = "Tap any truck to see sizes and prices",
            durationMs = 2500,
            shouldScan = true
        ))
    }
    
    // ========================================
    // Common Setup Methods
    // ========================================
    
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
     * Setup hint text views for displaying tutorial messages
     */
    private fun setupHintViews() {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        
        // Create container for hint text
        hintContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(0, 0, 0, 200)
            }
        }
        
        // Title text (step indicator)
        titleTextView = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        hintContainer?.addView(titleTextView)
        
        // Main hint text
        hintTextView = TextView(activity).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        hintContainer?.addView(hintTextView)
        
        rootView.addView(hintContainer)
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
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener {
                skipTutorial()
            }
        }
        
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(skipButton)
    }
    
    // ========================================
    // Step Execution
    // ========================================
    
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
        
        // Update hint text
        updateHintText(step.spokenText, index + 1, steps.size)
        
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
            
            // Move to next step after duration
            handler.postDelayed({
                executeStep(index + 1)
            }, step.durationMs)
        }
    }
    
    /**
     * Update the hint text displayed on screen
     */
    private fun updateHintText(text: String, currentStep: Int, totalSteps: Int) {
        titleTextView?.text = "Step $currentStep of $totalSteps"
        hintTextView?.text = text
        
        // Animate hint text
        hintContainer?.alpha = 0f
        hintContainer?.animate()
            ?.alpha(1f)
            ?.setDuration(300)
            ?.start()
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
    
    // ========================================
    // Tutorial Completion
    // ========================================
    
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
        // Mark as completed based on tutorial type
        when (currentTutorialType) {
            TutorialType.HOME -> onboardingManager.markHomeTutorialCompleted()
            TutorialType.LOCATION_INPUT -> onboardingManager.markLocationInputTutorialCompleted()
            TutorialType.MAP_BOOKING -> onboardingManager.markMapBookingTutorialCompleted()
            TutorialType.TRUCK_SELECTION -> onboardingManager.markTruckSelectionTutorialCompleted()
        }
        
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
        rootView.removeView(hintContainer)
        
        ttsManager?.release()
        
        overlayView = null
        skipButton = null
        hintContainer = null
        hintTextView = null
        titleTextView = null
        ttsManager = null
    }
}
