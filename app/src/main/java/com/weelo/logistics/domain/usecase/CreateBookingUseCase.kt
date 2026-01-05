package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.domain.model.BookingModel
import com.weelo.logistics.domain.repository.BookingRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for creating a booking
 * Includes validation and business logic
 */
class CreateBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository
) {
    suspend operator fun invoke(booking: BookingModel): Result<BookingModel> {
        Timber.d("Creating booking from ${booking.fromLocation.address} to ${booking.toLocation.address}")
        
        // Validate booking data
        if (!booking.fromLocation.isValid()) {
            return Result.Error(
                WeeloException.ValidationException("Invalid pickup location")
            )
        }
        
        if (!booking.toLocation.isValid()) {
            return Result.Error(
                WeeloException.ValidationException("Invalid drop location")
            )
        }
        
        if (booking.distanceKm <= 0) {
            return Result.Error(
                WeeloException.ValidationException("Invalid distance")
            )
        }
        
        // Create booking
        return try {
            bookingRepository.createBooking(booking)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create booking")
            Result.Error(
                WeeloException.BookingException("Failed to create booking: ${e.message}")
            )
        }
    }
}
