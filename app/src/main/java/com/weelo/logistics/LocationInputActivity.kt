package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.weelo.logistics.core.util.gone
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.visible
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.presentation.location.LocationInputViewModel
import com.weelo.logistics.presentation.location.LocationNavigationEvent
import dagger.hilt.android.AndroidEntryPoint

/**
 * LocationInputActivity - Location Selection Screen
 * 
 * Second screen in booking flow where users enter pickup and drop-off locations.
 * 
 * Features:
 * - Manual text input for locations
 * - Google Places Autocomplete integration
 * - Recent locations display (max 5)
 * - Input validation via ViewModel
 * 
 * Architecture:
 * - MVVM pattern with Hilt DI
 * - Uses Google Places API for autocomplete
 * - LiveData for reactive UI updates
 * 
 * User Flow:
 * 1. User enters FROM location (pickup)
 * 2. User enters TO location (drop-off)
 * 3. User clicks Continue → navigates to MapBookingActivity
 * 
 * @see LocationInputViewModel for business logic
 * @see MapBookingActivity for next screen
 * @see PlacesHelper for Google Places integration
 */
@AndroidEntryPoint
class LocationInputActivity : AppCompatActivity() {

    // ViewModel injected by Hilt
    private val viewModel: LocationInputViewModel by viewModels()

    // UI Components
    private lateinit var fromLocationInput: android.widget.AutoCompleteTextView
    private lateinit var toLocationInput: android.widget.AutoCompleteTextView
    private lateinit var continueButton: Button
    private lateinit var backButton: ImageView
    private lateinit var selectOnMapButton: Button
    private lateinit var addStopsButton: Button
    private lateinit var recentLocationsContainer: LinearLayout
    private lateinit var intermediateStopsContainer: LinearLayout
    private lateinit var bottomDottedLine: View
    
    // UI State Views
    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: TextView? = null
    
    // Intermediate stops data
    private val intermediateStops = mutableListOf<String>()

    // ========================================
    // Lifecycle Methods
    // ========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_input)

        initializeViews()
        setupListeners()
        observeViewModel()
        
        // Restore locations if passed from map
        @Suppress("DEPRECATION")
        val fromLoc = intent.getParcelableExtra<Location>("FROM_LOCATION")
        fromLoc?.let {
            fromLocationInput.setText(it.address)
        }
        @Suppress("DEPRECATION")
        val toLoc = intent.getParcelableExtra<Location>("TO_LOCATION")
        toLoc?.let {
            toLocationInput.setText(it.address)
        }
        
        // Restore existing stops if passed
        val existingStops = intent.getStringArrayExtra("INTERMEDIATE_STOPS")
        if (existingStops != null && existingStops.isNotEmpty()) {
            existingStops.forEach { stopName ->
                intermediateStops.add(stopName)
                val stopView = createIntermediateStopView(intermediateStops.size)
                intermediateStopsContainer.addView(stopView)
                val stopInput = stopView.findViewById<android.widget.AutoCompleteTextView>(R.id.stopLocationInput)
                stopInput?.setText(stopName)
            }
            bottomDottedLine.visible()
        }
        
