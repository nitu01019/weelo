package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.remote.geocoding.GeocodingDataSource
import com.weelo.logistics.domain.model.LocationModel
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for calculating distance between two locations using Google Directions API
 */
class CalculateRouteDistanceUseCase @Inject constructor(
    private val geocodingDataSource: GeocodingDataSource
) {
    suspend operator fun invoke(
        fromLocation: LocationModel,
        toLocation: LocationModel
    ): Result<Int> {
        // Validate that we have coordinates
        if (fromLocation.latitude == 0.0 || fromLocation.longitude == 0.0) {
            Timber.w("From location missing coordinates, using mock distance")
            return Result.Success(generateMockDistance())
        }
        
        if (toLocation.latitude == 0.0 || toLocation.longitude == 0.0) {
            Timber.w("To location missing coordinates, using mock distance")
            return Result.Success(generateMockDistance())
        }
        
        // Calculate actual distance using Directions API
        return when (val result = geocodingDataSource.calculateDistance(
            fromLocation.latitude,
            fromLocation.longitude,
            toLocation.latitude,
            toLocation.longitude
        )) {
            is Result.Success -> {
                // Validate distance is within reasonable range
                val distance = result.data
                if (distance in Constants.MIN_DISTANCE_KM..Constants.MAX_DISTANCE_KM) {
                    Result.Success(distance)
                } else {
                    Timber.w("Distance $distance km is out of range, using mock")
                    Result.Success(generateMockDistance())
                }
            }
            is Result.Error -> {
                // Fallback to mock distance if API fails
                Timber.e(result.exception, "Failed to calculate distance, using mock")
                Result.Success(generateMockDistance())
            }
            is Result.Loading -> Result.Loading
        }
    }
    
    /**
     * Generate a reasonable mock distance for demo purposes
     */
    private fun generateMockDistance(): Int {
        return (50..200).random()
    }
}
