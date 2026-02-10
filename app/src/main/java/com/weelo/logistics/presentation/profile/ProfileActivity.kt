package com.weelo.logistics.presentation.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.weelo.logistics.R
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.data.local.preferences.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =============================================================================
 * PROFILE ACTIVITY - Rapido-Style Menu Screen
 * =============================================================================
 * 
 * SCALABILITY:
 * - Lightweight UI with list-based menu items
 * - Loads data on-demand from backend
 * - Handles millions of concurrent users
 * 
 * EASY UNDERSTANDING:
 * - Simple click handlers for each menu item
 * - Clear navigation flow
 * - Standard Android patterns
 * 
 * MODULARITY:
 * - Each menu item navigates to separate screen
 * - Profile/rating card at top, menu items below
 * - Reusable UI components
 * 
 * CODING STANDARDS:
 * - Consistent naming
 * - Material Design 3
 * - Proper error handling
 * - Clean code structure
 * =============================================================================
 */
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    
    // SCALABILITY: Inject PreferencesManager for instant cache access
    @Inject
    lateinit var preferencesManager: PreferencesManager

    // UI Elements
    private lateinit var profileName: TextView
    private lateinit var profilePhone: TextView
    private lateinit var profileCard: View
    private lateinit var skeletonProfileCard: View
    private lateinit var helpItem: View
    private lateinit var parcelItem: View
    private lateinit var paymentItem: View
    private lateinit var myRidesItem: View
    private lateinit var safetyItem: View
    private lateinit var referItem: View
    private lateinit var rewardsItem: View
    private lateinit var powerPassItem: View
    private lateinit var coinsItem: View
    private lateinit var notificationsItem: View
    private lateinit var logoutButton: Button
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        
        // RAPIDO-STYLE: Load cached data IMMEDIATELY (before ViewModel)
        loadCachedDataSync()
        
        setupListeners()
        observeViewModel()
        
        // SCALABILITY: Load profile data from backend with caching
        // This will refresh in background if data already shown
        viewModel.loadProfile()
    }

    /**
     * Initialize all views
     * 
     * CODING STANDARDS: Clear view initialization
     */
    private fun initViews() {
        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        
        // Back button in toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Profile elements
        profileName = findViewById(R.id.profileName)
        profilePhone = findViewById(R.id.profilePhone)
        profileCard = findViewById(R.id.profileCard)
        skeletonProfileCard = findViewById(R.id.skeletonProfileCard)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        // Menu items
        helpItem = findViewById(R.id.helpItem)
        parcelItem = findViewById(R.id.parcelItem)
        paymentItem = findViewById(R.id.paymentItem)
        myRidesItem = findViewById(R.id.myRidesItem)
        safetyItem = findViewById(R.id.safetyItem)
        referItem = findViewById(R.id.referItem)
        rewardsItem = findViewById(R.id.rewardsItem)
        powerPassItem = findViewById(R.id.powerPassItem)
        coinsItem = findViewById(R.id.coinsItem)
        notificationsItem = findViewById(R.id.notificationsItem)
        logoutButton = findViewById(R.id.logoutButton)
    }

    /**
     * Setup click listeners for all menu items
     * 
     * MODULARITY: Each menu item has separate handler
     * EASY UNDERSTANDING: Clear click actions
     */
    private fun setupListeners() {
        // MODULARITY: Profile card click - navigate to edit screen
        profileCard.setOnClickListener {
            showToast("Edit Profile - Coming Soon")
            // TODO: Navigate to profile edit screen
        }

        // Help menu item
        helpItem.setOnClickListener {
            showToast("Help - Coming Soon")
            // TODO: Navigate to help center
        }

        // Parcel menu item
        parcelItem.setOnClickListener {
            showToast("Parcel - Send Items - Coming Soon")
            // TODO: Navigate to parcel booking screen
        }

        // Payment menu item
        paymentItem.setOnClickListener {
            showToast("Payment - Coming Soon")
            // TODO: Navigate to payment/wallet screen
        }

        // My Rides menu item
        myRidesItem.setOnClickListener {
            showToast("My Rides - Coming Soon")
            // TODO: Navigate to ride history
        }

        // Safety menu item
        safetyItem.setOnClickListener {
            showToast("Safety - Coming Soon")
            // TODO: Navigate to safety screen
        }

        // Refer and Earn menu item
        referItem.setOnClickListener {
            showToast("Refer and Earn - Coming Soon")
            // TODO: Navigate to referral screen
        }

        // My Rewards menu item
        rewardsItem.setOnClickListener {
            showToast("My Rewards - Coming Soon")
            // TODO: Navigate to rewards screen
        }

        // Power Pass menu item
        powerPassItem.setOnClickListener {
            showToast("Power Pass - Coming Soon")
            // TODO: Navigate to power pass subscription
        }

        // Weelo Coins menu item
        coinsItem.setOnClickListener {
            showToast("Weelo Coins - Coming Soon")
            // TODO: Navigate to coins/wallet screen
        }

        // Notifications menu item
        notificationsItem.setOnClickListener {
            showToast("Notifications - Coming Soon")
            // TODO: Navigate to notification settings
        }

        // Logout button
        logoutButton.setOnClickListener {
            viewModel.logout()
        }
    }

    /**
     * Observe ViewModel state changes
     * 
     * SCALABILITY: Efficient state management with Flow
     * CODING STANDARDS: Proper lifecycle handling
     * 
     * RAPIDO-STYLE: Uses skeleton loading (no loading spinner)
     * NEVER hides data once displayed
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ProfileUiState.Loading -> {
                        // RAPIDO-STYLE: Only show skeleton if NO data currently displayed
                        if (profileName.text.isEmpty()) {
                            skeletonProfileCard.visibility = View.VISIBLE
                            profileCard.visibility = View.GONE
                            timber.log.Timber.d("Loading with no data - showing skeleton")
                        } else {
                            // Keep showing current data while refreshing
                            timber.log.Timber.d("Loading with data - keeping card visible")
                        }
                        loadingOverlay.visibility = View.GONE
                    }
                    
                    is ProfileUiState.Success -> {
                        // RAPIDO-STYLE: Hide skeleton, show real content
                        skeletonProfileCard.visibility = View.GONE
                        profileCard.visibility = View.VISIBLE
                        loadingOverlay.visibility = View.GONE
                        
                        // Populate profile data with fallback
                        // Show "Customer" if name is empty, not the phone number
                        profileName.text = state.profile.name.ifEmpty { "Customer" }
                        profilePhone.text = formatPhoneNumber(state.profile.phone)
                        
                        timber.log.Timber.d("Profile updated: ${state.profile.name}")
                    }
                    
                    is ProfileUiState.Error -> {
                        skeletonProfileCard.visibility = View.GONE
                        profileCard.visibility = View.VISIBLE
                        loadingOverlay.visibility = View.GONE
                        showToast(state.message)
                    }
                    
                    is ProfileUiState.Saved -> {
                        loadingOverlay.visibility = View.GONE
                        showToast("Profile saved successfully!")
                    }
                    
                    is ProfileUiState.LoggedOut -> {
                        // MODULARITY: Navigate to login screen
                        showToast("Logged out")
                        val intent = Intent(
                            this@ProfileActivity, 
                            com.weelo.logistics.presentation.auth.LoginActivity::class.java
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    /**
     * Load cached profile data IMMEDIATELY (Rapido-style)
     * 
     * SCALABILITY:
     * - Reads cache synchronously on UI thread (instant)
     * - Shows data BEFORE ViewModel emits state
     * - No waiting for network/database
     * 
     * RAPIDO-STYLE:
     * - Number ALWAYS visible
     * - No blank screens
     * - Skeleton only on true first load
     */
    private fun loadCachedDataSync() {
        lifecycleScope.launch {
            val cached = preferencesManager.getCachedProfile()
            
            if (cached != null) {
                // RAPIDO-STYLE: Show cached data INSTANTLY
                timber.log.Timber.d("Displaying cached profile instantly: ${cached.name}")
                
                profileName.text = cached.name.ifEmpty { "Customer" }
                profilePhone.text = formatPhoneNumber(cached.phone)
                
                // Show real card, hide skeleton
                profileCard.visibility = View.VISIBLE
                skeletonProfileCard.visibility = View.GONE
            } else {
                // First time - no cache
                timber.log.Timber.d("No cache - will show skeleton")
                
                // Show skeleton for first load
                profileCard.visibility = View.GONE
                skeletonProfileCard.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Format phone number for display
     * 
     * EASY UNDERSTANDING: Simple string formatting
     */
    private fun formatPhoneNumber(phone: String): String {
        return if (phone.length == 10) {
            phone
        } else if (phone.startsWith("+91")) {
            phone.substring(3)
        } else {
            phone
        }
    }
}
