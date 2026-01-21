package com.weelo.logistics.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ProfileViewModel - Handles profile data and operations
 * 
 * Features:
 * - Load profile from backend
 * - Update profile to backend (saves to database)
 * - Logout functionality
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Load profile from backend
     */
    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            try {
                when (val result = authRepository.getProfile()) {
                    is Result.Success -> {
                        val user = result.data.data?.profile
                        if (user != null) {
                            _uiState.value = ProfileUiState.Success(
                                profile = ProfileData(
                                    id = user.id,
                                    phone = user.phone,
                                    name = user.name ?: "",
                                    email = user.email,
                                    company = user.company,
                                    gstNumber = user.gstNumber,
                                    profilePhoto = user.profilePhoto
                                )
                            )
                        } else {
                            // Profile not found - use fallback from token manager
                            loadFallbackProfile()
                        }
                    }
                    is Result.Error -> {
                        // API error - use fallback from token manager
                        loadFallbackProfile()
                    }
                    is Result.Loading -> {
                        _uiState.value = ProfileUiState.Loading
                    }
                }
            } catch (e: Exception) {
                // Any unexpected error - use fallback
                android.util.Log.e("ProfileViewModel", "Error loading profile: ${e.message}", e)
                loadFallbackProfile()
            }
        }
    }
    
    /**
     * Load fallback profile from token manager when API fails
     */
    private fun loadFallbackProfile() {
        val phone = tokenManager.getUserPhone() ?: "Unknown"
        val userId = tokenManager.getUserId() ?: ""
        
        _uiState.value = ProfileUiState.Success(
            profile = ProfileData(
                id = userId,
                phone = phone,
                name = "",
                email = null,
                company = null,
                gstNumber = null,
                profilePhoto = null
            )
        )
    }

    /**
     * Update profile to backend
     * This saves the data to the database
     */
    fun updateProfile(
        name: String,
        email: String?,
        company: String?,
        gstNumber: String?
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            try {
                when (val result = authRepository.updateProfile(
                    name = name,
                    email = email,
                    company = company,
                    gstNumber = gstNumber
                )) {
                    is Result.Success -> {
                        val user = result.data.data?.profile
                        _uiState.value = ProfileUiState.Saved
                        
                        // Then show updated profile
                        if (user != null) {
                            _uiState.value = ProfileUiState.Success(
                                profile = ProfileData(
                                    id = user.id,
                                    phone = user.phone,
                                    name = user.name ?: name,
                                    email = user.email ?: email,
                                    company = user.company ?: company,
                                    gstNumber = user.gstNumber ?: gstNumber,
                                    profilePhoto = user.profilePhoto
                                )
                            )
                        } else {
                            // Profile saved but response empty - show with input values
                            val userId = tokenManager.getUserId() ?: ""
                            val phone = tokenManager.getUserPhone() ?: ""
                            _uiState.value = ProfileUiState.Success(
                                profile = ProfileData(
                                    id = userId,
                                    phone = phone,
                                    name = name,
                                    email = email,
                                    company = company,
                                    gstNumber = gstNumber,
                                    profilePhoto = null
                                )
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.value = ProfileUiState.Error(result.exception.message ?: "Failed to save profile")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error updating profile: ${e.message}", e)
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    /**
     * Logout user
     */
    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _uiState.value = ProfileUiState.LoggedOut
        }
    }
}

/**
 * Profile UI State
 */
sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: ProfileData) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
    object Saved : ProfileUiState()
    object LoggedOut : ProfileUiState()
}

/**
 * Profile Data Model
 */
data class ProfileData(
    val id: String,
    val phone: String,
    val name: String,
    val email: String?,
    val company: String?,
    val gstNumber: String?,
    val profilePhoto: String?
)
