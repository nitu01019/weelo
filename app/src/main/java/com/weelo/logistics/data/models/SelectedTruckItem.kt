package com.weelo.logistics.data.models

/**
 * Data class representing a selected truck item
 * Used for displaying confirmed truck selections in a horizontal list
 * 
 * Designed for scalability with immutable data and proper equals/hashCode
 */
data class SelectedTruckItem(
    val id: String,                    // Unique identifier (e.g., "trailer_32ft_1")
    val truckTypeId: String,           // Truck type (e.g., "trailer", "tanker")
    val truckTypeName: String,         // Display name (e.g., "Trailer")
    val specification: String,          // Subtype specification (e.g., "32 Feet", "8-11 Ton")
    val iconResource: Int,             // Drawable resource ID for truck icon
    var quantity: Int = 1              // Number of this truck type selected
) {
    /**
     * Creates a unique key for this selection
     * Used for efficient lookup and updates
     */
    fun getUniqueKey(): String = "${truckTypeId}_${specification}"
}
