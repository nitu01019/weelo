package com.weelo.logistics.ui.utils

import android.content.Context
import android.graphics.Typeface
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.weelo.logistics.R

/**
 * Animation Helper - Reusable UI Animations
 * 
 * Provides hardware-accelerated animations for common UI patterns.
 * 
 * SCALABILITY:
 * - Static methods (no instance overhead)
 * - GPU-accelerated properties (scaleX, scaleY)
 * - Efficient for millions of users
 * 
 * MODULARITY:
 * - Single source of truth for animations
 * - Reusable across activities
 * - Easy to maintain and update
 */
object AnimationHelper {
    
    /**
     * Animate button to selected state
     * 
     * Uses hardware-accelerated properties for 60fps performance:
     * - scaleX/scaleY: GPU-rendered
     * - Duration: 200ms + 100ms (smooth transition)
     * 
     * @param context Context for color resources
     * @param button CardView to animate
     * @param textView TextView inside button
     */
    fun animateButtonSelected(
        context: Context,
        button: CardView,
        textView: TextView
    ) {
        button.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                button.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.ic_launcher_background)
                )
                button.cardElevation = 4f
                textView.setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                textView.setTypeface(null, Typeface.BOLD)
                
                // Return to normal scale
                button.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    /**
     * Animate button to unselected state
     * 
     * Instant color change (no animation) for better UX.
     * Animation only on selection feels more responsive.
     * 
     * @param context Context for color resources
     * @param button CardView to update
     * @param textView TextView inside button
     */
    fun animateButtonUnselected(
        context: Context,
        button: CardView,
        textView: TextView
    ) {
        button.setCardBackgroundColor(
            ContextCompat.getColor(context, R.color.background_gray)
        )
        button.cardElevation = 0f
        textView.setTextColor(
            ContextCompat.getColor(context, R.color.text_secondary)
        )
        textView.setTypeface(null, Typeface.NORMAL)
    }
}
