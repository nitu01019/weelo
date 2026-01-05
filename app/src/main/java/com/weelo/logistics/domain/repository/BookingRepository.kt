package com.weelo.logistics.domain.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.BookingModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for booking operations
 */
interface BookingRepository {
    
    /**
     * Create a new booking
     */
    suspend fun createBooking(booking: BookingModel): Result<BookingModel>
    
    /**
     * Get booking by ID
     */
    suspend fun getBookingById(bookingId: String): Result<BookingModel>
    
    /**
     * Get all user bookings
     */
    fun getUserBookings(): Flow<Result<List<BookingModel>>>
    
    /**
     * Cancel a booking
     */
    suspend fun cancelBooking(bookingId: String): Result<Unit>
    
    /**
     * Calculate distance between two locations
     */
    suspend fun calculateDistance(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Result<Int>
}
