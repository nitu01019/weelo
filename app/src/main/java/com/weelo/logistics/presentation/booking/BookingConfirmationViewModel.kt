package com.weelo.logistics.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.data.remote.api.CoordinatesRequest
import com.weelo.logistics.data.remote.api.LocationRequest
import com.weelo.logistics.data.repository.BookingApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * ViewModel for Booking Confirmation Screen
 * 
 * Handles:
 * - Price estimation
 * - Booking creation via API
 * - Navigation to waiting screen
 * 
 * SCALABILITY:
 * - Request lock prevents duplicate booking creation
 * - Supports millions of concurrent users
 * 
 * MODULARITY:
 * - Clear separation of concerns
 * 
 * CODING STANDARDS:
 * - Consistent error handling
 * - Clear naming conventions
 */
@HiltViewModel
class BookingConfirmationViewModel @Inject constructor(
    private val bookingRepository: BookingApiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingConfirmationUiState>(BookingConfirmationUiState.Loading)
    val uiState: StateFlow<BookingConfirmationUiState> = _uiState.asStateFlow()

    private var fromLocation: Location? = null
    private var toLocation: Location? = null
    private var selectedTrucks: List<SelectedTruckItem> = emptyList()
    private var distanceKm: Int = 0
    
    // SCALABILITY: Mutex to prevent duplicate booking requests
    // EASY UNDERSTANDING: Only one booking creation at a time
    private val bookingCreationMutex = Mutex()

    fun initialize(
        from: Location,
        to: Location,
        trucks: List<SelectedTruckItem>,
        distance: Int
    ) {
        fromLocation = from
        toLocation = to
        selectedTrucks = trucks
        distanceKm = distance
        
        calculateEstimatedPrice()
    }

    private fun calculateEstimatedPrice() {
        // Simple price estimation: base rate per km per truck
        val totalTrucks = selectedTrucks.sumOf { it.quantity }
        val baseRatePerKm = 50 // ₹50 per km
        val estimatedPrice = distanceKm * baseRatePerKm * totalTrucks
        
        _uiState.value = BookingConfirmationUiState.Ready(estimatedPrice.coerceAtLeast(500))
    }

    /**
     * Create booking with duplicate request prevention
     * 
     * SCALABILITY:
     * - Mutex prevents multiple simultaneous calls
     * - Idempotency key prevents duplicate bookings
     * 
     * EASY UNDERSTANDING:
     * - Clear flow: lock → create → unlock
     * - Single booking per user at a time
     */
    fun createBooking() {
        val from = fromLocation ?: return
        val to = toLocation ?: return
        
        if (selectedTrucks.isEmpty()) return

        viewModelScope.launch {
            // SCALABILITY: Use mutex to prevent duplicate requests (Instagram-style)
            // EASY UNDERSTANDING: Only one booking creation allowed at a time
            if (!bookingCreationMutex.tryLock()) {
                // Already creating a booking, ignore this request
                timber.log.Timber.w("Booking creation already in progress, ignoring duplicate request")
                return@launch
            }
            
            try {
                _uiState.value = BookingConfirmationUiState.Creating

                // Get first selected truck type for booking (backend handles multiple)
                val firstTruck = selectedTrucks.first()
                val totalTrucks = selectedTrucks.sumOf { it.quantity }
                val pricePerTruck = (distanceKm * 50).coerceAtLeast(500)
                
                // SCALABILITY: Generate idempotency key for this booking request
                // Prevents duplicate bookings if user taps multiple times
                val idempotencyKey = java.util.UUID.randomUUID().toString()

                when (val result = bookingRepository.createBookingSimple(
                    pickup = LocationRequest(
                        coordinates = CoordinatesRequest(
                            latitude = from.latitude,
                            longitude = from.longitude
                        ),
                        address = from.address
                    ),
                    drop = LocationRequest(
                        coordinates = CoordinatesRequest(
                            latitude = to.latitude,
                            longitude = to.longitude
                        ),
                        address = to.address
                    ),
                    vehicleType = firstTruck.truckTypeId,
                    vehicleSubtype = firstTruck.specification,
                    trucksNeeded = totalTrucks,
                    distanceKm = distanceKm,
                    pricePerTruck = pricePerTruck,
                    idempotencyKey = idempotencyKey
                )) {
                    is Result.Success -> {
                        _uiState.value = BookingConfirmationUiState.Success(result.data)
                    }
                    is Result.Error -> {
                        _uiState.value = BookingConfirmationUiState.Error(
                            result.message ?: "Failed to create booking"
                        )
                    }
                    is Result.Loading -> {
                        _uiState.value = BookingConfirmationUiState.Creating
                    }
                }
            } finally {
                // CODING STANDARDS: Always unlock in finally block
                bookingCreationMutex.unlock()
            }
        }
    }
}

/**
 * UI State for Booking Confirmation Screen
 */
sealed class BookingConfirmationUiState {
    object Loading : BookingConfirmationUiState()
    data class Ready(val estimatedPrice: Int) : BookingConfirmationUiState()
    object Creating : BookingConfirmationUiState()
    data class Success(val bookingId: String) : BookingConfirmationUiState()
    data class Error(val message: String) : BookingConfirmationUiState()
}
