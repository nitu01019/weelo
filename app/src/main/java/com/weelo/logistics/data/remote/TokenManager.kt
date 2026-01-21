package com.weelo.logistics.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token Manager - Secure storage for authentication tokens
 * 
 * SECURITY:
 * - Uses Android EncryptedSharedPreferences
 * - Tokens encrypted at rest using AES-256
 * - Master key stored in Android Keystore
 * - Tokens never logged or exposed
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_FILE_NAME = "weelo_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ROLE = "user_role"
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
        if (System.currentTimeMillis() > (expiry - 300_000)) {
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
        return System.currentTimeMillis() > (expiry - 600_000)
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
    }
}
