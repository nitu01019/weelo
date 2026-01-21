package com.weelo.logistics.core.util

import android.app.Activity
import android.os.Build
import com.weelo.logistics.R

/**
 * =============================================================================
 * TRANSITION HELPER - Smooth Screen Navigation
 * =============================================================================
 * 
 * Provides consistent, smooth transitions between activities.
 * 
 * NAVIGATION PATTERN:
 * - Forward navigation: New screen slides in from RIGHT
 * - Back navigation: Current screen slides out to RIGHT
 * 
 * USAGE:
 * ```kotlin
 * // When starting a new activity
 * startActivity(intent)
 * TransitionHelper.slideInFromRight(this)
 * 
 * // When going back
 * finish()
 * TransitionHelper.slideOutToRight(this)
 * ```
 * =============================================================================
 */
object TransitionHelper {
    
    // =========================================================================
    // PRIMARY NAVIGATION - Right to Left (Forward)
    // =========================================================================
    
    /**
     * Apply smooth slide-in-from-right transition
     * Use when navigating FORWARD to a new screen
     * 
     * @param activity The current activity
     */
    fun slideInFromRight(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = R.anim.nav_enter_right_to_left,
            exitAnim = R.anim.nav_exit_right_to_left,
            isOpen = true
        )
    }
    
    /**
     * Apply smooth slide-out-to-right transition
     * Use when navigating BACK (finishing current screen)
     * 
     * @param activity The current activity
     */
    fun slideOutToRight(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = R.anim.nav_enter_left_to_right,
            exitAnim = R.anim.nav_exit_left_to_right,
            isOpen = false
        )
    }
    
    // =========================================================================
    // LEGACY METHODS (Keep for backward compatibility)
    // =========================================================================
    
    /**
     * Apply slide-in-left transition when opening a new activity
     * @deprecated Use slideInFromRight() instead
     */
    fun applySlideInLeftTransition(activity: Activity) {
        slideInFromRight(activity)
    }
    
    /**
     * Apply slide-out-right transition when closing an activity
     * @deprecated Use slideOutToRight() instead
     */
    fun applySlideOutRightTransition(activity: Activity) {
        slideOutToRight(activity)
    }
    
    // =========================================================================
    // FADE TRANSITIONS
    // =========================================================================
    
    /**
     * Apply fade transition - for modal-like screens
     * 
     * @param activity The current activity
     */
    fun fadeIn(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = android.R.anim.fade_in,
            exitAnim = android.R.anim.fade_out,
            isOpen = true
        )
    }
    
    /**
     * Apply fade out transition
     * 
     * @param activity The current activity
     */
    fun fadeOut(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = android.R.anim.fade_in,
            exitAnim = android.R.anim.fade_out,
            isOpen = false
        )
    }
    
    // =========================================================================
    // VERTICAL TRANSITIONS (Bottom sheets, modals)
    // =========================================================================
    
    /**
     * Apply slide-up transition - for bottom sheet style screens
     * 
     * @param activity The current activity
     */
    fun slideUp(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = R.anim.slide_in_from_bottom,
            exitAnim = R.anim.slide_out_to_top,
            isOpen = true
        )
    }
    
    /**
     * Apply slide-down transition - for dismissing bottom sheet style screens
     * 
     * @param activity The current activity
     */
    fun slideDown(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = R.anim.slide_in_from_top,
            exitAnim = R.anim.slide_out_to_bottom,
            isOpen = false
        )
    }
    
    // =========================================================================
    // NO ANIMATION (Instant transition)
    // =========================================================================
    
    /**
     * No animation - instant transition
     * Use sparingly, only when animation doesn't make sense
     * 
     * @param activity The current activity
     */
    fun noAnimation(activity: Activity) {
        applyTransition(
            activity = activity,
            enterAnim = 0,
            exitAnim = 0,
            isOpen = true
        )
    }
    
    // =========================================================================
    // CUSTOM TRANSITIONS
    // =========================================================================
    
    /**
     * Apply custom animation resources
     * 
     * @param activity The current activity
     * @param enterAnim Enter animation resource ID
     * @param exitAnim Exit animation resource ID
     */
    fun applyCustomTransition(activity: Activity, enterAnim: Int, exitAnim: Int) {
        applyTransition(
            activity = activity,
            enterAnim = enterAnim,
            exitAnim = exitAnim,
            isOpen = true
        )
    }
    
    // =========================================================================
    // INTERNAL HELPER
    // =========================================================================
    
    /**
     * Apply transition with API version handling
     */
    private fun applyTransition(
        activity: Activity,
        enterAnim: Int,
        exitAnim: Int,
        isOpen: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34)
            val overrideType = if (isOpen) {
                Activity.OVERRIDE_TRANSITION_OPEN
            } else {
                Activity.OVERRIDE_TRANSITION_CLOSE
            }
            activity.overrideActivityTransition(overrideType, enterAnim, exitAnim)
        } else {
            // Android 13 and below
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(enterAnim, exitAnim)
        }
    }
}

/**
 * Extension function for Activity to easily apply forward navigation
 */
fun Activity.navigateWithSlide() {
    TransitionHelper.slideInFromRight(this)
}

/**
 * Extension function for Activity to easily apply back navigation
 */
fun Activity.finishWithSlide() {
    finish()
    TransitionHelper.slideOutToRight(this)
}
