# Weelo - Quick Start Guide for Backend Team

## 🎯 TL;DR - 30 Second Summary

- ✅ **100% Native Android Kotlin app**
- ✅ **All UI screens are complete and functional**
- ✅ **Uses mock/local data currently**
- ⚠️ **Ready for backend API integration**
- 📱 **Clean Architecture + MVVM pattern**
- 🗄️ **Room database for caching**
- 🔧 **Retrofit pre-configured**

---

## 📋 What You Need to Know

### Current State
```
Frontend: 100% Complete ✅
Backend:  0% Integrated ⚠️
```

### What Works Now (Without Backend)
- All 7 screens fully functional
- Google Maps integration
- Google Places autocomplete
- Local data storage (Room)
- Mock booking creation
- Distance calculation (formula-based)

### What Needs Backend
- Real vehicle availability
- Dynamic pricing
- Booking creation/tracking
- User authentication
- Location sync across devices

---

## 🗺️ User Flow (What User Sees)

```
Home Screen
    ↓
Enter Locations (FROM → TO)
    ↓
View Route on Map + Select Category (Truck/Tractor/Tempo)
    ↓
Select Specific Vehicle Type (9 trucks / 5 tractors)
    ↓
See Pricing Breakdown
    ↓
Confirm Booking ✅
```

**Every screen is UI-complete!**

---

## 📂 Important Files to Know

### 1. **Constants** (Update BASE_URL here)
```
app/src/main/java/com/weelo/logistics/core/util/Constants.kt
```
```kotlin
const val BASE_URL = "https://api.weelo.in/v1/"  // ← CHANGE THIS
```

### 2. **Retrofit Setup** (Already configured)
```
app/src/main/java/com/weelo/logistics/core/di/AppModule.kt
```

### 3. **Repositories** (Add API calls here)
```
app/src/main/java/com/weelo/logistics/data/repository/
├── VehicleRepositoryImpl.kt     # Vehicles API
├── BookingRepositoryImpl.kt     # Bookings API
└── LocationRepositoryImpl.kt    # Locations API
```

### 4. **Mock Data** (What you're replacing)
```
app/src/main/java/com/weelo/logistics/data/models/
├── TruckConfig.kt               # Hardcoded truck types
├── TractorMachineryConfig.kt    # Hardcoded tractors
└── JCBMachineryConfig.kt        # Hardcoded machinery
```

---

## 🚀 5-Step Integration Process

### Step 1: Create API Service Interface
**File to create**: `data/remote/api/WeeloApiService.kt`

```kotlin
interface WeeloApiService {
    
    @GET("vehicles/list")
    suspend fun getVehicles(
        @Query("category") category: String,
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double
    ): Response<VehicleListResponse>
    
    @POST("bookings/create")
    suspend fun createBooking(
        @Body request: CreateBookingRequest
    ): Response<BookingResponse>
    
    @POST("pricing/calculate")
    suspend fun calculatePricing(
        @Body request: PricingRequest
    ): Response<PricingResponse>
}
```

### Step 2: Create Response DTOs
**File to create**: `data/remote/dto/VehicleResponse.kt`

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
    val basePrice: Int,
    val pricePerKm: Double,
    val availableCount: Int
) {
    fun toDomain(): VehicleModel {
        return VehicleModel(
            id = id,
            name = name,
            category = VehicleCategory.valueOf(category),
            capacityRange = capacityRange,
            description = "",
            priceMultiplier = pricePerKm / 10,
            basePrice = basePrice,
            nearbyCount = availableCount
        )
    }
}
```

### Step 3: Update Repository
**File to update**: `data/repository/VehicleRepositoryImpl.kt`

```kotlin
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val apiService: WeeloApiService,  // ← ADD THIS
    private val context: Context
) : VehicleRepository {

    override fun getAllVehicles(): Flow<Result<List<VehicleModel>>> {
        return flow {
            emit(Result.Loading)
            
            try {
                val response = apiService.getVehicles(...)
                
                if (response.isSuccessful && response.body() != null) {
                    val vehicles = response.body()!!.vehicles.map { it.toDomain() }
                    
                    // Cache in Room
                    vehicleDao.insertVehicles(vehicles.map { it.toEntity() })
                    
                    emit(Result.Success(vehicles))
                } else {
                    // Fallback to cache
                    val cached = vehicleDao.getAllVehicles().first()
                    emit(Result.Success(cached.map { it.toDomain() }))
                }
            } catch (e: Exception) {
                // Error fallback
                val cached = vehicleDao.getAllVehicles().first()
                emit(Result.Success(cached.map { it.toDomain() }))
            }
        }
    }
}
```

### Step 4: Provide API Service
**File to update**: `core/di/AppModule.kt`

```kotlin
@Provides
@Singleton
fun provideWeeloApiService(retrofit: Retrofit): WeeloApiService {
    return retrofit.create(WeeloApiService::class.java)
}
```

### Step 5: Test
```bash
# Update BASE_URL in Constants.kt
# Run app
./gradlew installDebug

