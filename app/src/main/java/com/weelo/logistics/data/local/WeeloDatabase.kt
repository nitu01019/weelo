package com.weelo.logistics.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.weelo.logistics.data.local.dao.LocationDao
import com.weelo.logistics.data.local.dao.VehicleDao
import com.weelo.logistics.data.local.entity.LocationEntity
import com.weelo.logistics.data.local.entity.VehicleEntity

/**
 * Room database for Weelo
 */
@Database(
    entities = [LocationEntity::class, VehicleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeeloDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun vehicleDao(): VehicleDao
}
