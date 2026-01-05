package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.presentation.home.HomeNavigationEvent
import com.weelo.logistics.presentation.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

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
 * @see HomeViewModel for business logic
 * @see LocationInputActivity for next screen in flow
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // ViewModel injected by Hilt
    private val viewModel: HomeViewModel by viewModels()
    
    // UI Components
    private lateinit var searchContainer: CardView

    // ========================================
    // Lifecycle Methods
    // ========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupListeners()
        observeViewModel()
    }

    // ========================================
    // UI Initialization
    // ========================================

    /**
     * Initializes all UI components
     */
    private fun initializeViews() {
        searchContainer = findViewById(R.id.searchContainer)
    }

    /**
     * Sets up click listeners for UI components
     */
    private fun setupListeners() {
        searchContainer.setOnClickListener {
            handleSearchClick()
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
}
