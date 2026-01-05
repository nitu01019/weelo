package com.weelo.logistics.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.weelo.logistics.domain.model.LocationModel
import java.util.UUID

/**
 * Room entity for Location
 */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Mapper functions
 */
fun LocationEntity.toDomain(): LocationModel {
    return LocationModel(
        id = id,
        address = address,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        pincode = pincode,
        isFavorite = isFavorite,
        timestamp = timestamp
    )
}

fun LocationModel.toEntity(): LocationEntity {
    return LocationEntity(
        id = id.ifEmpty { UUID.randomUUID().toString() },
        address = address,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        pincode = pincode,
        isFavorite = isFavorite,
        timestamp = timestamp
    )
}
