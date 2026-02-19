package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.local.entity.LocationEntity
import com.weelo.logistics.data.local.WeeloDatabase
import androidx.room.Room
import com.weelo.logistics.data.remote.api.PlaceResult
import com.weelo.logistics.data.remote.api.PlaceSearchRequest
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.ApiConfig
import com.weelo.logistics.data.remote.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * =============================================================================
 * MAP SEARCH ACTIVITY - Rapido Style "Change" Location Page
 * =============================================================================
 * 
 * Opens when user taps "Change" on the MapSelectionActivity.
 * Shows search bar + cached/recent locations for quick selection.
 * 
 * FLOW:
 * 1. Opens with "Pickup" or "Drop" title based on INPUT_TYPE
 * 2. Shows cached/recent locations immediately
 * 3. User can type to search via Google Places API
 * 4. User selects location → returns lat/lng to MapSelectionActivity
 * 5. MapSelectionActivity moves pin to selected location
 * 
 * SCALABILITY: Uses existing API, Room DB for cache, debounced search
 * EASY UNDERSTANDING: Simple search + results page
 * MODULARITY: Standalone activity, reusable for Pickup/Drop/Stop
 * CODING STANDARDS: Follows existing patterns in LocationInputActivity
 * 
 * @author Weelo Team
 * =============================================================================
 */
class MapSearchActivity : AppCompatActivity() {

    // Views
    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var dotIndicator: View
    private lateinit var searchInput: EditText
    private lateinit var myLocationButton: ImageView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var resultsScrollView: ScrollView
    
