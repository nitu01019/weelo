package com.weelo.logistics.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket Service for real-time updates
 * 
 * Handles real-time communication:
 * - Booking status updates
 * - Truck assignment notifications
 * - Driver location tracking
 * 
 * SECURITY:
 * - Authenticated via JWT token
 * - Room-based isolation (only sees own data)
 */
@Singleton
class WebSocketService @Inject constructor(
    private val tokenManager: TokenManager
) {
    private var socket: Socket? = null
    private var isConnected = false

    /**
     * Socket events
     */
    object Events {
        // Client -> Server
        const val JOIN_BOOKING = "join_booking"
        const val LEAVE_BOOKING = "leave_booking"
        
        // Server -> Client
        const val CONNECTED = "connected"
        const val BOOKING_UPDATED = "booking_updated"
        const val TRUCK_ASSIGNED = "truck_assigned"
        const val LOCATION_UPDATED = "location_updated"
        const val ASSIGNMENT_STATUS_CHANGED = "assignment_status_changed"
        const val ERROR = "error"
    }

    /**
     * Connect to WebSocket server
     */
    fun connect(): Boolean {
        if (isConnected) return true

        val token = tokenManager.getAccessToken()
        if (token.isNullOrBlank()) {
            Timber.w("Cannot connect WebSocket: No auth token")
            return false
        }

        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                timeout = 10000
            }

            socket = IO.socket(ApiConfig.SOCKET_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                isConnected = true
                Timber.d("WebSocket connected")
            }

            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                isConnected = false
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Timber.d("WebSocket disconnected: $reason")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Timber.e("WebSocket connection error: $error")
            }

            socket?.on(Events.CONNECTED) { args ->
                Timber.d("WebSocket authenticated: ${args.firstOrNull()}")
            }

            socket?.on(Events.ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Timber.e("WebSocket error: $error")
            }

            socket?.connect()
            return true
        } catch (e: Exception) {
            Timber.e(e, "WebSocket connect failed")
            return false
        }
    }

    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        socket?.disconnect()
        socket = null
        isConnected = false
        Timber.d("WebSocket disconnected manually")
    }

    /**
     * Join a booking room for real-time updates
     */
    fun joinBookingRoom(bookingId: String) {
        if (!isConnected) {
            Timber.w("Cannot join room: Not connected")
            return
        }
        socket?.emit(Events.JOIN_BOOKING, bookingId)
        Timber.d("Joined booking room: $bookingId")
    }

    /**
     * Leave a booking room
     */
    fun leaveBookingRoom(bookingId: String) {
        socket?.emit(Events.LEAVE_BOOKING, bookingId)
        Timber.d("Left booking room: $bookingId")
    }

    /**
     * Listen for booking updates as Flow
     */
    fun bookingUpdates(): Flow<BookingUpdateEvent> = callbackFlow {
        val listener = Emitter.Listener { args ->
            try {
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = BookingUpdateEvent(
                        bookingId = data.optString("bookingId"),
                        status = data.optString("status"),
                        trucksFilled = data.optInt("trucksFilled", -1),
                        trucksNeeded = data.optInt("trucksNeeded", -1)
                    )
                    trySend(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing booking update")
            }
        }

        socket?.on(Events.BOOKING_UPDATED, listener)

        awaitClose {
            socket?.off(Events.BOOKING_UPDATED, listener)
        }
    }

    /**
     * Listen for truck assignments as Flow
     */
    fun truckAssignments(): Flow<TruckAssignedEvent> = callbackFlow {
        val listener = Emitter.Listener { args ->
            try {
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val assignment = data.optJSONObject("assignment")
                    val event = TruckAssignedEvent(
                        bookingId = data.optString("bookingId"),
                        assignmentId = assignment?.optString("id") ?: "",
                        vehicleNumber = assignment?.optString("vehicleNumber") ?: "",
                        driverName = assignment?.optString("driverName") ?: "",
                        status = assignment?.optString("status") ?: ""
                    )
                    trySend(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing truck assignment")
            }
        }

        socket?.on(Events.TRUCK_ASSIGNED, listener)

        awaitClose {
            socket?.off(Events.TRUCK_ASSIGNED, listener)
        }
    }

    /**
     * Listen for location updates as Flow
     */
    fun locationUpdates(): Flow<LocationUpdateEvent> = callbackFlow {
        val listener = Emitter.Listener { args ->
            try {
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = LocationUpdateEvent(
                        tripId = data.optString("tripId"),
                        driverId = data.optString("driverId"),
                        latitude = data.optDouble("latitude"),
                        longitude = data.optDouble("longitude"),
                        speed = data.optDouble("speed").toFloat(),
                        bearing = data.optDouble("bearing").toFloat(),
                        timestamp = data.optString("timestamp")
                    )
                    trySend(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing location update")
            }
        }

        socket?.on(Events.LOCATION_UPDATED, listener)

        awaitClose {
            socket?.off(Events.LOCATION_UPDATED, listener)
        }
    }

    /**
     * Listen for assignment status changes as Flow
     */
    fun assignmentStatusChanges(): Flow<AssignmentStatusEvent> = callbackFlow {
        val listener = Emitter.Listener { args ->
            try {
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = AssignmentStatusEvent(
                        assignmentId = data.optString("assignmentId"),
                        tripId = data.optString("tripId"),
                        status = data.optString("status"),
                        vehicleNumber = data.optString("vehicleNumber")
                    )
                    trySend(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing assignment status")
            }
        }

        socket?.on(Events.ASSIGNMENT_STATUS_CHANGED, listener)

        awaitClose {
            socket?.off(Events.ASSIGNMENT_STATUS_CHANGED, listener)
        }
    }

    fun isConnected(): Boolean = isConnected
}

// Event data classes
data class BookingUpdateEvent(
    val bookingId: String,
    val status: String,
    val trucksFilled: Int,
    val trucksNeeded: Int
)

data class TruckAssignedEvent(
    val bookingId: String,
    val assignmentId: String,
    val vehicleNumber: String,
    val driverName: String,
    val status: String
)

data class LocationUpdateEvent(
    val tripId: String,
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val timestamp: String
)

data class AssignmentStatusEvent(
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val vehicleNumber: String
)
