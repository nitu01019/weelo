# Weelo Customer App - AI Agent Guidelines

## Project Overview
Weelo is an Android logistics app (Kotlin) for booking trucks, tractors, and JCB machinery for transportation needs.

## Available Skills

### App Review and Optimization Skill
**Location**: `.weelo-skills/app-review-optimization.skill.md`

**Purpose**: Comprehensive screen-by-screen review and optimization planning

**Usage Commands**:
- `/review-screen [screen-name]` - Review a specific screen
- `/review-all-screens` - Full app review
- `/review-category [category]` - Category-specific review (ui-ux, performance, code-quality, accessibility)
- `/generate-optimization-plan [priority]` - Generate optimization plan (critical, high, medium, all)

**Screen Names for Review**:
- `splash` - Splash/Launch screen
- `login` - Authentication screen
- `main` - Home/vehicle selection
- `map-booking` - Map-based booking
- `location-input` - Location search
- `map-selection` - Pin-based selection
- `truck-types` - Truck selection
- `tractor-machinery` - Tractor/JCB selection
- `booking-request` - Booking submission
- `booking-confirmation` - Confirmation screen
- `booking-tracking` - Live tracking
- `confirm-pickup` - Pickup confirmation
- `pricing` - Price display
- `profile` - User profile

## Code Guidelines

### Architecture
- **Pattern**: MVVM with Clean Architecture layers
- **DI**: Hilt/Dagger
- **Async**: Kotlin Coroutines + Flow
- **UI State**: StateFlow/LiveData

### Key Directories
```
app/src/main/java/com/weelo/logistics/
├── core/          # Base classes, DI, utilities
├── data/          # Repositories, data sources, models
├── domain/        # Use cases, domain models, repository interfaces
├── presentation/  # Activities, ViewModels, UI components
├── adapters/      # RecyclerView adapters
├── tutorial/      # Onboarding/tutorial components
├── ui/            # Dialogs, custom views
└── utils/         # Helper utilities
```

### Before Making Changes
1. Read existing code thoroughly
2. Do NOT duplicate files or logic
3. Edit existing files instead of creating new ones
4. Ensure build passes after changes
5. Test the affected feature end-to-end

## Quick Reference

### Main User Flow
1. Splash → Login → Main (Home)
2. Main → Select Vehicle Type → Map Booking
3. Map Booking → Location Input → Truck Selection
4. Truck Selection → Booking Request → Confirmation
5. Confirmation → Tracking → Pickup Confirmation
