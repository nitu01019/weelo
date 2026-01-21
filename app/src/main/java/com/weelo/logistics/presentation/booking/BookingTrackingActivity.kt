package com.weelo.logistics.presentation.booking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.weelo.logistics.R
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.databinding.ActivityBookingTrackingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Booking Tracking Activity
 * 
 * Shows real-time tracking of trucks for a booking with:
 * - Live map with driver location
 * - Driver info and contact
 * - Route visualization
 * - ETA updates
 * - Trip sharing and SOS options
 */
@AndroidEntryPoint
class BookingTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityBookingTrackingBinding
    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    // Tracking data
    private var bookingId: String? = null
    private var driverPhone: String? = null
    private var pickupLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null
    private var currentDriverLatLng: LatLng? = null

    companion object {
        private const val EXTRA_BOOKING_ID = "booking_id"
        private const val EXTRA_DRIVER_NAME = "driver_name"
        private const val EXTRA_DRIVER_PHONE = "driver_phone"
        private const val EXTRA_VEHICLE_NUMBER = "vehicle_number"
        private const val EXTRA_VEHICLE_TYPE = "vehicle_type"
        private const val EXTRA_PICKUP_LAT = "pickup_lat"
        private const val EXTRA_PICKUP_LNG = "pickup_lng"
        private const val EXTRA_DROP_LAT = "drop_lat"
        private const val EXTRA_DROP_LNG = "drop_lng"
        private const val EXTRA_PICKUP_ADDRESS = "pickup_address"
        private const val EXTRA_DROP_ADDRESS = "drop_address"

        fun newIntent(context: Context, bookingId: String): Intent {
            return Intent(context, BookingTrackingActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
            }
        }

        fun newIntent(
            context: Context,
            bookingId: String,
            driverName: String,
            driverPhone: String,
            vehicleNumber: String,
            vehicleType: String,
            pickupLat: Double,
            pickupLng: Double,
            dropLat: Double,
            dropLng: Double,
            pickupAddress: String,
            dropAddress: String
        ): Intent {
            return Intent(context, BookingTrackingActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
                putExtra(EXTRA_DRIVER_NAME, driverName)
                putExtra(EXTRA_DRIVER_PHONE, driverPhone)
                putExtra(EXTRA_VEHICLE_NUMBER, vehicleNumber)
                putExtra(EXTRA_VEHICLE_TYPE, vehicleType)
                putExtra(EXTRA_PICKUP_LAT, pickupLat)
                putExtra(EXTRA_PICKUP_LNG, pickupLng)
                putExtra(EXTRA_DROP_LAT, dropLat)
                putExtra(EXTRA_DROP_LNG, dropLng)
                putExtra(EXTRA_PICKUP_ADDRESS, pickupAddress)
                putExtra(EXTRA_DROP_ADDRESS, dropAddress)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        setupUI()
        setupMap()
        setupBackPressHandler()
        startLocationUpdates()
    }

    private fun extractIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        driverPhone = intent.getStringExtra(EXTRA_DRIVER_PHONE)

        // Extract coordinates
        val pickupLat = intent.getDoubleExtra(EXTRA_PICKUP_LAT, 0.0)
        val pickupLng = intent.getDoubleExtra(EXTRA_PICKUP_LNG, 0.0)
        val dropLat = intent.getDoubleExtra(EXTRA_DROP_LAT, 0.0)
        val dropLng = intent.getDoubleExtra(EXTRA_DROP_LNG, 0.0)

        if (pickupLat != 0.0 && pickupLng != 0.0) {
            pickupLatLng = LatLng(pickupLat, pickupLng)
        }
        if (dropLat != 0.0 && dropLng != 0.0) {
            dropLatLng = LatLng(dropLat, dropLng)
        }

        // Set default Jammu coordinates if not provided
        if (pickupLatLng == null) {
            pickupLatLng = LatLng(32.7266, 74.8570)
        }
        if (dropLatLng == null) {
            dropLatLng = LatLng(32.7355, 74.8702)
        }
    }

    private fun setupUI() {
        binding.apply {
            // Set driver info from intent or defaults
            tvDriverName.text = intent.getStringExtra(EXTRA_DRIVER_NAME) ?: "Driver"
            tvVehicleNumber.text = intent.getStringExtra(EXTRA_VEHICLE_NUMBER) ?: "Vehicle"
            tvVehicleType.text = intent.getStringExtra(EXTRA_VEHICLE_TYPE) ?: "Truck"
            tvPickupAddress.text = intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: "Pickup Location"
            tvDropAddress.text = intent.getStringExtra(EXTRA_DROP_ADDRESS) ?: "Drop Location"

            // Back button
            btnBack.setOnClickListener {
                finish()
                TransitionHelper.applySlideOutRightTransition(this@BookingTrackingActivity)
            }

            // Call driver
            btnCallDriver.setOnClickListener {
                callDriver()
            }

            // Share trip
            btnShareTrip.setOnClickListener {
                shareTrip()
            }

            // SOS/Emergency
            btnEmergency.setOnClickListener {
                showEmergencyOptions()
            }

            // Setup bottom sheet
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            // Map settings
            googleMap?.uiSettings?.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = true
            }

            // Add pickup marker
            pickupLatLng?.let { pickup ->
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(pickup)
                        .title("Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

            // Add drop marker
            dropLatLng?.let { drop ->
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(drop)
                        .title("Drop")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            // Draw route line
            if (pickupLatLng != null && dropLatLng != null) {
                googleMap?.addPolyline(
                    PolylineOptions()
                        .add(pickupLatLng, dropLatLng)
                        .width(8f)
                        .color(getColor(R.color.primary_blue))
                        .geodesic(true)
                )
            }

            // Fit camera to show all markers
            fitCameraToMarkers()

            // Add initial driver marker (simulated position)
            addDriverMarker()

        } catch (e: Exception) {
            Timber.e(e, "Error setting up map")
            Toast.makeText(this, "Error loading map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fitCameraToMarkers() {
        val boundsBuilder = LatLngBounds.Builder()
        pickupLatLng?.let { boundsBuilder.include(it) }
        dropLatLng?.let { boundsBuilder.include(it) }
        currentDriverLatLng?.let { boundsBuilder.include(it) }

        try {
            val bounds = boundsBuilder.build()
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            // Fallback to pickup location
            pickupLatLng?.let {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
            }
        }
    }

    private fun addDriverMarker() {
        // Initial driver position (between pickup and drop for demo)
        pickupLatLng?.let { pickup ->
            dropLatLng?.let { drop ->
                val driverLat = pickup.latitude + (drop.latitude - pickup.latitude) * 0.3
                val driverLng = pickup.longitude + (drop.longitude - pickup.longitude) * 0.3
                currentDriverLatLng = LatLng(driverLat, driverLng)

                driverMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(currentDriverLatLng!!)
                        .title("Driver")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
            }
        }
    }

    private fun startLocationUpdates() {
        // Simulate real-time driver location updates
        lifecycleScope.launch {
            while (isActive) {
                delay(5000) // Update every 5 seconds
                simulateDriverMovement()
            }
        }
    }

    private fun simulateDriverMovement() {
        // Simulate driver moving towards pickup/drop
        currentDriverLatLng?.let { current ->
            pickupLatLng?.let { target ->
                val newLat = current.latitude + (target.latitude - current.latitude) * 0.1
                val newLng = current.longitude + (target.longitude - current.longitude) * 0.1
                currentDriverLatLng = LatLng(newLat, newLng)

                // Update marker on UI thread
                runOnUiThread {
                    driverMarker?.position = currentDriverLatLng!!

                    // Update ETA (simulated)
                    val distance = calculateDistance(currentDriverLatLng!!, target)
                    val etaMinutes = (distance / 500).toInt().coerceAtLeast(1) // Assume 500m per minute
                    binding.tvEta.text = "ETA: $etaMinutes mins"
                }
            }
        }
    }

    private fun calculateDistance(from: LatLng, to: LatLng): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(from.latitude)) * Math.cos(Math.toRadians(to.latitude)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    private fun callDriver() {
        val phone = driverPhone ?: return
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareTrip() {
        val shareText = buildString {
            append("Track my Weelo trip!\n")
            append("Booking ID: $bookingId\n")
            append("From: ${binding.tvPickupAddress.text}\n")
            append("To: ${binding.tvDropAddress.text}\n")
            append("Driver: ${binding.tvDriverName.text}\n")
            append("Vehicle: ${binding.tvVehicleNumber.text}")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Trip"))
    }

    private fun showEmergencyOptions() {
        val options = arrayOf("Call Police (100)", "Call Ambulance (102)", "Call Emergency Contact")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Emergency")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> dialNumber("100")
                    1 -> dialNumber("102")
                    2 -> Toast.makeText(this, "Emergency contact not set", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                TransitionHelper.applySlideOutRightTransition(this@BookingTrackingActivity)
            }
        })
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }
}
