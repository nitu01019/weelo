package com.weelo.logistics.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * TOKEN MANAGER - Secure & Thread-Safe Token Storage
 * =============================================================================
 * 
 * SECURITY:
 * - Uses Android EncryptedSharedPreferences
 * - Tokens encrypted at rest using AES-256
 * - Master key stored in Android Keystore
 * - Tokens never logged or exposed
 * 
 * THREAD SAFETY:
 * - Mutex-protected token refresh to prevent race conditions
 * - Rate limiting to prevent excessive refresh attempts
 * - Atomic flags for concurrent access control
 * 
 * =============================================================================
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_FILE_NAME = "weelo_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_LAST_REFRESH_ATTEMPT = "last_refresh_attempt"
        
        // Rate limiting: minimum 30 seconds between refresh attempts
        private const val MIN_REFRESH_INTERVAL_MS = 30_000L
        
        // Token expiry buffer: refresh 5 minutes before expiry
        private const val EXPIRY_BUFFER_MS = 300_000L
        
        // Proactive refresh: start refreshing 10 minutes before expiry
        private const val PROACTIVE_REFRESH_BUFFER_MS = 600_000L
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // Thread safety: Mutex for token refresh operations
    private val refreshMutex = Mutex()
    
    // Flag to indicate refresh is in progress
    private val isRefreshing = AtomicBoolean(false)
    
    // Timestamp of last refresh attempt (for rate limiting)
    private val lastRefreshAttempt = AtomicLong(0)

    /**
     * Save authentication tokens securely
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Int
    ) {
        val expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
        
        Timber.d("$TAG: Tokens saved, expires in ${expiresInSeconds}s")
    }

    /**
     * Save user info securely
     */
    fun saveUserInfo(userId: String, phone: String, role: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_PHONE, phone)
            .putString(KEY_USER_ROLE, role)
            .apply()
        
        Timber.d("$TAG: User info saved for $userId")
    }

    /**
     * Get access token
     * Returns null if not present or expired (with 5 min buffer)
     */
    fun getAccessToken(): String? {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        if (token.isNullOrBlank()) {
            return null
        }
        
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        // Check if token is expired (with 5 min buffer)
        if (System.currentTimeMillis() > (expiry - EXPIRY_BUFFER_MS)) {
            Timber.d("$TAG: Token expired or near expiry")
            return null
        }
        
        return token
    }
    
    /**
     * Get access token without expiry check
     * Used internally for refresh logic
     */
    fun getAccessTokenRaw(): String? {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Get refresh token
     */
    fun getRefreshToken(): String? {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }

    /**
     * Check if token needs refresh (within 10 min of expiry)
     */
    fun needsTokenRefresh(): Boolean {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() > (expiry - PROACTIVE_REFRESH_BUFFER_MS)
    }
    
    /**
     * Check if token is completely expired (no buffer)
     */
    fun isTokenExpired(): Boolean {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() > expiry
    }
    
    /**
     * Get time until token expires (in milliseconds)
     */
    fun getTimeUntilExpiry(): Long {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return maxOf(0, expiry - System.currentTimeMillis())
    }
    
    // =========================================================================
    // THREAD-SAFE TOKEN REFRESH
    // =========================================================================
    
    /**
     * Check if refresh is allowed (rate limiting)
     */
    fun canAttemptRefresh(): Boolean {
        val timeSinceLastAttempt = System.currentTimeMillis() - lastRefreshAttempt.get()
        val canRefresh = timeSinceLastAttempt >= MIN_REFRESH_INTERVAL_MS
        
        if (!canRefresh) {
            Timber.d("$TAG: Refresh rate limited, ${MIN_REFRESH_INTERVAL_MS - timeSinceLastAttempt}ms remaining")
        }
        
        return canRefresh
    }
    
    /**
     * Mark that a refresh attempt is starting
     * Returns false if another refresh is already in progress
     */
    fun startRefreshAttempt(): Boolean {
        if (!canAttemptRefresh()) {
            return false
        }
        
        val started = isRefreshing.compareAndSet(false, true)
        if (started) {
            lastRefreshAttempt.set(System.currentTimeMillis())
            Timber.d("$TAG: Refresh attempt started")
        } else {
            Timber.d("$TAG: Refresh already in progress")
        }
        
        return started
    }
    
    /**
     * Mark that a refresh attempt has completed
     */
    fun endRefreshAttempt() {
        isRefreshing.set(false)
        Timber.d("$TAG: Refresh attempt ended")
    }
    
    /**
     * Check if refresh is currently in progress
     */
    fun isRefreshInProgress(): Boolean {
        return isRefreshing.get()
    }
    
    /**
     * Execute a token refresh operation with mutex protection
     * Ensures only one refresh happens at a time across all threads
     */
    suspend fun <T> withRefreshLock(block: suspend () -> T): T {
        return refreshMutex.withLock {
            try {
                block()
            } finally {
                endRefreshAttempt()
            }
        }
    }

    /**
     * Get user ID
     */
    fun getUserId(): String? {
        return encryptedPrefs.getString(KEY_USER_ID, null)
    }

    /**
     * Get user phone
     */
    fun getUserPhone(): String? {
        return encryptedPrefs.getString(KEY_USER_PHONE, null)
    }

    /**
     * Get user role
     */
    fun getUserRole(): String? {
        return encryptedPrefs.getString(KEY_USER_ROLE, null)
    }

    /**
     * Clear all tokens (logout)
     */
    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_ROLE)
            .apply()
        
        // Reset refresh state
        isRefreshing.set(false)
        lastRefreshAttempt.set(0)
        
        Timber.d("$TAG: All tokens cleared")
    }
    
    /**
     * Get token status for debugging
     */
    @Suppress("UNUSED_VARIABLE") // expiry kept for debugging purposes
    fun getTokenStatus(): TokenStatus {
        val hasAccessToken = !getAccessTokenRaw().isNullOrBlank()
        val hasRefreshToken = !getRefreshToken().isNullOrBlank()
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        return TokenStatus(
            hasAccessToken = hasAccessToken,
            hasRefreshToken = hasRefreshToken,
            isExpired = isTokenExpired(),
            needsRefresh = needsTokenRefresh(),
            timeUntilExpiry = getTimeUntilExpiry(),
            isRefreshing = isRefreshing.get(),
            canAttemptRefresh = canAttemptRefresh()
        )
    }
}

/**
 * Token status for monitoring/debugging
 */
data class TokenStatus(
    val hasAccessToken: Boolean,
    val hasRefreshToken: Boolean,
    val isExpired: Boolean,
    val needsRefresh: Boolean,
    val timeUntilExpiry: Long,
    val isRefreshing: Boolean,
    val canAttemptRefresh: Boolean
)
