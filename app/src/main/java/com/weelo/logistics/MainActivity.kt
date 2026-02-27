package com.weelo.logistics

import android.content.Intent
import android.os.Bundle
import timber.log.Timber
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.repository.BookingApiRepository
import com.weelo.logistics.presentation.home.HomeNavigationEvent
import com.weelo.logistics.presentation.home.HomeViewModel
import com.weelo.logistics.presentation.profile.ProfileActivity
import com.weelo.logistics.tutorial.OnboardingManager
import com.weelo.logistics.tutorial.TutorialCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainActivity - Home/Landing Screen
 * 
 * This is the entry point of the Weelo Logistics application.
 * Displays the main search interface and navigation to booking flow.
 * 
 * Architecture:
 * - Follows MVVM pattern with Hilt dependency injection
 * - Uses ViewModel for business logic and state management
 * - Observes LiveData for UI updates and navigation
 * 
 * User Flow:
 * 1. User sees home screen with search bar
 * 2. User clicks search → navigates to LocationInputActivity
 * 
 * Features:
 * - First-time user tutorial showing welcome and how to search
 * 
 * @see HomeViewModel for business logic
 * @see LocationInputActivity for next screen in flow
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var bookingRepository: BookingApiRepository

    // ViewModel injected by Hilt
    private val viewModel: HomeViewModel by viewModels()
    
    // UI Components
    private lateinit var searchContainer: CardView
    private var profileButton: LinearLayout? = null
    private var trackButton: LinearLayout? = null
    
    // Tutorial
    private var tutorialCoordinator: TutorialCoordinator? = null
    private var reconcileActiveOrderJob: Job? = null
    private var activeOrderRoutedThisSession: Boolean = false

    // ========================================
    // Lifecycle Methods
    // ========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupListeners()
        observeViewModel()
        handleNotificationIntent(intent)

        // Start tutorial for first-time users (after layout settles)
        window.decorView.post {
            startTutorialIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Route FCM notification taps to the correct screen.
     * Extras: notification_type, booking_id, trip_id, trip_status
     */
    private fun handleNotificationIntent(intent: Intent?) {
        val notificationType = intent?.getStringExtra("notification_type") ?: return
        val bookingId = intent.getStringExtra("booking_id")
        @Suppress("UNUSED_VARIABLE") // read to clear extra; may be used for future deep-link routing
        val tripId = intent.getStringExtra("trip_id")
        val tripStatus = intent.getStringExtra("trip_status")
        // Clear extras so rotation / activity recreation doesn't re-trigger navigation
        intent.removeExtra("notification_type")
        intent.removeExtra("booking_id")
        intent.removeExtra("trip_id")
        intent.removeExtra("trip_status")
        when (notificationType) {
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_BOOKING_CONFIRMED,
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_DRIVER_ASSIGNED,
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_DRIVER_ARRIVING,
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_TRIP_STARTED,
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_TRIP_STATUS_UPDATE,
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_TRIP_UPDATE -> {
                // Route using bookingId when available, fall back to tripId for trip-only updates
                // Only route with bookingId — tripId is not a valid substitute for API calls
                val routingId = bookingId?.takeIf { it.isNotBlank() }
                Timber.d("Routing trip notification: type=$notificationType bookingId=$routingId tripStatus=$tripStatus")
                if (routingId != null) {
                    val trackIntent = com.weelo.logistics.presentation.booking.BookingTrackingActivity
                        .newIntent(this, routingId)
                    startActivity(trackIntent)
                } else {
                    Timber.w("Trip notification received without bookingId — cannot route to tracking screen")
                }
            }
            com.weelo.logistics.data.remote.WeeloFirebaseService.TYPE_TRIP_COMPLETED -> {
                val myTripsIntent = com.weelo.logistics.presentation.booking.MyBookingsActivity
                    .newIntent(this)
                startActivity(myTripsIntent)
            }
            else -> { /* stay on home screen */ }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        reconcileActiveOrderJob?.cancel()
        tutorialCoordinator = null
    }

    override fun onResume() {
        super.onResume()
        reconcileActiveOrderOnResume()
    }
    
    // ========================================
    // Tutorial
    // ========================================
    
    /**
     * Start home tutorial for first-time users
     */
    private fun startTutorialIfNeeded() {
        val onboardingManager = OnboardingManager.getInstance(this)
        
        tutorialCoordinator = TutorialCoordinator(
            activity = this,
            onboardingManager = onboardingManager,
            onComplete = {
                tutorialCoordinator = null
            }
        )
        
        tutorialCoordinator?.startHomeTutorial()
    }

    // ========================================
    // UI Initialization
    // ========================================

    /**
     * Initializes all UI components
     */
    private fun initializeViews() {
        searchContainer = findViewById(R.id.searchContainer)
        // Find bottom nav buttons
        profileButton = findViewById(R.id.profileNav)
        trackButton = findViewById(R.id.trackNav)
    }

    /**
     * Sets up click listeners for UI components
     */
    private fun setupListeners() {
        searchContainer.setOnClickListener {
            handleSearchClick()
        }
        
        // Profile navigation
        profileButton?.setOnClickListener {
            navigateToProfile()
        }
        
        // My Trips navigation
        trackButton?.setOnClickListener {
            navigateToMyBookings()
        }
    }

    // ========================================
    // User Interactions
    // ========================================

    /**
     * Handles search container click
     * Triggers navigation to location input screen
     */
    private fun handleSearchClick() {
        viewModel.onSearchClicked()
    }

    // ========================================
    // ViewModel Observation
    // ========================================

    /**
     * Observes ViewModel state and navigation events
     * Handles UI updates and screen transitions
     */
    private fun observeViewModel() {
        observeUiState()
        observeNavigationEvents()
    }

    /**
     * Observes UI state changes from ViewModel
     * Handles loading states and error messages
     */
    private fun observeUiState() {
        viewModel.uiState.observe(this) { state ->
            state.errorMessage?.let { error ->
                showToast(error)
                viewModel.clearError()
            }
        }
    }

    /**
     * Observes navigation events from ViewModel
     * Handles screen transitions with proper animations
     */
    private fun observeNavigationEvents() {
        viewModel.navigationEvent.observe(this) { event ->
            when (event) {
                is HomeNavigationEvent.NavigateToVehicleDetails -> {
                    // Handle vehicle details navigation
                }                is HomeNavigationEvent.NavigateToLocationInput -> {
                    navigateToLocationInput()
                }
            }
        }
    }

    // ========================================
    // Navigation
    // ========================================

    /**
     * Navigates to LocationInputActivity
     * User will enter pickup and drop-off locations
     */
    private fun navigateToLocationInput() {
        val intent = Intent(this, LocationInputActivity::class.java)
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    /**
     * Navigates to ProfileActivity
     * User can view and edit their profile
     */
    private fun navigateToProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    /**
     * Navigates to MyBookingsActivity
     * User can see active/completed bookings and track trucks
     */
    private fun navigateToMyBookings() {
        val intent = com.weelo.logistics.presentation.booking.MyBookingsActivity.newIntent(this)
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
    }

    private fun reconcileActiveOrderOnResume() {
        if (activeOrderRoutedThisSession) return
        reconcileActiveOrderJob?.cancel()
        reconcileActiveOrderJob = lifecycleScope.launch {
            try {
                val activeResult = bookingRepository.checkActiveOrder()
                val activeOrderId = when (activeResult) {
                    is Result.Success -> activeResult.data?.orderId
                    else -> null
                } ?: return@launch

                val statusResult = bookingRepository.getOrderStatus(activeOrderId)
                val isActive = (statusResult as? Result.Success)?.data?.isActive == true
                if (!isActive) return@launch

                activeOrderRoutedThisSession = true
                Timber.i("MainActivity: restoring active order flow for $activeOrderId")
                startActivity(
                    com.weelo.logistics.presentation.booking.BookingTrackingActivity
                        .newIntent(this@MainActivity, activeOrderId)
                )
            } catch (e: Exception) {
                Timber.w(e, "MainActivity: active order reconcile failed")
            }
        }
    }
}
