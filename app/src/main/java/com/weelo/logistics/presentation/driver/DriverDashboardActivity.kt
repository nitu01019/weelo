package com.weelo.logistics.presentation.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.weelo.logistics.R
import com.weelo.logistics.databinding.ActivityDriverDashboardBinding
import kotlinx.coroutines.launch

/**
 * DriverDashboardActivity - Main dashboard for driver features
 * Based on PRD-04: Driver Features - Trip Acceptance & GPS Tracking
 * 
 * Features:
 * - Availability toggle (ON/OFF for receiving trips)
 * - Today's summary (Trips, Distance, Earnings)
 * - Active trip display
 * - Quick Actions (SOS, Navigation, Report Issue, Call Support)
 * - Bottom navigation (Home, Trips, Profile)
 */
class DriverDashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDriverDashboardBinding
    private val viewModel: DriverDashboardViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupObservers()
        setupQuickActions()
    }
    
    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Weelo Driver"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // Availability Toggle
        binding.availabilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateAvailability(isChecked)
        }
        
        // Active Trip Card Click
        binding.activeTripCard.setOnClickListener {
            viewModel.currentTrip.value?.let { trip ->
                // Navigate to trip details
                Toast.makeText(this, "Opening trip: ${trip.tripId}", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to TripDetailsActivity
            }
        }
        
        // View Details Button
        binding.btnViewDetails.setOnClickListener {
            viewModel.currentTrip.value?.let { trip ->
                // Navigate to trip details
                Toast.makeText(this, "Trip details: ${trip.tripId}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Navigate Button - Opens Google Maps
        binding.btnNavigate.setOnClickListener {
            viewModel.currentTrip.value?.let { trip ->
                openGoogleMapsNavigation(trip.deliveryLat, trip.deliveryLng)
            }
        }
        
        // Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_trips -> {
                    // Navigate to trips list
                    Toast.makeText(this, "Trips", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_profile -> {
                    // Navigate to profile
                    Toast.makeText(this, "Profile", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupQuickActions() {
        // SOS Emergency Button
        binding.btnSos.setOnClickListener {
            handleSOS()
        }
        
        // Navigate to Destination
        binding.btnQuickNavigate.setOnClickListener {
            viewModel.currentTrip.value?.let { trip ->
                openGoogleMapsNavigation(trip.deliveryLat, trip.deliveryLng)
            } ?: run {
                Toast.makeText(this, "No active trip", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Report Issue
        binding.btnReportIssue.setOnClickListener {
            handleReportIssue()
        }
        
        // Call Support
        binding.btnCallSupport.setOnClickListener {
            callSupport()
        }
    }
    
    private fun setupObservers() {
        // Observe availability status
        lifecycleScope.launch {
            viewModel.isAvailable.collect { isAvailable ->
                binding.availabilitySwitch.isChecked = isAvailable
                updateAvailabilityUI(isAvailable)
            }
        }
        
        // Observe today's summary
        lifecycleScope.launch {
            viewModel.todaySummary.collect { summary ->
                binding.tvTripCount.text = summary.tripCount.toString()
                binding.tvDistance.text = "${summary.distance} km"
                binding.tvEarnings.text = "₹${summary.earnings}"
            }
        }
        
        // Observe current trip
        lifecycleScope.launch {
            viewModel.currentTrip.collect { trip ->
                if (trip != null) {
                    showActiveTrip(trip)
                } else {
                    hideActiveTrip()
                }
            }
        }
        
        // Observe driver name
        lifecycleScope.launch {
            viewModel.driverName.collect { name ->
                binding.tvGreeting.text = "Hello, $name! 👋"
            }
        }
    }
    
    private fun updateAvailabilityUI(isAvailable: Boolean) {
        if (isAvailable) {
            binding.tvAvailabilityStatus.text = "Status: ● AVAILABLE"
            binding.availabilityCard.setCardBackgroundColor(
                resources.getColor(R.color.success_green_light, null)
            )
            binding.tvAvailabilityLabel.text = "Toggle to stop receiving trip requests"
        } else {
            binding.tvAvailabilityStatus.text = "Status: ● OFFLINE"
            binding.availabilityCard.setCardBackgroundColor(
                resources.getColor(R.color.gray_light, null)
            )
            binding.tvAvailabilityLabel.text = "Toggle to start receiving trip requests"
        }
    }
    
    private fun showActiveTrip(trip: ActiveTrip) {
        binding.activeTripCard.visibility = android.view.View.VISIBLE
        binding.tvNoActiveTrip.visibility = android.view.View.GONE
        
        binding.tvTripId.text = "Trip #${trip.tripId}"
        binding.tvVehicleNumber.text = "🚛 ${trip.vehicleNumber}"
        binding.tvVehicleType.text = trip.vehicleType
        binding.tvRoute.text = "📍 ${trip.pickup} → ${trip.delivery}"
        binding.tvTripStatus.text = "Status: ${trip.status}"
        binding.tvTripStartTime.text = "Started: ${trip.startTime}"
    }
    
    private fun hideActiveTrip() {
        binding.activeTripCard.visibility = android.view.View.GONE
        binding.tvNoActiveTrip.visibility = android.view.View.VISIBLE
        binding.tvNoActiveTrip.text = "No active trips"
    }
    
    // Quick Actions Implementation
    
    private fun handleSOS() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency SOS")
            .setMessage("This will alert Weelo support team and emergency contacts. Do you want to proceed?")
            .setPositiveButton("Yes, Send SOS") { _, _ ->
                viewModel.sendSOSAlert()
                Toast.makeText(this, "SOS alert sent! Support team will contact you shortly", Toast.LENGTH_LONG).show()
                // Also call emergency number
                callEmergencyNumber()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleReportIssue() {
        val options = arrayOf(
            "Vehicle breakdown",
            "Accident",
            "Road blockage",
            "Load issue",
            "Documentation problem",
            "Other"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Issue")
            .setItems(options) { _, which ->
                val issue = options[which]
                viewModel.reportIssue(issue)
                Toast.makeText(this, "Issue reported: $issue", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun callSupport() {
        val supportNumber = "tel:+919876543210" // Weelo support number
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(supportNumber))
        startActivity(intent)
    }
    
    private fun callEmergencyNumber() {
        val emergencyNumber = "tel:112" // Emergency number
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(emergencyNumber))
        startActivity(intent)
    }
    
    private fun openGoogleMapsNavigation(lat: Double, lng: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Google Maps not installed, open in browser
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
