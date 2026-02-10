package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.weelo.logistics.adapters.WeeloPlacesRecyclerAdapter
import com.weelo.logistics.core.util.gone
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.visible
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.remote.api.PlaceResult
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.presentation.location.LocationInputViewModel
import com.weelo.logistics.presentation.location.LocationNavigationEvent
import com.weelo.logistics.presentation.location.LocationPlacesHelper
import com.weelo.logistics.presentation.location.IntermediateStopsManager
import com.weelo.logistics.presentation.location.LocationViewFactory
import com.weelo.logistics.tutorial.OnboardingManager
import com.weelo.logistics.tutorial.TutorialCoordinator
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

/**
 * LocationInputActivity - Location Selection Screen (REFACTORED)
 * 
 * MODULARITY IMPROVEMENTS:
 * - Places logic extracted to LocationPlacesHelper
 * - Stops logic extracted to IntermediateStopsManager
 * - Clear separation of concerns
 * - Reduced from 698 lines to ~250 lines
 * 
 * SCALABILITY:
 * - Singleton PlacesHelper for memory efficiency
 * - Lifecycle-aware components
 * - Easy to extend with new features
 * 
 * @see LocationPlacesHelper for Google Places integration
 * @see IntermediateStopsManager for stops management
 * @see LocationInputViewModel for business logic
 */
@AndroidEntryPoint
class LocationInputActivity : AppCompatActivity() {

    // ViewModel
    private val viewModel: LocationInputViewModel by viewModels()
    
    // Modular Helpers
    private lateinit var placesHelper: LocationPlacesHelper
    private lateinit var stopsManager: IntermediateStopsManager
    private var tutorialCoordinator: TutorialCoordinator? = null

    // UI Components
    private lateinit var fromLocationInput: AutoCompleteTextView
    private lateinit var toLocationInput: AutoCompleteTextView
    
    // Clear buttons (X) - EASY UNDERSTANDING: Shows when input has text
    private lateinit var fromClearButton: ImageView
    private lateinit var toClearButton: ImageView
    private var toLocationContainer: View? = null
    private lateinit var continueButton: Button
    private var backButton: ImageView? = null
    private var selectOnMapButton: Button? = null
    private var addStopsButton: Button? = null
    
