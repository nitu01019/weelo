package com.weelo.logistics.presentation.trucks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.model.VehicleModel
import com.weelo.logistics.domain.usecase.GetAllVehiclesUseCase
import com.weelo.logistics.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for TruckTypesActivity
 * 
 * Now auth-ready! Future enhancement example:
 * - Authenticated users: Show personalized vehicle recommendations
 * - Guest users: Show default vehicle list
 * 
 * State Safety:
 * - Owns its own UI state (TruckTypesUiState)
 * - Vehicle data loaded independently
 * - Selection state survives rotation
 */
@HiltViewModel
class TruckTypesViewModel @Inject constructor(
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase,
    
) : BaseViewModel() {

    private val _uiState = MutableLiveData<TruckTypesUiState>()
    val uiState: LiveData<TruckTypesUiState> = _uiState

    private val _navigationEvent = MutableLiveData<TruckTypesNavigationEvent>()
    val navigationEvent: LiveData<TruckTypesNavigationEvent> = _navigationEvent

    init {
        loadVehicles()
    }

    fun loadVehicles() {
        viewModelScope.launch {
            _uiState.value = TruckTypesUiState(isLoading = true)

            getAllVehiclesUseCase()
                .onEach { result ->
                    when (result) {
                        is Result.Success -> {
                            Timber.d("Loaded ${result.data.size} vehicles")
                            _uiState.value = TruckTypesUiState(
                                vehicles = result.data,
                                isLoading = false
                            )
                        }
                        is Result.Error -> {
                            Timber.e(result.exception, "Failed to load vehicles")
                            _uiState.value = TruckTypesUiState(
                                errorMessage = result.exception.message,
                                isLoading = false
                            )
                        }
                        is Result.Loading -> {
                            _uiState.value = TruckTypesUiState(isLoading = true)
                        }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun onVehicleSelected(vehicle: VehicleModel) {
        _uiState.value = _uiState.value?.copy(selectedVehicle = vehicle)
    }

    fun onContinueClicked(fromLocation: LocationModel, toLocation: LocationModel) {
        val selectedVehicle = _uiState.value?.selectedVehicle
        if (selectedVehicle != null) {
            _navigationEvent.value = TruckTypesNavigationEvent.NavigateToPricing(
                fromLocation, toLocation, selectedVehicle
            )
        } else {
            _uiState.value = _uiState.value?.copy(
                errorMessage = "Please select a vehicle type"
            )
        }
    }

    fun clearErrorState() {
        _uiState.value = _uiState.value?.copy(errorMessage = null)
    }
}

/**
 * UI State for Truck Types screen
 * 
 * DATA SAFETY:
 * - vehicles is never null (defaults to emptyList)
 * - selectedVehicle is nullable (no selection is valid state)
 * - UI renders safely with zero vehicles (shows empty state)
 * - Partial vehicle data handled by VehicleModel validation
 */
data class TruckTypesUiState(
    val vehicles: List<VehicleModel> = emptyList(),
    val selectedVehicle: VehicleModel? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Check if we have vehicles to show
     */
    fun hasVehicles(): Boolean = vehicles.isNotEmpty()
    
    /**
     * Check if we should show empty state
     */
    fun isEmpty(): Boolean = !isLoading && vehicles.isEmpty() && errorMessage == null
    
    /**
     * Check if a vehicle is selected
     */
    fun hasSelection(): Boolean = selectedVehicle != null
    
    /**
     * Check if user can proceed (vehicle selected)
     */
    fun canProceed(): Boolean = selectedVehicle != null
    
    /**
     * Get vehicle count for UI display
     */
    fun getVehicleCount(): Int = vehicles.size
}

/**
 * Navigation events for Truck Types screen
 */
sealed class TruckTypesNavigationEvent {
    data class NavigateToPricing(
        val fromLocation: LocationModel,
        val toLocation: LocationModel,
        val vehicle: VehicleModel
    ) : TruckTypesNavigationEvent()
}
