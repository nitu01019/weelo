package com.weelo.logistics.core.util

import com.weelo.logistics.data.remote.ApiConfig

/**
 * Application-wide constants
 */
object Constants {
    
    // Network - Backend URL
    // NOW USES ApiConfig for centralized URL management!
    // To change URL, update ApiConfig.kt -> DEVICE_IP
    val BASE_URL: String get() = ApiConfig.BASE_URL
    
    // PERFORMANCE FIX: Reduced from 30s to fail faster and show retry UI sooner.
    // Backend with warm Redis responds in <2s. If it takes >15s, something is wrong
    // and the circuit breaker / retry mechanism should kick in.
    const val CONNECT_TIMEOUT = 15L   // was 30L — TCP connect should be instant
    const val READ_TIMEOUT = 20L      // was 30L — API responses should be <5s
    const val WRITE_TIMEOUT = 15L     // was 30L — request upload is small
    
    // Location
    const val DEFAULT_LOCATION_LAT = 32.7266
    const val DEFAULT_LOCATION_LNG = 74.8570
    const val DEFAULT_CITY = "Jammu"
    const val MAX_RECENT_LOCATIONS = 10
    
    // Pricing
    const val GST_RATE = 0.18
    const val MIN_DISTANCE_KM = 1
    const val MAX_DISTANCE_KM = 10000
    
    // Validation
    const val MIN_LOCATION_LENGTH = 2
    const val MAX_LOCATION_LENGTH = 100
    
    // Database
    const val DATABASE_NAME = "weelo_database"
    const val DATABASE_VERSION = 1
    
    // Preferences
    const val PREFS_NAME = "weelo_preferences"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_TOKEN = "user_token"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
}
