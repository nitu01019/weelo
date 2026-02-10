package com.weelo.logistics.presentation.pricing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.weelo.logistics.R
import com.weelo.logistics.databinding.ActivityPricingBinding
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.core.util.getParcelableArrayListExtraCompat
import com.weelo.logistics.presentation.booking.BookingConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * PricingActivity - Shows pricing breakdown and booking confirmation
 * 
 * ARCHITECTURE:
 * - Uses MVVM with PricingViewModel
 * - Hilt dependency injection
 * - StateFlow for reactive UI
 * - Proper loading states
 * 
 * SCALABILITY:
 * - Backend pricing API integration
 * - Supports surge pricing
 * - Vehicle suggestions for cost optimization
 * 
 * @see PricingViewModel for business logic
 */
@AndroidEntryPoint
class PricingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPricingBinding
    private val viewModel: PricingViewModel by viewModels()

    private var fromLocation: Location? = null
    private var toLocation: Location? = null
    private var selectedTrucks: ArrayList<SelectedTruckItem>? = null
    private var distanceKm: Int = 0

    companion object {
        fun newIntent(
            context: Context,
            fromLocation: Location,
            toLocation: Location,
            selectedTrucks: ArrayList<SelectedTruckItem>,
            distanceKm: Int,
            cargoWeightKg: Int? = null
        ): Intent {
            return Intent(context, PricingActivity::class.java).apply {
                putExtra("from_location", fromLocation)
                putExtra("to_location", toLocation)
                putParcelableArrayListExtra("selected_trucks", selectedTrucks)
                putExtra("distance_km", distanceKm)
                cargoWeightKg?.let { putExtra("cargo_weight_kg", it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPricingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        extractIntentData()
        setupUI()
        observeViewModel()
        initializeViewModel()
    }

    private fun extractIntentData() {
        fromLocation = intent.getParcelableExtraCompat("from_location")
        toLocation = intent.getParcelableExtraCompat("to_location")
        selectedTrucks = intent.getParcelableArrayListExtraCompat("selected_trucks")
        distanceKm = intent.getIntExtra("distance_km", 50)
    }

    private fun setupUI() {
        binding.apply {
            // Route info
            pickupText.text = fromLocation?.address ?: "Pickup Location"
            dropText.text = toLocation?.address ?: "Drop Location"
            distanceText.text = "~ $distanceKm km"

            // Vehicle info (initial display)
            val truckCount = selectedTrucks?.sumOf { it.quantity } ?: 1
            val firstTruck = selectedTrucks?.firstOrNull()
            vehicleName.text = firstTruck?.truckTypeName ?: "Open Truck"
            vehicleCapacity.text = firstTruck?.specification ?: "Standard"
            vehicleDescription.text = "$truckCount truck(s) selected"

            // Back button
            backButton.setOnClickListener { finish() }

            // Confirm button
            confirmBookingButton.setOnClickListener { 
                viewModel.createBooking()
            }
        }
    }

    private fun initializeViewModel() {
        val from = fromLocation ?: return
        val to = toLocation ?: return
        val trucks = selectedTrucks ?: return

        val cargoWeight = intent.getIntExtra("cargo_weight_kg", 0).takeIf { it > 0 }

        viewModel.initialize(
            from = from,
            to = to,
            trucks = trucks.toList(),
            distance = distanceKm,
            cargoWeight = cargoWeight
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observePricingState() }
                launch { observeSuggestionsState() }
            }
        }
    }

    private suspend fun observePricingState() {
        viewModel.uiState.collectLatest { state ->
            when (state) {
                is PricingUiState.Loading -> showLoading()
                is PricingUiState.PriceLoaded -> showPricing(state)
                is PricingUiState.Creating -> showCreatingBooking()
                is PricingUiState.BookingCreated -> navigateToConfirmation(state.bookingId)
                is PricingUiState.OrderCreated -> navigateToOrderConfirmation(state)
                is PricingUiState.Error -> showError(state.message)
            }
        }
    }
    
    /**
     * Navigate to order confirmation screen with multi-truck request info
     */
    private fun navigateToOrderConfirmation(state: PricingUiState.OrderCreated) {
        // Broadcast info available for future use (logging, analytics)
        @Suppress("UNUSED_VARIABLE")
        val broadcastInfo = state.broadcastGroups.joinToString("\n") { group ->
            "${group.count}x ${group.vehicleType} ${group.vehicleSubtype} → ${group.transportersNotified} transporters"
        }
        
        Toast.makeText(
            this,
            "Order created! ${state.totalTrucks} trucks requested.\n${state.transportersNotified} transporters notified.\nTimeout: ${state.timeoutSeconds / 60} min",
            Toast.LENGTH_LONG
        ).show()
        
        // Navigate to booking confirmation with orderId
        val intent = Intent(this, BookingConfirmationActivity::class.java).apply {
            putExtra("ORDER_ID", state.orderId)
            putExtra("BOOKING_ID", state.orderId) // For backward compatibility
            putExtra("TOTAL_TRUCKS", state.totalTrucks)
            putExtra("TOTAL_AMOUNT", state.totalAmount)
            putExtra("TRANSPORTERS_NOTIFIED", state.transportersNotified)
            putExtra("TIMEOUT_SECONDS", state.timeoutSeconds)
        }
        startActivity(intent)
        finish()
    }

    private suspend fun observeSuggestionsState() {
        viewModel.suggestionsState.collectLatest { state ->
            when (state) {
                is SuggestionsUiState.Hidden -> hideSuggestions()
                is SuggestionsUiState.Loading -> showSuggestionsLoading()
                is SuggestionsUiState.Available -> showSuggestions(state)
            }
        }
    }

    // ========================================
    // UI State Handlers
    // ========================================

    private fun showLoading() {
        binding.apply {
            confirmBookingButton.isEnabled = false
            confirmBookingButton.alpha = 0.5f
        }
    }

    private fun hideLoading() {
        binding.apply {
            confirmBookingButton.isEnabled = true
            confirmBookingButton.alpha = 1.0f
        }
    }

    private fun showPricing(state: PricingUiState.PriceLoaded) {
        hideLoading()
        
        binding.apply {
            // Base fare
            baseFareText.text = "₹${state.basePrice}"
            
            // Distance charge
            distanceChargeText.text = "₹${state.distanceCharge}"

            // Calculate GST (5%)
            val subtotal = state.basePrice + state.distanceCharge + state.tonnageCharge
            val gst = (subtotal * 0.05).toInt()
            gstText.text = "₹$gst"

            // Total
            totalAmountText.text = "₹${state.totalPrice}"
            bottomTotalText.text = "₹${state.totalPrice}"

            // Update distance label with slab info
            distanceChargeLabel.text = "Distance (${state.distanceSlab})"

            // Capacity info (if available)
            state.capacityInfo?.let { capacity ->
                vehicleCapacity.text = "Capacity: ${capacity.capacityKg}kg (${capacity.capacityTons} tons)"
                vehicleCapacity.visibility = View.VISIBLE
            }
        }
    }

    private fun showCreatingBooking() {
        binding.apply {
            confirmBookingButton.isEnabled = false
            confirmBookingButton.text = "Creating booking..."
        }
    }

    private fun showError(message: String) {
        hideLoading()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        // Show retry option
        binding.confirmBookingButton.apply {
            text = "Retry"
            isEnabled = true
            setOnClickListener { initializeViewModel() }
        }
    }

    // ========================================
    // Suggestions UI (simplified - no UI elements yet)
    // ========================================

    private fun hideSuggestions() {
        // Suggestions UI not implemented in layout yet
    }

    private fun showSuggestionsLoading() {
        // Suggestions UI not implemented in layout yet
    }

    private fun showSuggestions(state: SuggestionsUiState.Available) {
        // Suggestions UI not implemented in layout yet
        // For now, just show a toast with potential savings
        if (state.potentialSavings > 0) {
            Toast.makeText(
                this, 
                "Tip: You could save ₹${state.potentialSavings} with ${state.recommendedOption.displayName}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ========================================
    // Navigation
    // ========================================

    private fun navigateToConfirmation(bookingId: String) {
        val intent = Intent(this, BookingConfirmationActivity::class.java).apply {
            putExtra("booking_id", bookingId)
            putExtra("from_location", fromLocation)
            putExtra("to_location", toLocation)
        }
        startActivity(intent)
        finish()
    }
}
