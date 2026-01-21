package com.weelo.logistics.presentation.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.data.repository.BookingApiRepository
import com.weelo.logistics.data.repository.PricingRepository
import com.weelo.logistics.data.repository.PricingEstimate
import com.weelo.logistics.data.repository.VehicleSuggestion
import com.weelo.logistics.data.repository.SuggestionsResponse
import com.weelo.logistics.data.repository.CapacityInfo
import com.weelo.logistics.data.repository.TruckSelection
import com.weelo.logistics.data.repository.OrderResult
import com.weelo.logistics.domain.model.LocationModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Pricing Screen
 * 
 * Enhanced with tonnage-based pricing and vehicle suggestions.
 * 
 * FEATURES:
 * - Tonnage-based pricing from backend
 * - Vehicle suggestions for cost optimization
 * - Distance slab pricing display
 * - Capacity information display
 * 
 * MODULAR DESIGN:
 * - Pricing fetched from /api/v1/pricing/estimate
 * - Suggestions from /api/v1/pricing/suggestions
 * - Booking created via /api/v1/bookings
 */
@HiltViewModel
class PricingViewModel @Inject constructor(
    private val pricingRepository: PricingRepository,
    private val bookingRepository: BookingApiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PricingUiState>(PricingUiState.Loading)
    val uiState: StateFlow<PricingUiState> = _uiState.asStateFlow()

    private val _suggestionsState = MutableStateFlow<SuggestionsUiState>(SuggestionsUiState.Hidden)
    val suggestionsState: StateFlow<SuggestionsUiState> = _suggestionsState.asStateFlow()

    private var fromLocation: Location? = null
    private var toLocation: Location? = null
    private var selectedTrucks: List<SelectedTruckItem> = emptyList()
    private var distanceKm: Int = 0
    private var cachedPricePerTruck: Int = 0
    private var cargoWeightKg: Int? = null

    fun initialize(
        from: Location,
        to: Location,
        trucks: List<SelectedTruckItem>,
        distance: Int,
        cargoWeight: Int? = null
    ) {
        fromLocation = from
        toLocation = to
        selectedTrucks = trucks
        distanceKm = distance
        cargoWeightKg = cargoWeight
        
        fetchPricing()
        
        // Fetch suggestions if cargo weight is provided
        if (cargoWeight != null && cargoWeight > 0) {
            fetchSuggestions(cargoWeight)
        }
    }

    private fun fetchPricing() {
        viewModelScope.launch {
            _uiState.value = PricingUiState.Loading

            val firstTruck = selectedTrucks.firstOrNull() ?: return@launch
            val totalTrucks = selectedTrucks.sumOf { it.quantity }

            when (val result = pricingRepository.getEstimate(
                vehicleType = firstTruck.truckTypeId,
                vehicleSubtype = firstTruck.specification,
                distanceKm = distanceKm,
                trucksNeeded = totalTrucks,
                cargoWeightKg = cargoWeightKg
            )) {
                is Result.Success<PricingEstimate> -> {
                    val data = result.data
                    cachedPricePerTruck = data.pricePerTruck
                    
                    _uiState.value = PricingUiState.PriceLoaded(
                        basePrice = data.basePrice,
                        distanceCharge = data.distanceCharge,
                        tonnageCharge = data.tonnageCharge,
                        surgeMultiplier = data.surgeMultiplier,
                        surgeFactor = data.surgeFactor,
                        distanceSlab = data.distanceSlab,
                        pricePerTruck = data.pricePerTruck,
                        totalPrice = data.totalPrice,
                        validForMinutes = data.validForMinutes,
                        capacityInfo = data.capacityInfo
                    )
                }
                is Result.Error -> {
                    _uiState.value = PricingUiState.Error(
                        result.message ?: result.exception.message ?: "Failed to fetch pricing"
                    )
                }
                is Result.Loading -> {
                    _uiState.value = PricingUiState.Loading
                }
            }
        }
    }

    /**
     * Fetch vehicle suggestions based on cargo weight
     */
    private fun fetchSuggestions(cargoWeight: Int) {
        viewModelScope.launch {
            _suggestionsState.value = SuggestionsUiState.Loading

            val firstTruck = selectedTrucks.firstOrNull()
            val totalTrucks = selectedTrucks.sumOf { it.quantity }

            when (val result = pricingRepository.getSuggestions(
                cargoWeightKg = cargoWeight,
                distanceKm = distanceKm,
                trucksNeeded = totalTrucks,
                currentVehicleType = firstTruck?.truckTypeId,
                currentVehicleSubtype = firstTruck?.specification
            )) {
                is Result.Success<SuggestionsResponse> -> {
                    val data = result.data
                    if (data.potentialSavings > 0 && data.recommendedOption != null) {
                        _suggestionsState.value = SuggestionsUiState.Available(
                            suggestions = data.suggestions,
                            recommendedOption = data.recommendedOption,
                            potentialSavings = data.potentialSavings
                        )
                    } else {
                        _suggestionsState.value = SuggestionsUiState.Hidden
                    }
                }
                is Result.Error -> {
                    _suggestionsState.value = SuggestionsUiState.Hidden
                }
                is Result.Loading -> {
                    _suggestionsState.value = SuggestionsUiState.Loading
                }
            }
        }
    }

    /**
     * Switch to a suggested vehicle
     */
    fun selectSuggestion(suggestion: VehicleSuggestion) {
        // Update selected trucks with the suggested vehicle
        val updatedTrucks = selectedTrucks.map { truck ->
            truck.copy(
                truckTypeId = suggestion.vehicleType,
                truckTypeName = suggestion.displayName.split(" - ").firstOrNull() ?: suggestion.vehicleType,
                specification = suggestion.vehicleSubtype
            )
        }
        selectedTrucks = updatedTrucks
        
        // Re-fetch pricing with new selection
        fetchPricing()
        _suggestionsState.value = SuggestionsUiState.Hidden
    }

    /**
     * Dismiss suggestions
     */
    fun dismissSuggestions() {
        _suggestionsState.value = SuggestionsUiState.Hidden
    }

    /**
     * Create order with multiple truck types
     * 
     * NEW SYSTEM: Each truck type creates separate TruckRequests
     * that are broadcast only to transporters who have matching vehicles.
     * 
     * Example: 2x Open 17ft + 3x Container 4ton = 5 TruckRequests
     * - 2 requests go to transporters with Open trucks
     * - 3 requests go to transporters with Container trucks
     */
    fun createBooking() {
        val from = fromLocation ?: return
        val to = toLocation ?: return
        
        if (selectedTrucks.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = PricingUiState.Creating

            // Convert selected trucks to TruckSelection format
            // Group by type+subtype and sum quantities
            val truckSelections = selectedTrucks
                .groupBy { "${it.truckTypeId}_${it.specification}" }
                .map { (_, trucks) ->
                    val first = trucks.first()
                    TruckSelection(
                        vehicleType = first.truckTypeId,
                        vehicleSubtype = first.specification,
                        quantity = trucks.sumOf { it.quantity },
                        pricePerTruck = cachedPricePerTruck
                    )
                }

            // Create pickup LocationModel
            val pickupLocation = LocationModel(
                latitude = from.latitude,
                longitude = from.longitude,
                address = from.address,
                city = from.city,
                state = from.state
            )

            // Create drop LocationModel
            val dropLocation = LocationModel(
                latitude = to.latitude,
                longitude = to.longitude,
                address = to.address,
                city = to.city,
                state = to.state
            )

            when (val result = bookingRepository.createOrder(
                pickup = pickupLocation,
                drop = dropLocation,
                distanceKm = distanceKm,
                trucks = truckSelections
            )) {
                is Result.Success<OrderResult> -> {
                    val orderResult = result.data
                    _uiState.value = PricingUiState.OrderCreated(
                        orderId = orderResult.orderId,
                        totalTrucks = orderResult.totalTrucks,
                        totalAmount = orderResult.totalAmount,
                        transportersNotified = orderResult.broadcastSummary.totalTransportersNotified,
                        timeoutSeconds = orderResult.timeoutSeconds,
                        broadcastGroups = orderResult.broadcastSummary.groupedBy.map { group ->
                            BroadcastGroupInfo(
                                vehicleType = group.vehicleType,
                                vehicleSubtype = group.vehicleSubtype,
                                count = group.count,
                                transportersNotified = group.transportersNotified
                            )
                        }
                    )
                }
                is Result.Error -> {
                    _uiState.value = PricingUiState.Error(
                        result.message ?: result.exception.message ?: "Failed to create order"
                    )
                }
                is Result.Loading -> {
                    _uiState.value = PricingUiState.Creating
                }
            }
        }
    }
}

