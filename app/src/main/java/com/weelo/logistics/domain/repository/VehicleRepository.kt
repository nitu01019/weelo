package com.weelo.logistics.domain.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.VehicleCategory
import com.weelo.logistics.domain.model.VehicleModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for vehicle operations
 */
interface VehicleRepository {
    
    /**
     * Get all available vehicles
     */
    fun getAllVehicles(): Flow<Result<List<VehicleModel>>>
    
    /**
     * Get vehicles by category
     */
    suspend fun getVehiclesByCategory(category: VehicleCategory): Result<List<VehicleModel>>
    
    /**
     * Get vehicle by ID
     */
    suspend fun getVehicleById(id: String): Result<VehicleModel>
    
    /**
     * Get available vehicle categories
     */
    suspend fun getVehicleCategories(): Result<List<VehicleCategory>>
    
    /**
     * Search vehicles by query
     */
    suspend fun searchVehicles(query: String): Result<List<VehicleModel>>
}
