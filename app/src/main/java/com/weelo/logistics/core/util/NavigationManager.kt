package com.weelo.logistics.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * NAVIGATION MANAGER - Centralized, Safe Navigation
 * =============================================================================
 * 
 * Provides centralized navigation with:
 * - Debounce protection against rapid clicks
 * - Consistent transitions throughout the app
 * - Activity result handling
 * - Navigation stack management
 * - Safe navigation with error handling
 * 
 * USAGE:
 * ```kotlin
 * // Simple navigation
 * navigationManager.navigateTo<HomeActivity>(this)
 * 
 * // With extras
 * navigationManager.navigateTo<BookingDetailActivity>(this) {
 *     putString("BOOKING_ID", bookingId)
 * }
 * 
 * // With flags
 * navigationManager.navigateTo<LoginActivity>(
 *     context = this,
 *     flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
 * )
 * 
 * // Navigate for result
 * navigationManager.navigateForResult(launcher, Intent(...))
 * ```
 * =============================================================================
 */
@Singleton
class NavigationManager @Inject constructor() {
    
    companion object {
        @PublishedApi
        internal const val TAG = "NavigationManager"
        
        // Debounce: Minimum time between navigations (in ms)
        @PublishedApi
        internal const val NAVIGATION_DEBOUNCE_MS = 500L
        
        // Singleton for extension function access
        @Volatile
        private var INSTANCE: NavigationManager? = null
        
        fun getInstance(): NavigationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NavigationManager().also { INSTANCE = it }
            }
        }
    }
    
    // Last navigation timestamp for debounce
    @PublishedApi
    internal val lastNavigationTime = AtomicLong(0)
    
    // Flag to track if navigation is in progress
    @PublishedApi
    internal val isNavigating = AtomicBoolean(false)
    
    init {
        INSTANCE = this
    }
    
    /**
     * Check if navigation is allowed (debounce check)
     */
    @PublishedApi
    internal fun canNavigate(): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastNavigationTime.get()
        
        if (now - lastTime < NAVIGATION_DEBOUNCE_MS) {
            Timber.d("$TAG: Navigation blocked (debounce)")
            return false
        }
        
        if (!isNavigating.compareAndSet(false, true)) {
            Timber.d("$TAG: Navigation blocked (already navigating)")
            return false
        }
        
        lastNavigationTime.set(now)
        
        // Reset navigating flag after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isNavigating.set(false)
        }, NAVIGATION_DEBOUNCE_MS)
        
        return true
    }
    
    /**
     * Navigate to an activity with type-safe generics
     * 
     * @param context Current context
     * @param transition Type of transition animation
     * @param flags Intent flags (optional)
     * @param finishCurrent Whether to finish current activity
     * @param extras Lambda to add extras to intent
     */
    inline fun <reified T : Activity> navigateTo(
        context: Context,
        transition: TransitionType = TransitionType.SLIDE_RIGHT,
        flags: Int? = null,
        finishCurrent: Boolean = false,
        extras: Bundle.() -> Unit = {}
    ) {
        if (!canNavigate()) return
        
        try {
            val intent = Intent(context, T::class.java).apply {
                flags?.let { this.flags = it }
                putExtras(Bundle().apply(extras))
            }
            
            context.startActivity(intent)
            
            // Apply transition if context is an Activity
            if (context is Activity) {
                applyTransition(context, transition)
                
                if (finishCurrent) {
                    context.finish()
                }
            }
            
            Timber.d("$TAG: Navigated to ${T::class.simpleName}")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Navigation failed to ${T::class.simpleName}")
            isNavigating.set(false)
        }
    }
    
    /**
     * Navigate with explicit intent
     */
    fun navigateTo(
        context: Context,
        intent: Intent,
        transition: TransitionType = TransitionType.SLIDE_RIGHT,
        finishCurrent: Boolean = false
    ) {
        if (!canNavigate()) return
        
        try {
            context.startActivity(intent)
            
            if (context is Activity) {
                applyTransition(context, transition)
                
                if (finishCurrent) {
                    context.finish()
                }
            }
            
            Timber.d("$TAG: Navigated with intent")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Navigation failed")
            isNavigating.set(false)
        }
    }
    
    /**
     * Navigate for result using ActivityResultLauncher
     */
    fun navigateForResult(
        launcher: ActivityResultLauncher<Intent>,
        intent: Intent,
        context: Context? = null,
        transition: TransitionType = TransitionType.SLIDE_RIGHT
    ) {
        if (!canNavigate()) return
        
        try {
            launcher.launch(intent)
            
            // Apply transition if context is provided
            if (context is Activity) {
                applyTransition(context, transition)
            }
            
            Timber.d("$TAG: Navigated for result")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Navigate for result failed")
            isNavigating.set(false)
        }
    }
    
    /**
     * Go back with animation
     */
    fun goBack(activity: Activity, transition: TransitionType = TransitionType.SLIDE_BACK) {
        if (!canNavigate()) return
        
        try {
            activity.finish()
            applyTransition(activity, transition)
            Timber.d("$TAG: Going back")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Go back failed")
            isNavigating.set(false)
        }
    }
    
    /**
     * Clear back stack and navigate to activity
     */
    inline fun <reified T : Activity> navigateAndClearStack(
        context: Context,
        transition: TransitionType = TransitionType.FADE,
        extras: Bundle.() -> Unit = {}
    ) {
        navigateTo<T>(
            context = context,
            transition = transition,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            finishCurrent = false,
            extras = extras
        )
    }
    
    /**
     * Navigate to activity and clear top
     */
    inline fun <reified T : Activity> navigateClearTop(
        context: Context,
        transition: TransitionType = TransitionType.SLIDE_RIGHT,
        extras: Bundle.() -> Unit = {}
    ) {
        navigateTo<T>(
            context = context,
            transition = transition,
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            finishCurrent = false,
            extras = extras
        )
    }
    
    /**
     * Apply transition animation
     */
    fun applyTransition(activity: Activity, transition: TransitionType) {
        when (transition) {
            TransitionType.SLIDE_RIGHT -> TransitionHelper.slideInFromRight(activity)
            TransitionType.SLIDE_BACK -> TransitionHelper.slideOutToRight(activity)
            TransitionType.SLIDE_UP -> TransitionHelper.slideUp(activity)
            TransitionType.SLIDE_DOWN -> TransitionHelper.slideDown(activity)
            TransitionType.FADE -> TransitionHelper.fadeIn(activity)
            TransitionType.NONE -> TransitionHelper.noAnimation(activity)
        }
    }
    
    /**
     * Types of navigation transitions
     */
    enum class TransitionType {
        SLIDE_RIGHT,    // Forward navigation (right to left)
        SLIDE_BACK,     // Back navigation (left to right)
        SLIDE_UP,       // Modal/bottom sheet style
        SLIDE_DOWN,     // Dismiss modal
        FADE,           // Fade in/out
        NONE            // No animation
    }
}

