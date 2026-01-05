package com.weelo.logistics.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Vehicle selection with quantity
 * Represents how many vehicles of each type user wants to book
 */
@Parcelize
data class VehicleSelection(
    val vehicleType: VehicleType,
    var quantity: Int = 0
) : Parcelable {
    
    fun getTotalPrice(baseDistance: Int): Int {
        val basePrice = (3000 * vehicleType.priceMultiplier).toInt()
        val perKmRate = (40 * vehicleType.priceMultiplier).toInt()
        val pricePerVehicle = basePrice + (baseDistance * perKmRate)
        return pricePerVehicle * quantity
    }
}
