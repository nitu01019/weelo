package com.weelo.logistics.data.local.dao

import androidx.room.*
import com.weelo.logistics.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Location operations
 */
@Dao
interface LocationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)
    
    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int = 10): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteLocations(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE id = :locationId")
    suspend fun getLocationById(locationId: String): LocationEntity?
    
    @Query("SELECT * FROM locations WHERE address LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchLocations(query: String): List<LocationEntity>
    
    @Query("UPDATE locations SET isFavorite = :isFavorite WHERE id = :locationId")
    suspend fun updateFavoriteStatus(locationId: String, isFavorite: Boolean)
    
    @Query("DELETE FROM locations")
    suspend fun clearAllLocations()
    
    @Query("DELETE FROM locations WHERE id = :locationId")
    suspend fun deleteLocation(locationId: String)
}
