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
     */
    @POST("bookings")
    suspend fun createBooking(
        @Header("Authorization") token: String,
        @Body request: CreateBookingRequest
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
    @POST("bookings/orders")
    suspend fun createOrder(
        @Header("Authorization") token: String,
        @Body request: CreateOrderRequest
    ): Response<CreateOrderResponse>

    /**
     * Get customer's orders with pagination
     */
    @GET("bookings/orders")
    suspend fun getMyOrders(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<OrdersListResponse>

    /**
     * Get order details with all truck requests
     */
    @GET("bookings/orders/{orderId}")
    suspend fun getOrderById(
        @Header("Authorization") token: String,
        @Path("orderId") orderId: String
    ): Response<OrderDetailResponse>

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
     */
    @PUT("profile")
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
    val email: String? = null
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
 */
data class CreateOrderRequest(
    val pickup: LocationRequest,
    val drop: LocationRequest,
    val distanceKm: Int,
    val trucks: List<TruckSelectionRequest>,
    val goodsType: String? = null,
    val weight: String? = null,
    val notes: String? = null,
    val scheduledAt: String? = null
)

/**
 * Individual truck selection within an order
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
