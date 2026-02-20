package com.weelo.logistics.presentation.booking

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.weelo.logistics.R
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.remote.WebSocketService
import com.weelo.logistics.data.remote.api.AssignedTruckData
import com.weelo.logistics.data.repository.TrackingRepository
import com.weelo.logistics.databinding.ActivityBookingTrackingBinding
import com.weelo.logistics.utils.MarkerAnimationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * =============================================================================
 * BOOKING TRACKING ACTIVITY — Multi-Truck Real-Time Tracking (Phase 4)
 * =============================================================================
 *
 * Shows real-time tracking of ALL trucks for a booking with:
 * - Multiple driver markers on Google Map (color-coded by status)
 * - Smooth animation using MarkerAnimationHelper (Ola/Uber style)
 * - Horizontal scrollable truck cards with driver info + ETA
 * - In-app status notification banner (slide-down, auto-dismiss)
 * - Single-truck deep dive (zoom to one truck, "Back to All")
 * - WebSocket real-time connection with auto-reconnect
 *
 * HOW IT WORKS:
 * ─────────────
 * 1. On open: fetch initial truck positions via REST API
 * 2. Connect to WebSocket → join booking room
 * 3. Receive location_updated events → animate markers smoothly
 * 4. Receive assignment_status_changed → update status badges + banner
 * 5. Each marker interpolates between points (the "live" illusion)
 *
 * SCALABILITY:
 * - WebSocket rooms: customer only receives events for their booking
 * - Redis PubSub: works across multiple backend servers
 * - Marker map keyed by tripId: O(1) lookup per update
 * - DiffUtil adapter: efficient partial updates for truck cards
 *
 * MODULARITY:
 * - TrackingRepository abstracts API + WebSocket
 * - MarkerAnimationHelper handles all animation math
 * - AssignedTrucksAdapter is fully reusable
 * - Status message mapping is centralized in getStatusMessage()
 *
 * =============================================================================
 */
