package com.weelo.logistics.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * =============================================================================
 * CACHED BOOKING ENTITY - Local Cache for Bookings
 * =============================================================================
 * 
 * Caches booking data locally for offline access and faster loading.
 * Syncs with server when online.
 * 
 * =============================================================================
 */
@Entity(tableName = "cached_bookings")
@TypeConverters(BookingConverters::class)
data class CachedBookingEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Booking status
     */
    val status: String,
    
    /**
     * Pickup location details
     */
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    
    /**
     * Drop location details
     */
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    
    /**
     * Vehicle details
     */
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    
    /**
     * Pricing
     */
    val estimatedPrice: Double,
    val finalPrice: Double? = null,
    val currency: String = "INR",
    
    /**
     * Distance and duration
     */
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    
    /**
     * Trucks assigned
     */
    val trucksNeeded: Int,
    val trucksFilled: Int = 0,
    val assignedTrucks: List<AssignedTruckInfo> = emptyList(),
    
    /**
     * Timestamps
     */
    val createdAt: Long,
    val scheduledAt: Long? = null,
    val completedAt: Long? = null,
    
    /**
     * Sync status
     */
    val lastSyncAt: Long = System.currentTimeMillis(),
    val needsSync: Boolean = false,
    val isLocalOnly: Boolean = false, // True if created offline
    
    /**
     * Additional data
     */
    val notes: String? = null,
    val customerPhone: String? = null
)

/**
 * Info about assigned truck
 */
data class AssignedTruckInfo(
    val assignmentId: String,
    val vehicleNumber: String,
    val driverName: String,
    val driverPhone: String,
    val status: String
)

/**
 * TypeConverters for complex types
 */
class BookingConverters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromAssignedTruckList(trucks: List<AssignedTruckInfo>): String {
        return gson.toJson(trucks)
    }
    
    @TypeConverter
    fun toAssignedTruckList(json: String): List<AssignedTruckInfo> {
        val type = object : TypeToken<List<AssignedTruckInfo>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Extension to check if booking can be shown
 */
fun CachedBookingEntity.isActive(): Boolean {
    return status in listOf("PENDING", "CONFIRMED", "IN_PROGRESS", "PARTIALLY_FILLED")
}

/**
 * Extension to check if booking is complete
 */
fun CachedBookingEntity.isCompleted(): Boolean {
    return status in listOf("COMPLETED", "CANCELLED")
}
