package com.weelo.logistics.presentation.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.weelo.logistics.R
import com.weelo.logistics.databinding.ActivityBookingRequestBinding
import com.weelo.logistics.domain.model.BookingStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Booking Request Activity
 * 
 * Shows real-time status of booking request:
 * - Broadcasting to transporters
 * - Trucks being assigned
 * - Driver details when assigned
 * - Option to track or cancel
 * 
 * This is shown after customer creates a booking.
 */
@AndroidEntryPoint
class BookingRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingRequestBinding
    private val viewModel: BookingRequestViewModel by viewModels()
    
    private lateinit var trucksAdapter: AssignedTrucksAdapter

    companion object {
        private const val EXTRA_BOOKING_ID = "booking_id"

        fun newIntent(context: Context, bookingId: String): Intent {
            return Intent(context, BookingRequestActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        if (bookingId.isNullOrBlank()) {
            Toast.makeText(this, "Invalid booking", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        observeViewModel()
        
        viewModel.loadBooking(bookingId)
    }

    private fun setupUI() {
        binding.apply {
            // Back button
            btnBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            // Cancel booking
            btnCancel.setOnClickListener {
                showCancelConfirmation()
            }

            // Track trucks
            btnTrackTrucks.setOnClickListener {
                viewModel.currentBooking.value?.id?.let { bookingId ->
                    // Navigate to tracking screen
                    startActivity(BookingTrackingActivity.newIntent(this@BookingRequestActivity, bookingId))
                }
            }

            // Refresh
            swipeRefresh.setOnRefreshListener {
                viewModel.refresh()
            }
        }
    }

    private fun setupRecyclerView() {
        trucksAdapter = AssignedTrucksAdapter(
            onTrackClick = { truck ->
                // Handle truck click - show details or track
                Toast.makeText(this, "Tracking ${truck.vehicleNumber}", Toast.LENGTH_SHORT).show()
            }
        )
        
        binding.rvAssignedTrucks.apply {
            layoutManager = LinearLayoutManager(this@BookingRequestActivity)
            adapter = trucksAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.swipeRefresh.isRefreshing = false
                
                when (state) {
                    is BookingRequestUiState.Loading -> showLoading()
                    is BookingRequestUiState.Success -> showBookingDetails(state)
                    is BookingRequestUiState.Error -> showError(state.message)
                }
            }
        }

        // Observe real-time updates
        lifecycleScope.launch {
            viewModel.truckAssignments.collectLatest { trucks ->
                trucksAdapter.submitList(trucks)
                updateTrucksCount(trucks.size)
            }
        }

        // Observe booking status changes
        lifecycleScope.launch {
            viewModel.bookingStatus.collectLatest { status ->
                updateStatusUI(status)
            }
        }
    }

    private fun showLoading() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            contentLayout.visibility = View.GONE
            errorLayout.visibility = View.GONE
        }
    }

    private fun showBookingDetails(state: BookingRequestUiState.Success) {
        binding.apply {
            progressBar.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE

            val booking = state.booking

            // Route info
            tvPickupAddress.text = booking.fromLocation.address
            tvDropAddress.text = booking.toLocation.address
            tvDistance.text = "${booking.distanceKm} km"

            // Vehicle info
            tvVehicleType.text = "${booking.vehicle.name} • ${booking.trucksNeeded} trucks"
            tvPricePerTruck.text = "₹${booking.pricing.totalAmount}/truck"
            tvTotalAmount.text = "Total: ₹${booking.pricing.totalAmount * booking.trucksNeeded}"

            // Trucks progress
            updateTrucksProgress(booking.trucksFilled, booking.trucksNeeded)

            // Status
            updateStatusUI(booking.status)

            // Show/hide buttons based on status
            btnCancel.visibility = if (booking.isActive()) View.VISIBLE else View.GONE
            btnTrackTrucks.visibility = if (booking.trucksFilled > 0) View.VISIBLE else View.GONE
        }
    }

    private fun updateTrucksProgress(filled: Int, needed: Int) {
        binding.apply {
            tvTrucksProgress.text = "$filled / $needed trucks assigned"
            progressTrucks.max = needed
            progressTrucks.progress = filled

            // Update progress color based on fill percentage
            val percentage = if (needed > 0) (filled.toFloat() / needed) else 0f
            when {
                percentage >= 1.0f -> {
                    progressTrucks.setIndicatorColor(getColor(R.color.success_green))
                    tvTrucksStatus.text = "All trucks assigned!"
                    tvTrucksStatus.setTextColor(getColor(R.color.success_green))
                }
                percentage >= 0.5f -> {
                    progressTrucks.setIndicatorColor(getColor(R.color.warning_orange))
                    tvTrucksStatus.text = "Finding more trucks..."
                    tvTrucksStatus.setTextColor(getColor(R.color.warning_orange))
                }
                else -> {
                    progressTrucks.setIndicatorColor(getColor(R.color.primary_blue))
                    tvTrucksStatus.text = "Broadcasting to transporters..."
                    tvTrucksStatus.setTextColor(getColor(R.color.primary_blue))
                }
            }
        }
    }

    private fun updateTrucksCount(count: Int) {
        viewModel.currentBooking.value?.let { booking ->
            updateTrucksProgress(count, booking.trucksNeeded)
        }
    }

    private fun updateStatusUI(status: BookingStatus) {
        binding.apply {
            // Hide try again button by default
            btnTryAgain.visibility = View.GONE
            
            when (status) {
                BookingStatus.PENDING -> {
                    statusChip.text = "Finding Trucks"
                    statusChip.setChipBackgroundColorResource(R.color.primary_blue)
                    lottieAnimation.setAnimation(R.raw.searching_animation)
                    lottieAnimation.playAnimation()
                    lottieAnimation.visibility = View.VISIBLE
                }
                BookingStatus.CONFIRMED -> {
                    statusChip.text = "Trucks Assigned"
                    statusChip.setChipBackgroundColorResource(R.color.success_green)
                    lottieAnimation.setAnimation(R.raw.success_animation)
                    lottieAnimation.playAnimation()
                }
                BookingStatus.DRIVER_ASSIGNED -> {
                    statusChip.text = "Drivers Ready"
                    statusChip.setChipBackgroundColorResource(R.color.success_green)
                    lottieAnimation.visibility = View.GONE
                }
                BookingStatus.IN_PROGRESS -> {
                    statusChip.text = "Trip In Progress"
                    statusChip.setChipBackgroundColorResource(R.color.warning_orange)
                    lottieAnimation.setAnimation(R.raw.truck_animation)
                    lottieAnimation.playAnimation()
                }
                BookingStatus.COMPLETED -> {
                    statusChip.text = "Completed"
                    statusChip.setChipBackgroundColorResource(R.color.success_green)
                    lottieAnimation.setAnimation(R.raw.complete_animation)
                    lottieAnimation.playAnimation()
                    btnCancel.visibility = View.GONE
                }
                BookingStatus.CANCELLED -> {
                    statusChip.text = "Cancelled"
                    statusChip.setChipBackgroundColorResource(R.color.error_red)
                    lottieAnimation.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                    btnTrackTrucks.visibility = View.GONE
                }
                BookingStatus.EXPIRED -> {
                    // Booking expired - show Try Again option
                    statusChip.text = "Expired"
                    statusChip.setChipBackgroundColorResource(R.color.error_red)
                    lottieAnimation.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                    btnTrackTrucks.visibility = View.GONE
                    
                    // Show Try Again button
                    btnTryAgain.visibility = View.VISIBLE
                    btnTryAgain.setOnClickListener {
                        showTryAgainOptions()
                    }
                    
                    // Update status text to explain what happened
                    tvTrucksStatus.text = "No transporters available. Try again?"
                    tvTrucksStatus.setTextColor(getColor(R.color.error_red))
                }
                BookingStatus.PARTIALLY_FILLED -> {
                    // Some trucks assigned but not all - show continue or try again
                    statusChip.text = "Partially Filled"
                    statusChip.setChipBackgroundColorResource(R.color.warning_orange)
                    lottieAnimation.visibility = View.GONE
                    
                    // Show Try Again button for remaining trucks
                    btnTryAgain.visibility = View.VISIBLE
                    btnTryAgain.text = "Find More Trucks"
                    btnTryAgain.setOnClickListener {
                        showPartialFillOptions()
                    }
                    
                    tvTrucksStatus.text = "Some trucks assigned. Find more?"
                    tvTrucksStatus.setTextColor(getColor(R.color.warning_orange))
                }
            }
        }
    }
    
    /**
     * Show options when booking expired without any trucks
     */
    private fun showTryAgainOptions() {
        AlertDialog.Builder(this)
            .setTitle("Try Again?")
            .setMessage("No transporters were available for your request. Would you like to search again?")
            .setPositiveButton("Search Again") { _, _ ->
                navigateBackToTruckSelection()
            }
            .setNeutralButton("Change Vehicle Type") { _, _ ->
                navigateBackToTruckSelection()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show options when booking partially filled
     */
    private fun showPartialFillOptions() {
        val booking = viewModel.currentBooking.value ?: return
        val remaining = booking.trucksNeeded - booking.trucksFilled
        
        AlertDialog.Builder(this)
            .setTitle("$remaining Trucks Remaining")
            .setMessage("${booking.trucksFilled} of ${booking.trucksNeeded} trucks have been assigned. What would you like to do?")
            .setPositiveButton("Continue with ${booking.trucksFilled} trucks") { _, _ ->
                // Continue with partial fulfillment
                viewModel.continueWithPartialFill()
            }
            .setNeutralButton("Search Again for All") { _, _ ->
                // Try again for all trucks
                navigateBackToTruckSelection()
            }
            .setNegativeButton("Cancel Booking") { _, _ ->
                viewModel.cancelBooking()
            }
            .show()
    }
    
    /**
     * Navigate back to truck selection to try again
     */
    private fun navigateBackToTruckSelection() {
        // Go back to TruckTypesActivity
        val intent = Intent(this, com.weelo.logistics.TruckTypesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            contentLayout.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            tvErrorMessage.text = message
            
            btnRetry.setOnClickListener {
                viewModel.refresh()
            }
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Booking?")
            .setMessage("Are you sure you want to cancel this booking? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                viewModel.cancelBooking()
            }
            .setNegativeButton("No, Keep it", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.connectRealTime()
    }

    override fun onPause() {
        super.onPause()
        viewModel.disconnectRealTime()
    }
}
