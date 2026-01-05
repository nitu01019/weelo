package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.domain.model.BookingModel
import com.weelo.logistics.domain.repository.BookingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.math.*

/**
 * Implementation of BookingRepository
 * Currently uses mock data - integrate with API later
 */
class BookingRepositoryImpl @Inject constructor() : BookingRepository {

    private val bookings = mutableListOf<BookingModel>()

    override suspend fun createBooking(booking: BookingModel): Result<BookingModel> {
        return try {
            // Simulate network delay
            delay(1000)
            
            // Generate booking ID
            val bookingWithId = booking.copy(
                id = UUID.randomUUID().toString()
            )
            
            // Save booking
            bookings.add(bookingWithId)
            
            Timber.d("Booking created: ${bookingWithId.id}")
            Result.Success(bookingWithId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create booking")
            Result.Error(WeeloException.BookingException("Failed to create booking"))
        }
    }

    override suspend fun getBookingById(bookingId: String): Result<BookingModel> {
        return try {
            delay(500)
            
            val booking = bookings.find { it.id == bookingId }
            if (booking != null) {
                Result.Success(booking)
            } else {
                Result.Error(WeeloException.BookingException("Booking not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get booking")
            Result.Error(WeeloException.BookingException("Failed to load booking"))
        }
    }

    override fun getUserBookings(): Flow<Result<List<BookingModel>>> {
        return flow {
            try {
                delay(500)
                emit(Result.Success(bookings.toList()))
            } catch (e: Exception) {
                Timber.e(e, "Failed to get user bookings")
                emit(Result.Error(WeeloException.BookingException("Failed to load bookings")))
            }
        }
    }

    override suspend fun cancelBooking(bookingId: String): Result<Unit> {
        return try {
            delay(500)
            
            val removed = bookings.removeIf { it.id == bookingId }
            if (removed) {
                Timber.d("Booking cancelled: $bookingId")
                Result.Success(Unit)
            } else {
                Result.Error(WeeloException.BookingException("Booking not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel booking")
            Result.Error(WeeloException.BookingException("Failed to cancel booking"))
        }
    }

    override suspend fun calculateDistance(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Result<Int> {
        return try {
            // Use Haversine formula to calculate distance
            val distance = calculateHaversineDistance(fromLat, fromLng, toLat, toLng)
            Result.Success(distance)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate distance")
            // Return a mock distance for now
            Result.Success((50..200).random())
        }
    }

    /**
     * Calculate distance using Haversine formula
     */
    private fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Int {
        val earthRadius = 6371.0 // Earth radius in kilometers

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distance = earthRadius * c

        return distance.roundToInt()
    }
}