# Check logs
adb logcat | grep "OkHttp"
```

---

## 🔌 Required APIs (Priority Order)

### Priority 1: CRITICAL (Core booking flow)

#### 1. Get Vehicles List
```
GET /api/vehicles/list
  ?category=truck
  &fromLat=32.7266
  &fromLng=74.8570
  &toLat=32.7357
  &toLng=74.8692
```

#### 2. Calculate Pricing
```
POST /api/pricing/calculate
{
  "vehicleId": "open",
  "subtypeId": "open_14t",
  "fromLocation": {...},
  "toLocation": {...},
  "distanceKm": 125
}
```

#### 3. Create Booking
```
POST /api/bookings/create
{
  "fromLocation": {...},
  "toLocation": {...},
  "vehicleId": "open",
  "subtypeId": "open_14t",
  "distanceKm": 125,
  "estimatedPrice": 8024
}
```

### Priority 2: HIGH (Enhanced experience)

#### 4. Calculate Route
```
POST /api/routing/calculate
{
  "fromLocation": {...},
  "toLocation": {...}
}
```

#### 5. Get Vehicle Availability
```
GET /api/vehicles/categories/availability
  ?fromLat=32.7266&fromLng=74.8570
```

### Priority 3: MEDIUM (User features)

#### 6. User Location Sync
```
GET /api/user/locations/recent
POST /api/user/locations/save
```

---

## 📊 API Response Format (Standard)

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "VEHICLE_NOT_AVAILABLE",
    "message": "No vehicles available",
    "details": "All vehicles are booked"
  }
}
```

---

## 🧪 Testing with Mock Server

### Option 1: MockAPI.io
1. Create mock endpoints at mockapi.io
2. Update `Constants.BASE_URL`
3. Test app with mock data

### Option 2: JSON Server
```bash
npm install -g json-server
json-server --watch db.json --port 3000
```

### Option 3: Postman Mock Server
1. Create collection in Postman
2. Add mock responses
3. Get mock URL
4. Update `BASE_URL`

---

## 🎯 Integration Checklist

### Pre-Integration
- [ ] Backend team has reviewed API specifications
- [ ] API base URL is ready
- [ ] Authentication strategy decided
- [ ] Error codes defined

### During Integration
- [ ] Update `Constants.BASE_URL`
- [ ] Create `WeeloApiService` interface
- [ ] Create all response/request DTOs
- [ ] Update repository implementations
- [ ] Add authentication interceptor (if needed)
- [ ] Test with staging API

### Post-Integration
- [ ] Test all user flows end-to-end
- [ ] Verify offline mode (cached data)
- [ ] Check error handling
- [ ] Performance testing
- [ ] Production deployment

---

## ❓ Common Questions

### Q: Do we need to change any Activity files?
**A**: No! Activities only observe ViewModels. No changes needed.

### Q: What if our API response format is different?
**A**: Create mapper functions in DTOs: `fun toDomain()` converts API model to app model.

### Q: How to handle authentication?
**A**: Create `AuthInterceptor` to add token to headers:
```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val token = getToken()
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

### Q: Can we integrate one screen at a time?
**A**: Yes! Each repository is independent. Start with vehicles, then booking, then locations.

### Q: How to test without breaking the app?
**A**: Use feature flags or create separate build variant for API testing.

### Q: What about pagination?
**A**: Add pagination parameters to repository methods and update UI to handle paged data.

---

## 📞 Next Steps

### For Backend Team
1. Review `BACKEND_INTEGRATION_GUIDE.md` for detailed API specs
2. Set up staging environment
3. Provide staging API URL
4. Coordinate with frontend for testing

### For Frontend Team (You)
1. Share these docs with backend team
2. Coordinate on API contracts
3. Create test plan
4. Prepare staging build

---

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| `COMPLETE_ARCHITECTURE_GUIDE.md` | Full architecture explanation |
| `BACKEND_INTEGRATION_GUIDE.md` | Complete API specifications |
| `SCREENS_DETAILED_BREAKDOWN.md` | Screen-by-screen analysis |
| `CODE_STRUCTURE_EXPLANATION.md` | Code organization & modularity |
| `QUICK_START_GUIDE.md` | This file - Quick reference |

---

## 🎉 Summary

**Your app is 100% ready for backend integration!**

- ✅ All UI complete
- ✅ Architecture is solid
- ✅ Retrofit configured
- ✅ Room database for caching
- ✅ Error handling ready
- ✅ Just add API calls!

**No UI changes needed. Only update Repository implementations.**

---

**Good luck with the integration! 🚀**

For questions: Review the detailed documentation files above.
