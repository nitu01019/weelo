package com.weelo.logistics.data.managers

import android.content.Context
import com.weelo.logistics.R
import com.weelo.logistics.data.models.VehicleCategory
import com.weelo.logistics.data.models.VehicleType

/**
 * VehicleDataManager - Central repository for vehicle data
 * Provides vehicle types and categories with professional icons
 */
class VehicleDataManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: VehicleDataManager? = null
        
        fun getInstance(context: Context): VehicleDataManager {
            return instance ?: synchronized(this) {
                instance ?: VehicleDataManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Get all vehicle types for detailed selection
     * Note: iconResource uses placeholder - emojis used in UI instead
     */
    fun getAllVehicleTypes(): List<VehicleType> {
        return listOf(
            VehicleType(
                id = "open",
                name = "Open",
                iconResource = android.R.drawable.ic_menu_gallery, // Placeholder
                capacityRange = "7.5 - 43 Ton",
                description = "Open body truck for general cargo",
                priceMultiplier = 1.2
            ),
            VehicleType(
                id = "container",
                name = "Container",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "7.5 - 30 Ton",
                description = "Enclosed container for protected cargo",
                priceMultiplier = 1.4
            ),
            VehicleType(
                id = "lcv",
                name = "LCV",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "2.5 - 7 Ton",
                description = "Light commercial vehicle",
                priceMultiplier = 0.8
            ),
            VehicleType(
                id = "mini",
                name = "Mini/Pickup",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "0.75 - 2 Ton",
                description = "Small pickup for light loads",
                priceMultiplier = 0.6
            ),
            VehicleType(
                id = "trailer",
                name = "Trailer",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "16 - 43 Ton",
                description = "Large trailer for heavy cargo",
                priceMultiplier = 1.8
            ),
            VehicleType(
                id = "tipper",
                name = "Tipper",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "9 - 30 Ton",
                description = "Tipper for construction materials",
                priceMultiplier = 1.3
            ),
            VehicleType(
                id = "tanker",
                name = "Tanker",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "8 - 36 Ton",
                description = "Tanker for liquid cargo",
                priceMultiplier = 1.5
            ),
            VehicleType(
                id = "dumper",
                name = "Dumper",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "9 - 36 Ton",
                description = "Dumper for bulk materials",
                priceMultiplier = 1.4
            ),
            VehicleType(
                id = "bulker",
                name = "Bulker",
                iconResource = android.R.drawable.ic_menu_gallery,
                capacityRange = "20 - 36 Ton",
                description = "Bulk carrier for loose cargo",
                priceMultiplier = 1.6
            )
        )
    }
    
    /**
     * Get vehicle categories for main screen
     * Note: iconResource uses placeholder - emojis used in UI instead
     */
    fun getVehicleCategories(): List<VehicleCategory> {
        return listOf(
            VehicleCategory(
                id = "truck",
                name = "Truck",
                iconResource = android.R.drawable.ic_menu_gallery,
                nearbyCount = 3
            ),
            VehicleCategory(
                id = "tractor",
                name = "Tractor",
                iconResource = android.R.drawable.ic_menu_gallery,
                nearbyCount = 5
            ),
            VehicleCategory(
                id = "tempo",
                name = "Tempo",
                iconResource = android.R.drawable.ic_menu_gallery,
                nearbyCount = 7
            )
        )
    }
    
    /**
     * Get vehicle type by ID
     */
    fun getVehicleTypeById(id: String): VehicleType? {
        return getAllVehicleTypes().find { it.id == id }
    }
}
