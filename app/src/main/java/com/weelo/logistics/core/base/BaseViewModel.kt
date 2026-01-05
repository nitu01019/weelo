package com.weelo.logistics.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for all ViewModels in the app
 * Provides:
 * - Common state management (Loading, Error, Success)
 * - Coroutine error handling
 * - Scalable state pattern
 * - Memory-efficient state management
 */
abstract class BaseViewModel : ViewModel() {

    // UI State management
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Error handler for all coroutines
    protected val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleError(throwable)
    }

    /**
     * Execute a suspend function with automatic loading and error handling
     * Perfect for API calls and long-running operations
     */
    protected fun executeWithLoading(
        showLoading: Boolean = true,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                if (showLoading) _uiState.value = UiState.Loading
                block()
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * Handle errors and update UI state
     */
    protected open fun handleError(throwable: Throwable) {
        val message = when (throwable) {
            is java.net.UnknownHostException -> "No internet connection"
            is java.net.SocketTimeoutException -> "Connection timeout"
            is IllegalArgumentException -> "Invalid input: ${throwable.message}"
            else -> throwable.message ?: "An unexpected error occurred"
        }
        _uiState.value = UiState.Error(message)
    }

    /**
     * Reset UI state to idle
     */
    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    /**
     * Common UI States
     */
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
