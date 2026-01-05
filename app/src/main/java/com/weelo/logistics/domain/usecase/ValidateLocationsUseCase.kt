package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.domain.model.LocationModel
import javax.inject.Inject

/**
 * Use case for validating pickup and drop locations
 */
class ValidateLocationsUseCase @Inject constructor() {
    
    operator fun invoke(fromLocation: LocationModel, toLocation: LocationModel): Result<Boolean> {
        // Validate from location
        if (!fromLocation.isValid()) {
            return Result.Error(
                WeeloException.ValidationException("Please enter a valid pickup location")
            )
        }
        
        // Validate to location
        if (!toLocation.isValid()) {
            return Result.Error(
                WeeloException.ValidationException("Please enter a valid drop location")
            )
        }
        
        // Check minimum length
        if (fromLocation.address.length < Constants.MIN_LOCATION_LENGTH) {
            return Result.Error(
                WeeloException.ValidationException("Pickup location is too short")
            )
        }
        
        if (toLocation.address.length < Constants.MIN_LOCATION_LENGTH) {
            return Result.Error(
                WeeloException.ValidationException("Drop location is too short")
            )
        }
        
        // Check if locations are different
        if (fromLocation.address.equals(toLocation.address, ignoreCase = true)) {
            return Result.Error(
                WeeloException.ValidationException("Pickup and drop locations must be different")
            )
        }
        
        return Result.Success(true)
    }
}