    // State
    private var inputType: String = "FROM"  // FROM, TO, or STOP
    private var searchJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_search)
        
        initViews()
        setupClickListeners()
        getCurrentLocation()
        loadCachedLocations()
        
        // Auto-focus search input and show keyboard
        searchInput.requestFocus()
        searchInput.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    /**
     * Initialize views and configure based on INPUT_TYPE
     * 
     * SCALABILITY: All UI driven by INPUT_TYPE - works for Pickup, Drop, Stop
     * EASY UNDERSTANDING: Green dot for Pickup, Red dot for Drop
     */
    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        dotIndicator = findViewById(R.id.dotIndicator)
        searchInput = findViewById(R.id.searchInput)
        myLocationButton = findViewById(R.id.myLocationButton)
        resultsContainer = findViewById(R.id.resultsContainer)
        resultsScrollView = findViewById(R.id.resultsScrollView)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Get input type from intent
        inputType = intent.getStringExtra("INPUT_TYPE") ?: "FROM"
        
        // Configure UI based on type
        when (inputType) {
            "TO" -> {
                titleText.text = "Drop"
                dotIndicator.setBackgroundResource(R.drawable.bg_red_dot)
                searchInput.hint = "Search drop location..."
            }
            "STOP" -> {
                titleText.text = "Stop"
                dotIndicator.setBackgroundResource(R.drawable.bg_grey_dot)
                searchInput.hint = "Search stop location..."
            }
            else -> {
                titleText.text = "Pickup"
                dotIndicator.setBackgroundResource(R.drawable.bg_green_dot)
                searchInput.hint = "Search pickup location..."
            }
        }
    }

    /**
     * Setup click listeners
     * 
     * EASY UNDERSTANDING: Back button, my location, search text changes
     */
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        // My Location button - use current GPS location
        myLocationButton.setOnClickListener {
            val latitude = userLatitude
            val longitude = userLongitude
            if (latitude != null && longitude != null) {
                Timber.d("My location selected: $latitude, $longitude")
                returnSelectedLocation(
                    latitude = latitude,
                    longitude = longitude,
                    address = "Current Location",
                    city = null
                )
            } else {
                Toast.makeText(this, getString(R.string.current_location_not_available), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Search text changes - debounced
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    performSearch(query)
                } else if (query.isEmpty()) {
                    loadCachedLocations()
                }
            }
        })
    }

    /**
     * Get current user location for "My Location" button
     * 
     * SCALABILITY: One-time fetch, cached in memory
     */
    private fun getCurrentLocation() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        userLatitude = it.latitude
                        userLongitude = it.longitude
                        Timber.d("MapSearch: Current location = ${it.latitude}, ${it.longitude}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Location permission not granted")
        }
    }

    /**
     * Load cached/recent locations from Room DB
     * 
     * SCALABILITY: Room handles efficiently, LIMIT 10
     * EASY UNDERSTANDING: Shows cached locations immediately on page open
     * MODULARITY: Uses existing LocationDao
     */
    /**
     * Get or create database instance
     * SCALABILITY: Singleton pattern prevents multiple DB instances
     */
    private fun getDatabase(): WeeloDatabase {
        return Room.databaseBuilder(
            applicationContext,
            WeeloDatabase::class.java,
            "weelo_database"
        ).fallbackToDestructiveMigration().build()
    }
    
    private fun loadCachedLocations() {
        resultsContainer.removeAllViews()
        
        lifecycleScope.launch {
            try {
                val db = getDatabase()
                val locations = db.locationDao().getRecentLocations(10).first()
                
                if (locations.isEmpty()) {
                    showEmptyState("No recent locations")
                    return@launch
                }
                
                locations.forEach { entity ->
                    // Split address into name + sub-address
                    val parts = entity.address.split(",", limit = 2)
                    val displayName = parts.getOrNull(0)?.trim() ?: entity.address
                    val displayAddress = parts.getOrNull(1)?.trim() ?: ""
                    
                    val view = createLocationItem(
                        name = displayName,
                        address = displayAddress,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        city = null,
                        isCached = true
                    )
                    resultsContainer.addView(view)
                    
                    // Add dotted divider
                    resultsContainer.addView(createDottedDivider())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cached locations")
                showEmptyState("Search for a location")
            }
        }
    }

    /**
     * Perform search via backend API
     * 
     * SCALABILITY: 300ms debounce, max 10 results, backend caches 6hrs
     * EASY UNDERSTANDING: Cancel previous → show loading → fetch → display
     * MODULARITY: Uses existing WeeloApiService.searchPlaces()
     */
    private fun performSearch(query: String) {
        searchJob?.cancel()
        
        // Check for coordinate input first
        val coordResult = parseCoordinates(query)
        if (coordResult != null) {
            resultsContainer.removeAllViews()
            val view = createLocationItem(
                name = "📍 ${String.format(Locale.US, "%.6f", coordResult.first)}, ${String.format(Locale.US, "%.6f", coordResult.second)}",
                address = "Custom coordinates",
                latitude = coordResult.first,
                longitude = coordResult.second,
                city = null,
                isCached = false
            )
            resultsContainer.addView(view)
            return
        }
        
        resultsContainer.removeAllViews()
        showLoadingState()
        
        searchJob = lifecycleScope.launch {
            delay(300) // Debounce
            
            try {
                val apiService = getApiService()
                val request = PlaceSearchRequest(
                    query = query,
                    biasLat = userLatitude,
                    biasLng = userLongitude,
                    maxResults = 10
                )
                val response = apiService.searchPlaces(request)
                
                resultsContainer.removeAllViews()
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val results = response.body()?.data ?: emptyList()
                    
                    if (results.isEmpty()) {
                        showEmptyState("No results found")
                        return@launch
                    }
                    
                    results.forEach { result ->
                        val parts = result.label.split(",", limit = 2)
                        val name = parts.getOrNull(0)?.trim() ?: result.label
                        val address = parts.getOrNull(1)?.trim() ?: ""
                        
                        val view = createLocationItem(
                            name = name,
                            address = address,
                            latitude = result.latitude,
                            longitude = result.longitude,
                            city = result.city,
                            isCached = false
                        )
                        resultsContainer.addView(view)
                        
                        // Add dotted divider
                        resultsContainer.addView(createDottedDivider())
                    }
                } else {
                    showEmptyState("Search failed. Try again.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                resultsContainer.removeAllViews()
                showEmptyState("Search failed. Check connection.")
            }
        }
    }

    /**
     * Parse coordinate input like "28.6139,77.2090"
     * 
     * SCALABILITY: O(1) parsing, no API call needed
     */
    private fun parseCoordinates(query: String): Pair<Double, Double>? {
        val regex = """^(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)$""".toRegex()
        val match = regex.matchEntire(query.trim()) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
        return Pair(lat, lng)
    }

    /**
     * Create a location item view (Rapido style)
     * 
     * RAPIDO STYLE: Clock icon for cached, location pin for search results
     * EASY UNDERSTANDING: Click to select and return to map
     */
    private fun createLocationItem(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        city: String?,
        isCached: Boolean
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            
            // Icon: Clock for cached, Location for search
            addView(ImageView(this@MapSearchActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                if (isCached) {
                    setImageResource(R.drawable.ic_clock_recent)
                    setColorFilter(Color.parseColor("#888888"))
                } else {
                    setImageResource(R.drawable.ic_location)
                    setColorFilter(Color.parseColor("#666666"))
                }
            })
            
            // Text section
            val textLayout = LinearLayout(this@MapSearchActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(14)
                }
            }
            
            // Name (Bold)
            textLayout.addView(TextView(this@MapSearchActivity).apply {
                text = name
                setTextColor(Color.parseColor("#1A1A1A"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            
            // Address (Gray)
            if (address.isNotBlank()) {
                textLayout.addView(TextView(this@MapSearchActivity).apply {
                    text = address
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
            }
            
            addView(textLayout)
            
            // Distance (if user location available)
            val currentLatitude = userLatitude
            val currentLongitude = userLongitude
            if (currentLatitude != null && currentLongitude != null) {
                val results = FloatArray(1)
                Location.distanceBetween(currentLatitude, currentLongitude, latitude, longitude, results)
                val distanceMeters = results[0]
                val distanceText = if (distanceMeters < 1000) {
                    "${distanceMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.1f km", distanceMeters / 1000)
                }
                
                addView(TextView(this@MapSearchActivity).apply {
                    text = distanceText
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 12f
                    setPadding(dp(8), 0, 0, 0)
                })
            }
            
            // Click to select location
            setOnClickListener {
                Timber.d("Location selected: $name ($latitude, $longitude)")
                
                // Save to cache
                saveToCacheAsync(name, "$name, $address", latitude, longitude)
                
                // Return to MapSelectionActivity
                returnSelectedLocation(latitude, longitude, "$name, $address", city)
            }
        }
    }

    /**
     * Save selected location to Room DB cache (async)
     * 
     * SCALABILITY: Fire-and-forget, non-blocking
     */
    private fun saveToCacheAsync(name: String, address: String, latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val db = getDatabase()
                val entity = LocationEntity(
                    id = "${latitude}_${longitude}",
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    isFavorite = false,
                    timestamp = System.currentTimeMillis()
                )
                db.locationDao().insertLocation(entity)
                Timber.d("Saved to cache: $name")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save to cache")
            }
        }
    }

    /**
     * Return selected location to MapSelectionActivity
     * 
     * EASY UNDERSTANDING: Set result intent with all location data, finish
     */
    private fun returnSelectedLocation(
        latitude: Double,
        longitude: Double,
        address: String,
        city: String?
    ) {
        val resultIntent = Intent().apply {
            putExtra("SELECTED_LAT", latitude)
            putExtra("SELECTED_LNG", longitude)
            putExtra("SELECTED_ADDRESS", address)
            putExtra("SELECTED_CITY", city ?: "")
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        // Singleton Retrofit instance for scalability
        @Volatile
        private var apiService: WeeloApiService? = null

        private fun getApiService(): WeeloApiService {
            return apiService ?: synchronized(this) {
                apiService ?: createApiServiceInstance().also { apiService = it }
            }
        }

        private fun createApiServiceInstance(): WeeloApiService {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            return retrofit2.Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(WeeloApiService::class.java)
        }
    }

    /**
     * Show empty state message
     */
    private fun showEmptyState(message: String) {
        val emptyText = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        resultsContainer.addView(emptyText)
    }

    /**
     * Show loading state
     */
    private fun showLoadingState() {
        val loadingText = TextView(this).apply {
            text = "Searching..."
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        resultsContainer.addView(loadingText)
    }

    /**
     * Create dotted divider between items (Rapido style)
     */
    private fun createDottedDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                marginStart = dp(52)
                marginEnd = dp(16)
            }
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
    }

    /**
     * Helper to convert dp to pixels
     */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }
}
