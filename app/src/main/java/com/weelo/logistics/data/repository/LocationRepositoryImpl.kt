package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.local.dao.LocationDao
import com.weelo.logistics.data.local.entity.toDomain
import com.weelo.logistics.data.local.entity.toEntity
import com.weelo.logistics.data.local.preferences.PreferencesManager
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of LocationRepository
 */
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val preferencesManager: PreferencesManager
) : LocationRepository {

    override suspend fun addRecentLocation(location: LocationModel): Result<Unit> {
        return try {
            val entity = location.toEntity()
            locationDao.insertLocation(entity)
            Timber.d("Added recent location: ${location.address}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add recent location")
            Result.Error(WeeloException.UnknownException("Failed to save location"))
        }
    }

    override fun getRecentLocations(): Flow<Result<List<LocationModel>>> {
        return locationDao.getRecentLocations(Constants.MAX_RECENT_LOCATIONS)
            .map<List<com.weelo.logistics.data.local.entity.LocationEntity>, Result<List<LocationModel>>> { entities ->
                val locations = entities.map { it.toDomain() }
                Result.Success(locations)
            }
            .catch { e ->
                Timber.e(e, "Failed to get recent locations")
                emit(Result.Error(WeeloException.UnknownException("Failed to load locations")))
            }
    }

    override suspend fun toggleFavorite(locationId: String): Result<Boolean> {
        return try {
            val location = locationDao.getLocationById(locationId)
            if (location != null) {
                val newFavoriteStatus = !location.isFavorite
                locationDao.updateFavoriteStatus(locationId, newFavoriteStatus)
                Timber.d("Toggled favorite for location: $locationId")
                Result.Success(newFavoriteStatus)
            } else {
                Result.Error(WeeloException.LocationException("Location not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle favorite")
            Result.Error(WeeloException.UnknownException("Failed to update favorite"))
        }
    }

    override fun getFavoriteLocations(): Flow<Result<List<LocationModel>>> {
        return locationDao.getFavoriteLocations()
            .map<List<com.weelo.logistics.data.local.entity.LocationEntity>, Result<List<LocationModel>>> { entities ->
                val locations = entities.map { it.toDomain() }
                Result.Success(locations)
            }
            .catch { e ->
                Timber.e(e, "Failed to get favorite locations")
                emit(Result.Error(WeeloException.UnknownException("Failed to load favorites")))
            }
    }

    override suspend fun searchLocations(query: String): Result<List<LocationModel>> {
        return try {
            val entities = locationDao.searchLocations(query)
            val locations = entities.map { it.toDomain() }
            Result.Success(locations)
        } catch (e: Exception) {
            Timber.e(e, "Failed to search locations")
            Result.Error(WeeloException.UnknownException("Search failed"))
        }
    }

    override suspend fun deleteLocation(locationId: String): Result<Unit> {
        return try {
            locationDao.deleteLocation(locationId)
            Timber.d("Deleted location: $locationId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete location: $locationId")
            Result.Error(WeeloException.UnknownException("Failed to delete location"))
        }
    }

    override suspend fun clearRecentLocations(): Result<Unit> {
        return try {
            locationDao.clearAllLocations()
            Timber.d("Cleared all recent locations")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear recent locations")
            Result.Error(WeeloException.UnknownException("Failed to clear locations"))
        }
    }

    override fun validateLocation(address: String): Result<Boolean> {
        return when {
            address.isBlank() -> {
                Result.Error(WeeloException.ValidationException("Location cannot be empty"))
            }
            address.length < Constants.MIN_LOCATION_LENGTH -> {
                Result.Error(WeeloException.ValidationException("Location is too short"))
            }
            address.length > Constants.MAX_LOCATION_LENGTH -> {
                Result.Error(WeeloException.ValidationException("Location is too long"))
            }
            else -> {
                Result.Success(true)
            }
        }
    }
}
