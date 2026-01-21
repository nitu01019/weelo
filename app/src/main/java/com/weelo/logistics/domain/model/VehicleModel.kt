package com.weelo.logistics.domain.model

/**
 * Domain model for Vehicle
 * 
 * DATA SAFETY:
 * - id, name, capacityTons are required (but can be empty)
 * - All numeric fields have safe defaults
 * - features list is never null (defaults to emptyList)
 * - description is optional (defaults to empty string)
 * - UI can render vehicle even with minimal data
 */
data class VehicleModel(
    val id: String,
    val name: String,
    val category: VehicleCategory,
    val capacityTons: String,
    val description: String = "",
    val priceMultiplier: Double = 1.0,
    val basePrice: Int = 3000,
    val pricePerKm: Int = 40,
    val features: List<String> = emptyList(),
    val availableCount: Int = 0
) {
    /**
     * Check if vehicle has basic required data
     */
    fun isValid(): Boolean = id.isNotBlank() && name.isNotBlank()
    
    /**
     * Check if vehicle is available for booking
     */
    fun isAvailable(): Boolean = availableCount > 0
    
    /**
     * Get display name safely
     */
    fun getDisplayName(): String = if (name.isNotBlank()) name else "Unknown Vehicle"
    
    /**
     * Get capacity display safely
     */
    fun getCapacityDisplay(): String = if (capacityTons.isNotBlank()) capacityTons else "N/A"
    
    /**
     * Get features count safely
     */
    fun getFeaturesCount(): Int = features.size
    
    /**
     * Check if vehicle has features to show
     */
    fun hasFeatures(): Boolean = features.isNotEmpty()
}

/**
 * Vehicle category enum
 * Must match backend vehicleTypeSchema: mini, lcv, tipper, container, trailer, tanker, bulker, open, dumper, tractor
 */
enum class VehicleCategory(val displayName: String, val apiValue: String) {
    OPEN("Open", "open"),
    CONTAINER("Container", "container"),
    LCV("LCV", "lcv"),
    MINI("Mini/Pickup", "mini"),
    TRAILER("Trailer", "trailer"),
    TIPPER("Tipper", "tipper"),
    TANKER("Tanker", "tanker"),
    DUMPER("Dumper", "dumper"),
    BULKER("Bulker", "bulker"),
    TRACTOR("Tractor", "tractor");
    
    companion object {
        fun fromId(id: String): VehicleCategory {
            return when (id.lowercase()) {
                "open" -> OPEN
                "container" -> CONTAINER
                "lcv" -> LCV
                "mini" -> MINI
                "trailer" -> TRAILER
                "tipper" -> TIPPER
                "tanker" -> TANKER
                "dumper" -> DUMPER
                "bulker" -> BULKER
                "tractor" -> TRACTOR
                else -> OPEN
            }
        }
    }
}
