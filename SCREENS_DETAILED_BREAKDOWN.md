# Weelo - Detailed Screen Breakdown

## 📱 Complete Screen-by-Screen Analysis

This document provides a **detailed breakdown of every screen** in the Weelo app, including what's implemented, what data is used, and how to integrate backend APIs.

---

## Screen 1: MainActivity (Home Screen)

### 🎯 Purpose
Landing page / Entry point for the booking flow

### 📄 Files
- **Activity**: `MainActivity.kt`
- **Layout**: `res/layout/activity_main.xml`
- **ViewModel**: `presentation/home/HomeViewModel.kt`

### 🎨 UI Components
- Search container (CardView) - Main CTA
- App branding/logo
- Welcome text

### 🔄 Current Data Flow
```
User Clicks Search
    ↓
MainActivity.handleSearchClick()
    ↓
HomeViewModel.onSearchClicked()
    ↓
Navigate to LocationInputActivity
```

### 💾 Current Data Source
- **None** - Pure UI navigation

### 🔌 Backend Integration Needed
**Optional** (can be added later):
- Featured vehicles API
- User's recent bookings
- Promotional banners

### ✅ Status
**100% Complete** - No backend needed for MVP

---

## Screen 2: LocationInputActivity

### 🎯 Purpose
Enter pickup (FROM) and drop-off (TO) locations

### 📄 Files
- **Activity**: `LocationInputActivity.kt` (662 lines)
- **Layout**: `res/layout/activity_location_input.xml`
- **ViewModel**: `presentation/location/LocationInputViewModel.kt`
- **Adapter**: `adapters/PlacesAutoCompleteAdapter.kt`

### 🎨 UI Components
```
┌─────────────────────────────────┐
│  [←]  Enter Locations           │
├─────────────────────────────────┤
│  FROM: [AutoComplete Input]     │
│        📍 Recent locations       │
│                                  │
│  TO:   [AutoComplete Input]     │
│        📍 Recent locations       │
│                                  │
│  [+ Add Intermediate Stop]      │
│                                  │
│  Recent Locations:               │
│  • Jammu Railway Station         │
│  • Pathankot Bus Stand           │
│                                  │
│  [Select on Map] [Continue →]   │
└─────────────────────────────────┘
```

### 🔄 Current Data Flow
```
User enters location
    ↓
AutoCompleteTextView triggers
    ↓
Google Places API (autocomplete)
    ↓
User selects location
    ↓
LocationInputViewModel.onContinueClicked()
    ↓
ValidateLocationsUseCase
    ↓
AddRecentLocationUseCase (saves to Room)
    ↓
Navigate to MapBookingActivity
```

### 💾 Current Data Source
1. **Google Places API** - For autocomplete suggestions
   - Uses `PlacesHelper.kt`
   - Real API integration already done
   
2. **Room Database** - For recent locations
   - Table: `LocationEntity`
   - DAO: `LocationDao`
   - Max 10 recent locations

### 🔌 Backend Integration Needed

#### API 1: Sync Recent Locations
```
GET /api/user/locations/recent
POST /api/user/locations/save
```

**Why**: Currently only stored locally. Should sync across devices.

#### API 2: Fetch Favorite Locations
```
GET /api/user/locations/favorites
POST /api/user/locations/favorite/{locationId}
```

**Why**: User's saved frequently used addresses.

### 📦 Data Models Used
```kotlin
// Domain Model
data class LocationModel(
    val id: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long
)

// Room Entity
@Entity(tableName = "locations")
data class LocationEntity(...)
```

### 🔧 Integration Steps
1. Create `LocationApiService` interface
2. Add API calls to `LocationRepositoryImpl`
3. Keep Room database as cache
4. Sync on app start and after each save

### ✅ Status
**UI: 100% Complete**  
**Backend: 30% (Google Places integrated, user sync pending)**

---

## Screen 3: MapBookingActivity

### 🎯 Purpose
View route on Google Maps and select vehicle category (Truck/Tractor/Tempo)

### 📄 Files
- **Activity**: `MapBookingActivity.kt` (443 lines)
- **Layout**: `res/layout/activity_map_booking.xml`
- **ViewModel**: None (activity-based logic)

