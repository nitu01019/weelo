package com.weelo.logistics.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.remote.WebSocketService
import com.weelo.logistics.data.remote.api.AssignedTruckData
import com.weelo.logistics.data.repository.BookingApiRepository
import com.weelo.logistics.domain.model.BookingModel
import com.weelo.logistics.domain.model.BookingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Booking Request Screen
 * 
 * Handles:
 * - Loading booking details
 * - Real-time updates via WebSocket
 * - Cancel booking
 * - Refresh
 */
@HiltViewModel
class BookingRequestViewModel @Inject constructor(
    private val bookingRepository: BookingApiRepository,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingRequestUiState>(BookingRequestUiState.Loading)
    val uiState: StateFlow<BookingRequestUiState> = _uiState.asStateFlow()

    private val _currentBooking = MutableStateFlow<BookingModel?>(null)
    val currentBooking: StateFlow<BookingModel?> = _currentBooking.asStateFlow()

    private val _truckAssignments = MutableStateFlow<List<AssignedTruckData>>(emptyList())
    val truckAssignments: StateFlow<List<AssignedTruckData>> = _truckAssignments.asStateFlow()

    private val _bookingStatus = MutableStateFlow(BookingStatus.PENDING)
    val bookingStatus: StateFlow<BookingStatus> = _bookingStatus.asStateFlow()

    private var currentBookingId: String? = null

    /**
     * Load booking details
     */
    fun loadBooking(bookingId: String) {
        currentBookingId = bookingId
        _uiState.value = BookingRequestUiState.Loading

        viewModelScope.launch {
            when (val result = bookingRepository.getBookingById(bookingId)) {
                is Result.Success -> {
                    val booking = result.data
                    _currentBooking.value = booking
                    _bookingStatus.value = booking.status
                    _uiState.value = BookingRequestUiState.Success(booking)
                    
                    // Load assigned trucks
                    loadAssignedTrucks(bookingId)
                }
                is Result.Error -> {
                    _uiState.value = BookingRequestUiState.Error(result.exception.message ?: "Failed to load booking")
                }
                is Result.Loading -> {
                    _uiState.value = BookingRequestUiState.Loading
                }
            }
        }
    }

    /**
     * Load trucks assigned to this booking
     */
    private fun loadAssignedTrucks(bookingId: String) {
        viewModelScope.launch {
            when (val result = bookingRepository.getAssignedTrucks(bookingId)) {
                is Result.Success -> {
                    _truckAssignments.value = result.data
                    
                    // Update booking with truck count
                    _currentBooking.value = _currentBooking.value?.copy(
                        trucksFilled = result.data.size
                    )
                }
                is Result.Error -> {
                    Timber.e("Failed to load trucks: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Connect to real-time updates
     */
    fun connectRealTime() {
        val bookingId = currentBookingId ?: return

        // Connect WebSocket
        if (webSocketService.connect()) {
            webSocketService.joinBookingRoom(bookingId)
            
            // Listen for booking updates
            viewModelScope.launch {
                webSocketService.bookingUpdates()
                    .filter { it.bookingId == bookingId }
                    .collect { event ->
                        Timber.d("Booking update: ${event.status}, trucks: ${event.trucksFilled}/${event.trucksNeeded}")
                        
                        // Update status
                        val newStatus = mapStatus(event.status)
                        _bookingStatus.value = newStatus
                        
                        // Update truck count
                        if (event.trucksFilled >= 0) {
                            _currentBooking.value = _currentBooking.value?.copy(
                                trucksFilled = event.trucksFilled,
                                status = newStatus
                            )
                            
                            // Reload UI
                            _currentBooking.value?.let {
                                _uiState.value = BookingRequestUiState.Success(it)
                            }
                        }
                    }
            }

            // Listen for truck assignments
            viewModelScope.launch {
                webSocketService.truckAssignments()
                    .filter { it.bookingId == bookingId }
                    .collect { event ->
                        Timber.d("Truck assigned: ${event.vehicleNumber} by ${event.driverName}")
                        
                        // Reload trucks list
                        loadAssignedTrucks(bookingId)
                    }
            }

            // Listen for assignment status changes
            viewModelScope.launch {
                webSocketService.assignmentStatusChanges()
                    .collect { event ->
                        Timber.d("Assignment status: ${event.assignmentId} -> ${event.status}")
                        
                        // Update the specific truck in the list
                        val updatedList = _truckAssignments.value.map { truck ->
                            if (truck.assignmentId == event.assignmentId) {
                                truck.copy(status = event.status)
                            } else {
                                truck
                            }
                        }
                        _truckAssignments.value = updatedList
                    }
            }
        }
    }

    /**
     * Disconnect from real-time updates
     */
    fun disconnectRealTime() {
        currentBookingId?.let { bookingId ->
            webSocketService.leaveBookingRoom(bookingId)
        }
    }

    /**
     * Refresh booking data
     */
    fun refresh() {
        currentBookingId?.let { loadBooking(it) }
    }

    /**
     * Cancel the booking
     */
    fun cancelBooking() {
        val bookingId = currentBookingId ?: return

        viewModelScope.launch {
            _uiState.value = BookingRequestUiState.Loading
            
            when (val result = bookingRepository.cancelBooking(bookingId)) {
                is Result.Success -> {
                    _bookingStatus.value = BookingStatus.CANCELLED
                    _currentBooking.value = _currentBooking.value?.copy(status = BookingStatus.CANCELLED)
                    _currentBooking.value?.let {
                        _uiState.value = BookingRequestUiState.Success(it)
                    }
                }
                is Result.Error -> {
                    _uiState.value = BookingRequestUiState.Error(result.exception.message ?: "Failed to cancel")
                }
                else -> {}
            }
        }
    }

    /**
     * Continue with partial fulfillment
     * When some trucks are assigned but not all, customer can choose to proceed
     * with the assigned trucks instead of waiting/searching again.
     */
    fun continueWithPartialFill() {
        val booking = _currentBooking.value ?: return
        
        viewModelScope.launch {
            // Update status to confirmed with partial trucks
            _bookingStatus.value = BookingStatus.CONFIRMED
            _currentBooking.value = booking.copy(
                status = BookingStatus.CONFIRMED,
                trucksNeeded = booking.trucksFilled  // Reduce to what we have
            )
            _currentBooking.value?.let {
                _uiState.value = BookingRequestUiState.Success(it)
            }
            
            // TODO: Call API to confirm partial fulfillment
            // bookingRepository.confirmPartialFill(bookingId)
        }
    }

    private fun mapStatus(status: String): BookingStatus {
        return when (status.lowercase()) {
            "active", "searching" -> BookingStatus.PENDING
            "partially_filled" -> BookingStatus.PARTIALLY_FILLED
            "fully_filled" -> BookingStatus.CONFIRMED
            "in_progress" -> BookingStatus.IN_PROGRESS
            "completed" -> BookingStatus.COMPLETED
            "cancelled" -> BookingStatus.CANCELLED
            "expired" -> BookingStatus.EXPIRED
            else -> BookingStatus.PENDING
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectRealTime()
    }
}

/**
 * UI State for Booking Request Screen
 */
sealed class BookingRequestUiState {
    object Loading : BookingRequestUiState()
    data class Success(val booking: BookingModel) : BookingRequestUiState()
    data class Error(val message: String) : BookingRequestUiState()
}
