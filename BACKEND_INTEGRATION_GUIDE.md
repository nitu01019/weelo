# Weelo - Backend Integration Guide

## 🎯 Overview

This document provides **complete instructions** for the backend team to integrate APIs with the Weelo Android app.

---

## 📋 Table of Contents

1. [Current State](#current-state)
2. [API Base Configuration](#api-base-configuration)
3. [Required APIs by Screen](#required-apis-by-screen)
4. [API Endpoints Specification](#api-endpoints-specification)
5. [Integration Points](#integration-points)
6. [Data Models](#data-models)
7. [Authentication Flow](#authentication-flow)
8. [Testing Guidelines](#testing-guidelines)

---

## 🔍 Current State

### What's Already Built (Frontend)
✅ **All UI screens are complete and functional**  
✅ **MVVM architecture with Clean Architecture**  
✅ **Retrofit configured and ready** (in `AppModule.kt`)  
✅ **Repository pattern implemented**  
✅ **Use cases defined for business logic**  
✅ **Room database for local caching**  
✅ **Google Maps integration**  

### What's Using Mock Data
⚠️ **All data is currently local/mock:**
- Vehicle listings (hardcoded in `VehicleRepositoryImpl`)
- Booking creation (mock implementation in `BookingRepositoryImpl`)
- Distance calculation (Haversine formula)
- Pricing (formula-based, not real-time)

### What Needs Backend Integration
🔄 **These need real APIs:**
1. User authentication (login/signup)
2. Vehicle availability by location
3. Real-time pricing
4. Booking creation and management
5. User profile and history
6. Location services (geocoding, route optimization)

---

## ⚙️ API Base Configuration

### Base URL Configuration

**Location**: `app/src/main/java/com/weelo/logistics/core/util/Constants.kt`

```kotlin
object Constants {
    const val BASE_URL = "https://api.weelo.in/v1/"  // ⚠️ UPDATE THIS
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
```

### Retrofit Setup (Already Configured)

**Location**: `app/src/main/java/com/weelo/logistics/core/di/AppModule.kt`

```kotlin
@Provides
@Singleton
fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
    return Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
```

**✅ Ready to use** - Just create API service interfaces

---

## 🗺️ Required APIs by Screen

### 1. **MainActivity** (Home Screen)
**APIs Needed**: None (or optional featured vehicles)

---

### 2. **LocationInputActivity**
**APIs Needed**:

#### A. Get Recent Locations
```
GET /api/locations/recent
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": "loc_123",
      "address": "Jammu Railway Station, Jammu",
      "latitude": 32.7266,
      "longitude": 74.8570,
      "timestamp": 1704067200000
    }
  ]
}
```

#### B. Save Location to History
```
POST /api/locations/history
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "address": "Jammu Railway Station",
  "latitude": 32.7266,
  "longitude": 74.8570
}
```

#### C. Autocomplete Locations (Optional - can use Google Places)
```
GET /api/locations/autocomplete?query={searchText}
```

**Response**:
```json
{
  "success": true,
  "suggestions": [
    {
      "placeId": "ChIJ...",
      "address": "Jammu Railway Station, Jammu, J&K",
      "latitude": 32.7266,
      "longitude": 74.8570
    }
  ]
}
```

---

### 3. **MapBookingActivity**
**APIs Needed**:

#### A. Calculate Route & Distance
```
POST /api/routing/calculate
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "fromLocation": {
    "latitude": 32.7266,
    "longitude": 74.8570
  },
  "toLocation": {
    "latitude": 32.7357,
    "longitude": 74.8692
  },
  "intermediateStops": [
    {
      "latitude": 32.7300,
      "longitude": 74.8600
    }
  ]
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "distanceKm": 125,
    "durationMinutes": 180,
    "polyline": "encoded_polyline_string",
    "estimatedFuelCost": 1500,
    "tollCharges": 250
  }
}
```

#### B. Get Vehicle Categories Availability
```
GET /api/vehicles/categories/availability
  ?fromLat=32.7266
  &fromLng=74.8570
  &toLat=32.7357
  &toLng=74.8692
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "categories": [
    {
      "id": "truck",
      "name": "Truck",
      "availableCount": 45,
      "estimatedWaitTime": 15
    },
    {
      "id": "tractor",
      "name": "Tractor",
      "availableCount": 12,
      "estimatedWaitTime": 30
    },
    {
      "id": "tempo",
      "name": "Tempo",
      "availableCount": 20,
      "estimatedWaitTime": 10
    }
  ]
}
```

---

### 4. **TruckTypesActivity**
**APIs Needed**:

#### A. Get Available Vehicles by Category
```
GET /api/vehicles/list
  ?category=truck
  &fromLat=32.7266
  &fromLng=74.8570
  &toLat=32.7357
  &toLng=74.8692
  &distanceKm=125
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "vehicles": [
    {
      "id": "open",
      "name": "Open Truck",
      "category": "TRUCK",
      "capacityRange": "7.5 - 43 Ton",
      "description": "Open body truck for general cargo",
      "basePrice": 3000,
      "pricePerKm": 12,
      "availableCount": 8,
      "subtypes": [
        {
          "id": "open_7t",
          "name": "7 Ton Open",
          "dimensions": "16ft x 6ft x 6ft",
          "capacity": "7 Ton",
          "price": 3500
        },
        {
          "id": "open_14t",
          "name": "14 Ton Open",
          "dimensions": "20ft x 7ft x 7ft",
          "capacity": "14 Ton",
          "price": 5000
        }
      ]
    },
    {
      "id": "container",
      "name": "Container Truck",
      "category": "TRUCK",
      "capacityRange": "7.5 - 30 Ton",
      "description": "Enclosed container for protected cargo",
      "basePrice": 3000,
      "pricePerKm": 14,
      "availableCount": 5,
      "subtypes": []
    }
  ]
}
```

#### B. Get Real-time Pricing
```
POST /api/pricing/calculate
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "vehicleId": "open",
  "subtypeId": "open_14t",
  "fromLocation": {
    "latitude": 32.7266,
    "longitude": 74.8570
  },
  "toLocation": {
    "latitude": 32.7357,
    "longitude": 74.8692
  },
  "distanceKm": 125,
  "loadWeight": 12,
  "scheduledDate": "2026-01-05T10:00:00Z"
}
```

**Response**:
```json
{
  "success": true,
  "pricing": {
    "basePrice": 5000,
    "distanceCharge": 1500,
    "loadingCharges": 300,
    "waitingCharges": 0,
    "subtotal": 6800,
    "gst": 1224,
    "totalPrice": 8024,
    "breakdown": {
      "basePrice": 5000,
      "perKmCharge": 12,
      "distanceKm": 125,
      "loadingCharges": 300,
      "gstRate": 0.18
    }
  }
}
```

---

### 5. **Booking Creation & Confirmation**
**APIs Needed**:

#### A. Create Booking
```
POST /api/bookings/create
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "fromLocation": {
    "address": "Jammu Railway Station, Jammu",
    "latitude": 32.7266,
    "longitude": 74.8570
  },
  "toLocation": {
    "address": "Pathankot, Punjab",
    "latitude": 32.2733,
    "longitude": 75.6522
  },
  "intermediateStops": [],
  "vehicleId": "open",
  "subtypeId": "open_14t",
  "distanceKm": 125,
  "estimatedPrice": 8024,
  "scheduledDate": "2026-01-05T10:00:00Z",
  "loadDetails": {
    "weight": 12,
    "description": "Construction materials",
    "requiresLabor": true
  },
  "contactDetails": {
    "name": "John Doe",
    "phone": "+91-9876543210",
    "alternatePhone": "+91-9876543211"
  }
}
```

**Response**:
```json
{
  "success": true,
  "booking": {
    "id": "BKG-20260103-001",
    "bookingNumber": "WL001234",
    "status": "PENDING",
    "estimatedPickupTime": "2026-01-05T10:00:00Z",
    "driverDetails": null,
    "vehicleDetails": null,
    "paymentStatus": "PENDING",
    "createdAt": "2026-01-03T18:30:00Z"
  },
  "message": "Booking created successfully. Driver will be assigned shortly."
}
```

#### B. Get Booking Details
```
GET /api/bookings/{bookingId}
Authorization: Bearer {token}
```

**Response**:
```json
{
  "success": true,
  "booking": {
    "id": "BKG-20260103-001",
    "bookingNumber": "WL001234",
    "status": "CONFIRMED",
    "fromLocation": {...},
    "toLocation": {...},
    "vehicleDetails": {
      "vehicleNumber": "JK01-AB-1234",
      "type": "14 Ton Open Truck",
      "driverName": "Rajesh Kumar",
      "driverPhone": "+91-9876543210",
      "driverPhoto": "https://cdn.weelo.in/drivers/photo_123.jpg"
    },
    "estimatedPickupTime": "2026-01-05T10:00:00Z",
    "actualPickupTime": null,
    "pricing": {...},
    "trackingUrl": "https://track.weelo.in/BKG-20260103-001"
  }
}
```

#### C. Cancel Booking
```
POST /api/bookings/{bookingId}/cancel
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "reason": "Plans changed"
}
```

---

## 🔐 Authentication Flow

### A. User Registration/Login (Future)
```
POST /api/auth/login
Content-Type: application/json
```

**Request Body**:
```json
{
  "phone": "+91-9876543210",
  "otp": "123456"
}
```

**Response**:
```json
{
  "success": true,
  "user": {
    "id": "user_123",
    "name": "John Doe",
    "phone": "+91-9876543210",
    "email": "john@example.com"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "refresh_token_here"
}
```

### Token Storage
**Location**: `PreferencesManager.kt`

```kotlin
fun saveAuthToken(token: String) {
    prefs.edit().putString(KEY_USER_TOKEN, token).apply()
}
```

### Add Auth Interceptor
**Create**: `app/src/main/java/com/weelo/logistics/core/network/AuthInterceptor.kt`

```kotlin
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = preferencesManager.getAuthToken()
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

---

## 🔧 Integration Steps for Backend Team

### Step 1: Create API Service Interface

**Create**: `app/src/main/java/com/weelo/logistics/data/remote/api/WeeloApiService.kt`

```kotlin
interface WeeloApiService {
    
    @GET("vehicles/list")
    suspend fun getVehicles(
        @Query("category") category: String,
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("distanceKm") distanceKm: Int
    ): Response<VehicleListResponse>
    
    @POST("bookings/create")
    suspend fun createBooking(
        @Body request: CreateBookingRequest
    ): Response<BookingResponse>
    
    @GET("bookings/{bookingId}")
    suspend fun getBooking(
        @Path("bookingId") bookingId: String
    ): Response<BookingResponse>
    
    @POST("routing/calculate")
    suspend fun calculateRoute(
        @Body request: RouteRequest
    ): Response<RouteResponse>
    
    @POST("pricing/calculate")
    suspend fun calculatePricing(
        @Body request: PricingRequest
    ): Response<PricingResponse>
}
```

### Step 2: Create Response DTOs

**Create**: `app/src/main/java/com/weelo/logistics/data/remote/dto/VehicleResponse.kt`

```kotlin
data class VehicleListResponse(
    val success: Boolean,
    val vehicles: List<VehicleDto>
)

data class VehicleDto(
    val id: String,
    val name: String,
    val category: String,
    val capacityRange: String,
    val description: String,
    val basePrice: Int,
    val pricePerKm: Double,
    val availableCount: Int,
    val subtypes: List<VehicleSubtypeDto>
)
```

### Step 3: Update Repository Implementation

**Update**: `app/src/main/java/com/weelo/logistics/data/repository/VehicleRepositoryImpl.kt`

```kotlin
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val apiService: WeeloApiService,  // ← Add this
    private val context: Context
) : VehicleRepository {

    override fun getAllVehicles(): Flow<Result<List<VehicleModel>>> {
        return flow {
            emit(Result.Loading)
            
            try {
                // Try to fetch from API
                val response = apiService.getVehicles(...)
                
                if (response.isSuccessful && response.body() != null) {
                    val vehicles = response.body()!!.vehicles.map { it.toDomain() }
                    
                    // Cache in local database
                    vehicleDao.insertVehicles(vehicles.map { it.toEntity() })
                    
                    emit(Result.Success(vehicles))
                } else {
                    // Fallback to cached data
                    val cached = vehicleDao.getAllVehicles().first()
                    emit(Result.Success(cached.map { it.toDomain() }))
                }
            } catch (e: Exception) {
                // On error, use cached data
                val cached = vehicleDao.getAllVehicles().first()
                if (cached.isNotEmpty()) {
                    emit(Result.Success(cached.map { it.toDomain() }))
                } else {
                    emit(Result.Error(WeeloException.NetworkException(e.message)))
                }
            }
        }
    }
}
```

---

## 📡 Error Handling

### Standard Error Response Format
```json
{
  "success": false,
  "error": {
    "code": "VEHICLE_NOT_AVAILABLE",
    "message": "No vehicles available for selected route",
    "details": "All vehicles are currently booked"
  }
}
```

### Error Codes to Implement
- `AUTH_FAILED` - Authentication failed
- `INVALID_TOKEN` - Token expired or invalid
- `VALIDATION_ERROR` - Input validation failed
- `VEHICLE_NOT_AVAILABLE` - No vehicles available
- `BOOKING_FAILED` - Booking creation failed
- `ROUTE_NOT_FOUND` - Route calculation failed
- `PAYMENT_FAILED` - Payment processing failed

---

## 🧪 Testing Guidelines

### 1. Test with Mock Server First
Use tools like:
- **Postman** mock server
- **MockAPI.io**
- **JSON Server**

### 2. Update Base URL
```kotlin
// For testing
const val BASE_URL = "https://mock-api.weelo.in/v1/"

// For production
const val BASE_URL = "https://api.weelo.in/v1/"
```

### 3. Enable Logging
Already configured in `AppModule.kt`:
```kotlin
HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

Check logs with tag: `OkHttp`

---

## 📝 Files to Modify for Integration

### Must Create:
1. ✅ `WeeloApiService.kt` - API interface
2. ✅ Response DTOs in `data/remote/dto/`
3. ✅ Request DTOs in `data/remote/dto/`
4. ✅ `AuthInterceptor.kt` - For token management

### Must Update:
1. ✅ `VehicleRepositoryImpl.kt` - Add API calls
2. ✅ `BookingRepositoryImpl.kt` - Add API calls
3. ✅ `LocationRepositoryImpl.kt` - Add API calls
4. ✅ `Constants.kt` - Update BASE_URL
5. ✅ `AppModule.kt` - Provide API service

### No Changes Needed:
- ✅ All Activities (UI)
- ✅ All ViewModels
- ✅ All Use Cases
- ✅ Room Database structure

---

## 🚀 Quick Start Checklist

- [ ] Backend provides API base URL
- [ ] Update `Constants.BASE_URL`
- [ ] Create `WeeloApiService` interface
- [ ] Create response/request DTOs
- [ ] Update repository implementations
- [ ] Add authentication interceptor
- [ ] Test with mock data first
- [ ] Test with staging API
- [ ] Deploy to production

---

## 📞 Contact

For any clarifications, the frontend team can be reached for:
- API contract discussions
- Response format confirmations
- Error handling strategies
- Performance optimization

**All UI is ready - just plug in the APIs!** 🎉

