package com.weelo.logistics.domain.model

/**
 * Domain model for Location
 * Clean domain entity without Android dependencies
 * 
 * DATA SAFETY:
 * - address is the only required field (but can be empty string)
 * - All numeric fields default to 0.0 (valid for "not set")
 * - All string fields default to empty string (never null)
 * - UI must check isValid() before using location
 * - Partial data is acceptable (location without coordinates)
 */
data class LocationModel(
    val id: String = "",
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create empty location (safe default)
         * UI can render this without crashing
         */
        fun empty() = LocationModel(address = "")
    }
    
    /**
     * Check if location has minimum required data
     * UI should validate before using location
     */
    fun isValid(): Boolean = address.isNotBlank() && address.length >= 2
    
    /**
     * Check if location has coordinates
     * Useful for map display
     */
    fun hasCoordinates(): Boolean = latitude != 0.0 && longitude != 0.0
    
    /**
     * Get display string safely
     * Never returns null or crashes
     */
    fun toShortString(): String = if (city.isNotBlank()) "$address, $city" else address
    
    /**
     * Get full address safely
     * Handles partial data gracefully
     */
    fun toFullString(): String {
        val parts = mutableListOf<String>()
        if (address.isNotBlank()) parts.add(address)
        if (city.isNotBlank()) parts.add(city)
        if (state.isNotBlank()) parts.add(state)
        if (pincode.isNotBlank()) parts.add(pincode)
        return parts.joinToString(", ")
    }
}
