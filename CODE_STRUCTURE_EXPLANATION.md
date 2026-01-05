# Weelo - Code Structure & Modularity Explanation

## 📂 Project Structure Overview

```
Weelo/
├── app/
│   ├── src/main/
│   │   ├── java/com/weelo/logistics/
│   │   │   ├── MainActivity.kt                    # Home screen
│   │   │   ├── LocationInputActivity.kt           # Location input
│   │   │   ├── MapBookingActivity.kt              # Map + category
│   │   │   ├── TruckTypesActivity.kt              # Truck selection
│   │   │   ├── TractorMachineryTypesActivity.kt   # Tractor/machinery
│   │   │   ├── MapSelectionActivity.kt            # Map pin selection
│   │   │   ├── WeeloApplication.kt                # App class
│   │   │   │
│   │   │   ├── presentation/          # 📱 UI Layer (ViewModels)
│   │   │   │   ├── home/
│   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   ├── location/
│   │   │   │   │   └── LocationInputViewModel.kt
│   │   │   │   ├── trucks/
│   │   │   │   │   └── TruckTypesViewModel.kt
│   │   │   │   └── base/
│   │   │   │       └── BaseViewModel.kt
│   │   │   │
│   │   │   ├── domain/               # 🧠 Business Logic Layer
│   │   │   │   ├── model/            # Domain models (pure Kotlin)
│   │   │   │   │   ├── LocationModel.kt
│   │   │   │   │   ├── VehicleModel.kt
│   │   │   │   │   └── BookingModel.kt
│   │   │   │   ├── repository/       # Repository contracts
│   │   │   │   │   ├── LocationRepository.kt
│   │   │   │   │   ├── VehicleRepository.kt
│   │   │   │   │   └── BookingRepository.kt
│   │   │   │   └── usecase/          # Business rules
│   │   │   │       ├── ValidateLocationsUseCase.kt
│   │   │   │       ├── GetAllVehiclesUseCase.kt
│   │   │   │       ├── CreateBookingUseCase.kt
│   │   │   │       └── CalculateRouteDistanceUseCase.kt
│   │   │   │
│   │   │   ├── data/                 # 💾 Data Layer
│   │   │   │   ├── local/            # Room database
│   │   │   │   │   ├── WeeloDatabase.kt
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── LocationDao.kt
│   │   │   │   │   │   └── VehicleDao.kt
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── LocationEntity.kt
│   │   │   │   │   │   └── VehicleEntity.kt
│   │   │   │   │   └── preferences/
│   │   │   │   │       └── PreferencesManager.kt
│   │   │   │   │
│   │   │   │   ├── remote/           # API layer (TO BE INTEGRATED)
│   │   │   │   │   ├── api/
│   │   │   │   │   │   └── GoogleMapsService.kt
│   │   │   │   │   ├── dto/          # API response models
│   │   │   │   │   │   ├── DirectionsResponse.kt
│   │   │   │   │   │   └── GeocodingResponse.kt
│   │   │   │   │   └── geocoding/
│   │   │   │   │       └── GeocodingDataSource.kt
│   │   │   │   │
│   │   │   │   ├── repository/       # Repository implementations
│   │   │   │   │   ├── LocationRepositoryImpl.kt
│   │   │   │   │   ├── VehicleRepositoryImpl.kt
│   │   │   │   │   └── BookingRepositoryImpl.kt
│   │   │   │   │
│   │   │   │   ├── models/           # Data transfer objects
│   │   │   │   │   ├── Location.kt
│   │   │   │   │   ├── VehicleType.kt
│   │   │   │   │   ├── TruckConfig.kt
│   │   │   │   │   └── VehicleSelection.kt
│   │   │   │   │
│   │   │   │   └── managers/         # Data managers
│   │   │   │       ├── LocationManager.kt
│   │   │   │       └── VehicleDataManager.kt
│   │   │   │
│   │   │   ├── core/                 # 🛠️ Core utilities
│   │   │   │   ├── di/               # Dependency injection
│   │   │   │   │   ├── AppModule.kt  # Retrofit, OkHttp, Gson
│   │   │   │   │   └── DataModule.kt # Room, Repositories
│   │   │   │   ├── base/             # Base classes
│   │   │   │   │   ├── BaseActivity.kt
│   │   │   │   │   └── BaseViewModel.kt
│   │   │   │   ├── common/           # Common classes
│   │   │   │   │   ├── Result.kt     # Result wrapper
│   │   │   │   │   ├── Resource.kt
│   │   │   │   │   ├── ErrorHandler.kt
│   │   │   │   │   └── WeeloException.kt
│   │   │   │   └── util/             # Utilities
│   │   │   │       ├── Constants.kt
│   │   │   │       ├── Extensions.kt
│   │   │   │       ├── PlacesHelper.kt
│   │   │   │       └── TransitionHelper.kt
│   │   │   │
│   │   │   ├── adapters/             # RecyclerView adapters
│   │   │   │   ├── PlacesAutoCompleteAdapter.kt
│   │   │   │   └── SelectedTrucksAdapter.kt
│   │   │   │
│   │   │   ├── utils/                # Utility classes
│   │   │   │   ├── NetworkUtils.kt
│   │   │   │   ├── ValidationUtils.kt
│   │   │   │   └── PolylineDecoder.kt
│   │   │   │
│   │   │   └── tutorial/             # Onboarding/tutorial
│   │   │       ├── TutorialCoordinator.kt
│   │   │       ├── OnboardingManager.kt
│   │   │       └── TextToSpeechManager.kt
│   │   │
│   │   └── res/                      # 🎨 Resources
│   │       ├── layout/               # XML layouts (18 files)
│   │       ├── drawable/             # Icons, backgrounds
│   │       ├── values/               # Colors, strings, themes
│   │       └── anim/                 # Animations
│   │
│   └── build.gradle                  # App dependencies
│
├── build.gradle                      # Project config
├── settings.gradle                   # Module settings
└── Documentation files (NEW!)
    ├── COMPLETE_ARCHITECTURE_GUIDE.md
    ├── BACKEND_INTEGRATION_GUIDE.md
    └── SCREENS_DETAILED_BREAKDOWN.md
```

