package com.weelo.logistics.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weelo.logistics.data.remote.api.PlaceResult
import timber.log.Timber

/**
 * =============================================================================
 * LOCATION CACHE SERVICE - Production-Grade Location Caching
 * =============================================================================
 * 
 * CRITICAL FOR PERFORMANCE & OFFLINE SUPPORT:
 * - Caches Google Places API results to reduce API calls
 * - Stores recent searches for instant autocomplete
 * - Provides offline fallback when network unavailable
 * - TTL-based cache invalidation
 * 
 * SCALABILITY:
 * - LRU cache strategy (keeps most recent/frequent)
 * - Memory-efficient with size limits
 * - Fast lookup with HashMap-based storage
 * 
 * MODULARITY:
 * - Singleton pattern for app-wide access
 * - Clear separation from LocationManager
 * - Easy to swap SharedPreferences with Room/SQLite
 * 
 * USAGE:
 * ```kotlin
 * val cache = LocationCacheService.getInstance(context)
 * 
 * // Cache search results
 * cache.cacheSearchResults("Jammu", listOf(place1, place2))
 * 
 * // Retrieve from cache
 * val cached = cache.getCachedSearchResults("Jammu")
 * 
 * // Cache single place
 * cache.cachePlaceDetails("place_id_123", placeResult)
 * ```
 * 
 * @author Weelo Team
 * @version 2.0.0
 * =============================================================================
 */
class LocationCacheService private constructor(context: Context) {

    // SECURITY: Use EncryptedSharedPreferences to protect cached location data at rest
    private val prefs: SharedPreferences = try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS_NAME + "_encrypted",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create encrypted prefs, falling back to standard")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "weelo_location_cache"
        private const val KEY_SEARCH_CACHE = "search_cache"
        private const val KEY_PLACE_CACHE = "place_cache"
        private const val KEY_CURRENT_LOCATION_CACHE = "current_location_cache"
        
        // Cache configuration
        private const val MAX_SEARCH_CACHE_SIZE = 50 // Store max 50 search queries
        private const val MAX_PLACE_CACHE_SIZE = 100 // Store max 100 place details
        private const val CACHE_TTL_HOURS = 24 // Cache valid for 24 hours
        
        @Volatile
        private var instance: LocationCacheService? = null
        
