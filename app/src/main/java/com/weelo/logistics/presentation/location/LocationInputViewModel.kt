package com.weelo.logistics.presentation.location

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.usecase.AddRecentLocationUseCase
import com.weelo.logistics.domain.usecase.DeleteRecentLocationUseCase
import com.weelo.logistics.domain.usecase.GetRecentLocationsUseCase
import com.weelo.logistics.domain.usecase.ValidateLocationsUseCase
import com.weelo.logistics.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Location Input Screen
 */
@HiltViewModel
class LocationInputViewModel @Inject constructor(
    private val validateLocationsUseCase: ValidateLocationsUseCase,
    private val getRecentLocationsUseCase: GetRecentLocationsUseCase,
    private val addRecentLocationUseCase: AddRecentLocationUseCase,
    private val deleteRecentLocationUseCase: DeleteRecentLocationUseCase
) : BaseViewModel() {

    private val _uiState = MutableLiveData<LocationInputUiState>()
    val uiState: LiveData<LocationInputUiState> = _uiState

    private val _navigationEvent = MutableLiveData<LocationNavigationEvent>()
    val navigationEvent: LiveData<LocationNavigationEvent> = _navigationEvent

    init {
        _uiState.value = LocationInputUiState()
        loadRecentLocations()
    }

    /**
     * Load recent locations from Room DB
     * SCALABILITY: Uses Flow for reactive updates
     */
    fun loadRecentLocations() {
        viewModelScope.launch(exceptionHandler) {
            getRecentLocationsUseCase()
                .onEach { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.value = _uiState.value?.copy(
                                recentLocations = result.data
                            )
                        }
                        is Result.Error -> {
                            Timber.e("Error loading recent locations: ${result.message}")
                        }
                        is Result.Loading -> {}
                    }
                }
                .launchIn(this)
        }
    }
    
    /**
     * Update a location (e.g., toggle favorite)
     * SCALABILITY: Updates Room DB in background
     * 
     * When toggling favorite ON:
     * - Updates timestamp to current time (moves to top of list)
     * - This ensures favorites appear first AND most recent favorites are at very top
     */
    fun updateLocation(location: LocationModel) {
        viewModelScope.launch(exceptionHandler) {
            try {
                // If toggling favorite ON, update timestamp to move to top
                val updatedLocation = if (location.isFavorite) {
                    location.copy(timestamp = System.currentTimeMillis())
                } else {
                    location
                }
                addRecentLocationUseCase(updatedLocation) // AddUseCase handles insert/update
                Timber.d("Location updated: ${location.address}, isFavorite=${location.isFavorite}")
                // Reload to reflect changes in UI
                loadRecentLocations()
            } catch (e: Exception) {
                Timber.e(e, "Failed to update location")
            }
        }
    }
    
    /**
     * Delete a location from recent list
     * SCALABILITY: Deletes from Room DB in background using DeleteRecentLocationUseCase
     * EASY UNDERSTANDING: Uses dedicated use case for single responsibility
     */
    fun deleteLocation(location: LocationModel) {
        viewModelScope.launch(exceptionHandler) {
            try {
                when (val result = deleteRecentLocationUseCase(location)) {
                    is Result.Success -> {
                        Timber.d("Location deleted successfully: ${location.address}")
                        loadRecentLocations() // Refresh list
                    }
                    is Result.Error -> {
                        Timber.e("Failed to delete location: ${result.message}")
                    }
                    is Result.Loading -> { /* Not applicable for this operation */ }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete location: ${location.address}")
            }
        }
    }

    /**
     * OPTIMIZED for lightning-fast navigation:
     * 1. Validate synchronously (no suspend, instant)
     * 2. Navigate IMMEDIATELY after validation
     * 3. Save recent locations in background (fire & forget)
     */
    fun onContinueClicked(fromAddress: String, toAddress: String) {
        val fromLocation = LocationModel(address = fromAddress)
        val toLocation = LocationModel(address = toAddress)
        
        // Validate synchronously - this is instant, no network calls
        val result = validateLocationsUseCase(fromLocation, toLocation)
        
        when (result) {
            is Result.Success -> {
                if (result.data == true) {
                    // Navigate IMMEDIATELY - don't wait for anything
                    _navigationEvent.value = LocationNavigationEvent.NavigateToMap(fromLocation, toLocation)
                    
                    // Save to recent locations in background (fire & forget - non-blocking)
                    viewModelScope.launch {
                        try {
                            addRecentLocationUseCase(fromLocation)
                            addRecentLocationUseCase(toLocation)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to save recent locations")
                        }
                    }
                } else {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = "Please enter valid locations"
                    )
                }
            }
            is Result.Error -> {
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    errorMessage = result.message ?: "Please enter valid locations"
                )
            }
            is Result.Loading -> {
                // Won't happen for synchronous validation
            }
        }
    }

    /**
     * Add a location to recent/cache history
     * 
     * SCALABILITY: Fire-and-forget background operation
     * EASY UNDERSTANDING: Simple add with auto-refresh
     * MODULARITY: Reusable for both search selections and favorites
     */
    fun addRecentLocation(location: LocationModel) {
        viewModelScope.launch(exceptionHandler) {
            try {
                when (val result = addRecentLocationUseCase(location)) {
                    is Result.Success -> {
                        Timber.d("Location cached: ${location.address}, favorite=${location.isFavorite}")
                        loadRecentLocations() // Refresh display
                    }
                    is Result.Error -> {
                        Timber.e("Failed to cache location: ${result.message}")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add recent location: ${location.address}")
            }
        }
    }

    fun onRecentLocationClicked(location: LocationModel, isFrom: Boolean) {
        if (isFrom) {
            _uiState.value = _uiState.value?.copy(fromLocation = location.address)
        } else {
            _uiState.value = _uiState.value?.copy(toLocation = location.address)
        }
    }

    override fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null)
    }
}

data class LocationInputUiState(
    val isLoading: Boolean = false,
    val fromLocation: String = "",
    val toLocation: String = "",
    val recentLocations: List<LocationModel> = emptyList(),
    val errorMessage: String? = null
) {
    fun isEmpty(): Boolean = recentLocations.isEmpty()
}

sealed class LocationNavigationEvent {
    data class NavigateToMap(val fromLocation: LocationModel, val toLocation: LocationModel) : LocationNavigationEvent()
}