**Total Kotlin Files**: 74  
**Total Layout Files**: 18  
**Lines of Code**: ~8,000+

---

## 🏗️ Architecture Pattern: Clean Architecture + MVVM

### Layer Separation

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│  (Activities, ViewModels, Adapters)     │
│  - UI logic only                        │
│  - Observes ViewModel                   │
│  - No direct data access                │
└────────────┬────────────────────────────┘
             │
             ↓
┌─────────────────────────────────────────┐
│          DOMAIN LAYER                   │
│  (Use Cases, Domain Models, Interfaces) │
│  - Business logic                       │
│  - Platform independent                 │
│  - Pure Kotlin                          │
└────────────┬────────────────────────────┘
             │
             ↓
┌─────────────────────────────────────────┐
│           DATA LAYER                    │
│  (Repositories, APIs, Database, Cache)  │
│  - Data sources                         │
│  - API calls                            │
│  - Local storage                        │
└─────────────────────────────────────────┘
```

### Why This Architecture?

1. **Separation of Concerns**: Each layer has one responsibility
2. **Testability**: Easy to unit test each layer independently
3. **Maintainability**: Changes in one layer don't affect others
4. **Scalability**: Easy to add new features
5. **Backend Integration**: Just update Data layer, no UI changes

---

## 🔄 Data Flow Example

### Example: User Selects a Truck

```
1. USER ACTION
   TruckTypesActivity
   User clicks on "14 Ton Open Truck"
   ↓

2. ACTIVITY → VIEWMODEL
   TruckTypesActivity.kt:
   onTruckSelected(truck)
   ↓
   viewModel.onVehicleSelected(truck)
   ↓

3. VIEWMODEL → USE CASE
   TruckTypesViewModel.kt:
   // Updates UI state
   _uiState.value = _uiState.value?.copy(selectedVehicle = truck)
   
   // When user clicks Continue
   onContinueClicked()
   ↓
   Validates selection
   ↓

4. USE CASE → REPOSITORY
   (Future) PricingUseCase:
   calculatePrice(vehicle, route)
   ↓
   pricingRepository.getPricing(...)
   ↓