### 🎨 UI Components
```
┌─────────────────────────────────┐
│  [←]  📍→📍  125 km             │
├─────────────────────────────────┤
│                                  │
│      🗺️ Google Map               │
│         📍 FROM                  │
│          ~~~~~                   │
│         📍 TO                    │
│                                  │
├─────────────────────────────────┤
│  Vehicle Categories:             │
│  [🚛 Truck]  [🚜 Tractor]        │
│  [📦 Tempo]                      │
├─────────────────────────────────┤
│  Distance: 125 km                │
│  Duration: ~3 hrs                │
│  [Continue →]                    │
└─────────────────────────────────┘
```

### 🔄 Current Data Flow
```
Receives FROM/TO locations from intent
    ↓
Google Maps SDK loads
    ↓
Add markers for FROM/TO
    ↓
Calculate route (Google Directions API)
    ↓
Draw polyline on map
    ↓
Calculate distance (Haversine formula as fallback)
    ↓
User selects category
    ↓
Navigate to TruckTypesActivity or TractorMachineryTypesActivity
```

### 💾 Current Data Source
1. **Google Maps SDK** - Map display ✅
2. **Google Directions API** - Route drawing (configured)
3. **Haversine Formula** - Distance calculation (fallback)
4. **Hardcoded** - Vehicle categories

### 🔌 Backend Integration Needed

#### API 1: Route Calculation
```
POST /api/routing/calculate
{
  "fromLocation": { "lat": 32.7266, "lng": 74.8570 },
  "toLocation": { "lat": 32.7357, "lng": 74.8692 },
  "intermediateStops": []
}

Response:
{
  "distanceKm": 125,
  "durationMinutes": 180,
  "polyline": "encoded_string",
  "estimatedFuelCost": 1500,
  "tollCharges": 250
}
```

**Why**: Need server-side route optimization, toll info, traffic data

#### API 2: Vehicle Category Availability
```
GET /api/vehicles/categories/availability
  ?fromLat=32.7266&fromLng=74.8570
  &toLat=32.7357&toLng=74.8692

Response:
{
  "categories": [
    {
      "id": "truck",
      "name": "Truck",
      "availableCount": 45,
      "estimatedWaitTime": 15
    },
    ...
  ]
}
```

**Why**: Show real-time vehicle availability

### 🔧 What Needs to Change
1. **Refactor to MVVM**: Create `MapBookingViewModel`
2. **Add API service**: Route calculation API
3. **Update distance logic**: Use API instead of formula
4. **Dynamic categories**: Fetch from backend

### ⚠️ Technical Debt
- No ViewModel (uses Activity logic)
- Should separate map logic from business logic
- Consider creating `MapManager` utility class

### ✅ Status
**UI: 100% Complete**  
**Backend: 40% (Maps work, but using fallback distance)**

---

## Screen 4A: TruckTypesActivity

### 🎯 Purpose
Select specific truck type (9 different trucks) and subtypes

### 📄 Files
- **Activity**: `TruckTypesActivity.kt` (1045 lines)
- **Layout**: `res/layout/activity_truck_types.xml`
- **ViewModel**: `presentation/trucks/TruckTypesViewModel.kt`
- **Config**: `data/models/TruckConfig.kt`

### 🎨 UI Components
```
┌─────────────────────────────────┐
│  [←]  Select Truck Type         │
├─────────────────────────────────┤
│  ┌────────┐  ┌────────┐         │
│  │ 🚛 Open│  │📦 Cont │         │
│  │7.5-43T │  │7.5-30T │         │
│  └────────┘  └────────┘         │
│  ┌────────┐  ┌────────┐         │
│  │ 🚚 LCV │  │ 🛻 Mini│         │
│  │2.5-7T  │  │0.75-2T │         │
│  └────────┘  └────────┘         │
│  ... (9 truck types total)       │
├─────────────────────────────────┤
│  Selected: 14 Ton Open Truck    │
│  [View Details] [Continue →]    │
└─────────────────────────────────┘
```

### 🚛 Available Truck Types
1. **Open** - 7.5 to 43 Ton
2. **Container** - 7.5 to 30 Ton
3. **LCV** - 2.5 to 7 Ton
4. **Mini/Pickup** - 0.75 to 2 Ton
5. **Trailer** - 16 to 43 Ton
6. **Tipper** - 9 to 30 Ton
7. **Tanker** - 8 to 36 Ton
8. **Dumper** - 9 to 36 Ton
9. **Bulker** - 20 to 36 Ton

