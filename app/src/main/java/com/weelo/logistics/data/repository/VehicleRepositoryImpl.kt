package com.weelo.logistics.data.repository

import android.content.Context
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.local.dao.VehicleDao
import com.weelo.logistics.data.local.entity.VehicleEntity
import com.weelo.logistics.data.local.entity.toDomain
import com.weelo.logistics.domain.model.VehicleCategory
import com.weelo.logistics.domain.model.VehicleModel
import com.weelo.logistics.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of VehicleRepository
 */
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val context: Context
) : VehicleRepository {

    init {
        // Initialize with default vehicles if database is empty
        Timber.d("VehicleRepository initialized")
    }

    override fun getAllVehicles(): Flow<Result<List<VehicleModel>>> {
        return flow {
            // First, check if we need to populate the database
            val existingVehicles = vehicleDao.getAllVehicles()
            
            existingVehicles
                .map { entities ->
                    if (entities.isEmpty()) {
                        // Populate with default vehicles
                        val defaultVehicles = getDefaultVehicles()
                        vehicleDao.insertVehicles(defaultVehicles)
                        Result.Success(defaultVehicles.map { it.toDomain() })
                    } else {
                        Result.Success(entities.map { it.toDomain() })
                    }
                }
                .catch { e ->
                    Timber.e(e, "Failed to get vehicles")
                    emit(Result.Error(WeeloException.UnknownException("Failed to load vehicles")))
                }
                .collect { emit(it) }
        }
    }

    override suspend fun getVehiclesByCategory(category: VehicleCategory): Result<List<VehicleModel>> {
        return try {
            val entities = vehicleDao.getVehiclesByCategory(category.name)
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get vehicles by category")
            Result.Error(WeeloException.UnknownException("Failed to load vehicles"))
        }
    }

    override suspend fun getVehicleById(id: String): Result<VehicleModel> {
        return try {
            val entity = vehicleDao.getVehicleById(id)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                // Fallback to default vehicles
                val defaultVehicle = getDefaultVehicles().find { it.id == id }
                if (defaultVehicle != null) {
                    Result.Success(defaultVehicle.toDomain())
                } else {
                    Result.Error(WeeloException.UnknownException("Vehicle not found"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get vehicle by ID")
            Result.Error(WeeloException.UnknownException("Failed to load vehicle"))
        }
    }

    override suspend fun getVehicleCategories(): Result<List<VehicleCategory>> {
        return try {
            val categories = VehicleCategory.values().toList()
            Result.Success(categories)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get vehicle categories")
            Result.Error(WeeloException.UnknownException("Failed to load categories"))
        }
    }

    override suspend fun searchVehicles(query: String): Result<List<VehicleModel>> {
        return try {
            val entities = vehicleDao.searchVehicles(query)
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Timber.e(e, "Failed to search vehicles")
            Result.Error(WeeloException.UnknownException("Search failed"))
        }
    }

    /**
     * Get default vehicles for initial population
     * 
     * These subtypes match the backend vehicle catalog for proper validation.
     * Format: id = "type_subtype", name = subtype name matching backend
     */
    private fun getDefaultVehicles(): List<VehicleEntity> {
        return listOf(
            // MINI / Pickup trucks
            VehicleEntity("mini_dost", "Pickup Truck - Dost", "MINI", "0.5 - 1.5 Ton", "Small pickup for light loads", 1.0, 500, 12, 12),
            VehicleEntity("mini_ace", "Mini Truck - Tata Ace", "MINI", "0.5 - 1 Ton", "Mini truck for local deliveries", 0.9, 500, 12, 10),
            
            // LCV - Light Commercial Vehicles
            VehicleEntity("lcv_open_14", "LCV Open - 14 Feet", "LCV", "2 - 4 Ton", "Light commercial vehicle - open body", 1.0, 800, 15, 8),
            VehicleEntity("lcv_open_17", "LCV Open - 17 Feet", "LCV", "3 - 5 Ton", "Light commercial vehicle - open body", 1.15, 800, 15, 6),
            VehicleEntity("lcv_container_14", "LCV Container - 14 Feet", "LCV", "2 - 4 Ton", "Light commercial vehicle - container", 1.1, 800, 15, 5),
            VehicleEntity("lcv_container_17", "LCV Container - 17 Feet", "LCV", "3 - 5 Ton", "Light commercial vehicle - container", 1.25, 800, 15, 4),
            
            // OPEN Trucks
            VehicleEntity("open_17", "Open Body - 17 Feet", "OPEN", "6 - 9 Ton", "Open body truck for general cargo", 1.0, 1200, 18, 5),
            VehicleEntity("open_19", "Open Body - 19 Feet", "OPEN", "8 - 12 Ton", "Open body truck for general cargo", 1.1, 1200, 18, 4),
            VehicleEntity("open_22", "Open Body - 22 Feet", "OPEN", "10 - 15 Ton", "Open body truck for general cargo", 1.2, 1200, 18, 3),
            VehicleEntity("open_32", "Open Body - 32 Feet", "OPEN", "15 - 21 Ton", "Open body truck for heavy cargo", 1.4, 1200, 18, 2),
            
            // CONTAINER Trucks
            VehicleEntity("container_20", "Container - 20 Feet", "CONTAINER", "8 - 14 Ton", "Enclosed container for protected cargo", 1.0, 1500, 20, 4),
            VehicleEntity("container_24", "Container - 24 Feet", "CONTAINER", "10 - 18 Ton", "Enclosed container for protected cargo", 1.15, 1500, 20, 3),
            VehicleEntity("container_32", "Container - 32 Feet", "CONTAINER", "16 - 25 Ton", "Enclosed container for protected cargo", 1.3, 1500, 20, 2),
            
            // TRAILER
            VehicleEntity("trailer_40", "Trailer - 40 Feet", "TRAILER", "20 - 30 Ton", "Large trailer for heavy cargo", 1.0, 2500, 25, 2),
            VehicleEntity("trailer_flatbed", "Flatbed Trailer", "TRAILER", "25 - 40 Ton", "Flatbed for oversized cargo", 1.3, 2500, 25, 1),
            
            // TIPPER
            VehicleEntity("tipper_10", "Tipper - 10 Wheeler", "TIPPER", "16 - 25 Ton", "Tipper for construction materials", 1.0, 2000, 22, 4),
            VehicleEntity("tipper_12", "Tipper - 12 Wheeler", "TIPPER", "25 - 35 Ton", "Large tipper for bulk materials", 1.2, 2000, 22, 2),
            
            // TANKER
            VehicleEntity("tanker_oil", "Tanker - Oil/Fuel", "TANKER", "10 - 24 KL", "Tanker for fuel/oil transport", 1.0, 2200, 24, 3),
            VehicleEntity("tanker_water", "Tanker - Water", "TANKER", "10 - 20 KL", "Water tanker", 0.9, 2200, 24, 2),
            
            // DUMPER
            VehicleEntity("dumper_10", "Dumper - 10 Wheeler", "DUMPER", "16 - 25 Ton", "Dumper for construction materials", 1.0, 2000, 22, 4),
            VehicleEntity("dumper_12", "Dumper - 12 Wheeler", "DUMPER", "25 - 35 Ton", "Large dumper for mining/construction", 1.2, 2000, 22, 2),
            
            // BULKER
            VehicleEntity("bulker_cement", "Bulker - Cement", "BULKER", "25 - 35 Ton", "Bulk carrier for cement", 1.0, 2500, 26, 2),
            VehicleEntity("bulker_fly_ash", "Bulker - Fly Ash", "BULKER", "20 - 30 Ton", "Bulk carrier for fly ash", 0.9, 2500, 26, 1)
        )
    }
}
