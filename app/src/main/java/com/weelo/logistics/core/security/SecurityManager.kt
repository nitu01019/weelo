package com.weelo.logistics.core.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * SECURITY MANAGER - Root Detection & App Integrity
 * =============================================================================
 * 
 * Provides security checks for:
 * - Root detection (using RootBeer library)
 * - App tampering detection
 * - Security warnings for users
 * 
 * USAGE:
 * - Check at app startup in WeeloApplication
 * - Warn users on rooted devices (don't block - reduces UX for power users)
 * 
 * =============================================================================
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val rootBeer = RootBeer(context)
    
    /**
     * Check if device is rooted
     * Uses multiple detection methods via RootBeer
     */
    fun isDeviceRooted(): Boolean {
        return try {
            rootBeer.isRooted
        } catch (e: Exception) {
            Timber.e(e, "Error checking root status")
            false // Assume not rooted if check fails
        }
    }
    
    /**
     * Check if device is rooted with extensive checks
     * More thorough but slightly slower
     */
    fun isDeviceRootedWithBusyBox(): Boolean {
        return try {
            rootBeer.isRootedWithBusyBoxCheck
        } catch (e: Exception) {
            Timber.e(e, "Error checking root status with BusyBox")
            false
        }
    }
    
    /**
     * Get security status for logging/analytics
     * Does NOT include sensitive information
     */
    fun getSecurityStatus(): SecurityStatus {
        return SecurityStatus(
            isRooted = isDeviceRooted(),
            hasRootManagementApps = rootBeer.detectRootManagementApps(),
            hasPotentiallyDangerousApps = rootBeer.detectPotentiallyDangerousApps(),
            hasTestKeys = rootBeer.detectTestKeys()
        )
    }
    
    /**
     * Log security status at app startup (no sensitive data)
     */
    fun logSecurityStatus() {
        val status = getSecurityStatus()
        if (status.hasSecurityConcerns()) {
            Timber.w("⚠️ Security concerns detected: ${status.getSummary()}")
        } else {
            Timber.i("✅ Device security check passed")
        }
    }
    
    data class SecurityStatus(
        val isRooted: Boolean,
        val hasRootManagementApps: Boolean,
        val hasPotentiallyDangerousApps: Boolean,
        val hasTestKeys: Boolean
    ) {
        fun hasSecurityConcerns(): Boolean {
            return isRooted || hasRootManagementApps || hasPotentiallyDangerousApps || hasTestKeys
        }
        
        fun getSummary(): String {
            val concerns = mutableListOf<String>()
            if (isRooted) concerns.add("rooted")
            if (hasRootManagementApps) concerns.add("root_apps")
            if (hasPotentiallyDangerousApps) concerns.add("dangerous_apps")
            if (hasTestKeys) concerns.add("test_keys")
            return concerns.joinToString(", ")
        }
    }
}
