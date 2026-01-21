package com.weelo.logistics.presentation.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.weelo.logistics.R
import com.weelo.logistics.adapters.SelectedTrucksAdapter
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.util.getParcelableArrayListExtraCompat
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.databinding.ActivityBookingConfirmationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Booking Confirmation Activity
 * 
 * Shows booking summary before customer confirms:
 * - Route (pickup → drop)
 * - Selected trucks with quantities
 * - Estimated pricing
 * - Book Now button to create booking
 * 
 * After booking is created, navigates to BookingRequestActivity
 * to show real-time status (like Rapido waiting for driver).
 */
@AndroidEntryPoint
class BookingConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingConfirmationBinding
    private val viewModel: BookingConfirmationViewModel by viewModels()
    
    private lateinit var trucksAdapter: ConfirmationTrucksAdapter

    companion object {
        private const val EXTRA_FROM_LOCATION = "from_location"
        private const val EXTRA_TO_LOCATION = "to_location"
        private const val EXTRA_SELECTED_TRUCKS = "selected_trucks"
        private const val EXTRA_DISTANCE_KM = "distance_km"

        fun newIntent(
            context: Context,
            fromLocation: Location,
            toLocation: Location,
            selectedTrucks: ArrayList<SelectedTruckItem>,
            distanceKm: Int
        ): Intent {
            return Intent(context, BookingConfirmationActivity::class.java).apply {
                putExtra(EXTRA_FROM_LOCATION, fromLocation)
                putExtra(EXTRA_TO_LOCATION, toLocation)
                putParcelableArrayListExtra(EXTRA_SELECTED_TRUCKS, selectedTrucks)
                putExtra(EXTRA_DISTANCE_KM, distanceKm)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract intent data
        val fromLocation = intent.getParcelableExtraCompat<Location>(EXTRA_FROM_LOCATION)
        val toLocation = intent.getParcelableExtraCompat<Location>(EXTRA_TO_LOCATION)
        val selectedTrucks = intent.getParcelableArrayListExtraCompat<SelectedTruckItem>(EXTRA_SELECTED_TRUCKS)
        val distanceKm = intent.getIntExtra(EXTRA_DISTANCE_KM, 0)

        if (fromLocation == null || toLocation == null || selectedTrucks.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid booking data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI(fromLocation, toLocation, selectedTrucks, distanceKm)
        setupRecyclerView(selectedTrucks)
        setupBackPressHandler()
        observeViewModel()
        
        // Initialize ViewModel with data
        viewModel.initialize(fromLocation, toLocation, selectedTrucks, distanceKm)
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                TransitionHelper.applySlideOutRightTransition(this@BookingConfirmationActivity)
            }
        })
    }

    private fun setupUI(
        fromLocation: Location,
        toLocation: Location,
        selectedTrucks: List<SelectedTruckItem>,
        distanceKm: Int
    ) {
        binding.apply {
            // Back button
            btnBack.setOnClickListener {
                finish()
                TransitionHelper.applySlideOutRightTransition(this@BookingConfirmationActivity)
            }

            // Route info
            tvPickupAddress.text = fromLocation.address
            tvDropAddress.text = toLocation.address
            tvDistance.text = "~ $distanceKm km"

            // Total trucks
            val totalTrucks = selectedTrucks.sumOf { it.quantity }
            tvTotalTrucks.text = totalTrucks.toString()

            // Estimated price (will be updated by ViewModel)
            tvEstimatedPrice.text = "₹ --"

            // Book Now button
            btnBookNow.setOnClickListener {
                viewModel.createBooking()
            }
        }
    }

    private fun setupRecyclerView(selectedTrucks: List<SelectedTruckItem>) {
        trucksAdapter = ConfirmationTrucksAdapter()
        binding.rvSelectedTrucks.apply {
            layoutManager = LinearLayoutManager(this@BookingConfirmationActivity)
            adapter = trucksAdapter
        }
        trucksAdapter.submitList(selectedTrucks)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is BookingConfirmationUiState.Loading -> showLoading()
                    is BookingConfirmationUiState.Ready -> {
                        hideLoading()
                        updatePricing(state.estimatedPrice)
                    }
                    is BookingConfirmationUiState.Creating -> showLoading()
                    is BookingConfirmationUiState.Success -> {
                        hideLoading()
                        navigateToBookingRequest(state.bookingId)
                    }
                    is BookingConfirmationUiState.Error -> {
                        hideLoading()
                        Toast.makeText(this@BookingConfirmationActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updatePricing(estimatedPrice: Int) {
        binding.tvEstimatedPrice.text = "₹ $estimatedPrice"
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnBookNow.isEnabled = false
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
        binding.btnBookNow.isEnabled = true
    }

    private fun navigateToBookingRequest(bookingId: String) {
        // Navigate to waiting/searching screen
        val intent = BookingRequestActivity.newIntent(this, bookingId)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

}

/**
 * Simple adapter for confirmation screen - read-only display
 */
class ConfirmationTrucksAdapter : androidx.recyclerview.widget.ListAdapter<SelectedTruckItem, ConfirmationTrucksAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<SelectedTruckItem>() {
        override fun areItemsTheSame(oldItem: SelectedTruckItem, newItem: SelectedTruckItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SelectedTruckItem, newItem: SelectedTruckItem) = oldItem == newItem
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_confirmation_truck, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val tvTruckName: android.widget.TextView = itemView.findViewById(R.id.tvTruckName)
        private val tvSpecification: android.widget.TextView = itemView.findViewById(R.id.tvSpecification)
        private val tvQuantity: android.widget.TextView = itemView.findViewById(R.id.tvQuantity)
        private val ivTruckIcon: android.widget.ImageView = itemView.findViewById(R.id.ivTruckIcon)

        fun bind(item: SelectedTruckItem) {
            tvTruckName.text = item.truckTypeName
            tvSpecification.text = item.specification
            tvQuantity.text = "x${item.quantity}"
            ivTruckIcon.setImageResource(item.iconResource)
        }
    }
}
