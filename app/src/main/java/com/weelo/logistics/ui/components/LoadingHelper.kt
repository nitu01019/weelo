package com.weelo.logistics.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.weelo.logistics.R

/**
 * =============================================================================
 * LOADING HELPER - View-Based Loading States
 * =============================================================================
 * 
 * Helper class for showing loading, error, and empty states in Activities.
 * Works with traditional ViewBinding/XML layouts.
 * 
 * USAGE:
 * ```kotlin
 * // In Activity
 * private lateinit var loadingHelper: LoadingHelper
 * 
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     loadingHelper = LoadingHelper(binding.contentContainer)
 *     
 *     // Show loading
 *     loadingHelper.showLoading()
 *     
 *     // Show error with retry
 *     loadingHelper.showError("Failed to load") { retryRequest() }
 *     
 *     // Show content
 *     loadingHelper.showContent()
 * }
 * ```
 * =============================================================================
 */
class LoadingHelper(
    private val container: ViewGroup,
    private val contentView: View? = null
) {
    private val context: Context = container.context
    
    private var loadingView: View? = null
    private var errorView: View? = null
    private var emptyView: View? = null
    
    /**
     * Show loading state
     */
    fun showLoading(message: String = "Loading...") {
        hideAll()
        contentView?.visibility = View.GONE
        
        if (loadingView == null) {
            loadingView = createLoadingView(message)
            container.addView(loadingView)
        } else {
            loadingView?.visibility = View.VISIBLE
            loadingView?.findViewById<TextView>(R.id.tvLoadingMessage)?.text = message
        }
        
        fadeIn(loadingView)
    }
    
    /**
     * Show error state with retry button
     */
    fun showError(
        message: String,
        title: String = "Something went wrong",
        onRetry: (() -> Unit)? = null
    ) {
        hideAll()
        contentView?.visibility = View.GONE
        
        if (errorView == null) {
            errorView = createErrorView(title, message, onRetry)
            container.addView(errorView)
        } else {
            errorView?.visibility = View.VISIBLE
            errorView?.findViewById<TextView>(R.id.tvErrorTitle)?.text = title
            errorView?.findViewById<TextView>(R.id.tvErrorMessage)?.text = message
            errorView?.findViewById<MaterialButton>(R.id.btnRetry)?.setOnClickListener {
                onRetry?.invoke()
            }
        }
        
        fadeIn(errorView)
    }
    
    /**
     * Show network error state
     */
    fun showNetworkError(onRetry: (() -> Unit)? = null) {
        showError(
            title = "No Internet Connection",
            message = "Please check your connection and try again",
            onRetry = onRetry
        )
    }
    
    /**
     * Show empty state
     */
    fun showEmpty(
        title: String,
        subtitle: String,
        @DrawableRes iconRes: Int = R.drawable.ic_info,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        hideAll()
        contentView?.visibility = View.GONE
        
        if (emptyView == null) {
            emptyView = createEmptyView(title, subtitle, iconRes, actionLabel, onAction)
            container.addView(emptyView)
        } else {
            emptyView?.visibility = View.VISIBLE
            emptyView?.findViewById<TextView>(R.id.tvEmptyTitle)?.text = title
            emptyView?.findViewById<TextView>(R.id.tvEmptySubtitle)?.text = subtitle
            emptyView?.findViewById<MaterialButton>(R.id.btnEmptyAction)?.apply {
                if (actionLabel != null) {
                    text = actionLabel
                    visibility = View.VISIBLE
                    setOnClickListener { onAction?.invoke() }
                } else {
                    visibility = View.GONE
                }
            }
        }
        
        fadeIn(emptyView)
    }
    
    /**
     * Show content (hide loading/error/empty)
     */
    fun showContent() {
        hideAll()
        contentView?.visibility = View.VISIBLE
        fadeIn(contentView)
    }
    
    /**
     * Hide all state views
     */
    private fun hideAll() {
        loadingView?.visibility = View.GONE
        errorView?.visibility = View.GONE
        emptyView?.visibility = View.GONE
    }
    
    /**
     * Create loading view programmatically
     */
    private fun createLoadingView(message: String): View {
        return LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            
            // Progress indicator
            addView(ProgressBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(64.dpToPx(), 64.dpToPx())
                indeterminateTintList = ContextCompat.getColorStateList(context, R.color.primary)
            })
            
            // Message
            addView(TextView(context).apply {
                id = R.id.tvLoadingMessage
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24.dpToPx() }
                text = message
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 16f
            })
        }
    }
    
    /**
     * Create error view programmatically
     */
    private fun createErrorView(
        title: String,
        message: String,
        onRetry: (() -> Unit)?
    ): View {
        return LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32.dpToPx(), 32.dpToPx(), 32.dpToPx(), 32.dpToPx())
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            
            // Error icon
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(88.dpToPx(), 88.dpToPx())
                setImageResource(R.drawable.ic_error_outline)
                imageTintList = ContextCompat.getColorStateList(context, R.color.error)
            })
            
            // Title
            addView(TextView(context).apply {
                id = R.id.tvErrorTitle
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24.dpToPx() }
                text = title
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            
            // Message
            addView(TextView(context).apply {
                id = R.id.tvErrorMessage
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpToPx() }
                text = message
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
            })
            
            // Retry button
            if (onRetry != null) {
                addView(MaterialButton(context).apply {
                    id = R.id.btnRetry
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24.dpToPx() }
                    text = "Try Again"
                    setOnClickListener { onRetry.invoke() }
                })
            }
        }
    }
    
    /**
     * Create empty view programmatically
     */
    private fun createEmptyView(
        title: String,
        subtitle: String,
        @DrawableRes iconRes: Int,
        actionLabel: String?,
        onAction: (() -> Unit)?
    ): View {
        return LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32.dpToPx(), 32.dpToPx(), 32.dpToPx(), 32.dpToPx())
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            
            // Icon
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(88.dpToPx(), 88.dpToPx())
                setImageResource(iconRes)
                imageTintList = ContextCompat.getColorStateList(context, R.color.primary)
            })
            
            // Title
            addView(TextView(context).apply {
                id = R.id.tvEmptyTitle
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24.dpToPx() }
                text = title
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            
            // Subtitle
            addView(TextView(context).apply {
                id = R.id.tvEmptySubtitle
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpToPx() }
                text = subtitle
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
            })
            
            // Action button
            if (actionLabel != null) {
                addView(MaterialButton(context).apply {
                    id = R.id.btnEmptyAction
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24.dpToPx() }
                    text = actionLabel
                    setOnClickListener { onAction?.invoke() }
                })
            }
        }
    }
    
    /**
     * Fade in animation
     */
    private fun fadeIn(view: View?) {
        view?.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
            fillAfter = true
        })
    }
    
    /**
     * DP to PX conversion
     */
    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}