        // Check if we should automatically add a stop
        if (intent.getBooleanExtra("AUTO_ADD_STOP", false)) {
            // Add stop automatically when coming from map
            addIntermediateStop()
        }
    }

    // ========================================
    // UI Initialization
    // ========================================

    /**
     * Initializes all UI components
     * Safe initialization - handles missing views gracefully
     */
    private fun initializeViews() {
        try {
            fromLocationInput = findViewById(R.id.fromLocationInput)
            toLocationInput = findViewById(R.id.toLocationInput)
            continueButton = findViewById(R.id.continueButton)
            backButton = findViewById(R.id.backButton)
            selectOnMapButton = findViewById(R.id.selectOnMapButton)
            addStopsButton = findViewById(R.id.addStopsButton)
            recentLocationsContainer = findViewById(R.id.recentJammu)
            intermediateStopsContainer = findViewById(R.id.intermediateStopsContainer)
            bottomDottedLine = findViewById(R.id.bottomDottedLine)
            
            // Setup Places Autocomplete adapters
            setupPlacesAutocomplete()
            
            // Optional state views (not in current layout, set to null)
            // These views can be added to the layout later if needed
            loadingView = null
            emptyView = null
            errorView = null
        } catch (e: Exception) {
            showToast("Error initializing screen: ${e.message}")
            finish()
        }
    }
    
    /**
     * Setup Google Places Autocomplete for inline suggestions
     */
    private fun setupPlacesAutocomplete() {
        try {
            // Initialize Places API
            if (!com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initialize(
                    applicationContext,
                    getString(R.string.google_maps_key)
                )
            }
            
            // Get Places client
            val placesClient = com.google.android.libraries.places.api.Places.createClient(this)
            
            // Create and set adapter for FROM location
            val fromAdapter = com.weelo.logistics.adapters.PlacesAutoCompleteAdapter(this, placesClient)
            fromLocationInput.setAdapter(fromAdapter)
            fromLocationInput.threshold = 1
            fromLocationInput.setOnItemClickListener { parent, _, position, _ ->
                val prediction = fromAdapter.getPrediction(position)
                prediction?.let {
                    fromLocationInput.setText(it.getFullText(null).toString())
                    fromLocationInput.dismissDropDown()
                }
            }
            
            // Create and set adapter for TO location
            val toAdapter = com.weelo.logistics.adapters.PlacesAutoCompleteAdapter(this, placesClient)
            toLocationInput.setAdapter(toAdapter)
            toLocationInput.threshold = 1
            toLocationInput.setOnItemClickListener { parent, _, position, _ ->
                val prediction = toAdapter.getPrediction(position)
                prediction?.let {
                    toLocationInput.setText(it.getFullText(null).toString())
                    toLocationInput.dismissDropDown()
                }
            }
            
            // Make fields editable
            fromLocationInput.isFocusable = true
            fromLocationInput.isFocusableInTouchMode = true
            toLocationInput.isFocusable = true
            toLocationInput.isFocusableInTouchMode = true
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error setting up location search")
        }
    }

    /**
     * Sets up click listeners for all interactive components
     */
    private fun setupListeners() {
        backButton.setOnClickListener { handleBackClick() }
        continueButton.setOnClickListener { handleContinueClick() }
        selectOnMapButton.setOnClickListener { handleSelectOnMapClick() }
        
        // Add stops button - Add intermediate stop functionality
        addStopsButton.setOnClickListener {
            addIntermediateStop()
        }
        
        // AutoCompleteTextView will show dropdown automatically when typing
        // No need for explicit click listeners as the adapter handles it
    }

    // ========================================
    // User Interactions
    // ========================================

    /**
     * Handles back button click
     * Returns to previous screen with slide animation
     */
    private fun handleBackClick() {
        finish()
        TransitionHelper.applySlideOutRightTransition(this)
    }

    /**
     * Handles continue button click
     * Validates inputs and proceeds to map screen
     */
    private fun handleContinueClick() {
        // Disable button immediately for instant feedback
        continueButton.isEnabled = false
        
        val fromAddress = fromLocationInput.text.toString()
        val toAddress = toLocationInput.text.toString()
        
        // Quick validation before calling ViewModel
        if (fromAddress.isBlank() || toAddress.isBlank()) {
            showToast("Please enter both locations")
            continueButton.isEnabled = true
            return
        }
        
        // Post to avoid blocking UI thread
        continueButton.postDelayed({
            viewModel.onContinueClicked(fromAddress, toAddress)
        }, 50)
    }

    /**
     * Handles "Select on Map" button click
     * Opens MapSelectionActivity to select location by dragging pin
     */
    private fun handleSelectOnMapClick() {
        // Navigate to map selection screen
        val intent = Intent(this, MapSelectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_MAP_SELECTION)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    /**
     * Adds an intermediate stop between pickup and drop locations
     */
    private fun addIntermediateStop() {
        if (intermediateStops.size >= MAX_INTERMEDIATE_STOPS) {
            showToast("Maximum $MAX_INTERMEDIATE_STOPS stops allowed")
            return
        }
        
        // Add empty stop to list
        intermediateStops.add("")
        
        // Show bottom dotted line
        bottomDottedLine.visible()
        
        // Create stop view
        val stopView = createIntermediateStopView(intermediateStops.size)
        intermediateStopsContainer.addView(stopView)
        
        // Focus on the new input
        val stopInput = stopView.findViewById<android.widget.AutoCompleteTextView>(R.id.stopLocationInput)
        stopInput?.requestFocus()
    }
    
    /**
     * Creates a view for an intermediate stop
     */
    private fun createIntermediateStopView(stopNumber: Int): View {
        val view = LayoutInflater.from(this).inflate(
            R.layout.item_intermediate_stop, 
            intermediateStopsContainer, 
            false
        )
        
        val stopInput = view.findViewById<android.widget.AutoCompleteTextView>(R.id.stopLocationInput)
        val stopNumberView = view.findViewById<TextView>(R.id.stopNumber)
        val removeButton = view.findViewById<ImageView>(R.id.removeStopButton)
        val dottedLineTop = view.findViewById<View>(R.id.dottedLineTop)
        
        // Set stop number
        stopNumberView?.text = stopNumber.toString()
        
        // Setup autocomplete for this stop
        setupStopAutocomplete(stopInput)
        
        // Handle text changes
        stopInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val index = stopNumber - 1
                if (index >= 0 && index < intermediateStops.size) {
                    intermediateStops[index] = s?.toString() ?: ""
                }
            }
        })
        
        // Remove button
        removeButton?.setOnClickListener {
            removeIntermediateStop(stopNumber - 1, view)
        }
        
        // Show top dotted line
        dottedLineTop?.visible()
        
        return view
    }
    
    /**
     * Removes an intermediate stop
     */
    private fun removeIntermediateStop(index: Int, view: View) {
        if (index >= 0 && index < intermediateStops.size) {
            intermediateStops.removeAt(index)
            intermediateStopsContainer.removeView(view)
            
            // Update stop numbers
            updateStopNumbers()
            
            // Hide bottom dotted line if no stops
            if (intermediateStops.isEmpty()) {
                bottomDottedLine.gone()
            }
        }
    }
    
    /**
     * Updates stop numbers after removal
     */
    private fun updateStopNumbers() {
        for (i in 0 until intermediateStopsContainer.childCount) {
            val stopView = intermediateStopsContainer.getChildAt(i)
            val stopNumberView = stopView.findViewById<TextView>(R.id.stopNumber)
            stopNumberView?.text = (i + 1).toString()
        }
    }
    
    /**
     * Setup autocomplete for intermediate stop
     */
    private fun setupStopAutocomplete(stopInput: android.widget.AutoCompleteTextView?) {
        if (stopInput == null) return
        
        try {
            val placesClient = com.google.android.libraries.places.api.Places.createClient(this)
            val adapter = com.weelo.logistics.adapters.PlacesAutoCompleteAdapter(this, placesClient)
            stopInput.setAdapter(adapter)
            stopInput.threshold = 1
            stopInput.setOnItemClickListener { parent, view, position, id ->
                val prediction = adapter.getPrediction(position)
                prediction?.let {
                    stopInput.setText(it.getFullText(null).toString())
                    stopInput.dismissDropDown()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========================================
    // Google Places Integration
    // ========================================

    /**
     * Handles result from Google Places Autocomplete
     * Updates FROM location input with selected place
     * 
     * @param requestCode Request code from Places API
     * @param resultCode Result status
     * @param data Intent containing place data
     */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle map selection result
        if (requestCode == REQUEST_CODE_MAP_SELECTION && resultCode == Activity.RESULT_OK) {
            data?.let {
                val selectedLocation = it.getParcelableExtra<Location>("SELECTED_LOCATION")
                selectedLocation?.let { loc ->
                    fromLocationInput.setText(loc.address)
                }
            }
            return
        }
    }

    // ========================================
    // ViewModel Observation
    // ========================================

    /**
     * Observes ViewModel state and navigation events
     */
    private fun observeViewModel() {
        observeUiState()
        observeNavigationEvents()
    }

    /**
     * Observes UI state changes
     * Handles loading states, recent locations, and errors
     * 
     * UI STATE COVERAGE:
     * - loading: Show loading indicator, disable buttons
     * - empty: Show empty state message
     * - partial: Show available data
     * - success: Show full content
     * - error: Show error message
     */
    private fun observeUiState() {
        viewModel.uiState.observe(this) { state ->
            // Prevent null state crashes
            if (state == null) return@observe
            
            // Handle different UI states
            when {
                state.isLoading -> showLoadingState()
                state.errorMessage != null -> showErrorState(state.errorMessage)
                state.isEmpty() -> showEmptyState()
                else -> showSuccessState()
            }
            
            // Update UI regardless of state
            updateButtonState(state.isLoading)
            displayRecentLocations(state.recentLocations)
        }
    }

    /**
     * Observes navigation events
     * Handles screen transitions
     */
    private fun observeNavigationEvents() {
        viewModel.navigationEvent.observe(this) { event ->
            when (event) {
                is LocationNavigationEvent.NavigateToMap -> {
                    // Navigate immediately without delay
                    navigateToMap(event.fromLocation, event.toLocation)
                    // Re-enable button in case user comes back
                    continueButton.postDelayed({ 
                        continueButton.isEnabled = true 
                    }, 500)
                }
            }
        }
    }

    // ========================================
    // UI Updates
    // ========================================

    /**
     * Updates continue button state based on loading
     * 
     * @param isLoading true if loading, false otherwise
     */
    private fun updateButtonState(isLoading: Boolean) {
        continueButton.isEnabled = !isLoading
    }

    // ========================================
    // UI State Management
    // ========================================
    
    /**
     * Shows loading state
     * Disables interactions, shows loading indicator
     */
    private fun showLoadingState() {
        loadingView?.visible()
        emptyView?.gone()
        errorView?.gone()
        recentLocationsContainer.gone()
        continueButton.isEnabled = false
    }
    
    /**
     * Shows empty state
     * No recent locations available
     */
    private fun showEmptyState() {
        loadingView?.gone()
        emptyView?.visible()
        errorView?.gone()
        recentLocationsContainer.gone()
        continueButton.isEnabled = true
    }
    
    /**
     * Shows error state
     * Displays error message
     */
    private fun showErrorState(errorMessage: String) {
        loadingView?.gone()
        emptyView?.gone()
        errorView?.visible()
        errorView?.text = errorMessage
        recentLocationsContainer.gone()
        continueButton.isEnabled = true
        
        // Also show toast for immediate feedback
        showToast(errorMessage)
        viewModel.clearError()
    }
    
    /**
     * Shows success state
     * Normal operation with data
     */
    private fun showSuccessState() {
        loadingView?.gone()
        emptyView?.gone()
        errorView?.gone()
        recentLocationsContainer.visible()
        continueButton.isEnabled = true
    }

    /**
     * Displays recent locations in UI
     * Shows max 5 most recent locations
     * 
     * SAFE DATA HANDLING:
     * - Handles empty list
     * - Handles null items gracefully
     * - Limits display to MAX_RECENT_LOCATIONS
     * 
     * @param locations List of recent locations from ViewModel
     */
    private fun displayRecentLocations(locations: List<LocationModel>) {
        try {
            recentLocationsContainer.removeAllViews()

            if (locations.isEmpty()) {
                recentLocationsContainer.gone()
                return
            }

            recentLocationsContainer.visible()
            locations.take(MAX_RECENT_LOCATIONS).forEach { location ->
                try {
                    addRecentLocationView(location)
                } catch (e: Exception) {
                    // Skip this location if there's an error
                    android.util.Log.e("LocationInput", "Error adding location view", e)
                }
            }
        } catch (e: Exception) {
            // Don't crash if recent locations fail
            android.util.Log.e("LocationInput", "Error displaying recent locations", e)
        }
    }

    /**
     * Creates and adds a recent location view to container
     * 
     * SAFE DATA HANDLING:
     * - Validates location has address
     * - Handles missing views in layout
     * - Safe click handling
     * 
     * @param location Location data to display
     */
    private fun addRecentLocationView(location: LocationModel) {
        // Skip if location is invalid
        if (!location.isValid()) return
        
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_recent_location, recentLocationsContainer, false)

        // Safely set text with fallback
        view.findViewById<TextView>(R.id.locationText)?.text = 
            location.toShortString()
        
        view.findViewById<TextView>(R.id.favoriteIcon)?.text = 
            if (location.isFavorite) "❤️" else "🤍"

        view.setOnClickListener {
            try {
                fromLocationInput.setText(location.address)
            } catch (e: Exception) {
                showToast("Error selecting location")
            }
        }

        recentLocationsContainer.addView(view)
    }

    // ========================================
    // Navigation
    // ========================================

    /**
     * Navigates to MapBookingActivity with selected locations
     * 
     * @param fromLocation Pickup location
     * @param toLocation Drop-off location
     */
    private fun navigateToMap(fromLocation: LocationModel, toLocation: LocationModel) {
        val intent = Intent(this, MapBookingActivity::class.java).apply {
            putExtra(KEY_FROM_LOCATION, Location(
                address = fromLocation.address,
                latitude = fromLocation.latitude,
                longitude = fromLocation.longitude
            ))
            putExtra(KEY_TO_LOCATION, Location(
                address = toLocation.address,
                latitude = toLocation.latitude,
                longitude = toLocation.longitude
            ))
            // Pass intermediate stops as string array
            if (intermediateStops.isNotEmpty()) {
                val validStops = intermediateStops.filter { it.isNotBlank() }.toTypedArray()
                putExtra("INTERMEDIATE_STOPS", validStops)
            }
        }
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    // ========================================
    // Constants
    // ========================================

    companion object {
        private const val MAX_RECENT_LOCATIONS = 5
        private const val MAX_INTERMEDIATE_STOPS = 3
        private const val KEY_FROM_LOCATION = "FROM_LOCATION"
        private const val KEY_TO_LOCATION = "TO_LOCATION"
        private const val REQUEST_CODE_MAP_SELECTION = 2001
    }
}
