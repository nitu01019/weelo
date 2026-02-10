package com.weelo.logistics.presentation.trucks

import com.weelo.logistics.R
import com.weelo.logistics.data.models.Location

/**
 * TruckPricingHelper - Utility functions for truck pricing and calculations
 * 
 * Extracted from TruckTypesActivity for better modularity.
 * Contains:
 * - Distance calculation
 * - Base price calculation
 * - Truck icon resource mapping
 * - Truck type descriptions
 */
object TruckPricingHelper {

    /**
     * Calculate distance between two locations in kilometers
     * Uses Haversine formula for accurate earth-surface distance
     */
    fun calculateDistanceKm(fromLocation: Location, toLocation: Location): Int {
        val lat1 = fromLocation.latitude
        val lon1 = fromLocation.longitude
        val lat2 = toLocation.latitude
        val lon2 = toLocation.longitude
        
        // If coordinates not available, return default
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) {
            return 50 // Default 50km
        }
        
        val R = 6371 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = R * c
        
        return distance.toInt().coerceAtLeast(1)
    }

    /**
     * Calculate base price for a truck type and distance
     * Fallback pricing when backend is unavailable
     * @param truckTypeId The truck type identifier
     * @param subtypeId Reserved for future subtype-specific pricing (currently unused)
     * @param distanceKm Distance in kilometers
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateBasePrice(truckTypeId: String, subtypeId: String, distanceKm: Int): Int {
        val baseRates = mapOf(
            "open" to 2000,
            "container" to 3500,
            "tipper" to 2500,
            "tanker" to 3000,
            "dumper" to 2800,
            "bulker" to 4000,
            "trailer" to 6000,
            "lcv" to 1500,
            "mini" to 1000
        )
        
        val baseRate = baseRates[truckTypeId] ?: 2000
        val distanceCharge = distanceKm * 40  // Rs 40 per km
        return baseRate + distanceCharge
    }

    /**
     * Get truck icon resource - uses the SAME PNG icons as main truck type cards
     * This ensures visual consistency across the entire app
     */
    fun getTruckIconResource(truckTypeId: String): Int {
        return when (truckTypeId.lowercase()) {
            "open" -> R.drawable.ic_open_main
            "container" -> R.drawable.ic_container_main
            "lcv" -> R.drawable.ic_lcv_main
            "mini" -> R.drawable.ic_mini_main
            "trailer" -> R.drawable.ic_trailer_main
            "tipper" -> R.drawable.ic_tipper_main
            "tanker" -> R.drawable.ic_tanker_main
            "dumper" -> R.drawable.ic_dumper_main
            "bulker" -> R.drawable.ic_bulker_main
            else -> R.drawable.ic_open_main
        }
    }

    /**
     * Get description text for each truck type
     */
    fun getTruckTypeDescription(truckTypeId: String): String {
        return when (truckTypeId.lowercase()) {
            "open" -> "Open body for general cargo"
            "container" -> "Enclosed container for secure transport"
            "lcv" -> "Light commercial vehicles"
            "mini" -> "Small trucks for local delivery"
            "trailer" -> "Heavy duty trailer trucks"
            "tipper" -> "For construction materials"
            "tanker" -> "For liquid cargo transport"
            "dumper" -> "For bulk material dumping"
            "bulker" -> "For bulk cargo transport"
            else -> "Heavy duty transport"
        }
    }

    /**
     * Get image padding adjustment for truck icons
     * Negative padding makes image larger for specific types
     */
    fun getIconPaddingDp(truckTypeId: String): Int {
        return when (truckTypeId) {
            "dumper" -> -10   // Dumper: 50% larger (negative padding)
            "tanker" -> -10   // Tanker: 50% larger
            "container" -> -10 // Container: 50% larger
            "mini" -> -10     // Mini: 50% larger
            "lcv" -> 1        // LCV: keep current size (slightly smaller)
            else -> 0         // All others: maximum size (no padding)
        }
    }
}
