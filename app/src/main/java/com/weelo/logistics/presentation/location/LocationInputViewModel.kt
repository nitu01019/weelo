package com.weelo.logistics.presentation.location

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.usecase.AddRecentLocationUseCase
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
    private val addRecentLocationUseCase: AddRecentLocationUseCase
) : BaseViewModel() {

    private val _uiState = MutableLiveData<LocationInputUiState>()
    val uiState: LiveData<LocationInputUiState> = _uiState

    private val _navigationEvent = MutableLiveData<LocationNavigationEvent>()
    val navigationEvent: LiveData<LocationNavigationEvent> = _navigationEvent

    init {
        _uiState.value = LocationInputUiState()
        loadRecentLocations()
    }

    private fun loadRecentLocations() {
        viewModelScope.launch(exceptionHandler) {
            getRecentLocationsUseCase()
                .onEach { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.value = _uiState.value?.copy(
                                recentLocations = result.data ?: emptyList()
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

    fun onContinueClicked(fromAddress: String, toAddress: String) {
        viewModelScope.launch(exceptionHandler) {
            _uiState.value = _uiState.value?.copy(isLoading = true)
            
            val fromLocation = LocationModel(address = fromAddress)
            val toLocation = LocationModel(address = toAddress)
            
            val result = validateLocationsUseCase(fromLocation, toLocation)
            
            when (result) {
                is Result.Success -> {
                    if (result.data == true) {
                        // Save to recent locations
                        addRecentLocationUseCase(fromLocation)
                        addRecentLocationUseCase(toLocation)
                        
                        _uiState.value = _uiState.value?.copy(isLoading = false)
                        _navigationEvent.value = LocationNavigationEvent.NavigateToMap(fromLocation, toLocation)
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
                    _uiState.value = _uiState.value?.copy(isLoading = true)
                }
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
