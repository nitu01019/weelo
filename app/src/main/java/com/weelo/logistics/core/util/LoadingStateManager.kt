package com.weelo.logistics.core.util

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.weelo.logistics.R

/**
 * LoadingStateManager - Centralized loading state management
 * 
 * MODULARITY: Reusable across all activities
 * SCALABILITY: Easy to customize loading UI
 * MAINTAINABILITY: Single point of loading state logic
 * 
 * Usage:
 * ```
 * private lateinit var loadingManager: LoadingStateManager
 * 
 * override fun onCreate() {
 *     loadingManager = LoadingStateManager(this)
 *     loadingManager.attachTo(rootView)
 * }
 * 
 * fun onLoading() {
 *     loadingManager.showLoading("Loading prices...")
 * }
 * 
 * fun onComplete() {
 *     loadingManager.hideLoading()
 * }
 * ```
 */
class LoadingStateManager(private val activity: Activity) {

    private var loadingOverlay: View? = null
    private var progressBar: ProgressBar? = null
    private var loadingText: TextView? = null
    private var isAttached = false

    /**
     * Attach loading overlay to a root view
     */
    fun attachTo(rootView: ViewGroup) {
        if (isAttached) return

        try {
            // Create overlay programmatically for flexibility
            loadingOverlay = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0x80000000.toInt()) // Semi-transparent black
                isClickable = true // Blocks touch events
                isFocusable = true
                visibility = View.GONE

                // Progress bar
                progressBar = ProgressBar(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                }
                addView(progressBar)

                // Loading text
                loadingText = TextView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                        topMargin = 150
                    }
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 16f
                }
                addView(loadingText)
            }

            rootView.addView(loadingOverlay)
            isAttached = true
        } catch (e: Exception) {
            // 4 PRINCIPLES COMPLIANCE:
            // SCALABILITY: Non-blocking logging (doesn't slow down UI thread)
            // EASY UNDERSTANDING: Clear error context for debugging
            // MODULARITY: Uses Android logging best practices
            // CODING STANDARDS: Industry standard (Firebase Crashlytics)
            if (com.weelo.logistics.BuildConfig.DEBUG) {
                // Development: Log to console for immediate debugging
                timber.log.Timber.e(e, "Failed to attach loading overlay")
            } else {
                // Production: Send to Crashlytics for monitoring
                // Note: Crashlytics should be initialized in Application class
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                        .recordException(e)
                } catch (crashlyticsError: Exception) {
                    // Fallback if Crashlytics not available
                    timber.log.Timber.e(e, "Failed to attach loading overlay")
                }
            }
        }
    }

    /**
     * Show loading state with optional message
     */
    fun showLoading(message: String = "Loading...") {
        activity.runOnUiThread {
            loadingText?.text = message
            loadingOverlay?.visibility = View.VISIBLE
        }
    }

    /**
     * Hide loading state
     */
    fun hideLoading() {
        activity.runOnUiThread {
            loadingOverlay?.visibility = View.GONE
        }
    }

    /**
     * Check if loading is visible
     */
    fun isLoading(): Boolean = loadingOverlay?.isVisible == true

    /**
     * Update loading message
     */
    fun updateMessage(message: String) {
        activity.runOnUiThread {
            loadingText?.text = message
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        loadingOverlay?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        loadingOverlay = null
        progressBar = null
        loadingText = null
        isAttached = false
    }
}

/**
 * Extension to show loading with automatic hide after action
 */
inline fun <T> LoadingStateManager.withLoading(
    message: String = "Loading...",
    action: () -> T
): T {
    showLoading(message)
    return try {
        action()
    } finally {
        hideLoading()
    }
}
