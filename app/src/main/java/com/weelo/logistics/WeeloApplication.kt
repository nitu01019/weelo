package com.weelo.logistics

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.weelo.logistics.core.security.SecurityManager
import com.weelo.logistics.core.util.OfflineHandler
import com.weelo.logistics.core.util.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application class for Weelo
 * Entry point for dependency injection and app-level initialization
 * 
 * INITIALIZATION ORDER:
 * 1. Timber logging (with Crashlytics in release)
 * 2. Security checks (root detection)
 * 3. Theme manager (dark/light mode)
 * 4. Offline handler (network monitoring)
 * 5. Google Places API
 * 6. Google Maps pre-initialization
 */
@HiltAndroidApp
class WeeloApplication : MultiDexApplication() {

    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging (with Crashlytics in production)
        initializeLogging()
        
        // Initialize Security (root detection, app integrity)
        initializeSecurity()
        
        // Initialize Theme Manager (dark mode support)
        initializeTheme()
        
        // Initialize Offline Handler (network monitoring)
        initializeOfflineHandler()
        
        // Initialize Google Places API
        initializePlacesApi()
        
        // Pre-initialize Google Maps for faster loading
        preInitializeMaps()
        
        Timber.d("Weelo Application initialized successfully")
    }
    
    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release: Log to Crashlytics (errors/warnings only)
            Timber.plant(CrashlyticsTree())
        }
    }
    
    private fun initializeSecurity() {
        try {
            // Run security checks asynchronously to not block startup
            securityManager.logSecurityStatus()
            
            // Set Crashlytics user properties (no PII)
            val status = securityManager.getSecurityStatus()
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("is_rooted", status.isRooted)
                setCustomKey("has_security_concerns", status.hasSecurityConcerns())
            }
            
            Timber.d("Security checks completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize security")
        }
    }
    
    private fun initializeTheme() {
        try {
            // =====================================================
            // CRITICAL: Force LIGHT mode BEFORE ThemeManager
            // =====================================================
            // This runs BEFORE any Activity window is drawn.
            // Prevents splash screen (and all screens) from dimming
            // in the evening when system switches to dark mode.
            //
            // SCALABILITY: One-time call at app level, affects all activities
            // EASY UNDERSTANDING: App ALWAYS uses light theme - no dark mode
            // =====================================================
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            
            ThemeManager.initialize(this)
            ThemeManager.applyTheme()
            Timber.d("Theme manager initialized - forced LIGHT mode")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize theme manager")
            // Fallback: Ensure light mode even if ThemeManager fails
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun initializeOfflineHandler() {
        try {
            OfflineHandler.initialize(this)
            Timber.d("Offline handler initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize offline handler")
        }
    }
    
    private fun initializePlacesApi() {
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            com.weelo.logistics.core.util.PlacesHelper.initialize(this, apiKey)
            Timber.d("Places API initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Places API")
        }
    }
    
    private fun preInitializeMaps() {
        try {
            com.weelo.logistics.presentation.booking.ConfirmPickupActivity.preInitializeMaps(this)
            Timber.d("Google Maps pre-initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to pre-initialize Google Maps")
        }
    }
    
    /**
     * Custom Timber tree for production
     * Logs errors and warnings to Firebase Crashlytics
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR || priority == Log.WARN) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.log("${tag ?: "Weelo"}: $message")
                t?.let { crashlytics.recordException(it) }
            }
        }
    }
}

