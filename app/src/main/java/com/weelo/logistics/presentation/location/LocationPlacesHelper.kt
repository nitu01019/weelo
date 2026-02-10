package com.weelo.logistics.presentation.location

import android.content.Context
import android.widget.AutoCompleteTextView
import com.weelo.logistics.adapters.WeeloPlacesAdapter
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.remote.api.WeeloApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Helper class for Places Autocomplete functionality
 * 
 * REFACTORED: Now uses AWS Location Service via Weelo Backend
 * instead of direct Google Places API calls.
 * 
 * Benefits:
 * - Scalable: Millions of users supported
 * - Cost-effective: AWS Location cheaper than Google
 * - Fast: Backend caches popular searches
 * - Modular: Easy to swap implementations
 * 
 * @author Weelo Team
 */
class LocationPlacesHelper(private val context: Context) {

    private var weeloApiService: WeeloApiService? = null
    private var isInitialized = false

    // Location bias for better search results (optional)
    private var biasLat: Double? = null
    private var biasLng: Double? = null

    /**
     * Initialize API service (call once)
     * Thread-safe initialization
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        return try {
            // Create lightweight OkHttp client for places - fast timeouts for quick response
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)  // Fast connect
                .readTimeout(5, TimeUnit.SECONDS)     // Fast read
                .writeTimeout(5, TimeUnit.SECONDS)    // Fast write
                .build()
            
            // Create Retrofit instance
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            weeloApiService = retrofit.create(WeeloApiService::class.java)
            isInitialized = true
            Timber.d("LocationPlacesHelper initialized - using AWS Location via backend")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LocationPlacesHelper")
            false
        }
    }

    /**
     * Set location bias for better search results
     * Call this with user's current location
     */
    fun setLocationBias(latitude: Double, longitude: Double) {
        biasLat = latitude
        biasLng = longitude
    }

    /**
     * Setup autocomplete for an AutoCompleteTextView
     * 
     * @param autoCompleteTextView The input field to setup
     * @param onPlaceSelected Callback when a place is selected
     */
    fun setupAutocomplete(
        autoCompleteTextView: AutoCompleteTextView,
        onPlaceSelected: ((String, Double, Double) -> Unit)? = null
    ) {
        if (!isInitialized) {
            if (!initialize()) {
                Timber.e("Cannot setup autocomplete - not initialized")
                return
            }
        }

        val apiService = weeloApiService ?: return

        try {
            val adapter = WeeloPlacesAdapter(
                context = context,
                weeloApiService = apiService,
                biasLat = biasLat,
                biasLng = biasLng
            )
            
            autoCompleteTextView.apply {
                setAdapter(adapter)
                threshold = 2  // Start searching after 2 characters
                isFocusable = true
                isFocusableInTouchMode = true
                
                setOnItemClickListener { _, _, position, _ ->
                    val place = adapter.getPlace(position)
                    place?.let {
                        val address = it.label
                        setText(address)
                        dismissDropDown()
                        onPlaceSelected?.invoke(address, it.latitude, it.longitude)
                    }
                }
            }
            
            Timber.d("Autocomplete setup complete for ${autoCompleteTextView.id}")
        } catch (e: Exception) {
            Timber.e(e, "Error setting up autocomplete")
        }
    }

    /**
     * Search places manually (for RecyclerView)
     */
    suspend fun searchPlaces(query: String, maxResults: Int = 5): List<com.weelo.logistics.data.remote.api.PlaceResult> {
        if (!isInitialized) {
            if (!initialize()) {
                return emptyList()
            }
        }

        val apiService = weeloApiService ?: return emptyList()

        return try {
            val response = apiService.searchPlaces(
                com.weelo.logistics.data.remote.api.PlaceSearchRequest(
                    query = query,
                    biasLat = biasLat,
                    biasLng = biasLng,
                    maxResults = maxResults
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error searching places")
            emptyList()
        }
    }

    /**
     * Reverse geocode: Coordinates -> Address
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): com.weelo.logistics.data.remote.api.PlaceResult? {
        if (!isInitialized) {
            if (!initialize()) {
                return null
            }
        }

        val apiService = weeloApiService ?: return null

        return try {
            val response = apiService.reverseGeocode(
                com.weelo.logistics.data.remote.api.ReverseGeocodeRequest(
                    latitude = lat,
                    longitude = lng
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    com.weelo.logistics.data.remote.api.PlaceResult(
                        placeId = "current_location", // Dummy ID
                        label = data.address,
                        address = data.address,
                        city = data.city,
                        latitude = data.latitude,
                        longitude = data.longitude
                    )
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reverse geocoding")
            null
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        weeloApiService = null
        isInitialized = false
        biasLat = null
        biasLng = null
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

