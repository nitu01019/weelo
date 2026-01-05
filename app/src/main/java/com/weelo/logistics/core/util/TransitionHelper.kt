package com.weelo.logistics.core.util

import android.app.Activity
import android.os.Build

/**
 * Helper object for handling activity transitions across different Android versions
 * Prevents black screen issues during navigation
 */
object TransitionHelper {
    
    /**
     * Apply slide-in-left transition when opening a new activity
     * @param activity The current activity
     */
    fun applySlideInLeftTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        }
    }
    
    /**
     * Apply slide-out-right transition when closing an activity
     * @param activity The current activity
     */
    fun applySlideOutRightTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        }
    }
}