### 🔄 Current Data Flow
```
TruckTypesActivity loads
    ↓
TruckTypesViewModel.loadVehicles()
    ↓
GetAllVehiclesUseCase
    ↓
VehicleRepository.getAllVehicles()
    ↓
Check Room database
    ↓
If empty, populate with TruckConfig data
    ↓
Display trucks in grid
    ↓
User clicks truck type
    ↓
Show subtypes bottom sheet
    ↓
User selects subtype
    ↓
Calculate pricing (formula-based)
    ↓
Show pricing bottom sheet
    ↓
User confirms → Create booking
```

### 💾 Current Data Source

#### Hardcoded Configuration (`TruckConfig.kt`)
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
                TruckSubtype("7 Ton Open", "16ft x 6ft", 7, 3500),
                TruckSubtype("14 Ton Open", "20ft x 7ft", 14, 5000),
                ...
            )
        ),
        ...
    )
}
```

#### Room Database Cache
- Table: `VehicleEntity`
- Populated from `TruckConfig` on first launch
- Used for offline access

### 🔌 Backend Integration Needed

#### API 1: Get Available Vehicles
```
GET /api/vehicles/list
  ?category=truck
  &fromLat=32.7266&fromLng=74.8570
  &toLat=32.7357&toLng=74.8692
  &distanceKm=125

Response:
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
        }
      ]
    }
  ]
}
```

#### API 2: Real-time Pricing
```
POST /api/pricing/calculate
{
  "vehicleId": "open",
  "subtypeId": "open_14t",
  "fromLocation": {...},
  "toLocation": {...},
  "distanceKm": 125,
  "loadWeight": 12,
  "scheduledDate": "2026-01-05T10:00:00Z"
}

Response:
{
  "pricing": {
    "basePrice": 5000,
    "distanceCharge": 1500,
    "loadingCharges": 300,
    "subtotal": 6800,
    "gst": 1224,
    "totalPrice": 8024,
    "breakdown": {...}
  }
}
```

### 🔧 Integration Steps
1. Update `VehicleRepositoryImpl` to call API
2. Add mapper: `VehicleDto.toDomain()`
3. Keep Room for caching
4. Add pricing API call before showing bottom sheet
5. Update `TruckTypesViewModel` to handle API states

### 📦 Data Models
```kotlin
// Domain Model
data class VehicleModel(
    val id: String,
    val name: String,
    val category: VehicleCategory,
    val capacityRange: String,
    val description: String,
    val priceMultiplier: Double,
    val basePrice: Int,
    val nearbyCount: Int
)

// Current config model
data class TruckType(...)
data class TruckSubtype(...)
```

### ✅ Status
**UI: 100% Complete**  
**Backend: 0% (All data hardcoded)**  
**Priority: HIGH** - Core booking flow

---

## Screen 4B: TractorMachineryTypesActivity

### 🎯 Purpose
Select tractor or construction machinery (JCB, etc.)

### 📄 Files
- **Activity**: `TractorMachineryTypesActivity.kt` (397 lines)
- **Layout**: `res/layout/activity_tractor_machinery_types.xml`
- **Config**: `data/models/TractorMachineryConfig.kt`

### 🔄 Similar to TruckTypesActivity
Same pattern as trucks but for:
- **Tractors** (with HP specifications)
- **JCB** (excavators, loaders)
- **Construction machinery**

### 🔌 Backend Integration
Same as TruckTypesActivity - just different vehicle category

### ✅ Status
**UI: 100% Complete**  
**Backend: 0% (All data hardcoded)**

---

## Screen 5: MapSelectionActivity

### 🎯 Purpose
Select location by dragging map pin (alternative to text input)

### 📄 Files
- **Activity**: `MapSelectionActivity.kt` (161 lines)
- **Layout**: `res/layout/activity_map_selection.xml`

### 🎨 UI Components
```
┌─────────────────────────────────┐
│  [←]  Select Location           │
├─────────────────────────────────┤
│                                  │
│      🗺️ Google Map               │
│            📍                    │
│      (draggable map)             │
│                                  │
├─────────────────────────────────┤
│  📍 Lat: 32.7266, Lng: 74.8570  │
│  [Confirm Location]              │
└─────────────────────────────────┘
```

### 🔄 Current Data Flow
```
User drags map
    ↓
