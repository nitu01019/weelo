package com.weelo.logistics.tutorial

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

/**
 * TutorialOverlayView - Custom view for tutorial spotlight effect
 * 
 * Creates a semi-transparent overlay with spotlight on target views
 * Supports animated highlighting and scanning effects
 * 
 * Modular design - can be removed without affecting other code
 */
class TutorialOverlayView(context: Context) : View(context) {
    
    private val overlayPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)  // 78% black overlay
        style = Paint.Style.FILL
    }
    
    private val spotlightPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#2196F3")  // Blue highlight
        style = Paint.Style.STROKE
        strokeWidth = 8f
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#2196F3"))
    }
    
    private var targetRect: RectF? = null
    private var highlightType: HighlightType = HighlightType.SPOTLIGHT
    private var animatedAlpha = 1f
    private var pulseAnimator: ValueAnimator? = null
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    /**
     * Set the target view to highlight
     */
    fun setTarget(view: View?, type: HighlightType = HighlightType.SPOTLIGHT) {
        this.highlightType = type
        
        if (view == null) {
            targetRect = null
            invalidate()
            return
        }
        
        // Get view position on screen
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        
        // Get overlay position
        val overlayLocation = IntArray(2)
        this.getLocationOnScreen(overlayLocation)
        
        // Calculate relative position
        val left = (location[0] - overlayLocation[0]).toFloat()
        val top = (location[1] - overlayLocation[1]).toFloat()
        val right = left + view.width
        val bottom = top + view.height
        
        // Add padding for better visibility
        val padding = 16f
        targetRect = RectF(
            left - padding,
            top - padding,
            right + padding,
            bottom + padding
        )
        
        // Start pulsing animation
        startPulseAnimation()
        
        invalidate()
    }
    
    /**
     * Start pulsing animation on highlight
     */
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.6f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                animatedAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Stop animations
     */
    fun stopAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw semi-transparent overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        
        targetRect?.let { rect ->
            when (highlightType) {
                HighlightType.SPOTLIGHT -> {
                    // Draw spotlight (clear circle)
                    val centerX = rect.centerX()
                    val centerY = rect.centerY()
                    val radius = maxOf(rect.width(), rect.height()) / 2f + 20f
                    canvas.drawCircle(centerX, centerY, radius, spotlightPaint)
                    
                    // Draw pulsing ring around spotlight
                    highlightPaint.alpha = (255 * animatedAlpha).toInt()
                    canvas.drawCircle(centerX, centerY, radius + 8f, highlightPaint)
                }
                
                HighlightType.RECTANGLE -> {
                    // Draw rectangle spotlight
                    canvas.drawRoundRect(rect, 12f, 12f, spotlightPaint)
                    
                    // Draw pulsing border
                    highlightPaint.alpha = (255 * animatedAlpha).toInt()
                    canvas.drawRoundRect(
                        RectF(rect.left - 4f, rect.top - 4f, rect.right + 4f, rect.bottom + 4f),
                        16f, 16f, highlightPaint
                    )
                }
                
                HighlightType.NONE -> {
                    // Just overlay, no spotlight
                }
            }
        }
    }
    
    /**
     * Clear target and stop animations
     */
    fun clear() {
        stopAnimations()
        targetRect = null
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }
}
