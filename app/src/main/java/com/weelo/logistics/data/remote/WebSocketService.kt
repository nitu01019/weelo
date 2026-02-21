package com.weelo.logistics.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * =============================================================================
 * WEBSOCKET SERVICE - Production-Ready Real-Time Communication
 * =============================================================================
 * 
 * Handles real-time communication with robust reconnection and fallback.
 * 
 * FEATURES:
 * - Exponential backoff reconnection (max 5 attempts)
 * - Connection state management with StateFlow
 * - Heartbeat/ping mechanism
 * - HTTP polling fallback when WebSocket fails
 * - Automatic reconnection on network restore
 * 
 * SECURITY:
 * - Authenticated via JWT token
 * - Room-based isolation (only sees own data)
 * 
 * =============================================================================
 */
@Singleton
class WebSocketService @Inject constructor(
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "WebSocketService"
        
        // Reconnection config
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_MULTIPLIER = 2.0
        
        // Heartbeat config
        private const val HEARTBEAT_INTERVAL_MS = 25000L // 25 seconds
        private const val HEARTBEAT_TIMEOUT_MS = 10000L  // 10 seconds
    }
    
    /**
     * Connection states
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED  // Max reconnect attempts reached
    }
    
    private var socket: Socket? = null
    
    // Connection state management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _socketEpoch = MutableStateFlow(0)
    
    // Reconnection tracking
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectJob: Job? = null
    
    // Heartbeat
    private var heartbeatJob: Job? = null
    private var lastPongTime = 0L
    
    // Coroutine scope for background tasks
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Current booking room (for reconnection)
    private var currentBookingRoom: String? = null

    /**
     * Socket events
     */
    object Events {
        // Client -> Server
        const val JOIN_BOOKING = "join_booking"
        const val LEAVE_BOOKING = "leave_booking"
        const val PING = "ping"
        
        // Server -> Client
        const val CONNECTED = "connected"
        const val BOOKING_UPDATED = "booking_updated"
        const val TRUCK_ASSIGNED = "truck_assigned"
        const val LOCATION_UPDATED = "location_updated"
        const val ASSIGNMENT_STATUS_CHANGED = "assignment_status_changed"
        const val BOOKING_COMPLETED = "booking_completed"
        const val PONG = "pong"
        const val ERROR = "error"
        // Order lifecycle events (PRD 4.2)
        const val ORDER_CANCELLED = "order_cancelled"
        const val ORDER_EXPIRED = "order_expired"
        const val BROADCAST_STATE_CHANGED = "broadcast_state_changed"
        const val TRUCKS_REMAINING_UPDATE = "trucks_remaining_update"
        const val BOOKING_FULLY_FILLED = "booking_fully_filled"
    }

    /**
     * Connect to WebSocket server
     */
    fun connect(): Boolean {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Timber.d("$TAG: Already connected")
            return true
        }
        
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Timber.d("$TAG: Connection already in progress")
            return true
        }

        val token = tokenManager.getAccessToken()
        if (token.isNullOrBlank()) {
            Timber.w("$TAG: Cannot connect - No auth token")
            return false
        }

        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = false // We handle reconnection manually
                timeout = 10000
                forceNew = true
            }

            socket = IO.socket(ApiConfig.SOCKET_URL, options)
            bumpSocketEpoch()
            setupSocketListeners()
            socket?.connect()
            
            Timber.d("$TAG: Connection initiated")
            return true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Connection failed")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
            return false
        }
    }
    
    /**
     * Setup all socket event listeners
     */
    private fun setupSocketListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                Timber.d("$TAG: Socket connected")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts.set(0)
                
                // Rejoin room if we were in one
                currentBookingRoom?.let { roomId ->
                    Timber.d("$TAG: Rejoining room: $roomId")
                    emit(Events.JOIN_BOOKING, roomId)
                }
                
                // Start heartbeat
                startHeartbeat()
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Timber.d("$TAG: Socket disconnected: $reason")
                
                stopHeartbeat()
                
                // Don't reconnect if manually disconnected
                if (_connectionState.value != ConnectionState.DISCONNECTED) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Timber.e("$TAG: Connection error: $error")
                
                if (_connectionState.value == ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                }
            }

            on(Events.CONNECTED) { args ->
                Timber.d("$TAG: Authenticated: ${args.firstOrNull()}")
            }
            
            on(Events.PONG) {
                lastPongTime = System.currentTimeMillis()
                Timber.v("$TAG: Pong received")
            }

            on(Events.ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Timber.e("$TAG: Server error: $error")
            }
        }
    }
    
    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        val attempts = reconnectAttempts.incrementAndGet()
        
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            Timber.w("$TAG: Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached")
            _connectionState.value = ConnectionState.FAILED
            return
        }
        
        // Calculate delay with exponential backoff
        val delay = min(
            INITIAL_RECONNECT_DELAY_MS * RECONNECT_MULTIPLIER.pow((attempts - 1).toDouble()),
            MAX_RECONNECT_DELAY_MS.toDouble()
        ).toLong()
        
        Timber.d("$TAG: Scheduling reconnect attempt $attempts in ${delay}ms")
        
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(delay)
            
            if (_connectionState.value == ConnectionState.RECONNECTING) {
                Timber.d("$TAG: Attempting reconnect ($attempts/$MAX_RECONNECT_ATTEMPTS)")
                
                // Disconnect existing socket
                socket?.disconnect()
                socket?.off()
                socket = null
                bumpSocketEpoch()
                
                // Attempt reconnection
                connect()
            }
        }
    }
    
    /**
     * Start heartbeat to detect connection issues
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        lastPongTime = System.currentTimeMillis()
        
        heartbeatJob = serviceScope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)
                
                // Send ping
                socket?.emit(Events.PING)
                Timber.v("$TAG: Ping sent")
                
                // Check if we received pong recently
                delay(HEARTBEAT_TIMEOUT_MS)
                
                val timeSincePong = System.currentTimeMillis() - lastPongTime
                if (timeSincePong > HEARTBEAT_INTERVAL_MS + HEARTBEAT_TIMEOUT_MS) {
                    Timber.w("$TAG: Heartbeat timeout - connection may be stale")
                    // Force reconnect
                    _connectionState.value = ConnectionState.RECONNECTING
                    socket?.disconnect()
                }
            }
        }
    }
    
    /**
     * Stop heartbeat
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        Timber.d("$TAG: Manual disconnect")
        
        _connectionState.value = ConnectionState.DISCONNECTED
        
        reconnectJob?.cancel()
        reconnectJob = null
        
        stopHeartbeat()
        
        currentBookingRoom = null
        reconnectAttempts.set(0)
        
        socket?.disconnect()
        socket?.off()
        socket = null
        bumpSocketEpoch()
    }
    
    /**
     * Force reconnect (for manual retry)
     */
    fun forceReconnect() {
        Timber.d("$TAG: Force reconnect requested")
        reconnectAttempts.set(0)
        _connectionState.value = ConnectionState.RECONNECTING
        
        socket?.disconnect()
        socket?.off()
        socket = null
        bumpSocketEpoch()
        
        connect()
    }

    private fun bumpSocketEpoch() {
        _socketEpoch.value = _socketEpoch.value + 1
    }

    /**
     * Join a booking room for real-time updates
     */
    fun joinBookingRoom(bookingId: String) {
        currentBookingRoom = bookingId
        
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Timber.w("$TAG: Cannot join room - Not connected (will join on reconnect)")
            // Try to connect
            connect()
            return
        }
        
        socket?.emit(Events.JOIN_BOOKING, bookingId)
        Timber.d("$TAG: Joined booking room: $bookingId")
    }

    /**
     * Leave a booking room
     */
    fun leaveBookingRoom(bookingId: String) {
        if (currentBookingRoom == bookingId) {
            currentBookingRoom = null
        }
        
        socket?.emit(Events.LEAVE_BOOKING, bookingId)
        Timber.d("$TAG: Left booking room: $bookingId")
    }

    /**
     * Resolve order id with alias fallback for backend compatibility.
     * Fallback chain: orderId -> bookingId -> broadcastId
     */
    private fun resolveOrderId(data: JSONObject): String {
        val orderId = data.optString("orderId", "").trim()
        if (orderId.isNotEmpty()) return orderId

        val bookingId = data.optString("bookingId", "").trim()
        if (bookingId.isNotEmpty()) return bookingId

        return data.optString("broadcastId", "").trim()
    }

    /**
     * Resolve trucksNeeded across payload variants.
     */
    private fun resolveTrucksNeeded(data: JSONObject): Int {
        return when {
            data.has("trucksNeeded") -> data.optInt("trucksNeeded", 0)
            data.has("totalTrucks") -> data.optInt("totalTrucks", 0)
            else -> data.optInt("trucksRemaining", 0) + data.optInt("trucksFilled", 0)
        }
    }

    /**
     * Reconnect-safe Socket.IO event Flow.
     *
     * IMPORTANT:
     * - When forceNew=true reconnect creates a NEW Socket instance.
     * - callbackFlow listeners attached to old socket won't receive new events.
     * - This helper tracks socket swaps and re-binds listener deterministically.
     */
    private fun <T> reconnectSafeEventFlow(
        eventName: String,
        parseErrorTag: String,
        parseEvent: (JSONObject) -> T?,
        onEvent: ((T) -> Unit)? = null
    ): Flow<T> = callbackFlow {
        val listener = Emitter.Listener { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@Listener
                val event = parseEvent(data) ?: return@Listener
                trySend(event)
                onEvent?.invoke(event)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error parsing $parseErrorTag")
            }
        }

        var attachedSocket: Socket? = null

        fun bindToLatestSocket() {
            val latestSocket = socket
            if (attachedSocket === latestSocket) return

            attachedSocket?.off(eventName, listener)
            attachedSocket = latestSocket
            attachedSocket?.on(eventName, listener)
        }

        bindToLatestSocket()

        val rebindJob = launch {
            combine(connectionState, _socketEpoch) { _, _ -> Unit }.collect {
                bindToLatestSocket()
            }
        }

        awaitClose {
            rebindJob.cancel()
            attachedSocket?.off(eventName, listener)
        }
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
                Timber.e(e, "$TAG: Error parsing booking update")
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
                Timber.e(e, "$TAG: Error parsing truck assignment")
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
                Timber.e(e, "$TAG: Error parsing location update")
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
                Timber.e(e, "$TAG: Error parsing assignment status")
            }
        }

        socket?.on(Events.ASSIGNMENT_STATUS_CHANGED, listener)

        awaitClose {
            socket?.off(Events.ASSIGNMENT_STATUS_CHANGED, listener)
        }
    }

    /**
     * Listen for booking_completed events.
     * Emitted when ALL trucks in a booking have completed delivery.
     * Used to auto-show the rating bottom sheet.
     */
    fun onBookingCompleted(): Flow<BookingCompletedEvent> = reconnectSafeEventFlow(
        eventName = Events.BOOKING_COMPLETED,
        parseErrorTag = "booking_completed",
        parseEvent = { data ->
            val bookingId = resolveOrderId(data)
            if (bookingId.isEmpty()) null else BookingCompletedEvent(bookingId = bookingId)
        },
        onEvent = { event ->
            Timber.i("$TAG: booking_completed received for ${event.bookingId}")
        }
    )

    /**
     * Listen for order_expired events.
     * Backend emits this when 120s timer fires and no transporter responded.
     * Customer app shows TimeoutBottomSheet with Retry + Cancel buttons.
     */
    fun onOrderExpired(): Flow<OrderExpiredEvent> = reconnectSafeEventFlow(
        eventName = Events.ORDER_EXPIRED,
        parseErrorTag = "order_expired",
        parseEvent = { data ->
            val resolvedOrderId = resolveOrderId(data)
            if (resolvedOrderId.isEmpty()) {
                null
            } else {
                OrderExpiredEvent(
                    orderId = resolvedOrderId,
                    status = data.optString("status", "expired"),
                    expiredAt = data.optString("expiredAt", ""),
                    totalTrucks = data.optInt("totalTrucks", 0),
                    trucksFilled = data.optInt("trucksFilled", 0)
                )
            }
        },
        onEvent = { event ->
            Timber.i("$TAG: order_expired received for ${event.orderId}")
        }
    )

    /**
     * Listen for order_cancelled events.
     * Backend emits this when customer's own cancel is confirmed server-side.
     * Customer app shows CancelSuccessSheet with Search Again + Go Back buttons.
     */
    fun onOrderCancelled(): Flow<OrderCancelledEvent> = reconnectSafeEventFlow(
        eventName = Events.ORDER_CANCELLED,
        parseErrorTag = "order_cancelled",
        parseEvent = { data ->
            val resolvedOrderId = resolveOrderId(data)
            if (resolvedOrderId.isEmpty()) {
                null
            } else {
                OrderCancelledEvent(
                    orderId = resolvedOrderId,
                    status = data.optString("status", "cancelled"),
                    reason = data.optString("reason", ""),
                    cancelledAt = data.optString("cancelledAt", "")
                )
            }
        },
        onEvent = { event ->
            Timber.i("$TAG: order_cancelled received for ${event.orderId}")
        }
    )

    /**
     * Listen for broadcast_state_changed events.
     * Backend emits as order moves through created → broadcasting → active lifecycle.
     */
    fun onBroadcastStateChanged(): Flow<BroadcastStateChangedEvent> = reconnectSafeEventFlow(
        eventName = Events.BROADCAST_STATE_CHANGED,
        parseErrorTag = "broadcast_state_changed",
        parseEvent = { data ->
            val resolvedOrderId = resolveOrderId(data)
            if (resolvedOrderId.isEmpty()) {
                null
            } else {
                BroadcastStateChangedEvent(
                    orderId = resolvedOrderId,
                    status = data.optString("status", "")
                )
            }
        }
    )

    /**
     * Listen for trucks_remaining_update events.
     * Backend emits as each truck gets assigned — customer sees live progress bar.
     */
    fun onTrucksRemainingUpdate(): Flow<TrucksRemainingUpdateEvent> = reconnectSafeEventFlow(
        eventName = Events.TRUCKS_REMAINING_UPDATE,
        parseErrorTag = "trucks_remaining_update",
        parseEvent = { data ->
            val resolvedOrderId = resolveOrderId(data)
            if (resolvedOrderId.isEmpty()) {
                null
            } else {
                TrucksRemainingUpdateEvent(
                    orderId = resolvedOrderId,
                    trucksNeeded = resolveTrucksNeeded(data),
                    trucksFilled = data.optInt("trucksFilled", 0)
                )
            }
        }
    )

    /**
     * Listen for booking_fully_filled events.
     * Backend emits when ALL trucks in order have been assigned.
     * Customer app navigates to BookingTrackingActivity.
     */
    fun onBookingFullyFilled(): Flow<BookingFullyFilledEvent> = reconnectSafeEventFlow(
        eventName = Events.BOOKING_FULLY_FILLED,
        parseErrorTag = "booking_fully_filled",
        parseEvent = { data ->
            val resolvedOrderId = resolveOrderId(data)
            if (resolvedOrderId.isEmpty()) {
                null
            } else {
                BookingFullyFilledEvent(orderId = resolvedOrderId)
            }
        },
        onEvent = { event ->
            Timber.i("$TAG: booking_fully_filled received for ${event.orderId}")
        }
    )

    /**
     * Get current connection state
     */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
    
    /**
     * Check if connection has failed (max retries exceeded)
     */
    fun hasFailed(): Boolean = _connectionState.value == ConnectionState.FAILED
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        serviceScope.cancel()
    }
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

data class BookingCompletedEvent(
    val bookingId: String
)

// PRD 4.2 — Order lifecycle events
data class OrderExpiredEvent(
    val orderId: String,
    val status: String,       // "expired" or "partially_filled"
    val expiredAt: String,
    val totalTrucks: Int,
    val trucksFilled: Int
)

data class OrderCancelledEvent(
    val orderId: String,
    val status: String,       // "cancelled"
    val reason: String,
    val cancelledAt: String
)

data class BroadcastStateChangedEvent(
    val orderId: String,
    val status: String        // "created" | "broadcasting" | "active"
)

data class TrucksRemainingUpdateEvent(
    val orderId: String,
    val trucksNeeded: Int,
    val trucksFilled: Int
)

data class BookingFullyFilledEvent(
    val orderId: String
)
