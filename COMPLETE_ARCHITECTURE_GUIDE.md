# Weelo Logistics App - Complete Architecture Guide

## 🏗️ Project Overview

**Weelo** is a **100% Native Android Kotlin** logistics vehicle booking application built with modern Android architecture patterns.

### Tech Stack
- **Language**: Kotlin 100%
- **Architecture**: Clean Architecture + MVVM
- **Dependency Injection**: Hilt (Dagger)
- **Database**: Room (SQLite)
- **Async**: Coroutines + Flow
- **Maps**: Google Maps SDK
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

### Current Status
✅ **UI is 100% complete** - All screens are fully functional with mock/local data  
⚠️ **Backend Integration Pending** - Ready for API integration

---

## 📱 Application Flow

### User Journey
```
1. MainActivity (Home)
   ↓ [User clicks search]
   
2. LocationInputActivity (Enter Locations)
   ↓ [User enters FROM/TO locations]
   
3. MapBookingActivity (Map View + Vehicle Category)
   ↓ [User selects vehicle category: Truck/Tractor/Tempo]
   
4A. TruckTypesActivity (Select Truck Type)
    ↓ [User selects specific truck type]
    
4B. TractorMachineryTypesActivity (Select Tractor/Machinery)
    ↓ [User selects specific machinery type]
    
5. Pricing Screen (Shows in bottom sheet)
   ↓ [User confirms booking]
   
6. Booking Confirmation ✅
```

---

## 🎯 Screen Breakdown

### 1. **MainActivity** (Home Screen)
**Purpose**: Entry point, search interface  
**Layout**: `activity_main.xml`  
**ViewModel**: `HomeViewModel`

**Features**:
- Search container to start booking flow
- Welcome screen with branding

**Current Implementation**: ✅ UI Only
- No API calls
- Simple navigation trigger

**Backend Integration Needed**: ❌ None for this screen

---

### 2. **LocationInputActivity** (Location Selection)
**Purpose**: Enter pickup and drop-off locations  
**Layout**: `activity_location_input.xml`  
**ViewModel**: `LocationInputViewModel`

**Features**:
- FROM location input (with autocomplete)
- TO location input (with autocomplete)
- Recent locations display (max 10)
- Intermediate stops support
- Google Places autocomplete integration
- Manual map selection option

**Current Implementation**:
- ✅ Google Places API for autocomplete
- ✅ Local Room database for recent locations
- ✅ Input validation via ViewModel
- ✅ Location saved locally

**Backend Integration Needed**:
- 🔄 Sync recent locations to user profile
- 🔄 Fetch user's favorite locations
- 🔄 Location history from server

---

### 3. **MapBookingActivity** (Map + Category Selection)
**Purpose**: View route on map and select vehicle category  
**Layout**: `activity_map_booking.xml`  
**ViewModel**: None (Activity-based, to be refactored)

**Features**:
- Google Maps with route display
- FROM/TO markers
- Distance calculation
- Vehicle category selection (Truck/Tractor/Tempo)
- Route polyline display

**Current Implementation**:
- ✅ Google Maps SDK integrated
- ✅ Mock distance calculation (Haversine formula)
- ✅ Local route drawing
- ⚠️ Uses Google Directions API (needs API key configuration)

**Backend Integration Needed**:
- 🔄 Real-time distance/duration from server
- 🔄 Route optimization API
- 🔄 Traffic-based pricing
- 🔄 Vehicle availability by category

---

### 4A. **TruckTypesActivity** (Truck Selection)
**Purpose**: Select specific truck type (9 truck types)  
**Layout**: `activity_truck_types.xml`  
**ViewModel**: `TruckTypesViewModel`

**Features**:
- Grid display of truck types
- Truck subtypes selection (bottom sheet)
- Multiple truck selection support
- Capacity and specs display

**Truck Types**:
1. Open (7.5 - 43 Ton)
2. Container (7.5 - 30 Ton)
3. LCV (2.5 - 7 Ton)
4. Mini/Pickup (0.75 - 2 Ton)
5. Trailer (16 - 43 Ton)
6. Tipper (9 - 30 Ton)
7. Tanker (8 - 36 Ton)
8. Dumper (9 - 36 Ton)
9. Bulker (20 - 36 Ton)

**Current Implementation**:
- ✅ Local vehicle data from `TruckConfig.kt`
- ✅ Room database for caching
- ✅ Selection state management