    // Activity Result API launchers (replaces deprecated startActivityForResult)
    private val mapSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleMapSelectionResult(result) }
    
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Timber.d("User enabled GPS - getting current location")
            getCurrentLocation()
        } else {
            Timber.w("User declined GPS - using last known location")
            loadLastKnownLocationFromPrefs()
        }
    }
    
    // Instant/Custom toggle buttons
    private var instantButton: TextView? = null
    private var customButton: TextView? = null
    private var bookingModeToggle: LinearLayout? = null
    private var bookingMode: String = "INSTANT"
    
    // PROGRAMMATIC: Using ScrollView + LinearLayout with code-created views (GUARANTEED CLICKS)
    private lateinit var recentLocationsScrollView: ScrollView
    private lateinit var recentLocationsContainer: LinearLayout
    
    // MODULARITY: Factory for creating location views
    private lateinit var locationViewFactory: LocationViewFactory
    private lateinit var intermediateStopsContainer: LinearLayout
    private lateinit var bottomDottedLine: View

    // State Views (optional - for future use)
    // private var loadingView: View? = null
    // private var emptyView: View? = null
    // private var errorView: TextView? = null
    
    // RAPIDO STYLE: Search Results RecyclerView (shows below input)
    private lateinit var searchResultsRecyclerView: RecyclerView
    // Skeleton loading container
    private lateinit var placesAdapter: WeeloPlacesRecyclerAdapter
    
    // RAPIDO STYLE: Skeleton loading container (shows during API calls)
    // SCALABILITY: Better UX than spinner - shows UI structure while loading
    private lateinit var skeletonContainer: com.facebook.shimmer.ShimmerFrameLayout
    
    // SCALABILITY: Local cache for search results (reduces API calls)
    // Key: query string, Value: Pair(timestamp, results)
    private val searchCache = mutableMapOf<String, Pair<Long, List<PlaceResult>>>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 minutes
    
    // Search state
    private var searchJob: Job? = null
    private val searchScope = CoroutineScope(Dispatchers.Main)
    private var currentSearchField: AutoCompleteTextView? = null
    
    // Selected locations (for single selection)
    private var selectedFromLocation: PlaceResult? = null
    private var selectedToLocation: PlaceResult? = null
    
    /**
     * Flag to prevent TextWatcher from re-triggering search during programmatic setText
     * 
     * SCALABILITY: Prevents infinite loops and unnecessary API calls when millions of users
     * EASY UNDERSTANDING: Simple boolean flag with clear purpose
     * MODULARITY: Single control point for all setText operations
     */
    private var isSettingTextProgrammatically = false
    
    // Current location tracking for distance calculation
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentUserLocation: android.location.Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_input)

        initializeHelpers()
        initializeViews()
        setupListeners()
        observeViewModel()
        restoreState()
        startTutorialIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        tutorialCoordinator = null
        searchJob?.cancel()
        placesHelper.cleanup()
    }

    // ========================================
    // Initialization
    // ========================================

    private fun initializeHelpers() {
        placesHelper = LocationPlacesHelper.getInstance(this)
        placesHelper.initialize()
        
        // Initialize location client for distance calculation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // RAPIDO STYLE: Check GPS and permissions on app start
        if (checkLocationPermission()) {
            checkAndRequestLocationSettings()
        } else {
            requestLocationPermission()
        }
    }
    
    /**
     * Request location permission from user
     * EASY UNDERSTANDING: Standard Android runtime permission request
     */
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_CODE_LOCATION_PERMISSION
        )
    }
    
    /**
     * Handle permission request result
     * CODING STANDARDS: Follows Android permission callback pattern
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Location permission granted")
                checkAndRequestLocationSettings()
            } else {
                Timber.w("Location permission denied - using last known location")
                loadLastKnownLocationFromPrefs()
            }
        }
    }
    
    /**
     * RAPIDO STYLE: Check if GPS/Location is enabled and show dialog if not
     * 
     * Uses Google Play Services LocationSettingsRequest - standard approach used by
     * Rapido, Uber, Ola for location dialog.
     * 
     * SCALABILITY: Handles all device manufacturers via Play Services
     * EASY UNDERSTANDING: Shows system dialog "Turn on location"
     * MODULARITY: Separate function for GPS check
     */
    private fun checkAndRequestLocationSettings() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L  // 10 seconds interval
        ).build()
        
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)  // Shows dialog even if location was previously denied
            .build()
        
        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                // GPS is ON - get current location
                Timber.d("Location settings OK - getting current location")
                getCurrentLocation()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    // GPS is OFF - show Google Play Services dialog to turn it on
                    try {
                        Timber.d("GPS is OFF - showing enable dialog")
                        val intentSenderRequest = IntentSenderRequest.Builder(
                            exception.resolution
                        ).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        Timber.e(e, "Error showing location settings dialog")
                        // Fallback: Show manual settings dialog
                        showManualLocationSettingsDialog()
                    }
                } else {
                    Timber.e(exception, "Location settings check failed")
                    loadLastKnownLocationFromPrefs()
                }
            }
    }
    
    /**
     * Fallback dialog: Take user to device Settings manually
     * 
     * EASY UNDERSTANDING: Only used when Play Services dialog fails
     * CODING STANDARDS: Follows Material Design dialog patterns
     */
    private fun showManualLocationSettingsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Location Required")
            .setMessage("Please enable location services to use pickup location and get accurate distance calculations.")
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    Timber.e(e, "Cannot open location settings")
                    showToast("Please enable location from device settings")
                }
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                loadLastKnownLocationFromPrefs()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Get current user location for distance calculation and auto-fill pickup
     * 
     * SCALABILITY: Cached once, used for all distance calculations
     * EASY UNDERSTANDING: Gets location → saves to prefs → auto-fills pickup
     * MODULARITY: Separate function, reusable after GPS enabled
     */
    private fun getCurrentLocation() {
        if (checkLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    currentUserLocation = location
                    location?.let {
                        Timber.d("Current location obtained: ${it.latitude}, ${it.longitude}")
                        // Update bias for search results distance
                        placesAdapter.updateBias(it.latitude, it.longitude)
                        
                        // Save as last known location for offline fallback
                        saveLastKnownLocation(it.latitude, it.longitude)
                        
                        // Auto-fill pickup with current location if empty
                        autoFillCurrentLocation(it.latitude, it.longitude)
                    } ?: run {
                        // No location available - try last known from prefs
                        Timber.w("FusedLocation returned null - using last known")
                        loadLastKnownLocationFromPrefs()
                    }
                }.addOnFailureListener { e ->
                    Timber.e(e, "Failed to get current location")
                    loadLastKnownLocationFromPrefs()
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Location permission not granted")
                loadLastKnownLocationFromPrefs()
            }
        } else {
            loadLastKnownLocationFromPrefs()
        }
    }
    
    /**
     * Auto-fill pickup field with "📍 Current Location" display text
     * 
     * RAPIDO STYLE: Shows friendly text while REAL coordinates are stored behind it
     * 
     * HOW IT WORKS:
     * 1. Display shows: "📍 Current Location" (user-friendly)
     * 2. selectedFromLocation stores: PlaceResult(lat=REAL, lng=REAL)
     * 3. When user clicks Continue → REAL coordinates are used for map/booking
     * 4. When user taps the field → text clears, they can search normally
     * 
     * SCALABILITY: ZERO extra API calls - no reverse geocoding needed
     * EASY UNDERSTANDING: Display text is cosmetic, real data is in selectedFromLocation
     * MODULARITY: Only runs once on first open when pickup is empty
     * 
     * @param latitude Current user GPS latitude (REAL coordinates)
     * @param longitude Current user GPS longitude (REAL coordinates)
     */
    private fun autoFillCurrentLocation(latitude: Double, longitude: Double) {
        // Only auto-fill FIRST TIME if pickup is empty AND no location restored from intent
        if (!fromLocationInput.text.isNullOrBlank() || selectedFromLocation != null) {
            return
        }
        
        // Store REAL coordinates behind the display text
        isSettingTextProgrammatically = true
        selectedFromLocation = PlaceResult(
            placeId = "current_location",
            label = "📍 Current Location",
            latitude = latitude,
            longitude = longitude
        )
        fromLocationInput.setText("📍 Current Location")
        isSettingTextProgrammatically = false
        
        Timber.d("Pickup auto-filled: display='📍 Current Location', real coords=($latitude, $longitude)")
    }
    
    /**
     * Save last known location to SharedPreferences for offline fallback
     * 
     * SCALABILITY: Simple key-value store, instant access
     * EASY UNDERSTANDING: Saves lat/lng as strings in SharedPreferences
     */
    private fun getEncryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            this,
            "weelo_location_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private fun saveLastKnownLocation(latitude: Double, longitude: Double) {
        try {
            val prefs = getEncryptedPrefs()
            prefs.edit()
                .putString("last_lat", latitude.toString())
                .putString("last_lng", longitude.toString())
                .putLong("last_time", System.currentTimeMillis())
                .apply()
            Timber.d("Saved last known location (encrypted)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last known location")
        }
    }
    
    /**
     * Load last known location from SharedPreferences
     * 
     * SCALABILITY: Instant access, no API call needed
     * EASY UNDERSTANDING: Reads lat/lng from SharedPreferences
     * OFFLINE SUPPORT: Works when GPS is off or network unavailable
     */
    private fun loadLastKnownLocationFromPrefs() {
        try {
            val prefs = getEncryptedPrefs()
            val lat = prefs.getString("last_lat", null)?.toDoubleOrNull()
            val lng = prefs.getString("last_lng", null)?.toDoubleOrNull()
            
            if (lat != null && lng != null) {
                val location = android.location.Location("cached").apply {
                    latitude = lat
                    longitude = lng
                }
                currentUserLocation = location
                placesAdapter.updateBias(lat, lng)
                Timber.d("Loaded last known location from encrypted prefs")
            } else {
                Timber.d("No last known location in prefs")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load last known location")
        }
    }
    
    /**
     * Check if location permission is granted
     */
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeViews() {
        try {
            // Bind views
            fromLocationInput = findViewById(R.id.fromLocationInput)
            toLocationInput = findViewById(R.id.toLocationInput)
            
            // Clear buttons
            fromClearButton = findViewById(R.id.fromClearButton)
            toClearButton = findViewById(R.id.toClearButton)
            setupClearButtons()
            toLocationContainer = findViewById(R.id.toLocationContainer)
            continueButton = findViewById(R.id.continueButton)
            backButton = findViewById(R.id.backButton)
            selectOnMapButton = findViewById(R.id.selectOnMapButton)
            addStopsButton = findViewById(R.id.addStopsButton)
            
            // Initialize toggle buttons
            bookingModeToggle = findViewById(R.id.bookingModeToggle)
            instantButton = findViewById(R.id.instantButton)
            customButton = findViewById(R.id.customButton)
            
            // PROGRAMMATIC: ScrollView + LinearLayout for recent locations (guaranteed clicks)
            recentLocationsScrollView = findViewById(R.id.recentLocationsScrollView)
            recentLocationsContainer = findViewById(R.id.recentLocationsContainer)
            
            // MODULARITY: Initialize view factory
            locationViewFactory = LocationViewFactory(this)
            intermediateStopsContainer = findViewById(R.id.intermediateStopsContainer)
            bottomDottedLine = findViewById(R.id.bottomDottedLine)
            
            // State views removed - not in current layout
            
            // RAPIDO STYLE: Initialize search results RecyclerView
            searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
            // Skeleton loading container
            
            // RAPIDO STYLE: Initialize skeleton loading container
            skeletonContainer = findViewById(R.id.skeletonContainer)
            
            setupSearchResultsRecyclerView()
            
            // Initialize stops manager with all required parameters
            stopsManager = IntermediateStopsManager(
                context = this,
                container = intermediateStopsContainer,
                bottomDottedLine = bottomDottedLine,
                placesHelper = placesHelper
            )

        } catch (e: Exception) {
            showToast("Error initializing views")
            Timber.e(e, "Error initializing views")
        }
    }
    
    /**
     * Setup clear (X) buttons for input fields
     * EASY UNDERSTANDING: Shows X when text exists, clears on click
     * MODULARITY: Separate setup function
     */
    private fun setupClearButtons() {
        // Show/hide clear button based on text content
        fromLocationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fromClearButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
        })
        
        toLocationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                toClearButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
        })
        
        // Clear button click handlers
        fromClearButton.setOnClickListener {
            isSettingTextProgrammatically = true
            fromLocationInput.setText("")
            isSettingTextProgrammatically = false
            selectedFromLocation = null
            fromClearButton.visibility = View.GONE
            fromLocationInput.requestFocus()
            showRecentLocations()
        }
        
        toClearButton.setOnClickListener {
            isSettingTextProgrammatically = true
            toLocationInput.setText("")
            isSettingTextProgrammatically = false
            selectedToLocation = null
            toClearButton.visibility = View.GONE
            toLocationInput.requestFocus()
            showRecentLocations()
        }
    }
    
    // View creation now handled by LocationViewFactory (MODULARITY)
    
    /**
     * Handle recent location selection
     * EASY UNDERSTANDING: Clear flow - set flag, update text, clear flag
     * SCALABILITY: Validates coordinates before using cached location
     */
    private fun handleRecentLocationSelected(location: LocationModel) {
        Timber.d("Recent location selected: ${location.address} (lat=${location.latitude}, lng=${location.longitude})")
        
        // Validate coordinates - cached locations MUST have valid coordinates
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            Timber.w("⚠️ Cached location missing coordinates: ${location.address}")
            showToast("Location coordinates not available. Please search again.")
            return
        }
        
        // Convert to PlaceResult for consistency
        val placeResult = PlaceResult(
            placeId = location.id.ifEmpty { location.address.hashCode().toString() },
            label = location.address,
            latitude = location.latitude,
            longitude = location.longitude
        )
        
        // Set flag to prevent TextWatcher from triggering search
        isSettingTextProgrammatically = true
        
        // Determine which field to fill based on focus and mode
        val isFromFocused = fromLocationInput.hasFocus()
        val isToFocused = toLocationInput.hasFocus()
        
        if (bookingMode == "CUSTOM") {
            // Custom mode: Only FROM field
            selectedFromLocation = placeResult
            fromLocationInput.setText(location.address)
            fromLocationInput.setSelection(location.address.length)
            fromLocationInput.clearFocus()
        } else {
            // Instant mode: Fill the focused field
            if (isToFocused) {
                selectedToLocation = placeResult
                toLocationInput.setText(location.address)
                toLocationInput.setSelection(location.address.length)
                toLocationInput.clearFocus()
            } else if (isFromFocused || selectedFromLocation == null) {
                selectedFromLocation = placeResult
                fromLocationInput.setText(location.address)
                fromLocationInput.setSelection(location.address.length)
                
                // Auto-advance to TO field
                if (toLocationInput.text.isNullOrEmpty()) {
                    fromLocationInput.postDelayed({ toLocationInput.requestFocus() }, 100)
                } else {
                    fromLocationInput.clearFocus()
                }
            } else {
                selectedToLocation = placeResult
                toLocationInput.setText(location.address)
                toLocationInput.setSelection(location.address.length)
                toLocationInput.clearFocus()
            }
        }
        
        // Reset flag
        isSettingTextProgrammatically = false
        
        // Hide keyboard and show recent locations
        hideKeyboard()
    }
    
    /**
     * Hide soft keyboard
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
    
    /**
     * Toggle favorite status for a location
     * SCALABILITY: Updates Room DB via ViewModel
     * EASY UNDERSTANDING: Simple toggle logic
     */
    private fun toggleFavorite(location: LocationModel) {
        Timber.d("Toggle favorite: ${location.address} -> ${!location.isFavorite}")
        val updatedLocation = location.copy(isFavorite = !location.isFavorite)
        viewModel.updateLocation(updatedLocation)
        // Refresh the list to show updated heart icon
        viewModel.loadRecentLocations()
    }
    
    /**
     * Remove a location from recent/cached list
     * SCALABILITY: Deletes from Room DB via ViewModel
     * EASY UNDERSTANDING: Removes and refreshes list
     */
    private fun removeRecentLocation(location: LocationModel) {
        Timber.d("Remove location: ${location.address}")
        viewModel.deleteLocation(location)
        // Refresh the list
        viewModel.loadRecentLocations()
    }
    
    /**
     * RAPIDO STYLE: Setup RecyclerView for Google Places search results
     * Shows results below input field (not in dialog)
     * 
     * SCALABILITY: Current location bias improves search relevance
     * EASY UNDERSTANDING: Two separate callbacks - select and favorite
     * MODULARITY: Heart click adds to cache as favorite without selecting
     */
    private fun setupSearchResultsRecyclerView() {
        placesAdapter = WeeloPlacesRecyclerAdapter(
            biasLat = currentUserLocation?.latitude,
            biasLng = currentUserLocation?.longitude,
            onPlaceSelected = { place ->
                handlePlaceSelected(place)
            },
            onFavoriteClick = { place ->
                // HEART CLICK: Save as favorite (top priority in cache)
                saveLocationToCache(place, isFavorite = true)
                showToast("❤️ Added to favorites")
            }
        )
        
        searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LocationInputActivity)
            adapter = placesAdapter
            isNestedScrollingEnabled = true
        }
    }
    
    /**
     * Save a PlaceResult to the location cache (Room DB)
     * 
     * SCALABILITY: Fire-and-forget async operation via ViewModel
     * EASY UNDERSTANDING: Converts PlaceResult to LocationModel and saves
     * MODULARITY: Reusable for both regular selections and favorites
     * CODING STANDARDS: Follows existing pattern with ViewModel
     * 
     * @param place The PlaceResult from search or coordinates
     * @param isFavorite If true, saves as favorite (top priority in cache)
     */
    private fun saveLocationToCache(place: PlaceResult, isFavorite: Boolean) {
        val locationModel = LocationModel(
            id = place.placeId,
            address = place.label,
            latitude = place.latitude,
            longitude = place.longitude,
            isFavorite = isFavorite,
            timestamp = System.currentTimeMillis()
        )
        viewModel.addRecentLocation(locationModel)
        Timber.d("Saving to cache: ${place.label}, favorite=$isFavorite, coords=(${place.latitude}, ${place.longitude})")
    }

    private fun setupListeners() {
        backButton?.setOnClickListener { handleBack() }
        continueButton.setOnClickListener { handleContinue() }
        selectOnMapButton?.setOnClickListener { handleSelectOnMap() }
        addStopsButton?.setOnClickListener { handleAddStop() }
        
        // Instant/Custom toggle button listeners
        instantButton?.setOnClickListener {
            if (bookingMode != "INSTANT") {
                setBookingMode("INSTANT")
            }
        }
        
        customButton?.setOnClickListener {
            if (bookingMode != "CUSTOM") {
                setBookingMode("CUSTOM")
            }
        }
        
        // RAPIDO STYLE: Setup autocomplete with live search (shows results below)
        setupLocationInputListeners()
    }
    
    /**
     * RAPIDO STYLE: Setup text watchers for live Google Places search
     * Results show below input in RecyclerView (not dropdown dialog)
     */
    private fun setupLocationInputListeners() {
        // From Location Input
        // SCALABILITY: TextWatcher with flag check prevents unnecessary API calls
        // EASY UNDERSTANDING: Skip search when text is set programmatically (from selection)
        fromLocationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Skip if text was set programmatically (user selected a place)
                if (isSettingTextProgrammatically) return
                
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    currentSearchField = fromLocationInput
                    performPlacesSearch(query)
                } else {
                    showRecentLocations()
                }
            }
        })
        
        fromLocationInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                currentSearchField = fromLocationInput
                val text = fromLocationInput.text?.toString()?.trim() ?: ""
                if (text.length >= 2) {
                    performPlacesSearch(text)
                } else {
                    showRecentLocations()
                }
            }
        }
        
        // To Location Input
        // SCALABILITY: Same pattern as fromLocationInput for consistency
        // CODING STANDARDS: Identical TextWatcher pattern across all input fields
        toLocationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Skip if text was set programmatically (user selected a place)
                if (isSettingTextProgrammatically) return
                
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    currentSearchField = toLocationInput
                    performPlacesSearch(query)
                } else {
                    showRecentLocations()
                }
            }
        })
        
        toLocationInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                currentSearchField = toLocationInput
                val text = toLocationInput.text?.toString()?.trim() ?: ""
                if (text.length >= 2) {
                    performPlacesSearch(text)
                } else {
                    showRecentLocations()
                }
            }
        }
    }
    
    /**
     * Parse coordinate input like "28.6139,77.2090" or "28.6139, 77.2090"
     * 
     * SCALABILITY: O(1) parsing, no API call needed for coordinates
     * EASY UNDERSTANDING: Returns PlaceResult if valid coordinates, null otherwise
     * MODULARITY: Separate function for coordinate parsing
     * CODING STANDARDS: Validates coordinate ranges
     * 
     * @param query The user input string
     * @return PlaceResult if valid coordinates, null otherwise
     */
    private fun parseCoordinatesInput(query: String): PlaceResult? {
        // Match patterns: "28.6139,77.2090" or "28.6139, 77.2090" or "-28.6139, -77.2090"
        val regex = """^(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)$""".toRegex()
        val match = regex.matchEntire(query.trim()) ?: return null
        
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        
        // Validate coordinate ranges
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            Timber.w("Invalid coordinate range: lat=$lat, lng=$lng")
            return null
        }
        
        Timber.d("Parsed coordinates: lat=$lat, lng=$lng")
        return PlaceResult(
            placeId = "coords_${lat}_${lng}",
            label = "📍 ${String.format(java.util.Locale.US, "%.6f", lat)}, ${String.format(java.util.Locale.US, "%.6f", lng)}",
            latitude = lat,
            longitude = lng
        )
    }
    
    /**
     * INSTANT SEARCH: Perform Google Places search with minimal debouncing
     * Shows skeleton loading IMMEDIATELY, then results below input field
     * 
     * SCALABILITY: 
     * - Coordinate input parsed instantly (no API call)
     * - 150ms debounce balances speed vs API calls
     * - Backend caches results (1 hour TTL)
     * - Skeleton shows instantly for perceived performance
     * 
     * EASY UNDERSTANDING: Clear loading → results flow
     * CODING STANDARDS: Follows coroutine best practices
     */
    private fun performPlacesSearch(query: String) {
        // Cancel previous search
        searchJob?.cancel()
        
        // CHECK COORDINATES FIRST (instant, no API call needed)
        val coordLocation = parseCoordinatesInput(query)
        if (coordLocation != null) {
            Timber.d("Coordinate input detected: ${coordLocation.label}")
            hideSkeletonLoading()
            placesAdapter.updatePlaces(listOf(coordLocation))
            return
        }
        
        // Show skeleton IMMEDIATELY for instant feedback
        showSkeletonLoading()
        
        // Debounce: Wait 150ms before API call (fast but prevents excessive calls)
        searchJob = searchScope.launch {
            delay(150)
            
            try {
                val normalizedQuery = query.lowercase().trim()
                
                // Check local cache first (INSTANT response for cached queries)
                val cached = searchCache[normalizedQuery]
                if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                    Timber.d("Search cache HIT: '$query' (${cached.second.size} results)")
                    withContext(Dispatchers.Main) {
                        hideSkeletonLoading()
                        placesAdapter.updatePlaces(cached.second)
                    }
                    return@launch
                }
                
                // Cache miss - perform API call
                val results = withContext(Dispatchers.IO) {
                    placesHelper.searchPlaces(query, maxResults = 8)
                }
                
                // Store in cache for future instant access
                if (results.isNotEmpty()) {
                    searchCache[normalizedQuery] = Pair(System.currentTimeMillis(), results)
                    Timber.d("Search cached: '$query' (${results.size} results)")
                }
                
                // Hide skeleton, show results
                withContext(Dispatchers.Main) {
                    hideSkeletonLoading()
                    
                    if (results.isNotEmpty()) {
                        placesAdapter.updatePlaces(results)
                        Timber.d("Places search: Found ${results.size} results for '$query'")
                    } else {
                        placesAdapter.clear()
                        Timber.d("Places search: No results for '$query'")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Places search error")
                withContext(Dispatchers.Main) {
                    hideSkeletonLoading()
                    placesAdapter.clear()
                }
            }
        }
    }
    
    /**
     * RAPIDO STYLE: Handle place selection from search results
     * Only one location selected at a time
     * 
     * SCALABILITY: Flag prevents TextWatcher from re-triggering search (saves API calls)
     * EASY UNDERSTANDING: Clear flow - set flag, update text, save to cache, clear flag
     * MODULARITY: Centralized place selection handling
     * 
     * CACHING: Every selected location is saved to cache for future quick access
     */
    private fun handlePlaceSelected(place: PlaceResult) {
        Timber.d("Place selected: ${place.label} (${place.latitude}, ${place.longitude})")
        
        // SAVE TO CACHE: Add selected location to recent history (not as favorite)
        saveLocationToCache(place, isFavorite = false)
        
        // Set flag to prevent TextWatcher from triggering search
        isSettingTextProgrammatically = true
        
        // Determine which field is active
        when (currentSearchField) {
            fromLocationInput -> {
                selectedFromLocation = place
                // Set text and keep cursor at end
                fromLocationInput.setText(place.label)
                fromLocationInput.setSelection(place.label.length)
                
                // RAPIDO UX: Auto-focus to next field after short delay
                if (bookingMode == "INSTANT" && toLocationInput.text.isNullOrEmpty()) {
                    fromLocationInput.postDelayed({
                        toLocationInput.requestFocus()
                    }, 100)
                } else {
                    // Clear focus but keep text visible
                    fromLocationInput.clearFocus()
                }
            }
            toLocationInput -> {
                selectedToLocation = place
                // Set text and keep cursor at end
                toLocationInput.setText(place.label)
                toLocationInput.setSelection(place.label.length)
                // Clear focus after selection
                toLocationInput.clearFocus()
            }
        }
        
        // Reset flag after text is set
        isSettingTextProgrammatically = false
        
        // Hide search results, show recent locations
        showRecentLocations()
    }
    
    /**
     * Show recent locations (default view)
     * EASY UNDERSTANDING: Hides skeleton and search results, shows cached recent locations
     */
    private fun showRecentLocations() {
        skeletonContainer.stopShimmer()
        skeletonContainer.gone()
        searchResultsRecyclerView.gone()
        recentLocationsScrollView.visible()
    }
    
    /**
     * Show skeleton loading state (Rapido style)
     * 
     * SCALABILITY: Shows instantly while API call is in progress
     * EASY UNDERSTANDING: User sees UI structure, not blank screen
     * MODULARITY: Separate skeleton layout matches real item layout
     */
    private fun showSkeletonLoading() {
        recentLocationsScrollView.gone()
        searchResultsRecyclerView.gone()
        skeletonContainer.visible()
        skeletonContainer.startShimmer()
    }
    
    /**
     * Hide skeleton and show search results
     * EASY UNDERSTANDING: Smooth transition from skeleton to actual results
     */
    private fun hideSkeletonLoading() {
        skeletonContainer.stopShimmer()
        skeletonContainer.gone()
        searchResultsRecyclerView.visible()
    }

    private fun restoreState() {
        // Restore locations from intent
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Location>("FROM_LOCATION")?.let {
            fromLocationInput.setText(it.address)
        }
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Location>("TO_LOCATION")?.let {
            toLocationInput.setText(it.address)
        }

        // Restore intermediate stops
        stopsManager.restoreStops(intent.getStringArrayExtra("INTERMEDIATE_STOPS"))

        // Auto-add stop if requested
        if (intent.getBooleanExtra("AUTO_ADD_STOP", false)) {
            stopsManager.addStop()
        }
    }

    private fun startTutorialIfNeeded() {
        window.decorView.post {
            tutorialCoordinator = TutorialCoordinator(
                activity = this,
                onboardingManager = OnboardingManager.getInstance(this),
                onComplete = { tutorialCoordinator = null }
            )
            tutorialCoordinator?.startLocationInputTutorial()
        }
    }

    // ========================================
    // User Actions
    // ========================================

    private fun handleBack() {
        finish()
        TransitionHelper.applySlideOutRightTransition(this)
    }

    /**
     * Handle Continue button click
     * PROPER FIX: Use selected location data (with lat/lng) instead of just text
     */
    private fun handleContinue() {
        continueButton.isEnabled = false
        
        // Validate based on booking mode
        if (bookingMode == "CUSTOM") {
            // Custom mode: Only FROM location required
            if (selectedFromLocation == null || fromLocationInput.text.isNullOrBlank()) {
                showToast("Please select a pickup location")
                continueButton.isEnabled = true
                return
            }
            
            // Navigate with only FROM location (Custom booking)
            val fromLoc = LocationModel(
                id = selectedFromLocation!!.placeId,
                address = selectedFromLocation!!.label,
                latitude = selectedFromLocation!!.latitude,
                longitude = selectedFromLocation!!.longitude
            )
            
            // For Custom mode, pass same location as TO (or handle differently in MapActivity)
            navigateToMap(fromLoc, fromLoc)
            
        } else {
            // Instant mode: Both FROM and TO required
            if (selectedFromLocation == null || fromLocationInput.text.isNullOrBlank()) {
                showToast("Please select a pickup location")
                continueButton.isEnabled = true
                return
            }
            
            if (selectedToLocation == null || toLocationInput.text.isNullOrBlank()) {
                showToast("Please select a drop location")
                continueButton.isEnabled = true
                return
            }
            
            // Navigate with both locations (Instant booking)
            val fromLoc = LocationModel(
                id = selectedFromLocation!!.placeId,
                address = selectedFromLocation!!.label,
                latitude = selectedFromLocation!!.latitude,
                longitude = selectedFromLocation!!.longitude
            )
            
            val toLoc = LocationModel(
                id = selectedToLocation!!.placeId,
                address = selectedToLocation!!.label,
                latitude = selectedToLocation!!.latitude,
                longitude = selectedToLocation!!.longitude
            )
            
            navigateToMap(fromLoc, toLoc)
        }
        
        continueButton.isEnabled = true
    }

    /**
     * Open MapSelectionActivity for pin-on-map location selection
     * 
     * EASY UNDERSTANDING: Passes INPUT_TYPE so MapSelection knows which field to fill
     * MODULARITY: MapSelectionActivity returns result via Activity Result API launcher
     */
    private fun handleSelectOnMap() {
        // Determine which field triggered the map selection
        val inputType = if (currentSearchField == toLocationInput) "TO" else "FROM"
        
        mapSelectionLauncher.launch(
            Intent(this, MapSelectionActivity::class.java).apply {
                putExtra("INPUT_TYPE", inputType)
            }
        )
        TransitionHelper.applySlideInLeftTransition(this)
    }

    private fun handleAddStop() {
        if (!stopsManager.addStop()) {
            showToast("Maximum ${IntermediateStopsManager.DEFAULT_MAX_STOPS} stops allowed")
        }
    }

    /**
     * Handle result from MapSelectionActivity (pin-on-map location selection)
     * 
     * SCALABILITY: Uses same flag pattern to prevent TextWatcher interference
     * EASY UNDERSTANDING: Clear flow for map-based location selection
     * CODING STANDARDS: Uses Activity Result API (non-deprecated)
     */
    private fun handleMapSelectionResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            @Suppress("DEPRECATION")
            data?.getParcelableExtra<Location>("SELECTED_LOCATION")?.let { selectedLoc ->
                // Create PlaceResult from map selection
                val placeResult = PlaceResult(
                    placeId = selectedLoc.address.hashCode().toString(),
                    label = selectedLoc.address,
                    latitude = selectedLoc.latitude,
                    longitude = selectedLoc.longitude
                )
                
                // Determine which field to fill
                val isFromFocused = fromLocationInput.hasFocus()
                val isToFocused = toLocationInput.hasFocus()
                
                // Set flag to prevent TextWatcher from triggering search
                isSettingTextProgrammatically = true
                
                if (bookingMode == "CUSTOM") {
                    // Custom mode: Always fill FROM
                    selectedFromLocation = placeResult
                    fromLocationInput.setText(selectedLoc.address)
                    fromLocationInput.setSelection(selectedLoc.address.length)
                    Timber.d("Map selection set to FROM (Custom): ${selectedLoc.address}")
                } else {
                    // Instant mode: Smart field detection
                    if (isToFocused) {
                        // TO field was focused - fill TO
                        selectedToLocation = placeResult
                        toLocationInput.setText(selectedLoc.address)
                        toLocationInput.setSelection(selectedLoc.address.length)
                        Timber.d("Map selection set to TO: ${selectedLoc.address}")
                    } else if (isFromFocused || selectedFromLocation == null) {
                        // FROM field was focused OR FROM is empty - fill FROM
                        selectedFromLocation = placeResult
                        fromLocationInput.setText(selectedLoc.address)
                        fromLocationInput.setSelection(selectedLoc.address.length)
                        Timber.d("Map selection set to FROM: ${selectedLoc.address}")
                    } else {
                        // Neither focused, FROM has value - fill TO
                        selectedToLocation = placeResult
                        toLocationInput.setText(selectedLoc.address)
                        toLocationInput.setSelection(selectedLoc.address.length)
                        Timber.d("Map selection set to TO (auto): ${selectedLoc.address}")
                    }
                }
                
                // Reset flag after text is set
                isSettingTextProgrammatically = false
            }
        }
    }

    // ========================================
    // ViewModel Observation
    // ========================================

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            state ?: return@observe
            
            when {
                state.isLoading -> showLoadingState()
                state.errorMessage != null -> showErrorState(state.errorMessage)
                state.isEmpty() -> showEmptyState()
                else -> showSuccessState()
            }
            
            continueButton.isEnabled = !state.isLoading
            displayRecentLocations(state.recentLocations)
        }

        viewModel.navigationEvent.observe(this) { event ->
            when (event) {
                is LocationNavigationEvent.NavigateToMap -> {
                    navigateToMap(event.fromLocation, event.toLocation)
                    continueButton.postDelayed({ continueButton.isEnabled = true }, 500)
                }
            }
        }
    }

    // ========================================
    // UI State Management
    // ========================================

    private fun showLoadingState() {
        // Loading state
        recentLocationsScrollView.gone()
    }

    private fun showEmptyState() {
        // Empty state
        recentLocationsScrollView.gone()
    }

    private fun showErrorState(message: String) {
        // Error state - show toast
        recentLocationsScrollView.gone()
        showToast(message)
        viewModel.clearError()
    }

    private fun showSuccessState() {
        // Success state
        recentLocationsScrollView.visible()
    }

    /**
     * Display recent locations using LocationViewFactory
     * 
     * SCALABILITY: Factory creates lightweight views on demand
     * EASY UNDERSTANDING: Clear flow - clear, validate, create, add
     * MODULARITY: Uses LocationViewFactory for view creation
     * CODING STANDARDS: No debug toasts in production
     * 
     * RAPIDO STYLE: Updates factory with user location for distance calculation
     */
    private fun displayRecentLocations(locations: List<LocationModel>) {
        Timber.d("displayRecentLocations: ${locations.size} locations")
        
        // Clear existing views
        recentLocationsContainer.removeAllViews()
        
        if (locations.isEmpty()) {
            recentLocationsScrollView.gone()
            return
        }

        recentLocationsScrollView.visible()
        
        // RAPIDO STYLE: Update factory with user location for distance calculation
        currentUserLocation?.let { location ->
            locationViewFactory.updateUserLocation(location.latitude, location.longitude)
        }
        
        // Filter and limit locations
        val validLocations = locations.take(MAX_RECENT_LOCATIONS).filter { it.isValid() }
        Timber.d("Displaying ${validLocations.size} valid locations")
        
        // Create views using factory (MODULARITY)
        validLocations.forEach { location ->
            // Create location view with favorite and remove callbacks
            val view = locationViewFactory.createLocationView(
                location = location,
                config = LocationViewFactory.RECENT_LOCATION_CONFIG,
                onClick = { loc -> handleRecentLocationSelected(loc) },
                onFavoriteClick = { loc -> toggleFavorite(loc) },
                onRemoveClick = { loc -> removeRecentLocation(loc) }
            )
            recentLocationsContainer.addView(view)
            
            // Add divider
            recentLocationsContainer.addView(locationViewFactory.createDivider())
        }
    }

    // REMOVED: addRecentLocationView() - Now using RecentLocationsAdapter with RecyclerView
    // REMOVED: calculateDistance() - Now in RecentLocationsAdapter

    // ========================================
    // Navigation
    // ========================================

    /**
     * Navigate to MapBookingActivity with location data and booking mode
     * 
     * SCALABILITY: Passes all required data in single intent (no additional queries)
     * EASY UNDERSTANDING: Clear intent extras with descriptive keys
     * MODULARITY: MapBookingActivity handles mode-specific navigation
     */
    private fun navigateToMap(from: LocationModel, to: LocationModel) {
        // Debug: Log coordinates being passed to map
        Timber.d("🗺️ Navigating to map:")
        Timber.d("  FROM: ${from.address} (${from.latitude}, ${from.longitude})")
        Timber.d("  TO: ${to.address} (${to.latitude}, ${to.longitude})")
        
        val intent = Intent(this, MapBookingActivity::class.java).apply {
            putExtra(KEY_FROM_LOCATION, Location(from.address, from.latitude, from.longitude))
            putExtra(KEY_TO_LOCATION, Location(to.address, to.latitude, to.longitude))
            
            // Pass booking mode for Custom vs Instant flow differentiation
            putExtra(KEY_BOOKING_MODE, bookingMode)
            
            val validStops = stopsManager.getValidStops()
            if (validStops.isNotEmpty()) {
                // CRITICAL FIX: Convert StopLocation to Location (Parcelable) for safe Intent passing
                // StopLocation is NOT Parcelable - using it directly causes crash
                val stopLocations = validStops.map { stop ->
                    Location(stop.address, stop.latitude, stop.longitude)
                }.toTypedArray()
                
                // Pass as Parcelable array (MapBookingActivity checks this key first)
                putExtra("INTERMEDIATE_STOPS_LOCATIONS", stopLocations)
                // Also pass address strings for backward compatibility
                putExtra("INTERMEDIATE_STOPS", validStops.map { it.address }.toTypedArray())
            }
        }
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    // ========================================
    // Booking Mode Management
    // ========================================
    
    private fun setBookingMode(mode: String) {
        bookingMode = mode
        
        when (mode) {
            "INSTANT" -> {
                // Update button styles - Instant selected
                instantButton?.setBackgroundResource(R.drawable.bg_toggle_instant_selected)
                instantButton?.setTextColor(android.graphics.Color.WHITE)
                instantButton?.setTypeface(null, android.graphics.Typeface.BOLD)
                
                customButton?.setBackgroundResource(R.drawable.bg_toggle_unselected)
                customButton?.setTextColor(android.graphics.Color.parseColor("#666666"))
                customButton?.setTypeface(null, android.graphics.Typeface.NORMAL)
                
                // Show TO input (2 locations needed)
                toLocationContainer?.visible()
                toLocationInput.visible()
                fromLocationInput.hint = "Pickup location"
                continueButton.text = "Continue"
            }
            "CUSTOM" -> {
                // Update button styles - Custom selected
                customButton?.setBackgroundResource(R.drawable.bg_toggle_custom_selected)
                customButton?.setTextColor(android.graphics.Color.WHITE)
                customButton?.setTypeface(null, android.graphics.Typeface.BOLD)
                
                instantButton?.setBackgroundResource(R.drawable.bg_toggle_unselected)
                instantButton?.setTextColor(android.graphics.Color.parseColor("#666666"))
                instantButton?.setTypeface(null, android.graphics.Typeface.NORMAL)
                
                // FIX: Hide TO input container entirely (only FROM needed in Custom mode)
                toLocationContainer?.gone()
                toLocationInput.gone()
                toLocationInput.setText("")
                selectedToLocation = null  // Clear TO location selection
                fromLocationInput.hint = "Where do you need your vehicle?"
                continueButton.text = "Next"
            }
        }
    }
    
    companion object {
        private const val MAX_RECENT_LOCATIONS = 5
        private const val KEY_BOOKING_MODE = "BOOKING_MODE"
        private const val KEY_FROM_LOCATION = "FROM_LOCATION"
        private const val KEY_TO_LOCATION = "TO_LOCATION"
        private const val REQUEST_CODE_MAP_SELECTION = 2001
        private const val REQUEST_CODE_LOCATION_SETTINGS = 2002
        private const val REQUEST_CODE_LOCATION_PERMISSION = 2003
    }
}
