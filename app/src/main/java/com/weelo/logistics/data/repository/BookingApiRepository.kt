package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.*
import com.weelo.logistics.domain.model.BookingModel
import com.weelo.logistics.domain.model.BookingStatus
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.model.PricingModel
import com.weelo.logistics.domain.model.VehicleModel
import com.weelo.logistics.domain.model.VehicleCategory
import com.weelo.logistics.domain.repository.BookingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Booking Repository Implementation with real API
 * 
 * Handles booking operations:
 * - Create bookings (broadcasts to transporters)
 * - Get booking status
 * - Track assigned trucks
 * - Cancel bookings
 */
class BookingApiRepository @Inject constructor(
    private val apiService: WeeloApiService,
    private val tokenManager: TokenManager
) : BookingRepository {

    /**
     * Get valid auth token, refreshing if necessary
     * 
     * This method checks if the token needs refresh and attempts to refresh it
     * before returning. If refresh fails or no token exists, throws an exception.
     */
    private suspend fun getAuthToken(): String {
        // First check if we have a valid (non-expired) token
        val currentToken = tokenManager.getAccessToken()
        
        // If we have a valid token, use it
        if (!currentToken.isNullOrBlank()) {
            return "Bearer $currentToken"
        }
        
        // Token is null/expired - try to refresh using refresh token
        val refreshToken = tokenManager.getRefreshToken()
        if (!refreshToken.isNullOrBlank()) {
            try {
                Timber.d("Token expired/missing, attempting refresh...")
                val response = apiService.refreshToken(
                    RefreshTokenRequest(refreshToken = refreshToken)
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        // Update tokens
                        tokenManager.saveTokens(
                            accessToken = data.accessToken,
                            refreshToken = refreshToken,
                            expiresInSeconds = data.expiresIn
                        )
                        Timber.d("Token refreshed successfully")
                        return "Bearer ${data.accessToken}"
                    }
                }
                
                // Refresh failed - clear tokens and require re-login
                Timber.w("Token refresh failed, clearing tokens")
                tokenManager.clearTokens()
            } catch (e: Exception) {
                Timber.e(e, "Token refresh error")
                tokenManager.clearTokens()
            }
        }
        
        // No valid token available - user needs to login again
        throw WeeloException.AuthException("Session expired. Please login again.")
    }
    
    /**
     * MODULARITY: Parse error messages from different response formats
     * EASY UNDERSTANDING: Extracts user-friendly error messages
     * SCALABILITY: Handles multiple backend error formats
     * 
     * Handles errors like:
     * - {"success": false, "error": {"code": "ACTIVE_ORDER_EXISTS", "message": "..."}}
     * - {"success": false, "message": "..."}
     * - Raw error body strings
     */
    private fun <T> parseErrorMessage(
        response: retrofit2.Response<T>,
        fallback: String = "Failed to create order. Please try again."
    ): String {
        try {
            // Try to parse error body JSON
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                try {
                    // Parse JSON manually to extract error message
                    val jsonObject = org.json.JSONObject(errorBody)
                    
                    // Check for error.message pattern (ACTIVE_ORDER_EXISTS format)
                    if (jsonObject.has("error")) {
                        val errorObj = jsonObject.getJSONObject("error")
                        if (errorObj.has("message")) {
                            return errorObj.getString("message")
                        }
                    }
                    
                    // Check for direct message field
                    if (jsonObject.has("message")) {
                        return jsonObject.getString("message")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse error JSON")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing error message")
        }
        
        return "Failed to create order. Please try again."
    }

    /**
     * Legacy sync version for backward compatibility
     * Note: This should be avoided - use suspend version instead
     */
    private fun getAuthTokenSync(): String {
        val token = tokenManager.getAccessToken()
        if (token.isNullOrBlank()) {
            Timber.w("No valid token available (sync call)")
            return "Bearer "
        }
        return "Bearer $token"
    }

    override suspend fun createBooking(booking: BookingModel): Result<BookingModel> {
        return try {
            val request = CreateBookingRequest(
                pickup = LocationRequest(
                    coordinates = CoordinatesRequest(
                        latitude = booking.fromLocation.latitude,
                        longitude = booking.fromLocation.longitude
                    ),
                    address = booking.fromLocation.address,
                    city = booking.fromLocation.city,
                    state = booking.fromLocation.state,
                    pincode = booking.fromLocation.pincode
                ),
                drop = LocationRequest(
                    coordinates = CoordinatesRequest(
                        latitude = booking.toLocation.latitude,
                        longitude = booking.toLocation.longitude
                    ),
                    address = booking.toLocation.address,
                    city = booking.toLocation.city,
                    state = booking.toLocation.state,
                    pincode = booking.toLocation.pincode
                ),
                vehicleType = booking.vehicle.category.apiValue,
                vehicleSubtype = booking.vehicle.name,
                trucksNeeded = booking.trucksNeeded,
                distanceKm = booking.distanceKm,
                pricePerTruck = booking.pricing.totalAmount
            )

            // SCALABILITY: Generate idempotency key for duplicate prevention
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            
            val response = apiService.createBooking(
                authorization = getAuthToken(),
                request = request,
                idempotencyKey = idempotencyKey
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data?.booking
                if (data != null) {
                    val createdBooking = mapToBookingModel(data, booking.vehicle)
                    Timber.d("Booking created: ${createdBooking.id}")
                    Result.Success(createdBooking)
                } else {
                    Result.Error(WeeloException.BookingException("Invalid response"))
                }
            } else {
                val error = response.body()?.error
                Timber.w("Booking creation failed: ${error?.code}")
                Result.Error(WeeloException.BookingException(error?.message ?: "Failed to create booking"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Create booking error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }

    override suspend fun getBookingById(bookingId: String): Result<BookingModel> {
        return try {
            val response = apiService.getBookingById(getAuthToken(), bookingId)

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data?.booking
                if (data != null) {
                    Result.Success(mapToBookingModel(data))
                } else {
                    Result.Error(WeeloException.BookingException("Booking not found"))
                }
            } else {
                val error = response.body()?.error
                Result.Error(WeeloException.BookingException(error?.message ?: "Failed to load booking"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Get booking error")
            Result.Error(WeeloException.NetworkException("Network error"))
        }
    }

    override fun getUserBookings(): Flow<Result<List<BookingModel>>> = flow {
        try {
            val token = getAuthToken()
            val response = apiService.getMyBookings(token)

            if (response.isSuccessful && response.body()?.success == true) {
                val bookings = response.body()?.data?.bookings?.map { mapToBookingModel(it) } ?: emptyList()
                emit(Result.Success(bookings))
            } else {
                val error = response.body()?.error
                emit(Result.Error(WeeloException.BookingException(error?.message ?: "Failed to load bookings")))
            }
        } catch (e: WeeloException.AuthException) {
            Timber.w("Auth error in getUserBookings: ${e.message}")
            emit(Result.Error(e))
        } catch (e: Exception) {
            Timber.e(e, "Get bookings error")
            emit(Result.Error(WeeloException.NetworkException("Network error")))
        }
    }

    override suspend fun cancelBooking(bookingId: String): Result<Unit> {
        return try {
            val response = apiService.cancelBooking(getAuthToken(), bookingId)

            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Booking cancelled: $bookingId")
                Result.Success(Unit)
            } else {
                val error = response.body()?.error
                Result.Error(WeeloException.BookingException(error?.message ?: "Failed to cancel booking"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Cancel booking error")
            Result.Error(WeeloException.NetworkException("Network error"))
        }
    }

    /**
     * Create booking with simple parameters (used by BookingConfirmationActivity)
     * Returns booking ID on success
     * 
     * SCALABILITY:
     * - Idempotency key prevents duplicate bookings
     * - Supports millions of concurrent requests
     * 
     * EASY UNDERSTANDING:
     * - Idempotency key = unique ID per booking attempt
     * - Backend rejects duplicate keys
     * 
     * CODING STANDARDS:
     * - Clear parameter naming
     * - Proper error handling
     */
    suspend fun createBookingSimple(
        pickup: LocationRequest,
        drop: LocationRequest,
        vehicleType: String,
        vehicleSubtype: String,
        trucksNeeded: Int,
        distanceKm: Int,
        pricePerTruck: Int,
        idempotencyKey: String
    ): Result<String> {
        return try {
            val request = CreateBookingRequest(
                pickup = pickup,
                drop = drop,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                trucksNeeded = trucksNeeded,
                distanceKm = distanceKm,
                pricePerTruck = pricePerTruck
            )

            // SCALABILITY: Pass idempotency key to prevent duplicate bookings
            val response = apiService.createBooking(
                authorization = getAuthToken(),
                request = request,
                idempotencyKey = idempotencyKey
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val bookingId = response.body()?.data?.booking?.id
                if (bookingId != null) {
                    Timber.d("Booking created: $bookingId (idempotency: $idempotencyKey)")
                    Result.Success(bookingId)
                } else {
                    Result.Error(WeeloException.BookingException("Invalid response - no booking ID"))
                }
            } else {
                val error = response.body()?.error
                Timber.w("Booking creation failed: ${error?.code}")
                Result.Error(WeeloException.BookingException(error?.message ?: "Failed to create booking"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Create booking error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }

    /**
     * Get assigned trucks for a booking
     */
    suspend fun getAssignedTrucks(bookingId: String): Result<List<AssignedTruckData>> {
        return try {
            val response = apiService.getAssignedTrucks(getAuthToken(), bookingId)

            if (response.isSuccessful && response.body()?.success == true) {
                val trucks = response.body()?.data?.trucks ?: emptyList()
                Result.Success(trucks)
            } else {
                val error = response.body()?.error
                Result.Error(WeeloException.BookingException(error?.message ?: "Failed to load trucks"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Get assigned trucks error")
            Result.Error(WeeloException.NetworkException("Network error"))
        }
    }

    override suspend fun calculateDistance(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Result<Int> {
        // Use local calculation for now
        // Could integrate with Google Maps API via backend
        return try {
            val distance = calculateHaversineDistance(fromLat, fromLng, toLat, toLng)
            Result.Success(distance)
        } catch (e: Exception) {
            Result.Error(WeeloException.BookingException("Failed to calculate distance"))
        }
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private fun mapToBookingModel(data: BookingData, vehicle: VehicleModel? = null): BookingModel {
        return BookingModel(
            id = data.id,
            fromLocation = LocationModel(
                latitude = data.pickup.coordinates.latitude,
                longitude = data.pickup.coordinates.longitude,
                address = data.pickup.address
            ),
            toLocation = LocationModel(
                latitude = data.drop.coordinates.latitude,
                longitude = data.drop.coordinates.longitude,
                address = data.drop.address
            ),
            vehicle = vehicle ?: VehicleModel(
                id = "",
                name = data.vehicleSubtype,
                category = VehicleCategory.fromId(data.vehicleType),
                capacityTons = "",
                basePrice = 0,
                pricePerKm = 0,
                priceMultiplier = 1.0
            ),
            distanceKm = data.distanceKm,
            pricing = PricingModel(
                baseFare = 0,
                distanceCharge = 0,
                gstAmount = 0,
                totalAmount = data.pricePerTruck,
                distanceKm = data.distanceKm,
                pricePerKm = 0
            ),
            status = mapStatus(data.status),
            createdAt = parseTimestamp(data.createdAt),
            trucksNeeded = data.trucksNeeded,
            trucksFilled = data.trucksFilled,
            broadcastId = data.id
        )
    }

    // ============================================================
    // NEW: ORDER SYSTEM (Multi-Truck Requests)
    // ============================================================

    /**
     * Create order with multiple truck types
     * 
     * Each truck type creates separate TruckRequests that are
     * broadcast only to transporters who have matching vehicles.
     * 
     * ROUTE POINTS (Intermediate Stops):
     * - If intermediateStops provided, builds full routePoints array
     * - Route: PICKUP → STOP1 → STOP2 → DROP
     * - Max 2 intermediate stops allowed
     * - Route is IMMUTABLE after order creation
     */
    suspend fun createOrder(
        pickup: LocationModel,
        drop: LocationModel,
        distanceKm: Int,
        trucks: List<TruckSelection>,
        goodsType: String? = null,
        @Suppress("UNUSED_PARAMETER") weight: String? = null, // Reserved for future use
        @Suppress("UNUSED_PARAMETER") notes: String? = null, // Reserved for future use
        intermediateStops: List<LocationModel> = emptyList()  // NEW: Intermediate stops
    ): Result<OrderResult> {
        return try {
            // Build routePoints array if we have intermediate stops
            val routePoints: List<RoutePointRequest>? = if (intermediateStops.isNotEmpty()) {
                mutableListOf<RoutePointRequest>().apply {
                    // First: Pickup
                    add(RoutePointRequest(
                        type = "PICKUP",
                        latitude = pickup.latitude,
                        longitude = pickup.longitude,
                        address = pickup.address,
                        city = pickup.city.ifBlank { null },
                        state = pickup.state.ifBlank { null }
                    ))
                    
                    // Middle: Intermediate stops
                    intermediateStops.forEach { stop ->
                        add(RoutePointRequest(
                            type = "STOP",
                            latitude = stop.latitude,
                            longitude = stop.longitude,
                            address = stop.address,
                            city = stop.city.ifBlank { null },
                            state = stop.state.ifBlank { null }
                        ))
                    }
                    
                    // Last: Drop
                    add(RoutePointRequest(
                        type = "DROP",
                        latitude = drop.latitude,
                        longitude = drop.longitude,
                        address = drop.address,
                        city = drop.city.ifBlank { null },
                        state = drop.state.ifBlank { null }
                    ))
                }
            } else null
            
            val request = CreateOrderRequest(
                // NEW: Route points with intermediate stops
                routePoints = routePoints,
                
                // Legacy pickup/drop (for backward compatibility)
                pickup = OrderLocationRequest(
                    latitude = pickup.latitude,
                    longitude = pickup.longitude,
                    address = pickup.address,
                    city = pickup.city.ifBlank { null },
                    state = pickup.state.ifBlank { null }
                ),
                drop = OrderLocationRequest(
                    latitude = drop.latitude,
                    longitude = drop.longitude,
                    address = drop.address,
                    city = drop.city.ifBlank { null },
                    state = drop.state.ifBlank { null }
                ),
                distanceKm = distanceKm,
                vehicleRequirements = trucks.map { truck ->
                    VehicleRequirementRequest(
                        vehicleType = truck.vehicleType,
                        vehicleSubtype = truck.vehicleSubtype,
                        quantity = truck.quantity,
                        pricePerTruck = truck.pricePerTruck
                    )
                },
                goodsType = goodsType
            )

            Timber.d("Creating order with ${trucks.sumOf { it.quantity }} trucks, ${intermediateStops.size} stops")
            
            val response = apiService.createOrder(getAuthToken(), request)
            
            val responseBody = response.body()
            val data = responseBody?.data
            if (response.isSuccessful && responseBody?.success == true && data != null) {
                Timber.i("Order created: ${data.order.id}")
                Timber.i("Total requests: ${data.broadcastSummary.totalRequests}")
                Timber.i("Transporters notified: ${data.broadcastSummary.totalTransportersNotified}")
                
                Result.Success(
                    OrderResult(
                        orderId = data.order.id,
                        totalTrucks = data.order.totalTrucks,
                        totalAmount = data.order.totalAmount,
                        status = data.order.status,
                        expiresIn = data.order.expiresIn,  // NEW: Get TTL from backend
                        truckRequests = data.truckRequests.map { req ->
                            TruckRequestResult(
                                id = req.id,
                                requestNumber = req.requestNumber,
                                vehicleType = req.vehicleType,
                                vehicleSubtype = req.vehicleSubtype,
                                pricePerTruck = req.pricePerTruck,
                                status = req.status
                            )
                        },
                        broadcastSummary = BroadcastSummaryResult(
                            totalRequests = data.broadcastSummary.totalRequests,
                            totalTransportersNotified = data.broadcastSummary.totalTransportersNotified,
                            groupedBy = data.broadcastSummary.groupedBy.map { group ->
                                BroadcastGroupResult(
                                    vehicleType = group.vehicleType,
                                    vehicleSubtype = group.vehicleSubtype,
                                    count = group.count,
                                    transportersNotified = group.transportersNotified
                                )
                            }
                        ),
                        timeoutSeconds = data.timeoutSeconds,
                        expiresAt = data.order.expiresAt
                    )
                )
            } else {
                // EASY UNDERSTANDING: Parse error properly to show user-friendly messages
                // SCALABILITY: Handles multiple error formats from backend
                val errorMsg = parseErrorMessage(response)
                Timber.e("Order creation failed: $errorMsg")
                Result.Error(WeeloException.BookingException(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Order creation exception")
            Result.Error(e, e.message ?: "Network error")
        }
    }

    /**
     * Get order details with all truck requests
     */
    suspend fun getOrderDetails(orderId: String): Result<OrderDetailResult> {
        return try {
            val response = apiService.getOrderById(getAuthToken(), orderId)
            
            val responseBody = response.body()
            val data = responseBody?.data
            if (response.isSuccessful && responseBody?.success == true && data != null) {
                Result.Success(
                    OrderDetailResult(
                        orderId = data.order.id,
                        status = data.order.status,
                        totalTrucks = data.summary.totalTrucks,
                        trucksFilled = data.summary.trucksFilled,
                        trucksSearching = data.summary.trucksSearching,
                        trucksExpired = data.summary.trucksExpired,
                        requests = data.requests.map { req ->
                            TruckRequestDetailResult(
                                id = req.id,
                                requestNumber = req.requestNumber,
                                vehicleType = req.vehicleType,
                                vehicleSubtype = req.vehicleSubtype,
                                pricePerTruck = req.pricePerTruck,
                                status = req.status,
                                assignedVehicleNumber = req.assignedVehicleNumber,
                                assignedDriverName = req.assignedDriverName,
                                assignedDriverPhone = req.assignedDriverPhone,
                                tripId = req.tripId
                            )
                        }
                    )
                )
            } else {
                val errorMsg = response.body()?.error?.message ?: "Failed to get order details"
                Result.Error(WeeloException.NetworkException(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Get order details exception")
            Result.Error(e, e.message ?: "Network error")
        }
    }

    // ============================================================
    // Helper methods
    // ============================================================

    private fun mapStatus(status: String): BookingStatus {
        return when (status.lowercase()) {
            "active" -> BookingStatus.PENDING
            "searching" -> BookingStatus.PENDING
            "partially_filled" -> BookingStatus.PENDING
            "fully_filled" -> BookingStatus.CONFIRMED
            "in_progress" -> BookingStatus.IN_PROGRESS
            "completed" -> BookingStatus.COMPLETED
            "cancelled" -> BookingStatus.CANCELLED
            "expired" -> BookingStatus.CANCELLED
            else -> BookingStatus.PENDING
        }
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            // Use SimpleDateFormat for API 24+ compatibility instead of java.time.Instant (API 26+)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Int {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * 
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (earthRadius * c).toInt()
    }
    
    // ============================================================
    // NEW: Cancel and Status Methods
    // ============================================================
    
    /**
     * Cancel active order with optional reason
     * 
     * SCALABILITY: Backend deletes from Redis + DB, notifies transporters + drivers
     * EASY UNDERSTANDING: Customer can cancel search before driver accepts
     * MODULARITY: Returns number of transporters/drivers notified + assignments released
     * 
     * @param orderId The order to cancel
     * @param reason Optional cancellation reason (from CancellationBottomSheet)
     */
    suspend fun cancelOrder(orderId: String, reason: String? = null): Result<CancelOrderData> {
        return try {
            val request = CancelOrderRequest(reason = reason)
            val response = apiService.cancelOrder(getAuthToken(), orderId, request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Timber.d("Order cancelled: $orderId, ${data.transportersNotified} transporters, ${data.driversNotified} drivers notified")
                    Result.Success(data)
                } else {
                    // Backend returned success but no data — still treat as success
                    Result.Success(CancelOrderData(orderId = orderId, status = "cancelled"))
                }
            } else {
                val errorMsg = parseErrorMessage(response, fallback = "Failed to cancel order. Please try again.")
                Timber.w("Cancel order failed: $errorMsg")
                Result.Error(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Cancel order error")
            Result.Error(Exception("Network error. Please try again."))
        }
    }
    
    /**
     * Get order status and remaining time
     * 
     * SCALABILITY: Used when app resumes to check if order still active
     * EASY UNDERSTANDING: Backend returns exact remaining seconds
     * MODULARITY: Backend is source of truth for timer
     */
    suspend fun getOrderStatus(orderId: String): Result<OrderStatusData> {
        return try {
            val response = apiService.getOrderStatus(getAuthToken(), orderId)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Timber.d("Order status: ${data.orderId}, remaining: ${data.remainingSeconds}s, active: ${data.isActive}")
                    Result.Success(data)
                } else {
                    Result.Error(Exception("Invalid response"))
                }
            } else {
                val errorMsg = parseErrorMessage(response)
                Timber.w("Get order status failed: $errorMsg")
                Result.Error(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Get order status error")
            Result.Error(Exception("Network error. Please try again."))
        }
    }
}

// ============================================================
// ORDER DATA CLASSES
// ============================================================

/**
 * Input for truck selection in an order
 */
data class TruckSelection(
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    val pricePerTruck: Int
)

/**
 * Result of creating an order
 */
data class OrderResult(
    val orderId: String,
    val totalTrucks: Int,
    val totalAmount: Int,
    val status: String,
    val truckRequests: List<TruckRequestResult>,
    val broadcastSummary: BroadcastSummaryResult,
    val timeoutSeconds: Int,
    val expiresIn: Int? = null,  // NEW: Duration in seconds from backend (for timer)
    val expiresAt: String
)

data class TruckRequestResult(
    val id: String,
    val requestNumber: Int,
    val vehicleType: String,
    val vehicleSubtype: String,
    val pricePerTruck: Int,
    val status: String
)

data class BroadcastSummaryResult(
    val totalRequests: Int,
    val totalTransportersNotified: Int,
    val groupedBy: List<BroadcastGroupResult>
)

data class BroadcastGroupResult(
    val vehicleType: String,
    val vehicleSubtype: String,
    val count: Int,
    val transportersNotified: Int
)

/**
 * Result of getting order details
 */
data class OrderDetailResult(
    val orderId: String,
    val status: String,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksSearching: Int,
    val trucksExpired: Int,
    val requests: List<TruckRequestDetailResult>
)

data class TruckRequestDetailResult(
    val id: String,
    val requestNumber: Int,
    val vehicleType: String,
    val vehicleSubtype: String,
    val pricePerTruck: Int,
    val status: String,
    val assignedVehicleNumber: String?,
    val assignedDriverName: String?,
    val assignedDriverPhone: String?,
    val tripId: String?
)
