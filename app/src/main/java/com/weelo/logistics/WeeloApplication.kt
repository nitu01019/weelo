package com.weelo.logistics

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import com.weelo.logistics.core.util.OfflineHandler
import com.weelo.logistics.core.util.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for Weelo
 * Entry point for dependency injection and app-level initialization
 * 
 * INITIALIZATION ORDER:
 * 1. Timber logging
 * 2. Theme manager (dark/light mode)
 * 3. Offline handler (network monitoring)
 * 4. Google Places API
 * 5. Google Maps pre-initialization
 */
@HiltAndroidApp
class WeeloApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        initializeLogging()
        
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
            Timber.plant(ReleaseTree())
        }
    }
    
    private fun initializeTheme() {
        try {
            ThemeManager.initialize(this)
            ThemeManager.applyTheme()
            Timber.d("Theme manager initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize theme manager")
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
     * Logs only warnings and errors to crash reporting
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.ERROR || priority == android.util.Log.WARN) {
                // Send to Crashlytics or other crash reporting service
                // Example: FirebaseCrashlytics.getInstance().recordException(t ?: Exception(message))
            }
        }
    }
}
