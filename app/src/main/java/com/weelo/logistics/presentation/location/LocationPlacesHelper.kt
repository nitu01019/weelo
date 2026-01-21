package com.weelo.logistics.presentation.location

import android.content.Context
import android.widget.AutoCompleteTextView
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.weelo.logistics.R
import com.weelo.logistics.adapters.PlacesAutoCompleteAdapter
import timber.log.Timber

/**
 * Helper class for Google Places Autocomplete functionality
 * 
 * MODULARITY: Extracts Places API setup from Activity
 * SCALABILITY: Single point of Places initialization
 * TESTABILITY: Can be mocked for unit tests
 * 
 * @author Weelo Team
 */
class LocationPlacesHelper(private val context: Context) {

    private var placesClient: PlacesClient? = null
    private var isInitialized = false

    /**
     * Initialize Places API (call once)
     * Thread-safe initialization
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        return try {
            if (!Places.isInitialized()) {
                Places.initialize(
                    context.applicationContext,
                    com.weelo.logistics.BuildConfig.MAPS_API_KEY
                )
            }
            placesClient = Places.createClient(context)
            isInitialized = true
            Timber.d("Places API initialized successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Places API")
            false
        }
    }

    /**
     * Setup autocomplete for an AutoCompleteTextView
     * 
     * @param autoCompleteTextView The input field to setup
     * @param onPlaceSelected Callback when a place is selected
     */
    fun setupAutocomplete(
        autoCompleteTextView: AutoCompleteTextView,
        onPlaceSelected: ((String) -> Unit)? = null
    ) {
        if (!isInitialized) {
            if (!initialize()) {
                Timber.e("Cannot setup autocomplete - Places not initialized")
                return
            }
        }

        val client = placesClient ?: return

        try {
            val adapter = PlacesAutoCompleteAdapter(context, client)
            autoCompleteTextView.apply {
                setAdapter(adapter)
                threshold = 1
                isFocusable = true
                isFocusableInTouchMode = true
                
                setOnItemClickListener { _, _, position, _ ->
                    val prediction = adapter.getPrediction(position)
                    prediction?.let {
                        val address = it.getFullText(null).toString()
                        setText(address)
                        dismissDropDown()
                        onPlaceSelected?.invoke(address)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up autocomplete")
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        placesClient = null
        isInitialized = false
    }

    companion object {
        @Volatile
        private var instance: LocationPlacesHelper? = null

        /**
         * Get singleton instance (for memory efficiency)
         */
        fun getInstance(context: Context): LocationPlacesHelper {
            return instance ?: synchronized(this) {
                instance ?: LocationPlacesHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
