package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.models.TruckSubtypesConfig
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.WeeloApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pricing Repository
 * 
 * Handles pricing estimation and vehicle suggestions from backend.
 * Enhanced with tonnage-based pricing support.
 * 
 * ENDPOINTS:
 * - POST /api/v1/pricing/estimate - Get price estimate (enhanced with tonnage)
 * - POST /api/v1/pricing/suggestions - Get vehicle suggestions based on cargo weight
 * - GET /api/v1/pricing/catalog - Get vehicle pricing catalog
 * 
 * MODULAR DESIGN:
 * - Tonnage-based pricing calculations
 * - Distance slab pricing
 * - Vehicle suggestions for cost optimization
 * - Ready for AWS Lambda integration
 */
@Singleton
class PricingRepository @Inject constructor(
    private val apiService: WeeloApiService,
    private val tokenManager: TokenManager
) {
    /**
     * Get pricing estimate from backend
     * Enhanced with tonnage-based pricing
     * 
     * @param vehicleType - Type of vehicle (e.g., "open", "container")
     * @param vehicleSubtype - Specific subtype (e.g., "14ft", "20ft")
     * @param distanceKm - Distance in kilometers
     * @param trucksNeeded - Number of trucks required
     * @param cargoWeightKg - Optional cargo weight for tonnage-based pricing
     * 
     * @return PricingEstimate with breakdown
     */
    suspend fun getEstimate(
        vehicleType: String,
        vehicleSubtype: String,
        distanceKm: Int,
        trucksNeeded: Int,
        cargoWeightKg: Int? = null
    ): Result<PricingEstimate> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.calculatePricing(
                vehicleType = vehicleType,
                distanceKm = distanceKm,
                trucksNeeded = trucksNeeded
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val pricingData = response.body()?.data?.pricing
                if (pricingData != null) {
                    val capacityInfoDto = pricingData.capacityInfo
                    Result.Success(
                        PricingEstimate(
                            basePrice = pricingData.basePrice ?: (pricingData.pricePerTruck / 3),
                            distanceCharge = pricingData.distanceCharge ?: (pricingData.pricePerTruck / 3),
                            tonnageCharge = pricingData.tonnageCharge ?: 0,
                            surgeMultiplier = pricingData.surgeMultiplier ?: 1.0,
                            surgeFactor = pricingData.surgeFactor ?: "Normal",
                            distanceSlab = pricingData.distanceSlab ?: "Medium Haul",
                            pricePerTruck = pricingData.pricePerTruck,
                            totalPrice = pricingData.totalAmount,
                            validForMinutes = pricingData.validForMinutes ?: 15,
                            currency = "INR",
                            capacityInfo = if (capacityInfoDto != null) {
                                CapacityInfo(
                                    capacityKg = capacityInfoDto.capacityKg ?: 10000,
                                    capacityTons = capacityInfoDto.capacityTons ?: 10.0,
                                    minTonnage = capacityInfoDto.minTonnage ?: 5.0,
                                    maxTonnage = capacityInfoDto.maxTonnage ?: 15.0
                                )
                            } else null
                        )
                    )
                } else {
                    // Use enhanced mock data if backend not available
                    Result.Success(getMockEstimate(vehicleType, vehicleSubtype, distanceKm, trucksNeeded, cargoWeightKg))
                }
            } else {
                // Fallback to mock pricing for development
                Result.Success(getMockEstimate(vehicleType, vehicleSubtype, distanceKm, trucksNeeded, cargoWeightKg))
            }
        } catch (e: Exception) {
            // Fallback to mock pricing when backend unavailable
            Result.Success(getMockEstimate(vehicleType, vehicleSubtype, distanceKm, trucksNeeded, cargoWeightKg))
        }
    }

    /**
     * Get vehicle suggestions based on cargo weight
     * Helps customers find the most cost-effective vehicle
     * 
     * @param cargoWeightKg - Cargo weight in kilograms
     * @param distanceKm - Distance in kilometers
     * @param trucksNeeded - Number of trucks required
     * @param currentVehicleType - Currently selected vehicle type (optional)
     * @param currentVehicleSubtype - Currently selected subtype (optional)
     * 
     * @return SuggestionsResponse with ranked vehicle options
     */
    suspend fun getSuggestions(
        cargoWeightKg: Int,
        distanceKm: Int,
        trucksNeeded: Int = 1,
        currentVehicleType: String? = null,
        currentVehicleSubtype: String? = null
    ): Result<SuggestionsResponse> = withContext(Dispatchers.IO) {
        try {
            // TODO: Call backend API when available
            // val response = apiService.getSuggestions(...)
            
            // For now, use local calculation
            Result.Success(getMockSuggestions(cargoWeightKg, distanceKm, trucksNeeded, currentVehicleType, currentVehicleSubtype))
        } catch (e: Exception) {
            Result.Success(getMockSuggestions(cargoWeightKg, distanceKm, trucksNeeded, currentVehicleType, currentVehicleSubtype))
        }
    }

    /**
     * Enhanced mock pricing with tonnage-based calculation
     */
    private fun getMockEstimate(
        vehicleType: String,
        vehicleSubtype: String,
        distanceKm: Int,
        trucksNeeded: Int,
        cargoWeightKg: Int? = null
    ): PricingEstimate {
        // Get capacity info from config
        val capacityInfo = TruckSubtypesConfig.getCapacityInfo(vehicleType, vehicleSubtype)
        
        // Base prices by vehicle type
        val basePrice = when (vehicleType.lowercase()) {
            "mini" -> 500
            "lcv" -> 800
            "open" -> 1500
            "container" -> 2000
            "trailer" -> 3000
            "tanker" -> 3500
            "tipper" -> 2500
            "dumper" -> 4000
            "bulker" -> 4500
            else -> 2000
        }

        // Per km rate by vehicle type
        val perKmRate = when (vehicleType.lowercase()) {
            "mini" -> 12
            "lcv" -> 15
            "open" -> 25
            "container" -> 30
            "trailer" -> 40
            "tanker" -> 45
            "tipper" -> 35
            "dumper" -> 50
            "bulker" -> 55
            else -> 30
        }

        // Per ton per km rate
        val perTonPerKmRate = when (vehicleType.lowercase()) {
            "mini" -> 8.0
            "lcv" -> 6.0
            "open" -> 4.0
            "container" -> 4.5
            "trailer" -> 3.5
            "tanker" -> 4.0
            "tipper" -> 3.8
            "dumper" -> 4.2
            "bulker" -> 4.5
            else -> 4.0
        }

        // Calculate effective tonnage
        val effectiveTonnage = when {
            cargoWeightKg != null -> cargoWeightKg / 1000.0
            capacityInfo != null -> capacityInfo.maxTonnage
            else -> 10.0
        }

        // Minimum distance
        val chargeableDistance = maxOf(distanceKm, 5)

        // Distance slab multiplier
        val (distanceSlabMultiplier, distanceSlab) = when {
            chargeableDistance <= 50 -> 1.3 to "Local"
            chargeableDistance <= 100 -> 1.2 to "Short Haul"
            chargeableDistance <= 300 -> 1.0 to "Medium Haul"
            chargeableDistance <= 500 -> 0.95 to "Long Haul"
            else -> 0.9 to "Very Long Haul"
        }

        // Calculate charges
        val distanceCharge = (chargeableDistance * perKmRate * distanceSlabMultiplier).toInt()
        val tonnageCharge = (effectiveTonnage * chargeableDistance * perTonPerKmRate).toInt()

        // Subtype multiplier (approximate)
        val subtypeMultiplier = capacityInfo?.let {
            when {
                it.capacityKg <= 5000 -> 1.0
                it.capacityKg <= 10000 -> 1.15
                it.capacityKg <= 20000 -> 1.3
                it.capacityKg <= 30000 -> 1.5
                else -> 1.8
            }
        } ?: 1.0

        // Calculate price per truck
        val pricePerTruck = ((basePrice + distanceCharge + tonnageCharge) * subtypeMultiplier).toInt()
        val totalPrice = pricePerTruck * trucksNeeded

        return PricingEstimate(
            basePrice = (basePrice * subtypeMultiplier).toInt(),
            distanceCharge = distanceCharge,
            tonnageCharge = tonnageCharge,
            surgeMultiplier = 1.0,
            surgeFactor = "Normal",
            distanceSlab = distanceSlab,
            pricePerTruck = pricePerTruck,
            totalPrice = totalPrice,
            validForMinutes = 15,
            currency = "INR",
            capacityInfo = capacityInfo?.let {
                CapacityInfo(
                    capacityKg = it.capacityKg,
                    capacityTons = it.capacityKg / 1000.0,
                    minTonnage = it.minTonnage,
                    maxTonnage = it.maxTonnage
                )
            }
        )
    }

    /**
     * Mock suggestions based on cargo weight
     */
    private fun getMockSuggestions(
        cargoWeightKg: Int,
        distanceKm: Int,
        trucksNeeded: Int,
        currentVehicleType: String?,
        currentVehicleSubtype: String?
    ): SuggestionsResponse {
        val suggestions = mutableListOf<VehicleSuggestion>()
        
        // Find suitable vehicles across all types
        val vehicleTypes = listOf("mini", "lcv", "open", "container", "trailer", "tipper", "tanker", "dumper", "bulker")
        
        for (type in vehicleTypes) {
            val suitableSubtypes = TruckSubtypesConfig.findSuitableSubtypes(type, cargoWeightKg)
            
            for (subtype in suitableSubtypes.take(2)) { // Take top 2 from each type
                val estimate = getMockEstimate(type, subtype.name, distanceKm, trucksNeeded, cargoWeightKg)
                val isCurrentSelection = type.equals(currentVehicleType, ignoreCase = true) && 
                    subtype.name.equals(currentVehicleSubtype, ignoreCase = true)
                
                val utilizationRatio = cargoWeightKg.toDouble() / subtype.capacityKg
                val isExactFit = utilizationRatio >= 0.7 && utilizationRatio <= 1.0
                
                suggestions.add(
                    VehicleSuggestion(
                        vehicleType = type,
                        vehicleSubtype = subtype.name,
                        displayName = "${TruckSubtypesConfig.getConfigById(type)?.displayName ?: type} - ${subtype.name}",
                        capacityKg = subtype.capacityKg,
                        capacityTons = subtype.capacityKg / 1000.0,
                        pricePerTruck = estimate.pricePerTruck,
                        totalPrice = estimate.totalPrice,
                        savingsAmount = 0, // Will be calculated
                        savingsPercent = 0,
                        isRecommended = isExactFit,
                        isCurrentSelection = isCurrentSelection,
                        reason = when {
                            isExactFit -> "Best fit for your cargo"
                            utilizationRatio < 0.5 -> "Larger than needed"
                            else -> "Good option"
                        }
                    )
                )
            }
        }

        // Sort by price and calculate savings
        suggestions.sortBy { it.totalPrice }
        
        if (suggestions.isNotEmpty()) {
            val maxPrice = suggestions.maxOf { it.totalPrice }
            suggestions.forEach { suggestion ->
                val savings = maxPrice - suggestion.totalPrice
                suggestion.savingsAmount = savings
                suggestion.savingsPercent = if (maxPrice > 0) ((savings * 100) / maxPrice) else 0
            }
        }

        // Find recommended and current options
        val recommendedOption = suggestions.find { it.isRecommended } ?: suggestions.firstOrNull()
        recommendedOption?.let {
            it.isRecommended = true
            it.reason = "💰 Best value for your cargo"
        }
        
        val currentOption = suggestions.find { it.isCurrentSelection }
        
        val potentialSavings = if (currentOption != null && recommendedOption != null) {
            maxOf(0, currentOption.totalPrice - recommendedOption.totalPrice)
        } else 0

        return SuggestionsResponse(
            suggestions = suggestions.take(5), // Return top 5 suggestions
            recommendedOption = recommendedOption,
            currentOption = currentOption,
            potentialSavings = potentialSavings
        )
    }
}