5. REPOSITORY → API/DATABASE
   PricingRepositoryImpl.kt:
   // Try API first
   val response = apiService.calculatePricing(...)
   
   // Cache result
   database.savePricing(...)
   ↓

6. RESPONSE BACK UP
   Repository returns Result<Pricing>
   ↓
   Use Case processes
   ↓
   ViewModel updates state
   ↓
   Activity observes LiveData
   ↓
   UI updates automatically
```

---

## 🧩 Modularity Explained

### 1. **Page-Level Modularity**

Each screen is **independent** and modular:

```kotlin
// MainActivity - Standalone
class MainActivity : AppCompatActivity() {
    private val viewModel: HomeViewModel by viewModels()
    
    fun navigateToLocationInput() {
        startActivity(Intent(this, LocationInputActivity::class.java))
    }
}

// LocationInputActivity - Standalone
class LocationInputActivity : AppCompatActivity() {
    private val viewModel: LocationInputViewModel by viewModels()
    
    fun navigateToMap(from: Location, to: Location) {
        val intent = Intent(this, MapBookingActivity::class.java).apply {
            putExtra("FROM_LOCATION", from)
            putExtra("TO_LOCATION", to)
        }
        startActivity(intent)
    }
}
```

**Benefits**:
- Add/remove screens easily
- Test screens independently
- Backend team can integrate one screen at a time

---

### 2. **Data Layer Modularity**

Each repository handles **one domain**:

#### LocationRepository
```kotlin
interface LocationRepository {
    fun getRecentLocations(): Flow<Result<List<LocationModel>>>
    suspend fun addRecentLocation(location: LocationModel): Result<Unit>
    suspend fun toggleFavorite(locationId: String): Result<Boolean>
}
```

#### VehicleRepository
```kotlin
interface VehicleRepository {
    fun getAllVehicles(): Flow<Result<List<VehicleModel>>>
    suspend fun getVehiclesByCategory(category: VehicleCategory): Result<List<VehicleModel>>
    suspend fun getVehicleById(id: String): Result<VehicleModel>
}
```

#### BookingRepository
```kotlin
interface BookingRepository {
    suspend fun createBooking(booking: BookingModel): Result<BookingModel>
    suspend fun getBookingById(bookingId: String): Result<BookingModel>
    fun getUserBookings(): Flow<Result<List<BookingModel>>>
}
```

**Each repository can be backend-integrated independently!**

---

### 3. **Use Case Modularity**

Each use case = **one business action**:

```kotlin
// Single responsibility
class ValidateLocationsUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(
        from: LocationModel, 
        to: LocationModel
    ): Result<Boolean> {
        // Validation logic
    }
}

// Another use case
class CreateBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository
) {
    suspend operator fun invoke(booking: BookingModel): Result<BookingModel> {
        // Booking creation logic
    }
}
```

**Benefits**:
- Easy to test
- Reusable across screens
- Business logic in one place

---

## 🔌 Backend Integration Points

### Where to Add API Calls

```kotlin
// BEFORE (Mock data)
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao
) : VehicleRepository {
    
    override fun getAllVehicles(): Flow<Result<List<VehicleModel>>> {
        return flow {
            // Get from hardcoded config
            val vehicles = TruckConfig.TRUCK_TYPES
            emit(Result.Success(vehicles))
        }
    }
}

