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
     */
    private fun getDefaultVehicles(): List<VehicleEntity> {
        return listOf(
            VehicleEntity("open", "Open", "OPEN", "7.5 - 43 Ton", "Open body truck for general cargo", 1.2, 3000, 40, 5),
            VehicleEntity("container", "Container", "CONTAINER", "7.5 - 30 Ton", "Enclosed container for protected cargo", 1.4, 3000, 40, 3),
            VehicleEntity("lcv", "LCV", "LCV", "2.5 - 7 Ton", "Light commercial vehicle", 0.8, 3000, 40, 8),
            VehicleEntity("mini", "Mini/Pickup", "MINI", "0.75 - 2 Ton", "Small pickup for light loads", 0.6, 3000, 40, 12),
            VehicleEntity("trailer", "Trailer", "TRAILER", "16 - 43 Ton", "Large trailer for heavy cargo", 1.8, 3000, 40, 2),
            VehicleEntity("tipper", "Tipper", "TIPPER", "9 - 30 Ton", "Tipper for construction materials", 1.3, 3000, 40, 4),
            VehicleEntity("tanker", "Tanker", "TANKER", "8 - 36 Ton", "Tanker for liquid cargo", 1.5, 3000, 40, 3),
            VehicleEntity("dumper", "Dumper", "DUMPER", "9 - 36 Ton", "Dumper for bulk materials", 1.4, 3000, 40, 4),
            VehicleEntity("bulker", "Bulker", "BULKER", "20 - 36 Ton", "Bulk carrier for loose cargo", 1.6, 3000, 40, 2)
        )
    }
}
