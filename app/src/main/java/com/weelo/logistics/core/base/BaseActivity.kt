package com.weelo.logistics.core.base

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.core.util.NavigationManager
import com.weelo.logistics.core.util.TransitionHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * =============================================================================
 * BASE ACTIVITY - Foundation for All Screens
 * =============================================================================
 * 
 * Provides:
 * - Smooth right-to-left navigation transitions
 * - Debounce-protected navigation
 * - Common error handling with WeeloException support
 * - Loading state management
 * - Lifecycle-aware flow collection
 * - Memory leak prevention
 * - Scalable architecture foundation
 * 
 * All activities should extend this class for consistent behavior.
 * =============================================================================
 */
abstract class BaseActivity : AppCompatActivity() {
    
    // Navigation manager for debounce-protected navigation
    protected val navigationManager: NavigationManager by lazy { 
        NavigationManager.getInstance() 
    }
    
    /**
     * Override to disable smooth back navigation for specific screens
     * Default is true (smooth transitions enabled)
     */
    protected open val enableSmoothBackNavigation: Boolean = true

    /**
     * Called after onCreate to initialize UI components
     * Override this instead of onCreate for better structure
     */
    abstract fun initializeUI()

    /**
     * Called to set up observers for ViewModels
     * Override this to observe LiveData/StateFlow
     */
    abstract fun setupObservers()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup smooth back navigation
        setupBackNavigation()
        
        try {
            initializeUI()
            setupObservers()
        } catch (e: Exception) {
            handleError(e, "Failed to initialize screen")
        }
    }
    
    /**
     * Setup smooth back navigation with right-to-left slide
     */
    private fun setupBackNavigation() {
        if (enableSmoothBackNavigation) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithSlideAnimation()
                }
            })
        }
    }
    
    /**
     * Finish activity with smooth slide-out animation
     * Call this instead of finish() for consistent transitions
     */
    protected fun finishWithSlideAnimation() {
        finish()
        TransitionHelper.slideOutToRight(this)
    }
    
    /**
     * Navigate to activity with smooth slide-in animation
     * Call this after startActivity() for consistent transitions
     */
    protected fun applyEnterTransition() {
        TransitionHelper.slideInFromRight(this)
    }
    
    // =========================================================================
    // DEBOUNCE-PROTECTED NAVIGATION
    // =========================================================================
    
    /**
     * Navigate to activity with debounce protection
     * Prevents rapid double-clicks from opening multiple screens
     */
    protected inline fun <reified T : Activity> navigateTo(
        transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_RIGHT,
        flags: Int? = null,
        finishCurrent: Boolean = false,
        extras: Bundle.() -> Unit = {}
    ) {
        navigationManager.navigateTo<T>(
            context = this,
            transition = transition,
            flags = flags,
            finishCurrent = finishCurrent,
            extras = extras
        )
    }
    
    /**
     * Navigate with intent and debounce protection
     */
    protected fun navigateTo(
        intent: Intent,
        transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_RIGHT,
        finishCurrent: Boolean = false
    ) {
        navigationManager.navigateTo(
            context = this,
            intent = intent,
            transition = transition,
            finishCurrent = finishCurrent
        )
    }
    
    /**
     * Navigate for result with debounce protection
     */
    protected fun navigateForResult(
        launcher: ActivityResultLauncher<Intent>,
        intent: Intent,
        transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_RIGHT
    ) {
        navigationManager.navigateForResult(launcher, intent, this, transition)
    }
    
    /**
     * Navigate and clear entire back stack
     */
    protected inline fun <reified T : Activity> navigateAndClearStack(
        transition: NavigationManager.TransitionType = NavigationManager.TransitionType.FADE,
        extras: Bundle.() -> Unit = {}
    ) {
        navigationManager.navigateAndClearStack<T>(
            context = this,
            transition = transition,
            extras = extras
        )
    }
    
    /**
     * Go back with debounce protection
     */
    protected fun goBack(transition: NavigationManager.TransitionType = NavigationManager.TransitionType.SLIDE_BACK) {
        navigationManager.goBack(this, transition)
    }

    // =========================================================================
    // LOADING STATE
    // =========================================================================
    
    /**
     * Show loading indicator
     * Override to customize loading UI
     */
    protected open fun showLoading() {
        // Default implementation - can be overridden
    }

    /**
     * Hide loading indicator
     */
    protected open fun hideLoading() {
        // Default implementation - can be overridden
    }

    // =========================================================================
    // ERROR HANDLING
    // =========================================================================
    
    /**
     * Show error message with proper handling
     */
    protected fun showError(message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        findViewById<View>(android.R.id.content)?.let { view ->
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            if (actionText != null && action != null) {
                snackbar.setAction(actionText) { action() }
            }
            snackbar.show()
        }
    }

    /**
     * Show toast message
     */
    protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /**
     * Handle errors with proper logging and user feedback
     * Supports WeeloException hierarchy for better error handling
     */
    protected open fun handleError(throwable: Throwable, fallbackMessage: String = "An error occurred") {
        // Log error for debugging
        Timber.e(throwable, "Error in ${this::class.simpleName}")
        
        // Handle WeeloException types
        when (throwable) {
            is WeeloException -> handleWeeloException(throwable)
            
            is java.net.UnknownHostException -> 
                showError("No internet connection", "Retry") { onRetry() }
            
            is java.net.SocketTimeoutException -> 
                showError("Connection timeout. Please try again", "Retry") { onRetry() }
            
            is IllegalStateException -> 
                showError("Invalid operation. Please try again")
            
            else -> showError(throwable.message ?: fallbackMessage, "Retry") { onRetry() }
        }
    }
    
    /**
     * Handle WeeloException with specific actions
     */
    protected open fun handleWeeloException(exception: WeeloException) {
        val message = exception.getUserMessage()
        
        when {
            exception.requiresReAuth -> {
                showError(message, "Login") {
                    onAuthRequired()
                }
            }
            exception.isRecoverable -> {
                showError(message, "Retry") {
                    onRetry()
                }
            }
            else -> showError(message)
        }
    }
    
    /**
     * Called when retry is requested
     * Override in child class to implement retry logic
     */
    protected open fun onRetry() {
        // Override in child class
    }
    
    /**
     * Called when re-authentication is required
     * Override to navigate to login
     */
    protected open fun onAuthRequired() {
        // Override in child class to navigate to login
        // Example: navigateAndClearStack<LoginActivity>()
    }

    // =========================================================================
    // FLOW COLLECTION
    // =========================================================================
    
    /**
     * Collect flow with lifecycle awareness to prevent memory leaks
     * This ensures flows are only collected when the activity is in STARTED state
     */
    protected fun <T> Flow<T>.collectWithLifecycle(action: (T) -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { action(it) }
            }
        }
    }

    /**
     * Safe navigation - catches exceptions during navigation
     */
    protected fun safeNavigate(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            handleError(e, "Navigation failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources - override in child classes if needed
    }
}
