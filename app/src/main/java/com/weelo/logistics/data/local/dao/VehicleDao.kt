package com.weelo.logistics.data.local.dao

import androidx.room.*
import com.weelo.logistics.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Vehicle operations
 */
@Dao
interface VehicleDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)
    
    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    
    @Query("SELECT * FROM vehicles WHERE category = :category ORDER BY name ASC")
    suspend fun getVehiclesByCategory(category: String): List<VehicleEntity>
    
    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: String): VehicleEntity?
    
    @Query("SELECT * FROM vehicles WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    suspend fun searchVehicles(query: String): List<VehicleEntity>
    
    @Query("DELETE FROM vehicles")
    suspend fun clearAllVehicles()
}
