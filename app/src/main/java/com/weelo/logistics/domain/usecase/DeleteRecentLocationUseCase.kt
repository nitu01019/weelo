package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.repository.LocationRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * =============================================================================
 * DELETE RECENT LOCATION USE CASE
 * =============================================================================
 * 
 * SCALABILITY:
 * - Single responsibility: Only handles location deletion
 * - Suspending function for non-blocking execution
 * - Delegates to repository for actual DB operation
 * 
 * EASY UNDERSTANDING:
 * - Simple invoke operator for clean usage: deleteUseCase(location)
 * - Clear validation before delete
 * - Proper error handling with Result wrapper
 * 
 * MODULARITY:
 * - Follows same pattern as AddRecentLocationUseCase
 * - Injected via Hilt @Inject constructor
 * - Can be unit tested with mock repository
 * 
 * CODING STANDARDS:
 * - Kotlin coroutines for async operations
 * - Result wrapper for success/error handling
 * - Timber logging for debugging
 * 
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
class DeleteRecentLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    /**
     * Delete a location from recent history
     * 
     * @param location The location to delete
     * @return Result.Success if deleted, Result.Error if failed
     */
    suspend operator fun invoke(location: LocationModel): Result<Unit> {
        // Validate location has an ID
        if (location.id.isBlank()) {
            Timber.w("Cannot delete location without ID: ${location.address}")
            return Result.Error(
                WeeloException.ValidationException("Location ID is required for deletion")
            )
        }
        
        Timber.d("Deleting location: ${location.id} - ${location.address}")
        return locationRepository.deleteLocation(location.id)
    }
    
    /**
     * Delete a location by ID directly
     * SCALABILITY: Alternative method when only ID is available
     * 
     * @param locationId The ID of the location to delete
     * @return Result.Success if deleted, Result.Error if failed
     */
    suspend fun deleteById(locationId: String): Result<Unit> {
        if (locationId.isBlank()) {
            return Result.Error(
                WeeloException.ValidationException("Location ID cannot be blank")
            )
        }
        
        Timber.d("Deleting location by ID: $locationId")
        return locationRepository.deleteLocation(locationId)
    }
}