// AFTER (With backend)
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val apiService: WeeloApiService  // ← ADD THIS
) : VehicleRepository {
    
    override fun getAllVehicles(): Flow<Result<List<VehicleModel>>> {
        return flow {
            emit(Result.Loading)
            
            try {
                // API call
                val response = apiService.getVehicles(...)
                
                if (response.isSuccessful) {
                    val vehicles = response.body()!!.vehicles.map { it.toDomain() }
                    
                    // Cache locally
                    vehicleDao.insertVehicles(vehicles.map { it.toEntity() })
                    
                    emit(Result.Success(vehicles))
                } else {
                    // Fallback to cache
                    val cached = vehicleDao.getAllVehicles().first()
                    emit(Result.Success(cached.map { it.toDomain() }))
                }
            } catch (e: Exception) {
                // Error handling
                emit(Result.Error(e))
            }
        }
    }
}
```

**No changes needed in**:
- ✅ Activities
- ✅ ViewModels
- ✅ Use Cases
- ✅ UI layouts

---

## 📦 Configuration Files

### 1. Hardcoded Vehicle Data

**File**: `data/models/TruckConfig.kt`

```kotlin
object TruckConfig {
    val TRUCK_TYPES = listOf(
        TruckType(
            id = "open",
            name = "Open",
            capacityRange = "7.5 - 43 Ton",
            description = "Open body truck for general cargo",
            priceMultiplier = 1.2,
            subtypes = listOf(
                TruckSubtype(
                    name = "7 Ton Open",
                    dimensions = "16ft x 6ft x 6ft",
                    capacity = 7,
                    price = 3500
                ),
                // ... 5 more subtypes
            )
        ),
        // ... 8 more truck types
    )
}
```

**Similar files**:
- `TractorMachineryConfig.kt` - Tractor types
- `JCBMachineryConfig.kt` - JCB machinery

**Purpose**: Currently used as fallback/mock data. Will be replaced by API responses.

---

### 2. Constants Configuration

**File**: `core/util/Constants.kt`

```kotlin
object Constants {
    // Backend URL - UPDATE THIS
    const val BASE_URL = "https://api.weelo.in/v1/"
    
    // Timeouts
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
    
    // Business rules
    const val GST_RATE = 0.18
    const val MAX_RECENT_LOCATIONS = 10
    const val MIN_DISTANCE_KM = 1
    const val MAX_DISTANCE_KM = 10000
    
    // Database
    const val DATABASE_NAME = "weelo_database"
}
```

---

## 🎯 How Backend Team Should Integrate

### Step-by-Step Process

#### Phase 1: Create API Service
```kotlin
// File: data/remote/api/WeeloApiService.kt
interface WeeloApiService {
    
    @GET("vehicles/list")
    suspend fun getVehicles(
        @Query("category") category: String,
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double
    ): Response<VehicleListResponse>
    
    @POST("bookings/create")
    suspend fun createBooking(
        @Body request: CreateBookingRequest
    ): Response<BookingResponse>
}
```

#### Phase 2: Create DTOs
```kotlin
// File: data/remote/dto/VehicleResponse.kt
data class VehicleListResponse(
    val success: Boolean,
    val vehicles: List<VehicleDto>
)

data class VehicleDto(
    val id: String,
    val name: String,
    val category: String,
    // ... other fields
) {
    // Mapper
    fun toDomain(): VehicleModel {
        return VehicleModel(
            id = id,
            name = name,
            category = VehicleCategory.valueOf(category),
            // ...
        )
    }
}
```

#### Phase 3: Update Repository
```kotlin
// Update: data/repository/VehicleRepositoryImpl.kt
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val apiService: WeeloApiService  // ADD THIS
) : VehicleRepository {
    // Add API calls as shown above
}
```

#### Phase 4: Provide in Hilt Module
```kotlin
// Update: core/di/AppModule.kt
@Provides
@Singleton
fun provideWeeloApiService(retrofit: Retrofit): WeeloApiService {
    return retrofit.create(WeeloApiService::class.java)
}
```

#### Phase 5: Test
```bash
# Run app
./gradlew installDebug

# Check logs
adb logcat | grep "OkHttp"
```

---

## 🧪 Testing Structure

### Unit Tests
**Location**: `app/src/test/java/`

```
test/
├── core/
│   └── DataSafetyTest.kt
├── domain/usecase/
│   └── ValidateLocationsUseCaseTest.kt
└── presentation/location/
    └── LocationInputViewModelTest.kt
