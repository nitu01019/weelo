package com.weelo.logistics.core.util

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.weelo.logistics.R

/**
 * BottomSheetHelper - Centralized Bottom Sheet Configuration
 * ===========================================================
 * 
 * Purpose:
 * - Provides consistent bottom sheet behavior across the app
 * - Optimized for scalability (millions of users)
 * - Easy to maintain - single source of truth for bottom sheet config
 * 
 * Architecture Benefits:
 * - MODULAR: All bottom sheet logic in one place
 * - SCALABLE: Efficient memory usage, no leaks
 * - MAINTAINABLE: Backend developers can easily understand and modify
 * - TESTABLE: Can be unit tested independently
 * 
 * Usage:
 * ```kotlin
 * val dialog = BottomSheetDialog(context)
 * dialog.setContentView(view)
 * BottomSheetHelper.configureBottomSheet(dialog, BottomSheetHelper.Style.COMPACT)
 * dialog.show()
 * ```
 * 
 * @author Weelo Team
 */
object BottomSheetHelper {

    /**
     * Bottom sheet display styles
     */
    enum class Style {
        /** Compact - wraps content, good for Trip Details, Search dialogs */
        COMPACT,
        
        /** Fixed height at 75% of screen - good for selection lists */
        FIXED_MEDIUM,
        
        /** Fixed height at 85% of screen - good for large forms */
        FIXED_LARGE,
        
        /** Full screen - for complex flows */
        FULL_SCREEN
    }

    /**
     * Configuration constants - tuned for optimal UX
     */
    private object Config {
        const val COMPACT_MAX_HEIGHT_PERCENT = 0.70f      // 70% of screen max
        const val FIXED_MEDIUM_HEIGHT_PERCENT = 0.75f    // 75% of screen
        const val FIXED_LARGE_HEIGHT_PERCENT = 0.60f     // 60% of screen for truck type selection bottom sheet
        const val CORNER_RADIUS_DP = 24                   // Rounded corners
    }

    /**
     * Configure a BottomSheetDialog with the specified style.
     * 
     * This method handles all the common configuration needed for
     * consistent bottom sheet behavior:
     * - Sets rounded background
     * - Configures behavior (draggable, expanded state, etc.)
     * - Sets appropriate height based on style
     * - Ensures bottom sheet is attached to bottom of screen
     * 
     * @param dialog The BottomSheetDialog to configure
     * @param style The display style to apply
     * @param isDismissable Whether the sheet can be dismissed by dragging
     */
    fun configureBottomSheet(
        dialog: BottomSheetDialog,
        style: Style = Style.COMPACT,
        isDismissable: Boolean = true
    ) {
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            
            bottomSheet?.let { sheet ->
                // Apply rounded background
                sheet.setBackgroundResource(R.drawable.bottom_sheet_rounded_bg)
                
                // Get screen height for calculations
                val screenHeight = sheet.context.resources.displayMetrics.heightPixels
                
                // Configure layout params based on style
                (sheet.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.height = when (style) {
                        Style.COMPACT -> ViewGroup.LayoutParams.WRAP_CONTENT
                        Style.FIXED_MEDIUM -> (screenHeight * Config.FIXED_MEDIUM_HEIGHT_PERCENT).toInt()
                        Style.FIXED_LARGE -> (screenHeight * Config.FIXED_LARGE_HEIGHT_PERCENT).toInt()
                        Style.FULL_SCREEN -> ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    params.gravity = Gravity.BOTTOM
                    sheet.layoutParams = params
                }
                
                // Configure behavior - same approach as MultiTruckSelectionBottomSheet
                val behavior = BottomSheetBehavior.from(sheet)
                
                // Calculate max height based on style
                val maxHeight = when (style) {
                    Style.COMPACT -> (screenHeight * Config.COMPACT_MAX_HEIGHT_PERCENT).toInt()
                    Style.FIXED_MEDIUM -> (screenHeight * Config.FIXED_MEDIUM_HEIGHT_PERCENT).toInt()
                    Style.FIXED_LARGE -> (screenHeight * Config.FIXED_LARGE_HEIGHT_PERCENT).toInt()
                    Style.FULL_SCREEN -> screenHeight
                }
                
                // Set fixed height and gravity to BOTTOM - ensures it stays at bottom, not middle
                val params = sheet.layoutParams
                params.height = maxHeight
                if (params is android.view.ViewGroup.MarginLayoutParams) {
                    (params as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.BOTTOM
                }
                sheet.layoutParams = params
                
                behavior.apply {
                    peekHeight = maxHeight
                    this.maxHeight = maxHeight
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                    isDraggable = isDismissable
                    isHideable = isDismissable
                    isFitToContents = true
                }
                
                // Force layout update
                sheet.requestLayout()
            }
        }
    }

    /**
     * Configure a BottomSheetBehavior for an embedded bottom sheet (not dialog).
     * Used for bottom sheets that are part of an Activity's layout.
     * 
     * @param bottomSheet The bottom sheet view
     * @param style The display style to apply
     */
    fun configureEmbeddedBottomSheet(
        bottomSheet: View,
        style: Style = Style.COMPACT
    ) {
        val behavior = BottomSheetBehavior.from(bottomSheet)
        val screenHeight = bottomSheet.context.resources.displayMetrics.heightPixels
        
        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = true
            isHideable = true
            isFitToContents = (style == Style.COMPACT)
            
            peekHeight = when (style) {
                Style.COMPACT -> BottomSheetBehavior.PEEK_HEIGHT_AUTO
                Style.FIXED_MEDIUM -> (screenHeight * Config.FIXED_MEDIUM_HEIGHT_PERCENT).toInt()
                Style.FIXED_LARGE -> (screenHeight * Config.FIXED_LARGE_HEIGHT_PERCENT).toInt()
                Style.FULL_SCREEN -> screenHeight
            }
        }
    }

    /**
     * Get optimal height for a bottom sheet based on content.
     * Useful for dynamic content sizing.
     * 
     * @param context The context
     * @param contentHeightDp Estimated content height in dp
     * @return The optimal height in pixels
     */
    fun getOptimalHeight(context: Context, contentHeightDp: Int): Int {
        val density = context.resources.displayMetrics.density
        val screenHeight = context.resources.displayMetrics.heightPixels
        val contentHeightPx = (contentHeightDp * density).toInt()
        val maxHeight = (screenHeight * Config.COMPACT_MAX_HEIGHT_PERCENT).toInt()
        
        return minOf(contentHeightPx, maxHeight)
    }

    /**
     * Extension function to easily configure any BottomSheetDialog
     */
    fun BottomSheetDialog.configure(
        style: Style = Style.COMPACT,
        isDismissable: Boolean = true
    ): BottomSheetDialog {
        configureBottomSheet(this, style, isDismissable)
        return this
    }
}
