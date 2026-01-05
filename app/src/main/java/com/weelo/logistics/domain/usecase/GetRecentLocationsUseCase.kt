package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting recent locations
 * Encapsulates business logic and makes it reusable
 */
class GetRecentLocationsUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<Result<List<LocationModel>>> {
        return locationRepository.getRecentLocations()
    }
}
