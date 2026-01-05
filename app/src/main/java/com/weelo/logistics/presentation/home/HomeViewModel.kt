package com.weelo.logistics.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
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
 * ViewModel for MainActivity (Home Screen)
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllVehiclesUseCase: GetAllVehiclesUseCase
) : BaseViewModel() {

    private val _uiState = MutableLiveData<HomeUiState>()
    val uiState: LiveData<HomeUiState> = _uiState

    private val _navigationEvent = MutableLiveData<HomeNavigationEvent>()
    val navigationEvent: LiveData<HomeNavigationEvent> = _navigationEvent

    init {
        _uiState.value = HomeUiState()
    }

    fun loadVehicleCategories() {
        viewModelScope.launch(exceptionHandler) {
            getAllVehiclesUseCase()
                .onEach { result ->
                    when (result) {
                        is Result.Loading -> {
                            _uiState.value = _uiState.value?.copy(isLoading = true)
                        }
                        is Result.Success -> {
                            _uiState.value = _uiState.value?.copy(
                                isLoading = false,
                                vehicleCategories = result.data ?: emptyList()
                            )
                        }
                        is Result.Error -> {
                            _uiState.value = _uiState.value?.copy(
                                isLoading = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
                .launchIn(this)
        }
    }

    fun onSearchClicked() {
        _navigationEvent.value = HomeNavigationEvent.NavigateToLocationInput
    }

    fun onVehicleCategoryClicked(category: VehicleModel) {
        _navigationEvent.value = HomeNavigationEvent.NavigateToVehicleDetails(category)
    }

    override fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null)
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val vehicleCategories: List<VehicleModel> = emptyList(),
    val errorMessage: String? = null
)

sealed class HomeNavigationEvent {
    object NavigateToLocationInput : HomeNavigationEvent()
    data class NavigateToVehicleDetails(val category: VehicleModel) : HomeNavigationEvent()
}
