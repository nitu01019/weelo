package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
import com.weelo.logistics.presentation.location.LocationPlacesHelper
import com.weelo.logistics.presentation.location.IntermediateStopsManager
import com.weelo.logistics.tutorial.OnboardingManager
import com.weelo.logistics.tutorial.TutorialCoordinator
import dagger.hilt.android.AndroidEntryPoint

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
    private lateinit var fromLocationInput: android.widget.AutoCompleteTextView
    private lateinit var toLocationInput: android.widget.AutoCompleteTextView
    private lateinit var continueButton: Button
    private lateinit var backButton: ImageView
    private lateinit var selectOnMapButton: Button
    private lateinit var addStopsButton: Button
    private lateinit var recentLocationsContainer: LinearLayout
    private lateinit var intermediateStopsContainer: LinearLayout
    private lateinit var bottomDottedLine: View

    // State Views
    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: TextView? = null

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
    }

    // ========================================
    // Initialization
    // ========================================

    private fun initializeHelpers() {
        placesHelper = LocationPlacesHelper.getInstance(this)
        placesHelper.initialize()
    }

    private fun initializeViews() {
        try {
            // Bind views
            fromLocationInput = findViewById(R.id.fromLocationInput)
            toLocationInput = findViewById(R.id.toLocationInput)
            continueButton = findViewById(R.id.continueButton)
            backButton = findViewById(R.id.backButton)
            selectOnMapButton = findViewById(R.id.selectOnMapButton)
            addStopsButton = findViewById(R.id.addStopsButton)
            recentLocationsContainer = findViewById(R.id.recentJammu)
            intermediateStopsContainer = findViewById(R.id.intermediateStopsContainer)
            bottomDottedLine = findViewById(R.id.bottomDottedLine)

            // Setup Places autocomplete using helper
            placesHelper.setupAutocomplete(fromLocationInput)
            placesHelper.setupAutocomplete(toLocationInput)

            // Initialize stops manager
            stopsManager = IntermediateStopsManager(
                context = this,
                container = intermediateStopsContainer,
                bottomDottedLine = bottomDottedLine,
                placesHelper = placesHelper
            )

        } catch (e: Exception) {
            showToast("Error initializing screen: ${e.message}")
            finish()
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener { handleBack() }
        continueButton.setOnClickListener { handleContinue() }
        selectOnMapButton.setOnClickListener { handleSelectOnMap() }
        addStopsButton.setOnClickListener { handleAddStop() }
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

    private fun handleContinue() {
        continueButton.isEnabled = false
        
        val from = fromLocationInput.text.toString()
        val to = toLocationInput.text.toString()
        
        if (from.isBlank() || to.isBlank()) {
            showToast("Please enter both locations")
            continueButton.isEnabled = true
            return
        }
        
        viewModel.onContinueClicked(from, to)
    }

    private fun handleSelectOnMap() {
        startActivityForResult(
            Intent(this, MapSelectionActivity::class.java),
            REQUEST_CODE_MAP_SELECTION
        )
        TransitionHelper.applySlideInLeftTransition(this)
    }

    private fun handleAddStop() {
        if (!stopsManager.addStop()) {
            showToast("Maximum ${IntermediateStopsManager.DEFAULT_MAX_STOPS} stops allowed")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MAP_SELECTION && resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra<Location>("SELECTED_LOCATION")?.let {
                fromLocationInput.setText(it.address)
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
        loadingView?.visible()
        emptyView?.gone()
        errorView?.gone()
        recentLocationsContainer.gone()
    }

    private fun showEmptyState() {
        loadingView?.gone()
        emptyView?.visible()
        errorView?.gone()
        recentLocationsContainer.gone()
    }

    private fun showErrorState(message: String) {
        loadingView?.gone()
        emptyView?.gone()
        errorView?.apply {
            visible()
            text = message
        }
        recentLocationsContainer.gone()
        showToast(message)
        viewModel.clearError()
    }

    private fun showSuccessState() {
        loadingView?.gone()
        emptyView?.gone()
        errorView?.gone()
        recentLocationsContainer.visible()
    }

    private fun displayRecentLocations(locations: List<LocationModel>) {
        recentLocationsContainer.removeAllViews()
        
        if (locations.isEmpty()) {
            recentLocationsContainer.gone()
            return
        }

        recentLocationsContainer.visible()
        locations.take(MAX_RECENT_LOCATIONS).forEach { location ->
            if (location.isValid()) {
                addRecentLocationView(location)
            }
        }
    }

    private fun addRecentLocationView(location: LocationModel) {
        val view = layoutInflater.inflate(
            R.layout.item_recent_location, 
            recentLocationsContainer, 
            false
        )
        
        view.findViewById<TextView>(R.id.locationText)?.text = location.toShortString()
        view.findViewById<TextView>(R.id.favoriteIcon)?.text = 
            if (location.isFavorite) "❤️" else "🤍"
        
        view.setOnClickListener {
            fromLocationInput.setText(location.address)
        }
        
        recentLocationsContainer.addView(view)
    }

    // ========================================
    // Navigation
    // ========================================

    private fun navigateToMap(from: LocationModel, to: LocationModel) {
        val intent = Intent(this, MapBookingActivity::class.java).apply {
            putExtra(KEY_FROM_LOCATION, Location(from.address, from.latitude, from.longitude))
            putExtra(KEY_TO_LOCATION, Location(to.address, to.latitude, to.longitude))
            
            val validStops = stopsManager.getValidStops()
            if (validStops.isNotEmpty()) {
                putExtra("INTERMEDIATE_STOPS", validStops.toTypedArray())
            }
        }
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    companion object {
        private const val MAX_RECENT_LOCATIONS = 5
        private const val KEY_FROM_LOCATION = "FROM_LOCATION"
        private const val KEY_TO_LOCATION = "TO_LOCATION"
        private const val REQUEST_CODE_MAP_SELECTION = 2001
    }
}
