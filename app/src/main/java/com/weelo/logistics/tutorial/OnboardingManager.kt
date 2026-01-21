package com.weelo.logistics.tutorial

import android.content.Context
import android.content.SharedPreferences

/**
 * OnboardingManager - Manages first-time user tutorial state
 * 
 * Handles checking if user has seen the tutorial before
 * Saves completion state to prevent repeated tutorials
 * 
 * Supports multiple tutorial screens:
 * - Home/Main screen tutorial
 * - Location input tutorial
 * - Truck selection tutorial
 * 
 * Modular design - can be easily removed if tutorial feature is removed
 */
class OnboardingManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "weelo_onboarding"
        private const val KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED = "truck_selection_tutorial_completed"
        private const val KEY_HOME_TUTORIAL_COMPLETED = "home_tutorial_completed"
        private const val KEY_LOCATION_INPUT_TUTORIAL_COMPLETED = "location_input_tutorial_completed"
        private const val KEY_MAP_BOOKING_TUTORIAL_COMPLETED = "map_booking_tutorial_completed"
        private const val KEY_FIRST_TIME_USER = "is_first_time_user"
        
        // Singleton instance
        @Volatile
        private var instance: OnboardingManager? = null
        
        fun getInstance(context: Context): OnboardingManager {
            return instance ?: synchronized(this) {
                instance ?: OnboardingManager(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    // ========================================
    // First Time User Check
    // ========================================
    
    /**
     * Check if this is the user's first time using the app
     */
    fun isFirstTimeUser(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_TIME_USER, true)
    }
    
    /**
     * Mark that user has completed initial onboarding
     */
    fun markOnboardingComplete() {
        sharedPreferences.edit()
            .putBoolean(KEY_FIRST_TIME_USER, false)
            .apply()
    }
    
    // ========================================
    // Home Tutorial (MainActivity)
    // ========================================
    
    /**
     * Check if home tutorial has been completed
     */
    fun isHomeTutorialCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_HOME_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Mark home tutorial as completed
     */
    fun markHomeTutorialCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_HOME_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    // ========================================
    // Location Input Tutorial
    // ========================================
    
    /**
     * Check if location input tutorial has been completed
     */
    fun isLocationInputTutorialCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_LOCATION_INPUT_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Mark location input tutorial as completed
     */
    fun markLocationInputTutorialCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_LOCATION_INPUT_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    // ========================================
    // Map Booking Tutorial
    // ========================================
    
    /**
     * Check if map booking tutorial has been completed
     */
    fun isMapBookingTutorialCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_MAP_BOOKING_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Mark map booking tutorial as completed
     */
    fun markMapBookingTutorialCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_MAP_BOOKING_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    // ========================================
    // Truck Selection Tutorial
    // ========================================
    
    /**
     * Check if truck selection tutorial has been completed
     * @return true if tutorial was already shown, false if first time
     */
    fun isTruckSelectionTutorialCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Mark truck selection tutorial as completed
     * Prevents tutorial from showing again
     */
    fun markTruckSelectionTutorialCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    // ========================================
    // Reset Functions (for testing)
    // ========================================
    
    /**
     * Reset truck selection tutorial state (for testing purposes)
     * Call this to see tutorial again
     */
    fun resetTutorial() {
        sharedPreferences.edit()
            .putBoolean(KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED, false)
            .apply()
    }
    
    /**
     * Reset all tutorials (for testing)
     */
    fun resetAllTutorials() {
        sharedPreferences.edit()
            .putBoolean(KEY_HOME_TUTORIAL_COMPLETED, false)
            .putBoolean(KEY_LOCATION_INPUT_TUTORIAL_COMPLETED, false)
            .putBoolean(KEY_MAP_BOOKING_TUTORIAL_COMPLETED, false)
            .putBoolean(KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED, false)
            .putBoolean(KEY_FIRST_TIME_USER, true)
            .apply()
    }
    
    /**
     * Clear all onboarding data
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
