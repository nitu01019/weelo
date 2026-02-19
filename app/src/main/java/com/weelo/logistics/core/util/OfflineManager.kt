package com.weelo.logistics.core.util

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * =============================================================================
 * OFFLINE MANAGER - Offline Support & Data Sync
 * =============================================================================
 * 
 * Manages offline data storage and sync operations.
 * 
 * FEATURES:
 * - Queue operations when offline
 * - Auto-sync when back online
 * - Cache frequently accessed data
 * - Pending operations tracking
 * 
 * USAGE:
 * ```kotlin
 * val offlineManager = OfflineManager.getInstance(context)
 * 
 * // Queue an action for when online
 * offlineManager.queueAction(
 *     type = "CREATE_BOOKING",
 *     data = bookingJson
 * )
 * 
 * // Get cached data
 * val cachedBookings = offlineManager.getCachedBookings()
 * ```
 * =============================================================================
 */
class OfflineManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    // Pending actions queue
    private val pendingActions = ConcurrentLinkedQueue<PendingAction>()
    
    // Observable state
    private val _hasPendingActions = MutableStateFlow(false)
    val hasPendingActions: StateFlow<Boolean> = _hasPendingActions.asStateFlow()
    
    private val _pendingActionsCount = MutableStateFlow(0)
    val pendingActionsCount: StateFlow<Int> = _pendingActionsCount.asStateFlow()
    
    init {
        loadPendingActions()
    }
    
    /**
     * Pending action data class
     */
    data class PendingAction(
        val id: String = System.currentTimeMillis().toString(),
        val type: String,
        val data: String,
        val timestamp: Long = System.currentTimeMillis(),
        var retryCount: Int = 0
    )
    
    // =========================================================================
    // PENDING ACTIONS
    // =========================================================================
    
    /**
     * Queue an action to be executed when online
     */
    fun queueAction(type: String, data: String) {
        synchronized(pendingActions) {
            val action = PendingAction(type = type, data = data)
            pendingActions.add(action)
            savePendingActions()
            updateState()
        }
        Timber.d("Action queued: $type, total pending: ${pendingActions.size}")
    }
    
    /**
     * Get all pending actions
     */
    fun getPendingActions(): List<PendingAction> = pendingActions.toList()
    
    /**
     * Mark action as completed and remove from queue
     */
    fun completeAction(actionId: String) {
        synchronized(pendingActions) {
            pendingActions.removeAll { it.id == actionId }
            savePendingActions()
            updateState()
        }
        Timber.d("Action completed: $actionId")
    }
    
    /**
     * Increment retry count for failed action
     */
    fun markActionFailed(actionId: String) {
        var removed = false
        synchronized(pendingActions) {
            pendingActions.find { it.id == actionId }?.let {
                it.retryCount++
                if (it.retryCount >= MAX_RETRIES) {
                    pendingActions.remove(it)
                    removed = true
                }
            }
            savePendingActions()
            updateState()
        }
        if (removed) Timber.w("Action removed after max retries: $actionId")
    }
    
    /**
     * Clear all pending actions
     */
    fun clearPendingActions() {
        synchronized(pendingActions) {
            pendingActions.clear()
            savePendingActions()
            updateState()
        }
        Timber.d("All pending actions cleared")
    }
    
    // =========================================================================
    // DATA CACHING
    // =========================================================================
    
    /**
     * Cache user profile
     */
    fun cacheUserProfile(profileJson: String) {
        prefs.edit().putString(KEY_CACHED_PROFILE, profileJson).apply()
        prefs.edit().putLong(KEY_PROFILE_TIMESTAMP, System.currentTimeMillis()).apply()
    }
    
    /**
     * Get cached user profile
     */
    fun getCachedUserProfile(): String? {
        val timestamp = prefs.getLong(KEY_PROFILE_TIMESTAMP, 0)
        // Cache valid for 24 hours
        if (System.currentTimeMillis() - timestamp > CACHE_DURATION_MS) {
            return null
        }
        return prefs.getString(KEY_CACHED_PROFILE, null)
    }
    
    /**
     * Cache recent bookings
     */
    fun cacheBookings(bookingsJson: String) {
        prefs.edit().putString(KEY_CACHED_BOOKINGS, bookingsJson).apply()
        prefs.edit().putLong(KEY_BOOKINGS_TIMESTAMP, System.currentTimeMillis()).apply()
    }
    
    /**
     * Get cached bookings
     */
    fun getCachedBookings(): String? {
        val timestamp = prefs.getLong(KEY_BOOKINGS_TIMESTAMP, 0)
        // Cache valid for 1 hour
        if (System.currentTimeMillis() - timestamp > CACHE_DURATION_SHORT_MS) {
            return null
        }
        return prefs.getString(KEY_CACHED_BOOKINGS, null)
    }
    
    /**
     * Cache vehicle types/pricing
     */
    fun cacheVehicleTypes(vehiclesJson: String) {
        prefs.edit().putString(KEY_CACHED_VEHICLES, vehiclesJson).apply()
        prefs.edit().putLong(KEY_VEHICLES_TIMESTAMP, System.currentTimeMillis()).apply()
    }
    
    /**
     * Get cached vehicle types
     */
    fun getCachedVehicleTypes(): String? {
        val timestamp = prefs.getLong(KEY_VEHICLES_TIMESTAMP, 0)
        // Cache valid for 6 hours
        if (System.currentTimeMillis() - timestamp > CACHE_DURATION_LONG_MS) {
            return null
        }
        return prefs.getString(KEY_CACHED_VEHICLES, null)
    }
    
    /**
     * Cache recent locations
     */
    fun cacheRecentLocations(locationsJson: String) {
        prefs.edit().putString(KEY_RECENT_LOCATIONS, locationsJson).apply()
    }
    
    /**
     * Get cached recent locations
     */
    fun getCachedRecentLocations(): String? {
        return prefs.getString(KEY_RECENT_LOCATIONS, null)
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        prefs.edit()
            .remove(KEY_CACHED_PROFILE)
            .remove(KEY_PROFILE_TIMESTAMP)
            .remove(KEY_CACHED_BOOKINGS)
            .remove(KEY_BOOKINGS_TIMESTAMP)
            .remove(KEY_CACHED_VEHICLES)
            .remove(KEY_VEHICLES_TIMESTAMP)
            .apply()
        Timber.d("Cache cleared")
    }
    
    // =========================================================================
    // DRAFT BOOKINGS
    // =========================================================================
    
    /**
     * Save draft booking (in progress)
     */
    fun saveDraftBooking(draftJson: String) {
        prefs.edit().putString(KEY_DRAFT_BOOKING, draftJson).apply()
    }
    
    /**
     * Get draft booking
     */
    fun getDraftBooking(): String? {
        return prefs.getString(KEY_DRAFT_BOOKING, null)
    }
    
    /**
     * Clear draft booking
     */
    fun clearDraftBooking() {
        prefs.edit().remove(KEY_DRAFT_BOOKING).apply()
    }
    
    /**
     * Check if has draft booking
     */
    fun hasDraftBooking(): Boolean {
        return prefs.contains(KEY_DRAFT_BOOKING)
    }
    
    // =========================================================================
    // INTERNAL METHODS
    // =========================================================================
    
    private fun loadPendingActions() {
        val json = prefs.getString(KEY_PENDING_ACTIONS, null) ?: return
        try {
            val type = object : TypeToken<List<PendingAction>>() {}.type
            val actions: List<PendingAction> = gson.fromJson(json, type)
            pendingActions.addAll(actions)
            updateState()
            Timber.d("Loaded ${actions.size} pending actions")
        } catch (e: Exception) {
            Timber.e(e, "Error loading pending actions")
        }
    }
    
    private fun savePendingActions() {
        val json = gson.toJson(pendingActions.toList())
        prefs.edit().putString(KEY_PENDING_ACTIONS, json).apply()
    }
    
    private fun updateState() {
        _hasPendingActions.value = pendingActions.isNotEmpty()
        _pendingActionsCount.value = pendingActions.size
    }
    
    companion object {
        private const val PREFS_NAME = "weelo_offline_prefs"
        
        // Keys
        private const val KEY_PENDING_ACTIONS = "pending_actions"
        private const val KEY_CACHED_PROFILE = "cached_profile"
        private const val KEY_PROFILE_TIMESTAMP = "profile_timestamp"
        private const val KEY_CACHED_BOOKINGS = "cached_bookings"
        private const val KEY_BOOKINGS_TIMESTAMP = "bookings_timestamp"
        private const val KEY_CACHED_VEHICLES = "cached_vehicles"
        private const val KEY_VEHICLES_TIMESTAMP = "vehicles_timestamp"
        private const val KEY_RECENT_LOCATIONS = "recent_locations"
        private const val KEY_DRAFT_BOOKING = "draft_booking"
        
        // Cache durations
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val CACHE_DURATION_SHORT_MS = 60 * 60 * 1000L // 1 hour
        private const val CACHE_DURATION_LONG_MS = 6 * 60 * 60 * 1000L // 6 hours
        
        private const val MAX_RETRIES = 3
        
        @Volatile
        private var INSTANCE: OfflineManager? = null
        
        fun getInstance(context: Context): OfflineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
