package com.weelo.logistics.data.remote.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Weelo Customer API Service
 * 
 * Central API interface for customer app operations.
 * All endpoints require authentication except where noted.
 * 
 * SECURITY: 
 * - Tokens passed via header, never logged
 * - All responses validated via schemas
 * - Rate limiting applied server-side
 */
interface WeeloApiService {

    // ============================================================
    // AUTHENTICATION
    // ============================================================

    /**
     * Send OTP to phone number
     * PUBLIC endpoint - no auth required
     */
    @POST("auth/send-otp")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): Response<SendOtpResponse>

    /**
     * Verify OTP and get tokens
     * PUBLIC endpoint - no auth required
     */
    @POST("auth/verify-otp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<VerifyOtpResponse>

    /**
     * Refresh access token
     */
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<RefreshTokenResponse>

    /**
     * Logout - invalidate refresh token
     */
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<LogoutResponse>

    // ============================================================
    // BOOKINGS
    // ============================================================

    /**
     * Create new booking (broadcasts to transporters)
     * 
     * SCALABILITY:
     * - Idempotency key prevents duplicate bookings
     * - Backend rejects duplicate keys within 24 hours
     * 
     * EASY UNDERSTANDING:
     * - Pass unique UUID as X-Idempotency-Key header
     * - Same key = same booking (no duplicate)
     */
    @POST("bookings")
    suspend fun createBooking(
        @Header("Authorization") authorization: String,
        @Body request: CreateBookingRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String
    ): Response<CreateBookingResponse>

    /**
     * Get customer's bookings with pagination
     */
    @GET("bookings")
    suspend fun getMyBookings(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("status") status: String? = null
    ): Response<BookingsListResponse>

    /**
     * Get single booking details
     */
    @GET("bookings/{bookingId}")
    suspend fun getBookingById(
        @Header("Authorization") token: String,
        @Path("bookingId") bookingId: String
    ): Response<BookingDetailResponse>

    /**
     * Get trucks assigned to a booking
     */
    @GET("bookings/{bookingId}/trucks")
    suspend fun getAssignedTrucks(
        @Header("Authorization") token: String,
        @Path("bookingId") bookingId: String
    ): Response<AssignedTrucksResponse>

    /**
     * Cancel booking
     */
    @PATCH("bookings/{bookingId}/cancel")
    suspend fun cancelBooking(
        @Header("Authorization") token: String,
        @Path("bookingId") bookingId: String
    ): Response<CancelBookingResponse>

    // ============================================================
    // ORDERS (NEW - Multi-Truck System)
    // ============================================================

    /**
     * Create new order with multiple truck types
     * 
     * Each truck type creates separate TruckRequests that are
     * broadcast to transporters who have matching vehicles.
     * 
     * Example: 2x Open 17ft + 3x Container 4ton = 5 TruckRequests
     * - 2 requests go to transporters with Open trucks
     * - 3 requests go to transporters with Container trucks
     */
    @POST("orders")
    suspend fun createOrder(
        @Header("Authorization") token: String,
        @Body request: CreateOrderRequest
    ): Response<CreateOrderResponse>

    /**
     * Get customer's orders with pagination
     */
    @GET("orders")
    suspend fun getMyOrders(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<OrdersListResponse>

    /**
     * Get order details with all truck requests
     */
    @GET("orders/{orderId}")
    suspend fun getOrderById(
        @Header("Authorization") token: String,
        @Path("orderId") orderId: String
    ): Response<OrderDetailResponse>
    
    /**
     * Cancel active order
     * 
     * SCALABILITY: Backend deletes from Redis + DB, notifies transporters
     * EASY UNDERSTANDING: Customer can cancel search before driver accepts
     * MODULARITY: Works with existing cancelOrder service
     */
    @DELETE("orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Header("Authorization") token: String,
        @Path("orderId") orderId: String
    ): Response<CancelOrderResponse>
    
    /**
     * Get order status and remaining time
     * 
     * SCALABILITY: Used when app resumes to check if order still active
     * EASY UNDERSTANDING: Backend returns exact remaining seconds
     * MODULARITY: Backend is source of truth for timer
     */
    @GET("orders/{orderId}/status")
    suspend fun getOrderStatus(
        @Header("Authorization") token: String,
        @Path("orderId") orderId: String
    ): Response<OrderStatusResponse>

    // ============================================================
    // CUSTOM BOOKING (Long-term contracts - weeks/months)
    // ============================================================

    /**
     * Submit custom booking request
     * 
     * SCALABILITY:
     * - Idempotency key prevents duplicate submissions
     * - Rate limited: 10 req/min per user
     * - Admin notified asynchronously via queue
     * 
     * EASY UNDERSTANDING:
     * - Pass unique UUID as X-Idempotency-Key header
     * - Same key = same request (no duplicate)
     */
    @POST("custom-booking")
    suspend fun submitCustomBooking(
        @Header("Authorization") token: String,
        @Body request: CustomBookingRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String
    ): Response<CustomBookingResponse>

    /**
     * Get customer's custom booking requests with pagination
     */
    @GET("custom-booking")
    suspend fun getMyCustomBookings(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): Response<CustomBookingsListResponse>

    /**
     * Get single custom booking request details
     */
    @GET("custom-booking/{requestId}")
    suspend fun getCustomBookingById(
        @Header("Authorization") token: String,
        @Path("requestId") requestId: String
    ): Response<CustomBookingDetailResponse>

    /**
     * Cancel a pending custom booking request
     */
    @POST("custom-booking/{requestId}/cancel")
    suspend fun cancelCustomBooking(
        @Header("Authorization") token: String,
        @Path("requestId") requestId: String
    ): Response<CustomBookingCancelResponse>

    // ============================================================
    // TRACKING
    // ============================================================

    /**
     * Get live tracking for a specific truck/trip
     */
    @GET("tracking/{tripId}")
    suspend fun getTripTracking(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): Response<TripTrackingResponse>

    /**
     * Get all truck locations for a booking (multi-truck view)
     */
    @GET("tracking/booking/{bookingId}")
    suspend fun getBookingTracking(
        @Header("Authorization") token: String,
        @Path("bookingId") bookingId: String
    ): Response<BookingTrackingResponse>

    // ============================================================
    // VEHICLES (Public)
    // ============================================================

    /**
     * Get vehicle types and subtypes
     * PUBLIC endpoint
     */
    @GET("vehicles/types")
    suspend fun getVehicleTypes(): Response<VehicleTypesResponse>

    /**
     * Calculate pricing for a trip
     * PUBLIC endpoint
     */
    @GET("vehicles/pricing")
    suspend fun calculatePricing(
        @Query("vehicleType") vehicleType: String,
        @Query("distanceKm") distanceKm: Int,
        @Query("trucksNeeded") trucksNeeded: Int
    ): Response<PricingResponse>

    // ============================================================
    // PROFILE MANAGEMENT
    // ============================================================

    /**
     * Get current user's profile
     */
    @GET("profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    /**
     * Update customer profile
     * Backend route: PUT /api/v1/profile/customer
     */
    @PUT("profile/customer")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ProfileResponse>

    /**
     * Check if profile is complete (for new users)
     */
    @GET("profile/status")
    suspend fun getProfileStatus(
        @Header("Authorization") token: String
    ): Response<ProfileStatusResponse>

    // ============================================================
    // GEOCODING (AWS Location Service via Backend)
    // ============================================================

    /**
     * Search for places by text query
     * Uses AWS Location Service Place Index
     * @see POST /api/v1/geocoding/search
     */
    @POST("geocoding/search")
    suspend fun searchPlaces(
        @Body request: PlaceSearchRequest
    ): Response<PlaceSearchResponse>

    /**
     * Reverse geocode: coordinates to address
     * Uses AWS Location Service
     * @see POST /api/v1/geocoding/reverse
     */
    @POST("geocoding/reverse")
    suspend fun reverseGeocode(
        @Body request: ReverseGeocodeRequest
    ): Response<ReverseGeocodeResponse>

    /**
     * Calculate route between two points
     * Uses AWS Location Service Route Calculator
     * Falls back to Haversine for routes > 400km
     * @see POST /api/v1/geocoding/route
     */
    @POST("geocoding/route")
    suspend fun calculateRoute(
        @Body request: RouteCalculationRequest
    ): Response<RouteCalculationResponse>

    /**
     * Calculate multi-waypoint route (pickup → stops → drop)
     * Returns road-following polyline for map display
     * @see POST /api/v1/geocoding/route-multi
     */
    @POST("geocoding/route-multi")
    suspend fun calculateRouteMulti(
        @Body request: MultiPointRouteRequest
    ): Response<MultiPointRouteResponse>

    /**
     * Check AWS Location Service status
     * @see GET /api/v1/geocoding/status
     */
    @GET("geocoding/status")
    suspend fun getGeocodingStatus(): Response<GeocodingStatusResponse>
}

// ============================================================
// REQUEST DATA CLASSES
// ============================================================

data class SendOtpRequest(
    val phone: String,
    val role: String = "customer"
)

data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val role: String = "customer"
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class CreateBookingRequest(
    val pickup: LocationRequest,
    val drop: LocationRequest,
    val vehicleType: String,
    val vehicleSubtype: String,
    val trucksNeeded: Int,
    val distanceKm: Int,
    val pricePerTruck: Int,
    val notes: String? = null,
    val scheduledAt: String? = null
)

data class LocationRequest(
    val coordinates: CoordinatesRequest,
    val address: String,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null
)

data class CoordinatesRequest(
    val latitude: Double,
    val longitude: Double
)

// ============================================================
// RESPONSE DATA CLASSES
// ============================================================

data class SendOtpResponse(
    val success: Boolean,
    val data: SendOtpData? = null,
    val error: ApiError? = null
)

data class SendOtpData(
    val message: String,
    val expiresIn: Int,
    val otp: String? = null  // Only in mock mode
)

data class VerifyOtpResponse(
    val success: Boolean,
    val data: VerifyOtpData? = null,
    val error: ApiError? = null
)

data class VerifyOtpData(
    val user: UserData,
    val tokens: TokensData,
    val isNewUser: Boolean
)

data class UserData(
    val id: String,
    val phone: String,
    val role: String,
    val name: String? = null,
    val email: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class TokensData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class RefreshTokenResponse(
    val success: Boolean,
    val data: RefreshTokenData? = null,
    val error: ApiError? = null
)

data class RefreshTokenData(
    val accessToken: String,
    val expiresIn: Int
)

data class LogoutResponse(
    val success: Boolean,
    val error: ApiError? = null
)

data class CreateBookingResponse(
    val success: Boolean,
    val data: CreateBookingData? = null,
    val error: ApiError? = null
)

data class CreateBookingData(
    val booking: BookingData
)

data class BookingsListResponse(
    val success: Boolean,
    val data: BookingsListData? = null,
    val error: ApiError? = null
)

data class BookingsListData(
    val bookings: List<BookingData>,
    val total: Int,
    val hasMore: Boolean
)

data class BookingDetailResponse(
    val success: Boolean,
    val data: BookingDetailData? = null,
    val error: ApiError? = null
)

data class BookingDetailData(
    val booking: BookingData
)

data class BookingData(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val pickup: LocationData,
    val drop: LocationData,
    val vehicleType: String,
    val vehicleSubtype: String,
    val trucksNeeded: Int,
    val trucksFilled: Int,
    val pricePerTruck: Int,
    val totalAmount: Int,
    val distanceKm: Int,
    val status: String,
    val notes: String? = null,
    val scheduledAt: String? = null,
    val createdAt: String,
    val expiresAt: String,
    val completedAt: String? = null
)

data class LocationData(
    val coordinates: CoordinatesData,
    val address: String
)

data class CoordinatesData(
    val latitude: Double,
    val longitude: Double
)

data class AssignedTrucksResponse(
    val success: Boolean,
    val data: AssignedTrucksData? = null,
    val error: ApiError? = null
)

data class AssignedTrucksData(
    val trucks: List<AssignedTruckData>
)

data class AssignedTruckData(
    val assignmentId: String,
    val tripId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val driverName: String,
    val driverPhone: String,
    val status: String,
    val assignedAt: String,
    val currentLocation: CoordinatesData? = null
)

data class CancelBookingResponse(
    val success: Boolean,
    val data: CancelBookingData? = null,
    val error: ApiError? = null
)

data class CancelBookingData(
    val booking: BookingData
)

data class TripTrackingResponse(
    val success: Boolean,
    val data: TripTrackingData? = null,
    val error: ApiError? = null
)

data class TripTrackingData(
    val tripId: String,
    val driverId: String,
    val vehicleNumber: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val status: String,
    val lastUpdated: String
)

data class BookingTrackingResponse(
    val success: Boolean,
    val data: BookingTrackingData? = null,
    val error: ApiError? = null
)

data class BookingTrackingData(
    val bookingId: String,
    val trucks: List<TripTrackingData>
)

data class VehicleTypesResponse(
    val success: Boolean,
    val data: VehicleTypesData? = null,
    val error: ApiError? = null
)

data class VehicleTypesData(
    val types: List<VehicleTypeData>
)

data class VehicleTypeData(
    val type: String,
    val displayName: String,
    val description: String,
    val subtypes: List<VehicleSubtypeData>
)

data class VehicleSubtypeData(
    val id: String,
    val name: String,
    val pricePerKm: Int
)

data class PricingResponse(
    val success: Boolean,
    val data: PricingData? = null,
    val error: ApiError? = null
)

data class PricingData(
    val pricing: PricingDetails
)

data class PricingDetails(
    val vehicleType: String,
    val distanceKm: Int,
    val trucksNeeded: Int,
    val pricePerKm: Int,
    val pricePerTruck: Int,
    val totalAmount: Int,
    val estimatedDuration: String,
    // Enhanced pricing fields (optional for backward compatibility)
    val basePrice: Int? = null,
    val distanceCharge: Int? = null,
    val tonnageCharge: Int? = null,
    val surgeMultiplier: Double? = null,
    val surgeFactor: String? = null,
    val distanceSlab: String? = null,
    val validForMinutes: Int? = null,
    val capacityInfo: CapacityInfoDto? = null
)

data class CapacityInfoDto(
    val capacityKg: Int? = null,
    val capacityTons: Double? = null,
    val minTonnage: Double? = null,
    val maxTonnage: Double? = null
)

// ============================================================
// PROFILE DATA CLASSES
// ============================================================

data class UpdateProfileRequest(
    val name: String,
    val email: String? = null,
    val company: String? = null,
    val gstNumber: String? = null,
    val profilePhoto: String? = null
)

data class ProfileResponse(
    val success: Boolean,
    val data: ProfileData? = null,
    val error: ApiError? = null
)

data class ProfileData(
    val profile: UserProfile
)

/**
 * User Profile Model
 * 
 * FOR BACKEND DEVELOPER:
 * - All fields except id, phone, role are nullable/optional
 * - isProfileComplete should be true when name is set
 * - Used for all user types (customer, transporter, driver)
 */
data class UserProfile(
    val id: String,
    val phone: String,
    val role: String,
    val name: String? = null,
    val email: String? = null,
    val profilePhoto: String? = null,
    val company: String? = null,
    val gstNumber: String? = null,
    val isVerified: Boolean = false,
    val isProfileComplete: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

data class ProfileStatusResponse(
    val success: Boolean,
    val data: ProfileStatusData? = null,
    val error: ApiError? = null
)

/**
 * Profile Status - Check if user needs to complete profile
 * 
 * FOR BACKEND DEVELOPER:
 * - Return isComplete = true when user has set their name
 * - missingFields lists what's needed for complete profile
 * - Used after login to decide navigation
 */
data class ProfileStatusData(
    val isComplete: Boolean,
    val missingFields: List<String>
)

// Common error structure
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)

// ============================================================
// ORDER DATA CLASSES (NEW - Multi-Truck System)
// ============================================================

/**
 * Request to create an order with multiple truck types
 * 
 * ROUTE POINTS (Intermediate Stops):
 * - If routePoints is provided, backend uses it for the full route
 * - If null, backend builds route from pickup + drop (backward compatible)
 * - Route: PICKUP → STOP → STOP → DROP (max 2 intermediate stops)
 * - Route is IMMUTABLE after order creation
 */
data class CreateOrderRequest(
    // NEW: Route points with intermediate stops (preferred)
    val routePoints: List<RoutePointRequest>? = null,
    
    // Legacy pickup/drop (backward compatible, used if routePoints is null)
    val pickup: OrderLocationRequest,
    val drop: OrderLocationRequest,
    
    val distanceKm: Int,
    val vehicleRequirements: List<VehicleRequirementRequest>,  // Backend expects this name
    val goodsType: String? = null,
    val cargoWeightKg: Int? = null,
    val scheduledAt: String? = null
)

/**
 * Location format for order creation - FLAT structure (no nested coordinates)
 * Backend schema: { latitude: number, longitude: number, address: string, city?: string, state?: string }
 */
data class OrderLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val state: String? = null
)

// ============================================================
// NEW: Cancel & Status Response Models
// ============================================================

/**
 * Response when cancelling an order
 * 
 * EASY UNDERSTANDING: Shows how many drivers were notified about cancellation
 */
data class CancelOrderResponse(
    val success: Boolean,
    val message: String,
    val data: CancelOrderData? = null
)

data class CancelOrderData(
    val message: String,
    val transportersNotified: Int
)

/**
 * Order status with remaining time
 * 
 * SCALABILITY: Backend calculates remaining time from database
 * EASY UNDERSTANDING: UI just displays this value, doesn't calculate
 */
data class OrderStatusResponse(
    val success: Boolean,
    val data: OrderStatusData? = null
)

data class OrderStatusData(
    val orderId: String,
    val status: String,
    val remainingSeconds: Int,
    val isActive: Boolean,
    val expiresAt: String
)

/**
 * Route Point for intermediate stops
 * 
 * IMPORTANT: Stops are defined BEFORE booking only!
 * - Type: PICKUP (first), STOP (intermediate), DROP (last)
 * - Max 4 points: 1 pickup + 2 stops + 1 drop
 * - Route is IMMUTABLE after order creation
 */
data class RoutePointRequest(
    val type: String,           // "PICKUP", "STOP", or "DROP"
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val state: String? = null
)

// =============================================================================
// ROUTE BREAKDOWN - ETA per leg (Response models)
// =============================================================================

/**
 * Single leg of a route (segment between two consecutive points)
 * 
 * EXAMPLE:
 * Leg 0: Delhi → Jaipur
 *   distanceKm: 270
 *   durationMinutes: 405 (6.75 hours)
 *   durationFormatted: "6 hrs 45 mins"
 */
data class RouteLegResponse(
    val fromIndex: Int,
    val toIndex: Int,
    val fromType: String,
    val toType: String,
    val fromAddress: String,
    val toAddress: String,
    val fromCity: String? = null,
    val toCity: String? = null,
    val distanceKm: Int,
    val durationMinutes: Int,
    val durationFormatted: String,
    val etaMinutes: Int                     // Cumulative ETA from trip start
) {
    /**
     * Get leg display string
     */
    val displayString: String
        get() {
            val from = fromCity ?: fromAddress.split(",").firstOrNull() ?: "Start"
            val to = toCity ?: toAddress.split(",").firstOrNull() ?: "End"
            return "$from → $to • $distanceKm km • $durationFormatted"
        }
}

/**
 * Complete route breakdown with all legs and totals
 * 
 * USE IN UI:
 * - Show each leg with distance and ETA
 * - Highlight current leg based on driver progress
 * - Show total trip summary
 */
data class RouteBreakdownResponse(
    val legs: List<RouteLegResponse> = emptyList(),
    val totalDistanceKm: Int = 0,
    val totalDurationMinutes: Int = 0,
    val totalDurationFormatted: String = "",
    val totalStops: Int = 0,
    val estimatedArrival: String? = null
) {
    /**
     * Get summary string (e.g., "910 km • 22 hrs 45 mins • 2 stops")
     */
    val summaryString: String
        get() {
            val stopsText = when (totalStops) {
                0 -> "Direct"
                1 -> "1 stop"
                else -> "$totalStops stops"
            }
            return "$totalDistanceKm km • $totalDurationFormatted • $stopsText"
        }
    
    /**
     * Check if breakdown has data
     */
    val hasData: Boolean
        get() = legs.isNotEmpty()
}

/**
 * Vehicle requirement within an order
 * Backend schema: { vehicleType, vehicleSubtype, quantity, pricePerTruck }
 */
data class VehicleRequirementRequest(
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    val pricePerTruck: Int
)

/**
 * @deprecated Use VehicleRequirementRequest instead
 */
data class TruckSelectionRequest(
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    val pricePerTruck: Int
)

/**
 * Response after creating an order
 */
data class CreateOrderResponse(
    val success: Boolean,
    val data: CreateOrderData? = null,
    val error: ApiError? = null
)

data class CreateOrderData(
    val order: OrderData,
    val truckRequests: List<TruckRequestData>,
    val broadcastSummary: BroadcastSummary,
    val timeoutSeconds: Int
)

/**
 * Order data model
 */
data class OrderData(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val pickup: LocationData,
    val drop: LocationData,
    val distanceKm: Int,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val totalAmount: Int,
    val goodsType: String? = null,
    val weight: String? = null,
    val status: String,
    val scheduledAt: String? = null,
    val expiresAt: String,
    val expiresIn: Int? = null,  // NEW: Duration in seconds from backend (for timer)
    val createdAt: String,
    val updatedAt: String
)

/**
 * Individual truck request within an order
 */
data class TruckRequestData(
    val id: String,
    val orderId: String,
    val requestNumber: Int,
    val vehicleType: String,
    val vehicleSubtype: String,
    val pricePerTruck: Int,
    val status: String,
    val assignedTransporterId: String? = null,
    val assignedTransporterName: String? = null,
    val assignedVehicleNumber: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val tripId: String? = null,
    val assignedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Summary of how requests were broadcast to transporters
 */
data class BroadcastSummary(
    val totalRequests: Int,
    val groupedBy: List<BroadcastGroup>,
    val totalTransportersNotified: Int
)

data class BroadcastGroup(
    val vehicleType: String,
    val vehicleSubtype: String,
    val count: Int,
    val transportersNotified: Int
)

/**
 * Response for list of orders
 */
data class OrdersListResponse(
    val success: Boolean,
    val data: OrdersListData? = null,
    val error: ApiError? = null
)

data class OrdersListData(
    val orders: List<OrderWithSummary>,
    val total: Int,
    val hasMore: Boolean
)

data class OrderWithSummary(
    val id: String,
    val customerId: String,
    val customerName: String,
    val pickup: LocationData,
    val drop: LocationData,
    val distanceKm: Int,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val totalAmount: Int,
    val status: String,
    val createdAt: String,
    val requestsSummary: RequestsSummary
)

data class RequestsSummary(
    val total: Int,
    val searching: Int,
    val assigned: Int,
    val completed: Int,
    val expired: Int
)

/**
 * Response for single order details
 */
data class OrderDetailResponse(
    val success: Boolean,
    val data: OrderDetailData? = null,
    val error: ApiError? = null
)

data class OrderDetailData(
    val order: OrderData,
    val requests: List<TruckRequestData>,
    val summary: OrderSummary
)

data class OrderSummary(
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksSearching: Int,
    val trucksExpired: Int
)

// ============================================================
// GEOCODING REQUEST/RESPONSE DTOs
// ============================================================

data class PlaceSearchRequest(
    val query: String,
    val biasLat: Double? = null,
    val biasLng: Double? = null,
    val maxResults: Int = 5
)

data class PlaceSearchResponse(
    val success: Boolean,
    val data: List<PlaceResult>? = null,
    val error: ApiError? = null
)

data class PlaceResult(
    val placeId: String,
    val label: String,
    val address: String? = null,
    val city: String? = null,
    val latitude: Double,
    val longitude: Double
)

data class ReverseGeocodeRequest(
    val latitude: Double,
    val longitude: Double
)

data class ReverseGeocodeResponse(
    val success: Boolean,
    val data: ReverseGeocodeData? = null,
    val error: ApiError? = null
)

data class ReverseGeocodeData(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null
)

data class RouteCalculationRequest(
    val from: RouteCoordinates,
    val to: RouteCoordinates,
    val truckMode: Boolean = true,
    val includePolyline: Boolean = false  // Request route geometry for map
)

data class RouteCoordinates(
    val latitude: Double,
    val longitude: Double
)

data class RouteCalculationResponse(
    val success: Boolean,
    val data: RouteCalculationData? = null,
    val error: ApiError? = null
)

data class RouteCalculationData(
    val distanceKm: Int,
    val durationMinutes: Int,
    val durationFormatted: String,
    val source: String,  // "aws" or "haversine"
    val polyline: List<List<Double>>? = null  // Route geometry [[lat, lng], ...]
)

data class GeocodingStatusResponse(
    val success: Boolean,
    val data: GeocodingStatusData? = null,
    val error: ApiError? = null
)

data class GeocodingStatusData(
    val available: Boolean,
    val service: String
)

// ============================================================
// MULTI-POINT ROUTE DATA CLASSES
// ============================================================

data class MultiPointRouteRequest(
    val points: List<RoutePoint>,
    val truckMode: Boolean = true,
    val includePolyline: Boolean = true
)

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val label: String? = null
)

data class MultiPointRouteResponse(
    val success: Boolean,
    val data: MultiPointRouteData? = null,
    val error: ApiError? = null
)

data class MultiPointRouteData(
    val distanceKm: Int,
    val durationMinutes: Int,
    val durationFormatted: String,
    val source: String,  // "aws" or "haversine"
    val polyline: List<List<Double>>? = null,  // Route geometry [[lat, lng], ...]
    val legs: List<RouteLegData>? = null
)

data class RouteLegData(
    val distanceKm: Int,
    val durationMinutes: Int
)

// ============================================================
// CUSTOM BOOKING DATA CLASSES (Long-term contracts)
// ============================================================

/**
 * Request to submit custom booking for long-term contracts
 * 
 * SCALABILITY:
 * - Vehicle requirements support multiple truck types
 * - Backend validates and queues for admin review
 */
data class CustomBookingRequest(
    val pickupCity: String,
    val pickupState: String? = null,
    val dropCity: String,
    val dropState: String? = null,
    val vehicleRequirements: List<CustomVehicleRequirement>,
    val startDate: String,           // ISO date: "2026-02-10"
    val endDate: String,             // ISO date: "2026-03-10"
    val isFlexible: Boolean = false, // Flexible on dates
    val goodsType: String? = null,
    val estimatedWeight: String? = null,
    val specialRequests: String? = null,
    val companyName: String? = null,
    val customerEmail: String? = null
)

data class CustomVehicleRequirement(
    val type: String,          // "open", "container", "flatbed"
    val subtype: String,       // "17ft", "20ft", "32ft"
    val quantity: Int
)

data class CustomBookingResponse(
    val success: Boolean,
    val message: String? = null,
    val data: CustomBookingData? = null,
    val error: ApiError? = null
)

data class CustomBookingData(
    val requestId: String,
    val status: String
)

data class CustomBookingsListResponse(
    val success: Boolean,
    val data: CustomBookingsListData? = null,
    val error: ApiError? = null
)

data class CustomBookingsListData(
    val requests: List<CustomBookingItem>,
    val total: Int,
    val hasMore: Boolean
)

data class CustomBookingItem(
    val id: String,
    val pickupCity: String,
    val dropCity: String,
    val vehicleRequirements: List<CustomVehicleRequirement>,
    val startDate: String,
    val endDate: String,
    val status: String,
    val createdAt: String
)

data class CustomBookingDetailResponse(
    val success: Boolean,
    val data: CustomBookingDetailData? = null,
    val error: ApiError? = null
)

data class CustomBookingDetailData(
    val request: CustomBookingItem
)

data class CustomBookingCancelResponse(
    val success: Boolean,
    val message: String? = null,
    val error: ApiError? = null
)
