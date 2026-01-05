package com.weelo.logistics

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDexApplication
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for Weelo
 * Entry point for dependency injection and app-level initialization
 */
@HiltAndroidApp
class WeeloApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Production logging - only log warnings and errors
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT) {
                        // In production, you might want to send to crash reporting service
                        // like Firebase Crashlytics
                        if (t != null) {
                            Log.println(priority, tag ?: "Weelo", "$message\n${Log.getStackTraceString(t)}")
                        } else {
                            Log.println(priority, tag ?: "Weelo", message)
                        }
                    }
                }
            })
        }
        
        // Initialize Google Places API
        try {
            val apiKey = getString(R.string.google_maps_key)
            com.weelo.logistics.core.util.PlacesHelper.initialize(this, apiKey)
            Timber.d("Places API initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Places API")
        }
        
        Timber.d("Weelo Application initialized")
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
