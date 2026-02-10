package com.weelo.logistics.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.weelo.logistics.data.local.dao.CachedBookingDao
import com.weelo.logistics.data.local.dao.LocationDao
import com.weelo.logistics.data.local.dao.PendingOperationDao
import com.weelo.logistics.data.local.dao.VehicleDao
import com.weelo.logistics.data.local.entity.*

/**
 * =============================================================================
 * WEELO DATABASE - Room Database with Offline-First Support
 * =============================================================================
 * 
 * ENTITIES:
 * - LocationEntity: Recent/favorite locations
 * - VehicleEntity: Cached vehicle data
 * - PendingOperationEntity: Offline operation queue
 * - CachedBookingEntity: Locally cached bookings
 * 
 * VERSION HISTORY:
 * - v1: Initial schema (locations, vehicles)
 * - v2: Added offline-first support (pending_operations, cached_bookings)
 * 
 * =============================================================================
 */
@Database(
    entities = [
        LocationEntity::class, 
        VehicleEntity::class,
        PendingOperationEntity::class,
        CachedBookingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(
    PendingOperationConverters::class,
    BookingConverters::class
)
abstract class WeeloDatabase : RoomDatabase() {
    
    // Existing DAOs
    abstract fun locationDao(): LocationDao
    abstract fun vehicleDao(): VehicleDao
    
    // New DAOs for offline-first
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun cachedBookingDao(): CachedBookingDao
}