@AndroidEntryPoint
class BookingTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    // =========================================================================
    // DEPENDENCIES (Hilt-injected)
    // =========================================================================

    @Inject lateinit var trackingRepository: TrackingRepository
    @Inject lateinit var bookingApiRepository: com.weelo.logistics.data.repository.BookingApiRepository
    @Inject lateinit var apiService: com.weelo.logistics.data.remote.api.WeeloApiService
    @Inject lateinit var tokenManager: com.weelo.logistics.data.remote.TokenManager

    // Rating: prevent showing rating sheet multiple times in same session
    private var ratingSheetShown = false

    // =========================================================================
    // VIEW BINDING & MAP
    // =========================================================================

    private lateinit var binding: ActivityBookingTrackingBinding
    private var googleMap: GoogleMap? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private var isMapReady = false

    // =========================================================================
    // MULTI-TRUCK MARKER SYSTEM
    // =========================================================================

    /** Driver markers keyed by tripId — O(1) lookup per location update */
    private val driverMarkers = mutableMapOf<String, Marker>()

    /** Route polylines keyed by tripId — for deep dive show/hide */
    private val routePolylines = mutableMapOf<String, Polyline>()

    /** Trip-to-assignment mapping — links location events to truck cards */
    private val tripToAssignment = mutableMapOf<String, String>()

    /** Currently focused truck (null = all trucks view) */
    private var focusedTripId: String? = null

    // =========================================================================
    // TRUCK CARDS ADAPTER
    // =========================================================================

    private lateinit var trucksAdapter: AssignedTrucksAdapter

    // =========================================================================
    // TRACKING DATA
    // =========================================================================

    private var bookingId: String? = null
    private var driverPhone: String? = null
    private var pickupLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null

    /** Handler for auto-dismissing status banner */
    private val bannerHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "BookingTracking"
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

        /** Banner auto-dismiss delay */
        private const val BANNER_DISMISS_DELAY_MS = 3500L

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

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        setupUI()
        setupTruckCards()
        setupMap()
        setupBackPressHandler()

        // fetchInitialData() is deferred to onMapReady() to prevent marker loss
        // if REST response arrives before GoogleMap is initialized.
        // startLocationUpdates() also deferred to onMapReady() so WebSocket
        // location updates are not silently dropped before the map is ready.
    }

    private fun extractIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        driverPhone = intent.getStringExtra(EXTRA_DRIVER_PHONE)

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

        // Do NOT set fallback coordinates — if intent extras are missing,
        // real coordinates will be fetched from the backend in fetchInitialData().
    }

    private fun setupUI() {
        binding.apply {
            // Set info from intent or defaults
            tvDriverName.text = intent.getStringExtra(EXTRA_DRIVER_NAME) ?: "Driver"
            tvVehicleNumber.text = intent.getStringExtra(EXTRA_VEHICLE_NUMBER) ?: "Vehicle"
            tvVehicleType.text = intent.getStringExtra(EXTRA_VEHICLE_TYPE) ?: "Truck"
            tvPickupAddress.text = intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: "Pickup Location"
            tvDropAddress.text = intent.getStringExtra(EXTRA_DROP_ADDRESS) ?: "Drop Location"

            btnBack.setOnClickListener {
                finish()
                TransitionHelper.applySlideOutRightTransition(this@BookingTrackingActivity)
            }

            btnCallDriver.setOnClickListener { callDriver() }
            btnShareTrip.setOnClickListener { shareTrip() }
            btnEmergency.setOnClickListener { showEmergencyOptions() }
            
            // Cancel Booking button — opens CancellationBottomSheet
            btnCancelBooking.setOnClickListener { showCancelBookingSheet() }

            // Back to All button (for deep dive exit)
            btnBackToAll.setOnClickListener { exitDeepDive() }

            // Bottom sheet
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /**
     * Setup horizontal truck cards RecyclerView.
     */
    private fun setupTruckCards() {
        trucksAdapter = AssignedTrucksAdapter(
            onTrackClick = { truck -> enterDeepDive(truck) }
        )

        binding.rvTruckCards.apply {
            layoutManager = LinearLayoutManager(
                this@BookingTrackingActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = trucksAdapter
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        startLocationUpdates() // Safe to start now — map is initialized

        try {
            googleMap?.uiSettings?.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = true
            }

            // Pickup marker (green)
            pickupLatLng?.let { pickup ->
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(pickup)
                        .title("Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

            // Drop marker (red)
            dropLatLng?.let { drop ->
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(drop)
                        .title("Drop")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            // Route polyline (pickup → drop) — stored so drawRoutePolyline() can replace it
            if (pickupLatLng != null && dropLatLng != null) {
                val polyline = googleMap?.addPolyline(
                    PolylineOptions()
                        .add(pickupLatLng, dropLatLng)
                        .width(8f)
                        .color(getColor(R.color.primary_blue))
                        .geodesic(true)
                )
                polyline?.let { routePolylines["main"] = it }
            }

            fitCameraToAllMarkers()

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error setting up map")
            Toast.makeText(this, "Error loading map", Toast.LENGTH_SHORT).show()
        }

        // Map is ready — now safe to add markers. Fetch initial data here.
        isMapReady = true
        fetchInitialData()
    }

    // =========================================================================
    // MULTI-TRUCK MARKER MANAGEMENT
    // =========================================================================

    /**
     * Get marker color based on truck status.
     * Yellow = heading to pickup, Blue = at pickup/loading, Green = in transit, Rose = completed.
     */
    private fun getMarkerHueForStatus(status: String): Float {
        return when (status.lowercase(java.util.Locale.ROOT)) {
            "pending", "driver_accepted", "heading_to_pickup" -> BitmapDescriptorFactory.HUE_YELLOW
            "at_pickup", "loading_complete" -> BitmapDescriptorFactory.HUE_AZURE
            "in_transit" -> BitmapDescriptorFactory.HUE_GREEN
            "completed" -> BitmapDescriptorFactory.HUE_ROSE
            else -> BitmapDescriptorFactory.HUE_ORANGE
        }
    }

    /**
     * Add or update a driver marker on the map.
     * Called from initial data fetch and real-time WebSocket updates.
     */
    private fun addOrUpdateDriverMarker(
        tripId: String,
        lat: Double,
        lng: Double,
        status: String,
        title: String = "Driver"
    ) {
        val hue = getMarkerHueForStatus(status)

        // Status-only update (lat/lng = 0.0) — just change icon color if marker exists
        // This is called when we receive a status change WebSocket event
        // (driver changed status but we don't have new coordinates)
        if (lat == 0.0 && lng == 0.0) {
            driverMarkers[tripId]?.setIcon(BitmapDescriptorFactory.defaultMarker(hue))
            return
        }

        val position = LatLng(lat, lng)

        val existingMarker = driverMarkers[tripId]
        if (existingMarker != null) {
            // Update marker icon color if status changed
            existingMarker.setIcon(BitmapDescriptorFactory.defaultMarker(hue))
        } else {
            // Create new marker
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .flat(true)
            )
            marker?.let { driverMarkers[tripId] = it }
        }
    }

    /**
     * Smoothly animate a truck marker to a new position.
     * Called on every location_updated WebSocket event.
     */
    private fun updateTruckLocation(
        tripId: String,
        lat: Double,
        lng: Double,
        speed: Float = 10f,
        bearing: Float? = null
    ) {
        val newPosition = LatLng(lat, lng)
        val marker = driverMarkers[tripId] ?: return

        val distance = MarkerAnimationHelper.calculateDistance(marker.position, newPosition)
        val duration = MarkerAnimationHelper.calculateOptimalDuration(
            speedMetersPerSec = speed,
            distanceMeters = distance,
            updateIntervalMs = 5000L
        )
        val newBearing = bearing ?: MarkerAnimationHelper.calculateBearing(marker.position, newPosition)

        runOnUiThread {
            MarkerAnimationHelper.animateMarkerWithRotation(
                marker = marker,
                toPosition = newPosition,
                toBearing = newBearing,
                duration = duration
            )

            // Update ETA for this truck
            updateEtaForTruck(tripId, newPosition)
        }

        Timber.d("$TAG: 📍 Truck $tripId → ($lat, $lng) speed=$speed")
    }

    // =========================================================================
    // REAL GOOGLE MAPS ETA (Phase 5)
    // =========================================================================
    //
    // Replaces the old crude straight-line estimate (distance/500) with
    // real driving-time ETA from Google Maps Directions API.
    //
    // HOW IT WORKS:
    //   1. fetchRealETA() called on initial load + every 60 seconds
    //   2. Calls GET /tracking/booking/:bookingId/eta (batch endpoint)
    //   3. Backend gets each truck's Redis location, calls Google Directions
    //   4. Response: { tripId → { durationMinutes, distanceKm, durationText } }
    //   5. Updates all truck card ETAs + main ETA display
    //
    // SCALABILITY:
    //   - Server-side 1hr cache per origin→dest pair
    //   - Client polls every 60s (not per location update — saves bandwidth)
    //   - Batch endpoint (1 HTTP call for ALL trucks, not N calls)
    //
    // FALLBACK:
    //   - If API fails, uses straight-line estimate (same as before)
    //   - Non-blocking — tracking screen works without ETA
    // =========================================================================

    /** Handler for periodic ETA refresh */
    private val etaHandler = Handler(Looper.getMainLooper())
    private val etaRefreshIntervalMs = 60_000L // 60 seconds

    /** Cached real ETAs keyed by tripId */
    private val realEtaMap = mutableMapOf<String, String>()

    /**
     * Start periodic real ETA fetching.
     * Called once after initial data loads.
     */
    private fun startRealEtaRefresh() {
        fetchRealETA()
        etaHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed) {
                    fetchRealETA()
                    etaHandler.postDelayed(this, etaRefreshIntervalMs)
                }
            }
        }, etaRefreshIntervalMs)
    }

    /**
     * Fetch real Google Maps ETA for all active trucks.
     * Batch endpoint: 1 HTTP call → ETAs for ALL trucks.
     */
    private fun fetchRealETA() {
        val id = bookingId ?: return
        val token = trackingRepository.getToken() ?: return

        lifecycleScope.launch {
            try {
                val response = trackingRepository.getApiService()
                    .getBookingETA("Bearer $token", id)

                if (response.isSuccessful && response.body()?.success == true) {
                    val etas = response.body()?.data?.etas ?: emptyMap()

                    etas.forEach { (tripId, etaData) ->
                        realEtaMap[tripId] = etaData.durationText

                        // Update truck card
                        val assignmentId = tripToAssignment[tripId]
                        if (assignmentId != null) {
                            trucksAdapter.updateEta(assignmentId, etaData.durationText)
                        }
                    }

                    // Update main ETA display with closest truck's ETA
                    val closestEta = etas.values.minByOrNull { it.durationMinutes }
                    if (closestEta != null) {
                        binding.tvEta.text = "ETA: ${closestEta.durationText}"
                    }

                    Timber.d("$TAG: 📍 Real ETA updated for ${etas.size} trucks")
                }
            } catch (e: Exception) {
                Timber.w("$TAG: Real ETA fetch failed, using fallback: ${e.message}")
            }
        }
    }

    /**
     * Fallback ETA estimate using straight-line distance.
     * Used when real ETA is not yet available or API fails.
     * Called on every location update for responsive feel.
     */
    private fun updateEtaForTruck(tripId: String, currentPos: LatLng) {
        // If we have a real ETA for this truck, don't overwrite with estimate
        if (realEtaMap.containsKey(tripId)) return

        val target = dropLatLng ?: return
        val distanceMeters = MarkerAnimationHelper.calculateDistance(currentPos, target)
        val etaMinutes = (distanceMeters / 500).toInt().coerceAtLeast(1)

        val etaText = if (etaMinutes >= 60) {
            "${etaMinutes / 60}h ${etaMinutes % 60}m"
        } else {
            "${etaMinutes} mins"
        }

        // Update adapter ETA
        val assignmentId = tripToAssignment[tripId]
        if (assignmentId != null) {
            trucksAdapter.updateEta(assignmentId, etaText)
        }

        // Update main ETA display (show closest truck's ETA)
        binding.tvEta.text = "ETA: ~$etaText"
    }

    /**
     * Fit camera to show all markers (pickup, drop, all driver markers).
     */
    private fun fitCameraToAllMarkers() {
        val boundsBuilder = LatLngBounds.Builder()
        pickupLatLng?.let { boundsBuilder.include(it) }
        dropLatLng?.let { boundsBuilder.include(it) }
        driverMarkers.values.forEach { boundsBuilder.include(it.position) }

        try {
            val bounds = boundsBuilder.build()
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to build camera bounds, falling back to pickup location")
            pickupLatLng?.let {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
            }
        }
    }

    // =========================================================================
    // DATA FETCHING — Initial Load via REST API
    // =========================================================================

    /**
     * Fetch initial truck positions and assignments from backend.
     * Called once on screen open. WebSocket handles subsequent updates.
     */
    private fun fetchInitialData() {
        val id = bookingId ?: return

        lifecycleScope.launch {
            try {
                showLoading()

                // Fetch assigned trucks (driver info, status) and tracking data (positions)
                val assignedTrucks = trackingRepository.getAssignedTrucks(id)
                val trackingData = trackingRepository.getBookingTracking(id)

                // Guard: if activity is finishing/destroyed, skip UI updates
                if (isFinishing || isDestroyed) return@launch

                hideLoading()

                if (assignedTrucks.isNotEmpty()) {
                    // Show truck cards
                    trucksAdapter.submitList(assignedTrucks)
                    binding.rvTruckCards.visibility = View.VISIBLE

                    // Build trip → assignment mapping
                    assignedTrucks.forEach { truck ->
                        tripToAssignment[truck.tripId] = truck.assignmentId
                    }

                    // Update bottom sheet with first truck's info
                    assignedTrucks.firstOrNull()?.let { first ->
                        binding.tvDriverName.text = first.driverName
                        binding.tvVehicleNumber.text = first.vehicleNumber
                        binding.tvVehicleType.text = first.vehicleType
                        driverPhone = first.driverPhone
                    }

                    // Update tracking status header
                    binding.tvTrackingStatus.text = if (assignedTrucks.size > 1) {
                        getString(R.string.tracking_n_trucks, assignedTrucks.size)
                    } else {
                        getString(R.string.tracking_your_truck)
                    }
                } else {
                    // No trucks assigned yet — show waiting state
                    binding.tvTrackingStatus.text = getString(R.string.waiting_for_truck)
                    Timber.w("$TAG: No assigned trucks found for booking $id")
                }

                // Place driver markers on map from tracking data
                trackingData.forEach { truck ->
                    addOrUpdateDriverMarker(
                        tripId = truck.tripId,
                        lat = truck.latitude,
                        lng = truck.longitude,
                        status = truck.status,
                        title = truck.vehicleNumber
                    )
                }

                // Fit camera to include all markers
                if (driverMarkers.isNotEmpty()) {
                    fitCameraToAllMarkers()
                }

                // Fetch and draw route polyline (road-following, blue line)
                fetchRoutePolyline()

                Timber.d("$TAG: Loaded ${assignedTrucks.size} trucks, ${trackingData.size} tracking points")

                // Start real Google Maps ETA refresh (every 60s)
                startRealEtaRefresh()

            } catch (e: Exception) {
                Timber.e("$TAG: Failed to load tracking data: ${e.message}")

                if (!isFinishing && !isDestroyed) {
                    hideLoading()
                    binding.tvTrackingStatus.text = getString(R.string.unable_to_load_tracking)

                    // Show retry snackbar — user can tap to retry
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, getString(R.string.failed_to_load_tracking), com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                        .setAction(getString(R.string.retry)) { fetchInitialData() }
                        .show()
                }
            }
        }
    }

    // =========================================================================
    // ROUTE POLYLINE — Road-Following Blue Line via Backend API
    // =========================================================================

    /**
     * Fetch road-following route from backend and draw polyline on map.
     * Uses the existing /geocoding/route-multi endpoint (AWS Location Service).
     *
     * The polyline shows the actual road route between pickup → drop,
     * not just a straight line. Blue color, 10px width.
     *
     * SCALABILITY: Backend handles API calls, client just renders
     * MODULARITY: Uses existing WeeloApiService.calculateRouteMulti()
     */
    private fun fetchRoutePolyline() {
        val pickup = pickupLatLng ?: return
        val drop = dropLatLng ?: return

        lifecycleScope.launch {
            try {
                val request = com.weelo.logistics.data.remote.api.MultiPointRouteRequest(
                    points = listOf(
                        com.weelo.logistics.data.remote.api.RoutePoint(
                            lat = pickup.latitude,
                            lng = pickup.longitude,
                            label = "Pickup"
                        ),
                        com.weelo.logistics.data.remote.api.RoutePoint(
                            lat = drop.latitude,
                            lng = drop.longitude,
                            label = "Drop"
                        )
                    ),
                    truckMode = true,
                    includePolyline = true
                )

                val response = trackingRepository.getApiService().calculateRouteMulti(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val polylineData = response.body()?.data?.polyline
                    if (polylineData != null && polylineData.isNotEmpty()) {
                        val roadPoints = polylineData.mapNotNull { coords ->
                            if (coords.size >= 2) LatLng(coords[0], coords[1]) else null
                        }
                        if (roadPoints.isNotEmpty()) {
                            drawRoutePolyline(roadPoints)
                            Timber.d("$TAG: Route polyline drawn with ${roadPoints.size} points")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to fetch route polyline — using straight line")
                // Straight line fallback is already drawn in onMapReady
            }
        }
    }

    /**
     * Draw road-following polyline on the map.
     * Removes the existing straight-line polyline and replaces with proper route.
     */
    private fun drawRoutePolyline(routePoints: List<LatLng>) {
        runOnUiThread {
            // Remove old straight-line polyline (drawn in onMapReady)
            routePolylines["main"]?.remove()

            val polyline = googleMap?.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .width(10f)
                    .color(getColor(R.color.primary_blue))
                    .geodesic(true)
            )
            polyline?.let { routePolylines["main"] = it }
        }
    }

    // =========================================================================
    // REAL-TIME WEBSOCKET — Location + Status Updates
    // =========================================================================

    /**
     * Connect to WebSocket and subscribe to real-time location + status updates.
     * Replaces the old simulateDriverMovement() approach.
     */
    private fun startLocationUpdates() {
        val id = bookingId ?: return

        // Connect and join booking room
        trackingRepository.startTracking(id)

        // Collect real-time location updates
        lifecycleScope.launch {
            trackingRepository.locationUpdates().collectLatest { event ->
                // Ensure marker exists for this trip
                if (!driverMarkers.containsKey(event.tripId)) {
                    addOrUpdateDriverMarker(
                        tripId = event.tripId,
                        lat = event.latitude,
                        lng = event.longitude,
                        status = "in_transit",
                        title = "Driver"
                    )
                }

                // Smooth animation to new position
                updateTruckLocation(
                    tripId = event.tripId,
                    lat = event.latitude,
                    lng = event.longitude,
                    speed = event.speed,
                    bearing = event.bearing
                )
            }
        }

        // Collect real-time status changes
        lifecycleScope.launch {
            trackingRepository.statusUpdates().collectLatest { event ->
                Timber.d("$TAG: Status change: ${event.assignmentId} → ${event.status}")

                // Update marker color
                addOrUpdateDriverMarker(
                    tripId = event.tripId,
                    lat = 0.0, // Don't change position
                    lng = 0.0,
                    status = event.status
                )

                // Update truck card status
                trucksAdapter.updateTruckStatus(event.assignmentId, event.status)

                // Show status notification banner
                showStatusBanner(event.status, event.vehicleNumber)
            }
        }

        // Listen for booking_completed → auto-show rating bottom sheet
        lifecycleScope.launch {
            trackingRepository.bookingCompleted().collectLatest { event ->
                timber.log.Timber.i("$TAG: Booking completed: ${event.bookingId}")
                if (!ratingSheetShown && !isFinishing && !isDestroyed) {
                    showRatingSheet()
                }
            }
        }

        // Monitor connection state for "Reconnecting..." indicator
        lifecycleScope.launch {
            trackingRepository.connectionState.collectLatest { state ->
                when (state) {
                    WebSocketService.ConnectionState.CONNECTED -> {
                        binding.chipLive.text = "LIVE"
                        binding.chipLive.setChipBackgroundColorResource(R.color.success_green)
                    }
                    WebSocketService.ConnectionState.RECONNECTING -> {
                        binding.chipLive.text = "RECONNECTING..."
                        binding.chipLive.setChipBackgroundColorResource(R.color.warning_orange)
                    }
                    WebSocketService.ConnectionState.FAILED -> {
                        binding.chipLive.text = "OFFLINE"
                        binding.chipLive.setChipBackgroundColorResource(R.color.error_red)
                    }
                    else -> { /* CONNECTING, DISCONNECTED — no change */ }
                }
            }
        }
    }

    // =========================================================================
    // STATUS NOTIFICATION BANNER (4.4)
    // =========================================================================

    /**
     * Show a slide-down status banner with auto-dismiss.
     * Rapido-style: Yellow background, bold text, 3.5s duration.
     */
    private fun showStatusBanner(status: String, vehicleNumber: String) {
        val message = getStatusMessage(status, vehicleNumber)
        if (message.isEmpty()) return

        runOnUiThread {
            binding.tvStatusBanner.text = message
            binding.statusBanner.visibility = View.VISIBLE

            // Slide down animation — use dp→px to match XML translationY="-100dp"
            val entryOffsetPx = (-100 * resources.displayMetrics.density)
            ObjectAnimator.ofFloat(binding.statusBanner, "translationY", entryOffsetPx, 0f).apply {
                duration = 300
                start()
            }

            // Auto-dismiss after 3.5 seconds
            bannerHandler.removeCallbacksAndMessages(null)
            bannerHandler.postDelayed({
                val dismissOffsetPx = (-100 * resources.displayMetrics.density)
                ObjectAnimator.ofFloat(binding.statusBanner, "translationY", 0f, dismissOffsetPx).apply {
                    duration = 250
                    start()
                }
                bannerHandler.postDelayed({
                    binding.statusBanner.visibility = View.GONE
                }, 250)
            }, BANNER_DISMISS_DELAY_MS)
        }
    }

    /**
     * Map status codes to human-readable notification messages.
     */
    private fun getStatusMessage(status: String, vehicleNumber: String): String {
        return when (status.lowercase(java.util.Locale.ROOT)) {
            "heading_to_pickup", "driver_accepted" -> "\uD83D\uDE9B Driver is heading to pickup"
            "at_pickup" -> "\uD83D\uDE9B Driver arrived at pickup"
            "loading_complete" -> "\uD83D\uDCE6 Loading complete, trip starting"
            "in_transit" -> "\uD83D\uDE9B Truck $vehicleNumber is on the way!"
            "arrived_at_drop" -> "\uD83D\uDCCD Driver arrived at destination"
            "completed" -> "✅ Delivery complete for $vehicleNumber"
            "driver_declined" -> "⚠️ Driver declined — reassigning..."
            else -> ""
        }
    }

    // =========================================================================
    // RATING BOTTOM SHEET (auto-popup on booking completion)
    // =========================================================================

    /**
     * Fetch pending ratings and show the rating bottom sheet.
     * Called when booking_completed WebSocket event is received.
     */
    private fun showRatingSheet() {
        if (ratingSheetShown || isFinishing || isDestroyed) return
        ratingSheetShown = true

        lifecycleScope.launch {
            try {
                val accessToken = tokenManager.getAccessToken()
                if (accessToken.isNullOrBlank()) {
                    timber.log.Timber.w("$TAG: Rating sheet skipped, token missing")
                    ratingSheetShown = false
                    return@launch
                }

                val token = "Bearer $accessToken"
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    apiService.getPendingRatings(token)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val pending = response.body()?.data ?: emptyList()
                    if (pending.isNotEmpty()) {
                        com.weelo.logistics.ui.bottomsheet.RatingBottomSheetFragment
                            .newInstance(pending)
                            .apply {
                                onAllRatingsComplete = {
                                    timber.log.Timber.i("$TAG: All ratings submitted from tracking")
                                }
                            }
                            .show(supportFragmentManager, "rating_sheet")
                    } else {
                        // Allow a retry in case backend eventual consistency delays pending ratings.
                        ratingSheetShown = false
                    }
                } else {
                    ratingSheetShown = false
                }
            } catch (e: Exception) {
                timber.log.Timber.w(e, "$TAG: Failed to fetch pending ratings")
                ratingSheetShown = false // Allow retry
            }
        }
    }

    // =========================================================================
    // SINGLE-TRUCK DEEP DIVE (4.5)
    // =========================================================================

    /**
     * Zoom into a single truck — hides other markers, shows driver info card.
     */
    private fun enterDeepDive(truck: AssignedTruckData) {
        focusedTripId = truck.tripId

        // Zoom to this truck's marker
        val marker = driverMarkers[truck.tripId]
        if (marker != null) {
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
            )
        }

        // Hide other truck markers
        driverMarkers.forEach { (tripId, m) ->
            m.isVisible = tripId == truck.tripId
        }

        // Update bottom sheet with focused truck info
        binding.tvDriverName.text = truck.driverName
        binding.tvVehicleNumber.text = truck.vehicleNumber
        binding.tvVehicleType.text = truck.vehicleType
        driverPhone = truck.driverPhone

        // Show "Back to All" button, hide truck cards
        binding.btnBackToAll.visibility = View.VISIBLE
        binding.rvTruckCards.visibility = View.GONE

        Timber.d("$TAG: Deep dive into truck ${truck.vehicleNumber}")
    }

    /**
     * Exit deep dive — show all markers again, reset camera.
     */
    private fun exitDeepDive() {
        focusedTripId = null

        // Show all markers
        driverMarkers.values.forEach { it.isVisible = true }

        // Reset camera to show all
        fitCameraToAllMarkers()

        // Hide "Back to All", show truck cards
        binding.btnBackToAll.visibility = View.GONE
        binding.rvTruckCards.visibility = View.VISIBLE

        Timber.d("$TAG: Exited deep dive, showing all trucks")
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Show CancellationBottomSheet for cancelling the booking with reason
     * 
     * SCALABILITY: Backend releases vehicles + drivers, notifies all parties
     * EASY UNDERSTANDING: User picks reason → confirms → navigates home
     * MODULARITY: Reuses CancellationBottomSheet from ui/bottomsheet package
     */
    private fun showCancelBookingSheet() {
        val orderId = bookingId ?: return
        
        val pickupAddr = intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: ""
        val dropAddr = intent.getStringExtra(EXTRA_DROP_ADDRESS) ?: ""
        val vehicleType = intent.getStringExtra(EXTRA_VEHICLE_TYPE) ?: ""
        
        val cancelSheet = com.weelo.logistics.ui.bottomsheet.CancellationBottomSheet.newInstance(
            pickupAddress = pickupAddr,
            dropAddress = dropAddr,
            vehicleSummary = vehicleType
        )
        
        cancelSheet.onCancellationConfirmed = { reason ->
            lifecycleScope.launch {
                try {
                    val result = bookingApiRepository.cancelOrder(orderId, reason)
                    when (result) {
                        is com.weelo.logistics.core.common.Result.Success -> {
                            cancelSheet.onCancelComplete(true)
                            timber.log.Timber.d("Booking cancelled: $orderId, reason: $reason")
                            android.widget.Toast.makeText(
                                this@BookingTrackingActivity,
                                "Booking cancelled successfully.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                        is com.weelo.logistics.core.common.Result.Error -> {
                            cancelSheet.onCancelComplete(false, result.exception.message)
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    cancelSheet.onCancelComplete(false, e.message)
                }
            }
        }
        
        cancelSheet.show(supportFragmentManager, "cancel_booking")
    }

    private fun callDriver() {
        val phone = driverPhone ?: return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
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
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Exit deep dive first, then exit activity
                if (focusedTripId != null) {
                    exitDeepDive()
                } else {
                    finish()
                    TransitionHelper.applySlideOutRightTransition(this@BookingTrackingActivity)
                }
            }
        })
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    override fun onDestroy() {
        super.onDestroy()

        // Leave WebSocket booking room
        bookingId?.let { trackingRepository.stopTracking(it) }

        // Cancel all marker animations to prevent memory leaks
        MarkerAnimationHelper.cancelAllAnimations()

        // Remove banner + ETA callbacks
        bannerHandler.removeCallbacksAndMessages(null)
        etaHandler.removeCallbacksAndMessages(null)

        Timber.d("$TAG: Activity destroyed, tracking stopped")
    }
}