```

### Testing with Mock Data
```kotlin
@Test
fun `validate locations returns success for valid inputs`() = runTest {
    // Given
    val from = LocationModel(address = "Jammu")
    val to = LocationModel(address = "Pathankot")
    
    // When
    val result = validateLocationsUseCase(from, to)
    
    // Then
    assertTrue(result is Result.Success)
}
```

---

## 📊 Database Schema

### Room Database
**File**: `data/local/WeeloDatabase.kt`

```kotlin
@Database(
    entities = [
        LocationEntity::class,
        VehicleEntity::class
    ],
    version = 1
)
abstract class WeeloDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun vehicleDao(): VehicleDao
}
```

### Tables

#### locations
```sql
CREATE TABLE locations (
    id TEXT PRIMARY KEY,
    address TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    timestamp INTEGER NOT NULL,
    isFavorite INTEGER DEFAULT 0
);
```

#### vehicles
```sql
CREATE TABLE vehicles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    capacityRange TEXT,
    description TEXT,
    priceMultiplier REAL,
    basePrice INTEGER,
    minDistance INTEGER,
    nearbyCount INTEGER
);
```

---

## 🎨 UI/Layout Structure

### Layout Files (18 total)

| Layout File | Used In | Purpose |
|-------------|---------|---------|
| `activity_main.xml` | MainActivity | Home screen |
| `activity_location_input.xml` | LocationInputActivity | Location input |
| `activity_map_booking.xml` | MapBookingActivity | Map view |
| `activity_truck_types.xml` | TruckTypesActivity | Truck grid |
| `activity_tractor_machinery_types.xml` | TractorMachineryTypesActivity | Tractor grid |
| `activity_map_selection.xml` | MapSelectionActivity | Map pin selection |
| `activity_pricing.xml` | Bottom sheet | Pricing details |
| `bottom_sheet_truck_subtypes.xml` | Bottom sheet | Truck subtypes |
| `bottom_sheet_machinery_subtypes.xml` | Bottom sheet | Machinery subtypes |
| `item_truck_card.xml` | RecyclerView | Truck card |
| `item_selected_truck.xml` | RecyclerView | Selected truck chip |
| `item_recent_location.xml` | RecyclerView | Recent location |
| `item_autocomplete_place.xml` | AutoComplete | Place suggestion |
| `loading_dialog.xml` | Dialog | Loading indicator |

### Reusable Components
- All truck cards use the same layout
- Bottom sheets follow consistent design
- Material Design 3 components

---

## 🔒 Error Handling

### Result Wrapper Pattern
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: WeeloException) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

### Custom Exceptions
```kotlin
sealed class WeeloException(message: String) : Exception(message) {
    class NetworkException(message: String?) : WeeloException(message ?: "Network error")
    class ValidationException(message: String) : WeeloException(message)
    class BookingException(message: String) : WeeloException(message)
    class LocationException(message: String) : WeeloException(message)
    class UnknownException(message: String?) : WeeloException(message ?: "Unknown error")
}
```

---

## 🚀 Key Takeaways for Backend Team

### ✅ What's Already Done
1. **Complete MVVM architecture**
2. **All UI screens functional**
3. **Repository pattern implemented**
4. **Retrofit configured**
5. **Room database for caching**
6. **Dependency injection with Hilt**
7. **Error handling framework**

### 🔄 What Backend Needs to Do
1. **Create API service interfaces**
2. **Define response/request DTOs**
3. **Update repository implementations**
4. **Test with mock server first**
5. **Deploy to staging**
6. **Production release**

### 🎯 Integration is Easy Because
- **Separation of concerns**: Only Data layer needs updates
- **Interfaces defined**: Just implement the contracts
- **Mock data ready**: Easy to compare API responses
- **Offline support**: Room database acts as cache
- **Error handling**: Result wrapper handles all cases

---

## 📞 Questions Backend Team Might Have

### Q: Do we need to change Activity code?
**A**: No! Activities only observe ViewModels.

### Q: Will UI break during integration?
**A**: No! Repositories handle fallback to cache.

### Q: Can we integrate one feature at a time?
**A**: Yes! Each repository is independent.

### Q: What if API response format differs?
**A**: Create mapper in DTO: `fun toDomain()`

### Q: How to handle pagination?
**A**: Add paging parameters to repository methods.

### Q: How to add authentication?
**A**: Create `AuthInterceptor` and add to OkHttpClient.

---

**The codebase is production-ready for backend integration!** 🚀