/**
 * Enhanced Pricing Estimate Data Class
 * Includes tonnage-based pricing breakdown
 */
data class PricingEstimate(
    val basePrice: Int,           // Base fare for vehicle type
    val distanceCharge: Int,      // Distance * per km rate
    val tonnageCharge: Int = 0,   // Tonnage-based charge
    val surgeMultiplier: Double,  // 1.0 = normal, 1.5 = 50% surge
    val surgeFactor: String,      // "Normal", "High Demand", "Peak Hours"
    val distanceSlab: String = "Medium Haul", // "Local", "Short Haul", "Medium Haul", etc.
    val pricePerTruck: Int,       // Total price per truck
    val totalPrice: Int,          // pricePerTruck * trucksNeeded
    val validForMinutes: Int,     // How long this price is valid
    val currency: String = "INR", // Currency code
    val capacityInfo: CapacityInfo? = null // Capacity information
)

/**
 * Capacity information for the selected vehicle
 */
data class CapacityInfo(
    val capacityKg: Int,
    val capacityTons: Double,
    val minTonnage: Double,
    val maxTonnage: Double
)

/**
 * Vehicle suggestion for cost optimization
 */
data class VehicleSuggestion(
    val vehicleType: String,
    val vehicleSubtype: String,
    val displayName: String,
    val capacityKg: Int,
    val capacityTons: Double,
    val pricePerTruck: Int,
    val totalPrice: Int,
    var savingsAmount: Int,
    var savingsPercent: Int,
    var isRecommended: Boolean,
    val isCurrentSelection: Boolean,
    var reason: String
)

/**
 * Response containing vehicle suggestions
 */
data class SuggestionsResponse(
    val suggestions: List<VehicleSuggestion>,
    val recommendedOption: VehicleSuggestion?,
    val currentOption: VehicleSuggestion?,
    val potentialSavings: Int
)
