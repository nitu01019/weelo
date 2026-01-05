# Weelo - Native Android Kotlin App

Weelo is a logistics vehicle booking application built entirely in **Kotlin** for Android.

## Features

- 🏠 **Home Screen**: Search and view nearby vehicles (Trucks, Tractors, Tempos)
- 📍 **Location Input**: Enter pickup and drop locations
- 🗺️ **Map View**: Google Maps integration with markers
- 🚚 **Truck Types**: Select from 9 different truck types
- 💰 **Pricing**: Detailed price breakdown with GST
- ✅ **Booking Confirmation**: Complete booking flow

## Tech Stack

- **Language**: Kotlin 100%
- **Maps**: Google Maps SDK
- **UI**: Native Android Views + Material Design
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Build Instructions

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Building Debug APK

```bash
cd android_kotlin
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on Device

```bash
./gradlew installDebug
```

Or manually:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── java/com/weelo/logistics/
│   ├── MainActivity.kt              # Home Screen
│   ├── LocationInputActivity.kt     # Location Input Screen
│   ├── MapBookingActivity.kt        # Map & Category Selection
│   ├── TruckTypesActivity.kt        # Truck Types Grid
│   └── PricingActivity.kt           # Pricing & Booking
├── res/
│   ├── layout/                      # XML Layouts
│   ├── values/                      # Colors, Strings, Themes
│   └── drawable/                    # Icons & Backgrounds
└── AndroidManifest.xml
```

## Package Details

- **Package Name**: com.weelo.logistics
- **Version**: 1.0.1 (versionCode: 2)
- **Google Maps API Key**: Configured in AndroidManifest.xml

## Permissions

- `ACCESS_FINE_LOCATION` - For map and location services
- `ACCESS_COARSE_LOCATION` - For approximate location
- `INTERNET` - For map tiles and API calls

## Branding

- **Primary Color**: #FF6B35 (Weelo Orange)
- **Made for**: India 🇮🇳
- **Developed in**: Jammu ❤️

---

**Note**: This is a complete rewrite from React Native to native Kotlin. All functionality has been preserved and enhanced with native Android performance.
