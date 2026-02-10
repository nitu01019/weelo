package com.weelo.logistics.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.local.preferences.PreferencesManager
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =============================================================================
 * PROFILE VIEW MODEL - With Local Caching
 * =============================================================================
 * 
 * SCALABILITY:
 * - Instagram-style caching (loads from cache first, then backend)
 * - No repeated API calls on app restart
 * - Handles millions of concurrent users
 * 
 * EASY UNDERSTANDING:
 * - Load from cache → Show immediately
 * - Fetch from backend → Update cache → Show fresh data
 * 
 * MODULARITY:
 * - Separate cache layer (PreferencesManager)
 * - Clean separation of concerns
 * 
 * ACCOUNT CONTAINERIZATION:
 * - Profile tied to userId (no data mixing)
 * - Cache cleared on logout
 * 
 * CODING STANDARDS:
 * - Proper error handling
 * - Clear method names
 * - Well-documented
 * =============================================================================
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Load profile - Instagram/Rapido-style caching
     * 
     * SCALABILITY:
     * 1. Load from cache FIRST (instant UI update, no loading spinner)
     * 2. Then fetch from backend in background
     * 3. Update cache with fresh data
     * 
     * RAPIDO-STYLE:
     * - NEVER emit Loading if cache exists
     * - Activity shows cached data BEFORE this runs
     * - This just refreshes in background
     * 
     * ACCOUNT CONTAINERIZATION:
     * - Cache is per-user (tied to userId)
     * - No data mixing between accounts
     */
    fun loadProfile() {
        viewModelScope.launch {
            // SCALABILITY: Try cache first (instant load)
            val cachedProfile = preferencesManager.getCachedProfile()
            
            if (cachedProfile != null) {
                // RAPIDO-STYLE: Cache exists, emit Success immediately
                // Activity already showing this data, but ViewModel confirms it
                timber.log.Timber.d("Cache exists: ${cachedProfile.name} - refreshing in background")
                
                _uiState.value = ProfileUiState.Success(
                    profile = ProfileData(
                        id = tokenManager.getUserId() ?: "",
                        phone = cachedProfile.phone,
                        name = cachedProfile.name,
                        email = cachedProfile.email,
                        company = null,
                        gstNumber = null,
                        profilePhoto = null
                    )
                )
                
                // SCALABILITY: Fetch from backend in background to get fresh data
                // No loading state (smooth UX like Rapido)
                fetchAndUpdateProfile(showLoading = false)
            } else {
                // RAPIDO-STYLE: TRUE first load (no cache exists)
                // Only NOW we emit Loading state
                timber.log.Timber.d("No cache - first load with Loading state")
                _uiState.value = ProfileUiState.Loading
                fetchAndUpdateProfile(showLoading = true)
            }
        }
    }
    
    /**
     * Fetch profile from backend and update cache
     * 
     * MODULARITY: Separated fetch logic for reuse
     */
    private suspend fun fetchAndUpdateProfile(showLoading: Boolean) {
        try {
            when (val result = authRepository.getProfile()) {
                is Result.Success -> {
                    val user = result.data.data?.profile
                    if (user != null) {
                        // ACCOUNT CONTAINERIZATION: Save to cache (tied to this user)
                        preferencesManager.saveProfile(
                            name = user.name ?: "",
                            phone = user.phone,
                            email = user.email,
                            role = "customer"
                        )
                        
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
                        timber.log.Timber.d("Profile fetched and cached")
                    } else {
                        // Profile not found - use fallback
                        loadFallbackProfile()
                    }
                }
                is Result.Error -> {
                    // API error - use fallback (but keep showing cached data if available)
                    if (!showLoading) {
                        // Already showing cached data, don't replace with error
                        timber.log.Timber.d("Background fetch failed, keeping cached data")
                    } else {
                        loadFallbackProfile()
                    }
                }
                is Result.Loading -> {
                    if (showLoading) {
                        _uiState.value = ProfileUiState.Loading
                    }
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error fetching profile: ${e.message}")
            if (showLoading) {
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
                        
                        // SCALABILITY: Update cache with new profile data
                        preferencesManager.saveProfile(
                            name = user?.name ?: name,
                            phone = user?.phone ?: tokenManager.getUserPhone() ?: "",
                            email = user?.email ?: email,
                            role = "customer"
                        )
                        
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
                timber.log.Timber.e(e, "Error updating profile: ${e.message}")
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    /**
     * Logout user - ACCOUNT CONTAINERIZATION
     * 
     * ACCOUNT CONTAINERIZATION:
     * - Clears ALL cached data (profile, tokens, preferences)
     * - Prevents data mixing between accounts
     * - Ensures next login starts fresh
     */
    fun logout() {
        viewModelScope.launch {
            // ACCOUNT CONTAINERIZATION: Clear tokens
            tokenManager.clearTokens()
            
            // ACCOUNT CONTAINERIZATION: Clear all cached profile data
            preferencesManager.clearAll()
            
            timber.log.Timber.i("Logout: All data cleared (account containerization)")
            
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
