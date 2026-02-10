package com.weelo.logistics.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.local.preferences.PreferencesManager
import com.weelo.logistics.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =============================================================================
 * LOGIN VIEW MODEL - With Profile Caching
 * =============================================================================
 * 
 * SCALABILITY:
 * - Saves profile to cache after successful login
 * - Next app open shows data instantly (Rapido-style)
 * 
 * ACCOUNT CONTAINERIZATION:
 * - Profile tied to logged-in user
 * - Cache cleared on logout (in ProfileViewModel)
 * 
 * CODING STANDARDS:
 * - Clean authentication flow
 * - Proper error handling
 * =============================================================================
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentPhone: String = ""
    private var lastMockOtp: String? = null
    
    // Prevent duplicate OTP verification requests
    private var isVerifying: Boolean = false

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun sendOtp(phone: String) {
        currentPhone = phone
        isVerifying = false  // Reset verification flag when sending new OTP
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
        // Prevent duplicate verification requests (auto-submit + button click)
        if (isVerifying || _uiState.value is LoginUiState.Success) {
            return
        }
        
        isVerifying = true
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            
            when (val result = authRepository.verifyOtp(currentPhone, otp)) {
                is Result.Success -> {
                    // RAPIDO-STYLE: Save profile to cache immediately after login
                    // This ensures next app open shows data instantly
                    try {
                        // Fetch profile and cache it
                        val profileResult = authRepository.getProfile()
                        if (profileResult is Result.Success) {
                            val user = profileResult.data.data?.profile
                            if (user != null) {
                                preferencesManager.saveProfile(
                                    name = user.name ?: "",
                                    phone = user.phone,
                                    email = user.email,
                                    role = "customer"
                                )
                                timber.log.Timber.d("Profile cached after login: ${user.name}, ${user.phone}")
                            }
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.e("Failed to cache profile after login: ${e.message}")
                        // Don't fail login if caching fails
                    }
                    
                    _uiState.value = LoginUiState.Success
                }
                is Result.Error -> {
                    isVerifying = false  // Allow retry on error
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
