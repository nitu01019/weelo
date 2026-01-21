package com.weelo.logistics.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.weelo.logistics.R
import com.weelo.logistics.core.util.NetworkMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * =============================================================================
 * OFFLINE BANNER VIEW - Shows network status
 * =============================================================================
 * 
 * Custom view that automatically shows/hides based on network connectivity.
 * 
 * USAGE in XML:
 * ```xml
 * <com.weelo.logistics.ui.components.OfflineBannerView
 *     android:id="@+id/offlineBanner"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"/>
 * ```
 * 
 * USAGE in Activity:
 * ```kotlin
 * binding.offlineBanner.observeNetworkState(this)
 * ```
 * =============================================================================
 */
class OfflineBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {
    
    private val networkMonitor = NetworkMonitor.getInstance(context)
    private var scope: CoroutineScope? = null
    private var wasOffline = false
    
    private val iconView: ImageView
    private val messageView: TextView
    
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(16.dpToPx(), 10.dpToPx(), 16.dpToPx(), 10.dpToPx())
        visibility = View.GONE
        
        // Icon
        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(18.dpToPx(), 18.dpToPx())
            setImageResource(R.drawable.ic_wifi_off)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
        }
        addView(iconView)
        
        // Spacer
        addView(View(context).apply {
            layoutParams = LayoutParams(8.dpToPx(), 0)
        })
        
        // Message
        messageView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
            text = "No Internet Connection"
        }
        addView(messageView)
    }
    
    /**
     * Start observing network state with lifecycle awareness
     */
    fun observeNetworkState(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        scope?.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                updateState(isOnline)
            }
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        scope?.cancel()
        scope = null
    }
    
    private fun updateState(isOnline: Boolean) {
        when {
            !isOnline -> showOffline()
            wasOffline && isOnline -> showBackOnline()
            else -> hide()
        }
        wasOffline = !isOnline
    }
    
    private fun showOffline() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.error))
        iconView.setImageResource(R.drawable.ic_wifi_off)
        messageView.text = "No Internet Connection"
        slideIn()
    }
    
    private fun showBackOnline() {
        setBackgroundColor(ContextCompat.getColor(context, R.color.success))
        iconView.setImageResource(R.drawable.ic_wifi_off) // TODO: Add wifi icon
        messageView.text = "Back Online"
        slideIn()
        
        // Auto-hide after 3 seconds
        postDelayed({
            slideOut()
        }, 3000)
    }
    
    private fun hide() {
        if (visibility == View.VISIBLE) {
            slideOut()
        }
    }
    
    private fun slideIn() {
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_in_from_top))
        }
    }
    
    private fun slideOut() {
        startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_out_to_top).apply {
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        })
    }
    
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