/**
 * UI State for Pricing Screen
 * Enhanced with tonnage and capacity information
 */
sealed class PricingUiState {
    object Loading : PricingUiState()
    
    data class PriceLoaded(
        val basePrice: Int,
        val distanceCharge: Int,
        val tonnageCharge: Int = 0,
        val surgeMultiplier: Double,
        val surgeFactor: String,
        val distanceSlab: String = "Medium Haul",
        val pricePerTruck: Int,
        val totalPrice: Int,
        val validForMinutes: Int,
        val capacityInfo: CapacityInfo? = null
    ) : PricingUiState()
    
    object Creating : PricingUiState()
    
    // Legacy: Single booking created (kept for backward compatibility)
    data class BookingCreated(val bookingId: String) : PricingUiState()
    
    // NEW: Order created with multiple truck requests
    data class OrderCreated(
        val orderId: String,
        val totalTrucks: Int,
        val totalAmount: Int,
        val transportersNotified: Int,
        val timeoutSeconds: Int,
        val broadcastGroups: List<BroadcastGroupInfo>
    ) : PricingUiState()
    
    data class Error(val message: String) : PricingUiState()
}

/**
 * Info about broadcast to transporters for a specific vehicle type
 */
data class BroadcastGroupInfo(
    val vehicleType: String,
    val vehicleSubtype: String,
    val count: Int,
    val transportersNotified: Int
)

/**
 * UI State for Vehicle Suggestions
 */
sealed class SuggestionsUiState {
    object Hidden : SuggestionsUiState()
    object Loading : SuggestionsUiState()
    
    data class Available(
        val suggestions: List<VehicleSuggestion>,
        val recommendedOption: VehicleSuggestion,
        val potentialSavings: Int
    ) : SuggestionsUiState()
}
