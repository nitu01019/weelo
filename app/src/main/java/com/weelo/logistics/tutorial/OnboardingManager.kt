package com.weelo.logistics.tutorial

import android.content.Context
import android.content.SharedPreferences

/**
 * OnboardingManager - Manages first-time user tutorial state
 * 
 * Handles checking if user has seen the tutorial before
 * Saves completion state to prevent repeated tutorials
 * 
 * Modular design - can be easily removed if tutorial feature is removed
 */
class OnboardingManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "weelo_onboarding"
        private const val KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED = "truck_selection_tutorial_completed"
        
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
    
    /**
     * Reset tutorial state (for testing purposes)
     * Call this to see tutorial again
     */
    fun resetTutorial() {
        sharedPreferences.edit()
            .putBoolean(KEY_TRUCK_SELECTION_TUTORIAL_COMPLETED, false)
            .apply()
    }
    
    /**
     * Clear all onboarding data
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
