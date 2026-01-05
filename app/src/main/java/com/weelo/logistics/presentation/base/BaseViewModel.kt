package com.weelo.logistics.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Simple Base ViewModel with error handling and loading states
 */
abstract class BaseViewModel : ViewModel() {

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Coroutine exception handler
     */
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Error in ViewModel")
        _error.value = throwable.message ?: "An error occurred"
        _isLoading.value = false
    }

    /**
     * Execute with loading and error handling
     */
    protected fun <T> executeWithLoading(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> T
    ) {
        viewModelScope.launch(exceptionHandler) {
            try {
                _isLoading.value = true
                _error.value = null
                block()
            } catch (e: Exception) {
                Timber.e(e, "Error")
                _error.value = e.message ?: "An error occurred"
                onError?.invoke(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear error
     */
    open fun clearError() {
        _error.value = null
    }
}
