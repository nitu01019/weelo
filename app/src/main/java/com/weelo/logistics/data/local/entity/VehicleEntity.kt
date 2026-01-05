package com.weelo.logistics.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.weelo.logistics.domain.model.VehicleCategory
import com.weelo.logistics.domain.model.VehicleModel

/**
 * Room entity for Vehicle
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val capacityTons: String,
    val description: String = "",
    val priceMultiplier: Double = 1.0,
    val basePrice: Int = 3000,
    val pricePerKm: Int = 40,
    val availableCount: Int = 0
)

/**
 * Mapper functions
 */
fun VehicleEntity.toDomain(): VehicleModel {
    return VehicleModel(
        id = id,
        name = name,
        category = VehicleCategory.fromId(category),
        capacityTons = capacityTons,
        description = description,
        priceMultiplier = priceMultiplier,
        basePrice = basePrice,
        pricePerKm = pricePerKm,
        features = getDefaultFeatures(),
        availableCount = availableCount
    )
}

fun VehicleModel.toEntity(): VehicleEntity {
    return VehicleEntity(
        id = id,
        name = name,
        category = category.name,
        capacityTons = capacityTons,
        description = description,
        priceMultiplier = priceMultiplier,
        basePrice = basePrice,
        pricePerKm = pricePerKm,
        availableCount = availableCount
    )
}

private fun getDefaultFeatures(): List<String> {
    return listOf(
        "Professional driver",
        "GPS tracking",
        "24/7 support",
        "Insurance covered"
    )
}
