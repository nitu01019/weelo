package com.weelo.logistics.domain.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for location operations
 * Defines contract for data layer to implement
 */
interface LocationRepository {
    
    /**
     * Add a location to recent history
     */
    suspend fun addRecentLocation(location: LocationModel): Result<Unit>
    
    /**
     * Get all recent locations
     */
    fun getRecentLocations(): Flow<Result<List<LocationModel>>>
    
    /**
     * Toggle favorite status for a location
     */
    suspend fun toggleFavorite(locationId: String): Result<Boolean>
    
    /**
     * Get favorite locations
     */
    fun getFavoriteLocations(): Flow<Result<List<LocationModel>>>
    
    /**
     * Search locations by query
     */
    suspend fun searchLocations(query: String): Result<List<LocationModel>>
    
    /**
     * Delete a specific location from recent history
     * SCALABILITY: Single record deletion, O(1) operation
     */
    suspend fun deleteLocation(locationId: String): Result<Unit>
    
    /**
     * Clear all recent locations
     */
    suspend fun clearRecentLocations(): Result<Unit>
    
    /**
     * Validate location address
     */
    fun validateLocation(address: String): Result<Boolean>
}
