package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Use case for adding a location to recent history
 */
class AddRecentLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(location: LocationModel): Result<Unit> {
        // Validate location before adding
        if (!location.isValid()) {
            return Result.Error(
                WeeloException.ValidationException("Invalid location address")
            )
        }
        
        return locationRepository.addRecentLocation(location)
    }
}