Map center changes
    ↓
GoogleMap.setOnCameraIdleListener()
    ↓
Get center LatLng
    ↓
Display coordinates
    ↓
User clicks Confirm
    ↓
Return location to previous screen
```

### 💾 Current Data Source
- Google Maps SDK
- Shows coordinates only (no address)

### 🔌 Backend Integration Needed

#### API: Reverse Geocoding
```
GET /api/geocoding/reverse
  ?lat=32.7266&lng=74.8570

Response:
{
  "success": true,
  "address": "Jammu Railway Station, Railway Rd, Jammu, J&K 180012",
  "city": "Jammu",
  "state": "Jammu & Kashmir",
  "pincode": "180012"
}
```

**Why**: Show human-readable address instead of coordinates

### 🔧 What Needs to Change
1. Add reverse geocoding API call
2. Update `updateAddressText()` method
3. Debounce API calls (wait 1 second after drag stops)

### ✅ Status
**UI: 100% Complete**  
**Backend: 50% (Works but shows coordinates only)**

---

## 🎯 Pricing & Booking (Bottom Sheet)

### 📄 Files
- **Layout**: `res/layout/activity_pricing.xml`
- **Shown in**: `TruckTypesActivity` as bottom sheet

### 🎨 UI Components
```
┌─────────────────────────────────┐
│  Pricing Details                 │
├─────────────────────────────────┤
│  14 Ton Open Truck               │
│  Jammu → Pathankot               │
│  Distance: 125 km                │
│                                  │
│  Base Price:        ₹5,000       │
│  Distance Charge:   ₹1,500       │
│  Loading Charges:   ₹300         │
│  ─────────────────────────       │
│  Subtotal:          ₹6,800       │
│  GST (18%):         ₹1,224       │
│  ─────────────────────────       │
│  Total:             ₹8,024       │
│                                  │
│  [Confirm Booking]               │
└─────────────────────────────────┘
```

### 🔄 Current Data Flow
```
User selects truck subtype
    ↓
Calculate price (formula)
    ↓
Show pricing bottom sheet
    ↓
User clicks Confirm
    ↓
CreateBookingUseCase
    ↓
BookingRepository.createBooking()
    ↓
Mock save (generates booking ID)
    ↓
Show success message
```

### 💾 Current Pricing Formula
```kotlin
val basePrice = 5000
val perKmRate = 12
val distanceCharge = distanceKm * perKmRate
val loadingCharges = 300
val subtotal = basePrice + distanceCharge + loadingCharges
val gst = subtotal * 0.18
val total = subtotal + gst
```

### 🔌 Backend Integration Needed
Use Pricing API (already documented above)

### ✅ Status
**UI: 100% Complete**  
**Backend: 0% (Formula-based pricing)**

---

## 📊 Summary Table

| Screen | UI Complete | Backend Integrated | Priority |
|--------|-------------|-------------------|----------|
| MainActivity | ✅ 100% | ✅ N/A | Low |
| LocationInputActivity | ✅ 100% | 🟨 30% (Places API done) | Medium |
| MapBookingActivity | ✅ 100% | 🟨 40% (Maps work) | High |
| TruckTypesActivity | ✅ 100% | ❌ 0% | **Critical** |
| TractorMachineryTypesActivity | ✅ 100% | ❌ 0% | High |
| MapSelectionActivity | ✅ 100% | 🟨 50% | Low |
| Pricing/Booking | ✅ 100% | ❌ 0% | **Critical** |

---

## 🚀 Integration Priority

### Phase 1: Core Booking Flow
1. ✅ Vehicle List API (`TruckTypesActivity`)
2. ✅ Pricing API
3. ✅ Booking Creation API
4. ✅ Route Calculation API

### Phase 2: Enhanced Features
1. User location sync
2. Vehicle availability
3. Booking history
4. Real-time tracking

### Phase 3: Optional
1. Reverse geocoding
2. Favorite locations
3. Payment integration
4. Notifications

---

**All screens are UI-complete. Just plug in the APIs!** 🎉
