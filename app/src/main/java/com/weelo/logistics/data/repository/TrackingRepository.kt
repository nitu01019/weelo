package com.weelo.logistics.data.repository

import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.WebSocketService
import com.weelo.logistics.data.remote.LocationUpdateEvent
import com.weelo.logistics.data.remote.AssignmentStatusEvent
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.api.AssignedTruckData
import com.weelo.logistics.data.remote.api.TripTrackingData
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * TRACKING REPOSITORY — Single Source of Truth for Live Tracking
 * =============================================================================
 *
 * Provides:
 * - Initial truck positions via REST API (GET /tracking/booking/:bookingId)
 * - Real-time location updates via WebSocket Flow
 * - Real-time status changes via WebSocket Flow
 * - Assigned trucks list via REST API (GET /bookings/:bookingId/trucks)
 *
 * SCALABILITY:
 * - WebSocket handles millions of concurrent connections via Redis PubSub
 * - REST calls are stateless — horizontally scalable
 * - Repository is @Singleton — one WebSocket connection per app instance
 *
 * MODULARITY:
 * - Activity doesn't know about API/WebSocket internals
 * - Easy to swap data source (e.g., mock for testing)
 * - Follows existing repository pattern (see BookingApiRepository)
 *
 * =============================================================================
 */
@Singleton
class TrackingRepository @Inject constructor(
    private val apiService: WeeloApiService,
    private val tokenManager: TokenManager,
    private val webSocketService: WebSocketService
) {
    companion object {
        private const val TAG = "TrackingRepository"
    }

    // =========================================================================
    // REST API — Initial Data Fetch
    // =========================================================================

    /**
     * Fetch all truck positions for a booking (initial load).
     * Called once when tracking screen opens.
     *
     * @param bookingId The booking to track
     * @return List of truck tracking data, or empty list on error
     */
    suspend fun getBookingTracking(bookingId: String): List<TripTrackingData> {
        return try {
            val accessToken = tokenManager.getAccessToken() ?: return emptyList()
            val token = "Bearer $accessToken"
            val response = apiService.getBookingTracking(token, bookingId)

            if (response.isSuccessful && response.body()?.success == true) {
                val trucks = response.body()?.data?.trucks ?: emptyList()
                Timber.d("$TAG: Fetched ${trucks.size} trucks for booking $bookingId")
                trucks
            } else {
                Timber.w("$TAG: Failed to fetch booking tracking: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error fetching booking tracking")
            emptyList()
        }
    }

    /**
     * Fetch assigned trucks for a booking (driver info, vehicle info, status).
     * Called once when tracking screen opens.
     *
     * @param bookingId The booking to get truck assignments for
     * @return List of assigned truck data, or empty list on error
     */
    suspend fun getAssignedTrucks(bookingId: String): List<AssignedTruckData> {
        return try {
            val accessToken = tokenManager.getAccessToken() ?: return emptyList()
            val token = "Bearer $accessToken"
            val response = apiService.getAssignedTrucks(token, bookingId)

            if (response.isSuccessful && response.body()?.success == true) {
                val trucks = response.body()?.data?.trucks ?: emptyList()
                Timber.d("$TAG: Fetched ${trucks.size} assigned trucks for booking $bookingId")
                trucks
            } else {
                Timber.w("$TAG: Failed to fetch assigned trucks: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error fetching assigned trucks")
            emptyList()
        }
    }

    /**
     * Fetch single trip tracking data.
     *
     * @param tripId The trip to track
     * @return Tracking data or null on error
     */
    suspend fun getTripTracking(tripId: String): TripTrackingData? {
        return try {
            val accessToken = tokenManager.getAccessToken() ?: return null
            val token = "Bearer $accessToken"
            val response = apiService.getTripTracking(token, tripId)

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data
            } else {
                Timber.w("$TAG: Failed to fetch trip tracking: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error fetching trip tracking")
            null
        }
    }

    // =========================================================================
    // WEBSOCKET — Real-Time Streams
    // =========================================================================

    /**
     * Connect to WebSocket and join booking room for real-time updates.
     * Must be called before collecting location/status flows.
     *
     * @param bookingId Room to join
     */
    fun startTracking(bookingId: String) {
        webSocketService.connect()
        webSocketService.joinBookingRoom(bookingId)
        Timber.d("$TAG: Started tracking for booking $bookingId")
    }

    /**
     * Leave booking room and optionally disconnect.
     *
     * @param bookingId Room to leave
     */
    fun stopTracking(bookingId: String) {
        webSocketService.leaveBookingRoom(bookingId)
        Timber.d("$TAG: Stopped tracking for booking $bookingId")
    }

    /**
     * Real-time location updates as Flow.
     * Each emission = one driver moved.
     *
     * Payload: { tripId, driverId, latitude, longitude, speed, bearing, timestamp }
     */
    fun locationUpdates(): Flow<LocationUpdateEvent> = webSocketService.locationUpdates()

    /**
     * Real-time assignment status changes as Flow.
     * Each emission = one truck's status changed (e.g., in_transit → completed).
     *
     * Payload: { assignmentId, tripId, status, vehicleNumber }
     */
    fun statusUpdates(): Flow<AssignmentStatusEvent> = webSocketService.assignmentStatusChanges()

    /**
     * Listen for booking_completed events (all trucks delivered).
     * Used to auto-show rating bottom sheet.
     */
    fun bookingCompleted(): Flow<com.weelo.logistics.data.remote.BookingCompletedEvent> = webSocketService.onBookingCompleted()

    /**
     * WebSocket connection state for showing "Reconnecting..." banner.
     */
    val connectionState = webSocketService.connectionState

    /**
     * Force reconnect (manual retry by user).
     */
    fun forceReconnect() = webSocketService.forceReconnect()

    /**
     * Get auth token for API calls.
     * Used by BookingTrackingActivity for route polyline fetch.
     */
    fun getToken(): String? = tokenManager.getAccessToken()

    /**
     * Get API service for direct calls (e.g., route polyline).
     * MODULARITY: Exposes apiService for features that don't
     * need repository abstraction (like polyline fetch).
     */
    fun getApiService(): WeeloApiService = apiService
}