// =============================================================================
// EXTENSION FUNCTIONS
// =============================================================================

/**
 * Extension to navigate with debounce protection
 */
inline fun <reified T : Activity> Activity.navigateTo(
    transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_RIGHT,
    flags: Int? = null,
    finishCurrent: Boolean = false,
    extras: Bundle.() -> Unit = {}
) {
    NavigationManager.getInstance().navigateTo<T>(
        context = this,
        transition = transition,
        flags = flags,
        finishCurrent = finishCurrent,
        extras = extras
    )
}

/**
 * Extension to go back with animation
 */
fun Activity.goBackSafe(transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_BACK) {
    NavigationManager.getInstance().goBack(this, transition)
}

/**
 * Extension to navigate and clear stack
 */
inline fun <reified T : Activity> Activity.navigateAndClearStack(
    transition: NavigationManager.TransitionType = NavigationManager.TransitionType.FADE,
    extras: Bundle.() -> Unit = {}
) {
    NavigationManager.getInstance().navigateAndClearStack<T>(
        context = this,
        transition = transition,
        extras = extras
    )
}

/**
 * Debounced click listener to prevent rapid clicks
 */
fun android.view.View.setDebouncedClickListener(
    debounceTimeMs: Long = 500L,
    onClick: (android.view.View) -> Unit
) {
    var lastClickTime = 0L
    
    setOnClickListener { view ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceTimeMs) {
            lastClickTime = currentTime
            onClick(view)
        }
    }
}