**Backend Integration Needed**:
- 🔄 Fetch available vehicles by location
- 🔄 Real-time vehicle availability
- 🔄 Dynamic pricing per vehicle type
- 🔄 Vehicle specifications from API

---

### 4B. **TractorMachineryTypesActivity** (Tractor/Machinery Selection)
**Purpose**: Select tractor or construction machinery  
**Layout**: `activity_tractor_machinery_types.xml`  
**ViewModel**: None (Activity-based)

**Features**:
- Tractor types (with HP specifications)
- JCB/Construction machinery
- Subtypes selection

**Current Implementation**:
- ✅ Local configuration data
- ✅ Similar to TruckTypesActivity

**Backend Integration Needed**:
- 🔄 Machinery availability by region
- 🔄 Operator availability
- 🔄 Hourly/daily rates from API

---

### 5. **MapSelectionActivity** (Pin Location on Map)
**Purpose**: Select precise location by dragging map  
**Layout**: `activity_map_selection.xml`  
**ViewModel**: None

**Features**:
- Draggable map with center pin
- Current location access
- Coordinate display
- Address reverse geocoding (TODO)

**Current Implementation**:
- ✅ Map interaction
- ⚠️ Shows coordinates only (geocoding pending)

**Backend Integration Needed**:
- 🔄 Reverse geocoding API
- 🔄 Address validation

---

## 🏛️ Architecture Layers

### Clean Architecture Structure
```
app/src/main/java/com/weelo/logistics/
│
├── presentation/          # UI Layer (Activities, ViewModels)
│   ├── home/
│   ├── location/
│   ├── trucks/
│   └── base/
│
├── domain/               # Business Logic Layer
│   ├── model/           # Domain models
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Use cases (business rules)
│
├── data/                # Data Layer
│   ├── local/          # Room database
│   │   ├── dao/
│   │   └── entity/
│   ├── remote/         # API (Retrofit - TO BE IMPLEMENTED)
│   │   ├── api/
│   │   └── dto/
│   ├── repository/     # Repository implementations
│   └── models/         # Data models
│
├── core/               # Core utilities
│   ├── di/            # Hilt dependency injection
│   ├── util/          # Extensions, helpers
│   └── common/        # Result wrapper, exceptions
│
└── utils/             # Utility classes
```

---

## 📊 Data Flow (MVVM Pattern)

### Current Flow (Local Data)
```
Activity → ViewModel → UseCase → Repository → Local DB/Mock Data
   ↑                                              ↓
   └────────── LiveData/StateFlow ←──────────────┘
```

### Target Flow (With Backend)
```
Activity → ViewModel → UseCase → Repository → Remote API + Cache
   ↑                                              ↓
   └────────── LiveData/StateFlow ←──────────────┘
                                                  ↓
                                            Local DB (Cache)
```

---

## 🗄️ Database Schema (Room)

### Tables

#### 1. **LocationEntity**
```kotlin
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val isFavorite: Boolean = false
)
```

#### 2. **VehicleEntity**
```kotlin
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val capacityRange: String,
    val description: String,
    val priceMultiplier: Double,
    val basePrice: Int,
    val minDistance: Int,
    val nearbyCount: Int
)
```

---

## 🔧 Dependency Injection (Hilt)

### Modules

#### **AppModule** (`core/di/AppModule.kt`)
Provides:
- Retrofit instance
- OkHttpClient
- Gson
- Google Maps Service

#### **DataModule** (`core/di/DataModule.kt`)
Provides:
- Room Database
- DAOs (LocationDao, VehicleDao)
- Repositories
- PreferencesManager

### Configuration
```kotlin
@HiltAndroidApp
class WeeloApplication : Application()

@AndroidEntryPoint
class MainActivity : AppCompatActivity()
```

All ViewModels are annotated with `@HiltViewModel`.

---

## 📦 Key Data Models

### Domain Models

#### LocationModel
```kotlin
data class LocationModel(
    val id: String = UUID.randomUUID().toString(),
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

#### VehicleModel
```kotlin
data class VehicleModel(
    val id: String,
    val name: String,
    val category: VehicleCategory,
    val capacityRange: String,
    val description: String,
    val priceMultiplier: Double,
    val basePrice: Int
)
```

#### BookingModel
```kotlin
data class BookingModel(
    val id: String? = null,
    val fromLocation: LocationModel,
    val toLocation: LocationModel,
    val vehicleType: String,
    val distanceKm: Int,
    val estimatedPrice: Double,
    val status: BookingStatus = BookingStatus.PENDING
)
```

---

