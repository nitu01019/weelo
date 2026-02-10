package com.weelo.logistics.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import java.util.UUID

/**
 * =============================================================================
 * PENDING OPERATION ENTITY - Offline Queue for Operations
 * =============================================================================
 * 
 * Stores operations that need to be synced when network is available.
 * Enables true offline-first architecture.
 * 
 * SUPPORTED OPERATIONS:
 * - CREATE_BOOKING: Queue booking requests
 * - UPDATE_BOOKING: Queue booking updates
 * - CANCEL_BOOKING: Queue booking cancellations
 * - UPDATE_PROFILE: Queue profile updates
 * 
 * =============================================================================
 */
@Entity(tableName = "pending_operations")
@TypeConverters(PendingOperationConverters::class)
data class PendingOperationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    /**
     * Type of operation
     */
    val operationType: OperationType,
    
    /**
     * JSON payload for the operation
     */
    val payload: String,
    
    /**
     * Current status of the operation
     */
    val status: OperationStatus = OperationStatus.PENDING,
    
    /**
     * Number of retry attempts
     */
    val retryCount: Int = 0,
    
    /**
     * Maximum retry attempts allowed
     */
    val maxRetries: Int = 3,
    
    /**
     * Error message if operation failed
     */
    val errorMessage: String? = null,
    
    /**
     * Timestamp when operation was created
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp of last attempt
     */
    val lastAttemptAt: Long? = null,
    
    /**
     * Priority (lower = higher priority)
     */
    val priority: Int = 10,
    
    /**
     * Related entity ID (e.g., booking ID)
     */
    val relatedEntityId: String? = null
)

/**
 * Types of operations that can be queued
 */
enum class OperationType {
    CREATE_BOOKING,
    UPDATE_BOOKING,
    CANCEL_BOOKING,
    UPDATE_PROFILE,
    SYNC_LOCATION,
    CUSTOM
}

/**
 * Status of pending operation
 */
enum class OperationStatus {
    PENDING,        // Waiting to be processed
    IN_PROGRESS,    // Currently being processed
    COMPLETED,      // Successfully completed
    FAILED,         // Failed after max retries
    CANCELLED       // Cancelled by user
}

/**
 * Room TypeConverters for enums
 */
class PendingOperationConverters {
    @TypeConverter
    fun fromOperationType(type: OperationType): String = type.name
    
    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)
    
    @TypeConverter
    fun fromOperationStatus(status: OperationStatus): String = status.name
    
    @TypeConverter
    fun toOperationStatus(value: String): OperationStatus = OperationStatus.valueOf(value)
}

/**
 * Helper to create pending booking operation
 */
fun createPendingBookingOperation(
    pickupLocation: String,
    dropLocation: String,
    pickupLat: Double,
    pickupLng: Double,
    dropLat: Double,
    dropLng: Double,
    vehicleType: String,
    vehicleSubtype: String,
    quantity: Int,
    scheduledTime: Long? = null,
    notes: String? = null
): PendingOperationEntity {
    val payload = mapOf(
        "pickupLocation" to pickupLocation,
        "dropLocation" to dropLocation,
        "pickupLat" to pickupLat,
        "pickupLng" to pickupLng,
        "dropLat" to dropLat,
        "dropLng" to dropLng,
        "vehicleType" to vehicleType,
        "vehicleSubtype" to vehicleSubtype,
        "quantity" to quantity,
        "scheduledTime" to scheduledTime,
        "notes" to notes
    )
    
    return PendingOperationEntity(
        operationType = OperationType.CREATE_BOOKING,
        payload = Gson().toJson(payload),
        priority = 1 // High priority for bookings
    )
}
