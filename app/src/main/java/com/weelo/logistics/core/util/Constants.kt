package com.weelo.logistics.core.util

/**
 * Application-wide constants
 */
object Constants {
    
    // Network
    const val BASE_URL = "https://api.weelo.in/v1/"
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
    
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