        fun getInstance(context: Context): LocationCacheService {
            return instance ?: synchronized(this) {
                instance ?: LocationCacheService(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    // =========================================================================
    // DATA CLASSES
    // =========================================================================
    
    /**
     * Cached search result with timestamp
     */
    data class CachedSearchResult(
        val query: String,
        val results: List<PlaceResult>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            val maxAge = CACHE_TTL_HOURS * 60 * 60 * 1000
            return age < maxAge
        }
    }
    
    /**
     * Cached place details with timestamp
     */
    data class CachedPlace(
        val placeId: String,
        val place: PlaceResult,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            val maxAge = CACHE_TTL_HOURS * 60 * 60 * 1000
            return age < maxAge
        }
    }
    
    // =========================================================================
    // SEARCH RESULTS CACHING
    // =========================================================================
    
    /**
     * Cache search results for a query
     * Uses LRU strategy - removes oldest if limit exceeded
     */
    fun cacheSearchResults(query: String, results: List<PlaceResult>) {
        try {
            val searchCache = getSearchCache().toMutableMap()
            
            // Add new entry
            searchCache[query.lowercase()] = CachedSearchResult(
                query = query,
                results = results
            )
            
            // Apply LRU - keep only most recent entries
            if (searchCache.size > MAX_SEARCH_CACHE_SIZE) {
                val sortedEntries = searchCache.entries
                    .sortedByDescending { it.value.timestamp }
                    .take(MAX_SEARCH_CACHE_SIZE)
                searchCache.clear()
                searchCache.putAll(sortedEntries.associate { it.key to it.value })
            }
            
            // Save to preferences
            val json = gson.toJson(searchCache)
            prefs.edit().putString(KEY_SEARCH_CACHE, json).apply()
            
            Timber.d("Cached search results for: $query (${results.size} results)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache search results")
        }
    }
    
    /**
     * Get cached search results for a query
     * Returns null if not found or expired
     */
    fun getCachedSearchResults(query: String): List<PlaceResult>? {
        try {
            val searchCache = getSearchCache()
            val cached = searchCache[query.lowercase()] ?: return null
            
            return if (cached.isValid()) {
                Timber.d("Cache HIT for query: $query (${cached.results.size} results)")
                cached.results
            } else {
                Timber.d("Cache EXPIRED for query: $query")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached search results")
            return null
        }
    }
    
    /**
     * Get all cached searches
     */
    private fun getSearchCache(): Map<String, CachedSearchResult> {
        val json = prefs.getString(KEY_SEARCH_CACHE, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, CachedSearchResult>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse search cache")
            emptyMap()
        }
    }
    
    // =========================================================================
    // PLACE DETAILS CACHING
    // =========================================================================
    
    /**
     * Cache place details by placeId
     */
    fun cachePlaceDetails(placeId: String, place: PlaceResult) {
        try {
            val placeCache = getPlaceCache().toMutableMap()
            
            // Add new entry
            placeCache[placeId] = CachedPlace(
                placeId = placeId,
                place = place
            )
            
            // Apply LRU
            if (placeCache.size > MAX_PLACE_CACHE_SIZE) {
                val sortedEntries = placeCache.entries
                    .sortedByDescending { it.value.timestamp }
                    .take(MAX_PLACE_CACHE_SIZE)
                placeCache.clear()
                placeCache.putAll(sortedEntries.associate { it.key to it.value })
            }
            
            // Save to preferences
            val json = gson.toJson(placeCache)
            prefs.edit().putString(KEY_PLACE_CACHE, json).apply()
            
            Timber.d("Cached place details for: $placeId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache place details")
        }
    }
    
    /**
     * Get cached place details by placeId
     */
    fun getCachedPlaceDetails(placeId: String): PlaceResult? {
        try {
            val placeCache = getPlaceCache()
            val cached = placeCache[placeId] ?: return null
            
            return if (cached.isValid()) {
                Timber.d("Cache HIT for place: $placeId")
                cached.place
            } else {
                Timber.d("Cache EXPIRED for place: $placeId")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached place details")
            return null
        }
    }
    
    /**
     * Get all cached places
     */
    private fun getPlaceCache(): Map<String, CachedPlace> {
        val json = prefs.getString(KEY_PLACE_CACHE, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, CachedPlace>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse place cache")
            emptyMap()
        }
    }
    
    // =========================================================================
    // CURRENT LOCATION CACHING
    // =========================================================================
    
    /**
     * Cache user's current location (for faster subsequent loads)
     */
    fun cacheCurrentLocation(place: PlaceResult) {
        try {
            val cached = CachedPlace(
                placeId = "current_location",
                place = place
            )
            val json = gson.toJson(cached)
            prefs.edit().putString(KEY_CURRENT_LOCATION_CACHE, json).apply()
            Timber.d("Cached current location: ${place.label}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache current location")
        }
    }
    
    /**
     * Get cached current location
     * Returns null if not found or expired (older than 1 hour)
     */
    fun getCachedCurrentLocation(): PlaceResult? {
        try {
            val json = prefs.getString(KEY_CURRENT_LOCATION_CACHE, null) ?: return null
            val type = object : TypeToken<CachedPlace>() {}.type
            val cached: CachedPlace = gson.fromJson(json, type) ?: return null
            
            // Current location cache expires faster (1 hour)
            val age = System.currentTimeMillis() - cached.timestamp
            val maxAge = 1 * 60 * 60 * 1000 // 1 hour
            
            return if (age < maxAge) {
                Timber.d("Cache HIT for current location")
                cached.place
            } else {
                Timber.d("Current location cache EXPIRED")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached current location")
            return null
        }
    }
    
    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    
    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        prefs.edit()
            .remove(KEY_SEARCH_CACHE)
            .remove(KEY_PLACE_CACHE)
            .remove(KEY_CURRENT_LOCATION_CACHE)
            .apply()
        Timber.d("All location caches cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val searchCache = getSearchCache()
        val placeCache = getPlaceCache()
        
        return CacheStats(
            searchCacheSize = searchCache.size,
            placeCacheSize = placeCache.size,
            hasCurrentLocation = getCachedCurrentLocation() != null
        )
    }
    
    data class CacheStats(
        val searchCacheSize: Int,
        val placeCacheSize: Int,
        val hasCurrentLocation: Boolean
    )
}
