package com.weelo.logistics.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.core.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException

/**
 * =============================================================================
 * PREFERENCES MANAGER - Secure Local Storage with DataStore
 * =============================================================================
 * 
 * SCALABILITY:
 * - Async DataStore (non-blocking)
 * - Handles millions of concurrent reads/writes
 * - Type-safe preference keys
 * 
 * EASY UNDERSTANDING:
 * - Simple save/get methods
 * - Flow-based reactive data
 * 
 * MODULARITY:
 * - Centralized preference storage
 * - Account containerization (clear on logout)
 * 
 * CODING STANDARDS:
 * - Proper error handling
 * - Kotlin Flow for reactive data
 * - Clear method names
 * =============================================================================
 */
class PreferencesManager(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = Constants.PREFS_NAME
    )

    companion object {
        // Authentication
        private val USER_ID = stringPreferencesKey(Constants.KEY_USER_ID)
        private val USER_TOKEN = stringPreferencesKey(Constants.KEY_USER_TOKEN)
        private val IS_LOGGED_IN = booleanPreferencesKey(Constants.KEY_IS_LOGGED_IN)
        
        // SCALABILITY: Cached profile data (no repeated API calls)
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_PHONE = stringPreferencesKey("user_phone")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_ROLE = stringPreferencesKey("user_role")
        
        // Cache timestamp for TTL
        private val PROFILE_CACHE_TIME = longPreferencesKey("profile_cache_time")
        
        // EASY UNDERSTANDING: Cache TTL = 24 hours (profile data doesn't change often)
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Save user ID
     */
    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = userId
        }
    }

    /**
     * Get user ID
     */
    val userId: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_ID]
        }

    /**
     * Save user token
     */
    suspend fun saveUserToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_TOKEN] = token
        }
    }

    /**
     * Get user token
     */
    val userToken: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_TOKEN]
        }

    /**
     * Set login status
     */
    suspend fun setLoggedIn(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = isLoggedIn
        }
    }

    /**
     * Check if user is logged in
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_LOGGED_IN] ?: false
        }

    /**
     * Clear all preferences (ACCOUNT CONTAINERIZATION)
     * 
     * SCALABILITY: Ensures no data mixing between accounts
     * EASY UNDERSTANDING: Called on logout to clear all user data
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        Timber.i("All preferences cleared - account containerization")
    }
    
    // =========================================================================
    // PROFILE CACHING (Instagram-style - save locally, no repeated fetches)
    // =========================================================================
    
    /**
     * Save user profile to cache
     * 
     * SCALABILITY: No repeated API calls on app restart
     * ACCOUNT CONTAINERIZATION: Profile tied to userId
     */
    suspend fun saveProfile(name: String, phone: String, email: String?, role: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
            preferences[USER_PHONE] = phone
            preferences[USER_EMAIL] = email ?: ""
            preferences[USER_ROLE] = role
            preferences[PROFILE_CACHE_TIME] = System.currentTimeMillis()
        }
        Timber.d("Profile cached locally: $name, $phone")
    }
    
    /**
     * Get cached user name
     */
    val userName: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading user name")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_NAME]
        }
    
    /**
     * Get cached user phone
     */
    val userPhone: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading user phone")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_PHONE]
        }
    
    /**
     * Get cached user email
     */
    val userEmail: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading user email")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_EMAIL]
        }
    
    /**
     * Get cached user role
     */
    val userRole: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading user role")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_ROLE]
        }
    
    /**
     * Check if profile cache is still valid (TTL check)
     * 
     * EASY UNDERSTANDING: Returns true if cached profile is fresh (< 24 hours old)
     */
    suspend fun isProfileCacheValid(): Boolean {
        val cacheTime = context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PROFILE_CACHE_TIME] ?: 0L
            }
            .first()
        
        val isValid = (System.currentTimeMillis() - cacheTime) < CACHE_TTL_MS
        Timber.d("Profile cache valid: $isValid (age: ${System.currentTimeMillis() - cacheTime}ms)")
        return isValid
    }
    
    /**
     * Get cached profile as a single suspend function
     * 
     * SCALABILITY: Returns immediately from cache (no network)
     */
    suspend fun getCachedProfile(): CachedProfile? {
        return try {
            val prefs = context.dataStore.data.first()
            val name = prefs[USER_NAME]
            val phone = prefs[USER_PHONE]
            val email = prefs[USER_EMAIL]
            val role = prefs[USER_ROLE]
            
            if (name != null && phone != null) {
                CachedProfile(name, phone, email, role)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting cached profile")
            null
        }
    }
}

/**
 * Data class for cached profile
 * 
 * MODULARITY: Simple data holder
 */
data class CachedProfile(
    val name: String,
    val phone: String,
    val email: String?,
    val role: String?
)
