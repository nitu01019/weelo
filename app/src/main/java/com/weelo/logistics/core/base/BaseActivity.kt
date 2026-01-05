package com.weelo.logistics.core.base

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base Activity for all activities in the app
 * Provides:
 * - Common error handling
 * - Loading state management
 * - Lifecycle-aware flow collection
 * - Memory leak prevention
 * - Scalable architecture foundation
 */
abstract class BaseActivity : AppCompatActivity() {

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
        try {
            initializeUI()
            setupObservers()
        } catch (e: Exception) {
            handleError(e, "Failed to initialize screen")
        }
    }

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
     */
    protected fun handleError(throwable: Throwable, fallbackMessage: String = "An error occurred") {
        // Log error for debugging (in production, send to crash analytics)
        throwable.printStackTrace()
        
        // Show user-friendly message
        val message = when (throwable) {
            is java.net.UnknownHostException -> "No internet connection"
            is java.net.SocketTimeoutException -> "Connection timeout. Please try again"
            is IllegalStateException -> "Invalid operation. Please try again"
            else -> throwable.message ?: fallbackMessage
        }
        
        showError(message, "Retry") {
            // Override in child class if retry logic needed
        }
    }

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
