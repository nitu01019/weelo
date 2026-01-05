package com.weelo.logistics.data.managers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.RecentLocation

/**
 * LocationManager - Manages recent locations and favorites
 * Provides persistence using SharedPreferences
 */
class LocationManager private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "weelo_locations"
        private const val KEY_RECENT_LOCATIONS = "recent_locations"
        private const val MAX_RECENT_LOCATIONS = 10
        
        @Volatile
        private var instance: LocationManager? = null
        
        fun getInstance(context: Context): LocationManager {
            return instance ?: synchronized(this) {
                instance ?: LocationManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Add a location to recent history
     */
    fun addRecentLocation(location: Location) {
        val recentLocations = getRecentLocations().toMutableList()
        
        // Remove duplicate if exists
        recentLocations.removeAll { it.location.address == location.address }
        
        // Add new location at the beginning
        recentLocations.add(0, RecentLocation(location))
        
        // Keep only MAX_RECENT_LOCATIONS
        val trimmedList = recentLocations.take(MAX_RECENT_LOCATIONS)
        
        // Save to preferences
        val json = gson.toJson(trimmedList)
        prefs.edit().putString(KEY_RECENT_LOCATIONS, json).apply()
    }
    
    /**
     * Get all recent locations
     */
    fun getRecentLocations(): List<RecentLocation> {
        val json = prefs.getString(KEY_RECENT_LOCATIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<RecentLocation>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Toggle favorite status for a location
     */
    fun toggleFavorite(location: Location): Boolean {
        val recentLocations = getRecentLocations().toMutableList()
        val index = recentLocations.indexOfFirst { it.location.address == location.address }
        
        if (index != -1) {
            val updated = recentLocations[index].copy(isFavorite = !recentLocations[index].isFavorite)
            recentLocations[index] = updated
            
            val json = gson.toJson(recentLocations)
            prefs.edit().putString(KEY_RECENT_LOCATIONS, json).apply()
            
            return updated.isFavorite
        }
        return false
    }
    
    /**
     * Clear all recent locations
     */
    fun clearRecentLocations() {
        prefs.edit().remove(KEY_RECENT_LOCATIONS).apply()
    }
}
