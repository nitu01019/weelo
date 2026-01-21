package com.weelo.logistics.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Login ViewModel
 * 
 * Handles authentication logic for customer login flow.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentPhone: String = ""
    private var lastMockOtp: String? = null

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun sendOtp(phone: String) {
        currentPhone = phone
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            
            when (val result = authRepository.sendOtp(phone)) {
                is Result.Success -> {
                    // In dev mode, backend returns the OTP
                    lastMockOtp = result.data.otp
                    _uiState.value = LoginUiState.OtpSent(phone, lastMockOtp)
                }
                is Result.Error -> {
                    _uiState.value = LoginUiState.Error(result.message ?: "Failed to send OTP")
                }
                is Result.Loading -> {
                    _uiState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            
            when (val result = authRepository.verifyOtp(currentPhone, otp)) {
                is Result.Success -> {
                    _uiState.value = LoginUiState.Success
                }
                is Result.Error -> {
                    _uiState.value = LoginUiState.Error(result.message ?: "Invalid OTP")
                }
                is Result.Loading -> {
                    _uiState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun resendOtp() {
        if (currentPhone.isNotEmpty()) {
            sendOtp(currentPhone)
        }
    }
}

/**
 * UI State for Login Screen
 */
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class OtpSent(val phone: String, val mockOtp: String?) : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
