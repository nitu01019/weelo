package com.weelo.logistics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.MapStyleOptions
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.utils.NetworkUtils
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.core.util.getParcelableArrayExtraCompat
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.api.RouteCalculationRequest
import com.weelo.logistics.data.remote.api.RouteCoordinates
import com.weelo.logistics.data.remote.api.MultiPointRouteRequest
import com.weelo.logistics.data.remote.api.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.lifecycle.lifecycleScope

/**
 * MapBookingActivity - Map & Booking Screen
 * Shows map with route, vehicle category selection
 * 
 * REFACTORED: Uses AWS Location Service via backend instead of Google Directions API
 */
class MapBookingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    // REMOVED: private lateinit var mapView: MapView - Using fragment instead
    private lateinit var backButton: CardView
    private lateinit var addStopButton: CardView
    private lateinit var truckCard: View
    private lateinit var tractorCard: View
    private lateinit var jcbCard: View
    private lateinit var tempoCard: View
    // REMOVED: private lateinit var continueButton: android.widget.Button
    // REMOVED: private lateinit var continueButton: android.widget.Button
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var fromLocation: Location
    private lateinit var toLocation: Location
    private var intermediateStops = mutableListOf<String>()
    private val intermediateStopsLatLng = mutableListOf<LatLng>()
    
    /**
     * Booking mode: "INSTANT" or "CUSTOM"
     * 
     * SCALABILITY: Determines navigation flow without additional API calls
     * EASY UNDERSTANDING: Clear mode differentiation
     * MODULARITY: Single point of mode-based logic
     */
    private var bookingMode: String = "INSTANT"
    
    // Default Jammu coordinates (fallback)
    private var userLatLng = LatLng(32.7266, 74.8570)
    private var destinationLatLng = LatLng(32.7355, 74.8702)

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val MAX_INTERMEDIATE_STOPS = 3
    
    // AWS Location API via Backend (replaces Google Directions)
    private lateinit var weeloApiService: WeeloApiService
    private var currentPolyline: Polyline? = null

    // Cached marker icons (loaded once, reused) - Production Pattern
    private var pickupPinIcon: com.google.android.gms.maps.model.BitmapDescriptor? = null
    private var dropPinIcon: com.google.android.gms.maps.model.BitmapDescriptor? = null
    private var stopPinIcon: com.google.android.gms.maps.model.BitmapDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL FIX: Set window background to white immediately to prevent dark screen
        window.setBackgroundDrawableResource(android.R.color.white)
        
        setContentView(R.layout.activity_map_booking)

        // Initialize Retrofit for Google Maps API
        initializeMapsService()
        
        // Pre-load marker icons (Production Pattern: cache at startup)
        loadMarkerIcons()

        // Get data from intent using compatibility function
        fromLocation = intent.getParcelableExtraCompat<Location>("FROM_LOCATION") ?: Location("Jammu")
        toLocation = intent.getParcelableExtraCompat<Location>("TO_LOCATION") ?: Location("Destination")
        
        // Get booking mode - determines INSTANT vs CUSTOM navigation flow
        // SCALABILITY: Single intent extra controls entire navigation logic
        bookingMode = intent.getStringExtra("BOOKING_MODE") ?: "INSTANT"
        timber.log.Timber.d("MapBookingActivity: bookingMode = $bookingMode")
        
        // Get intermediate stops - prefer Location array with coordinates
        val stopsLocations = intent.getParcelableArrayExtraCompat<Location>("INTERMEDIATE_STOPS_LOCATIONS")
        if (stopsLocations != null && stopsLocations.isNotEmpty()) {
            // Use actual coordinates from Location objects
            for (stop in stopsLocations) {
                intermediateStops.add(stop.address)
                if (stop.latitude != 0.0 && stop.longitude != 0.0) {
                    intermediateStopsLatLng.add(LatLng(stop.latitude, stop.longitude))
                }
            }
        } else {
            // Fallback: Get addresses only (legacy format)
            val stopsArray = intent.getStringArrayExtra("INTERMEDIATE_STOPS") ?: arrayOf()
            intermediateStops.addAll(stopsArray.toList())
            // Note: Without coordinates, stops will need geocoding or be placed on straight line
        }
        
        // Fallback for old intent extras
        if (!fromLocation.isValid()) {
            fromLocation = Location(intent.getStringExtra("PICKUP_LOCATION") ?: "Jammu")
            toLocation = Location(intent.getStringExtra("DROP_LOCATION") ?: "Destination")
        }
        
        // Use actual coordinates if available
        if (fromLocation.latitude != 0.0 && fromLocation.longitude != 0.0) {
            userLatLng = LatLng(fromLocation.latitude, fromLocation.longitude)
        }
        if (toLocation.latitude != 0.0 && toLocation.longitude != 0.0) {
            destinationLatLng = LatLng(toLocation.latitude, toLocation.longitude)
        }

        // Initialize views
        backButton = findViewById(R.id.backButton)
        addStopButton = findViewById(R.id.addStopButton)
        truckCard = findViewById(R.id.truckCard)
        tractorCard = findViewById(R.id.tractorCard)
        jcbCard = findViewById(R.id.jcbCard)
        tempoCard = findViewById(R.id.tempoCard)
        // REMOVED: continueButton = findViewById(R.id.continueButton)
        // REMOVED: continueButton = findViewById(R.id.continueButton)
        bottomSheet = findViewById(R.id.bottomSheet)
        
        // Setup bottom sheet behavior BEFORE map initialization
        setupBottomSheet()

        // CRITICAL FIX: Use SupportMapFragment (matches layout) instead of MapView
        // Initialize map fragment - this is the correct approach for fragment-based maps
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        
        // POST the map loading to allow UI to render first (prevents dark screen)
        mapFragment?.view?.post {
            mapFragment.getMapAsync(this)
        } ?: run {
            // Fallback: load immediately if post fails
            mapFragment?.getMapAsync(this)
        }

        // Back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Add Stop button - Go back to location input page and auto-add stop
        addStopButton.setOnClickListener {
            val intent = Intent(this, LocationInputActivity::class.java).apply {
                putExtra("AUTO_ADD_STOP", true)
                // Pass current locations back
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
                // Pass existing stops
                if (intermediateStops.isNotEmpty()) {
                    putExtra("INTERMEDIATE_STOPS", intermediateStops.toTypedArray())
                }
            }
            startActivity(intent)
            finish() // Close map activity
        }

        // Truck category - Navigate based on booking mode
        // SCALABILITY: Single check determines entire navigation flow
        // EASY UNDERSTANDING: CUSTOM skips TruckTypesActivity, goes to CustomBookingActivity
        // MODULARITY: Each mode has clear, separate flow
        truckCard.setOnClickListener {
            navigateToVehicleSelection("truck")
        }

        // Tractor category - Navigate based on booking mode
        tractorCard.setOnClickListener {
            navigateToVehicleSelection("tractor")
        }

        // JCB category - Navigate based on booking mode
        jcbCard.setOnClickListener {
            navigateToVehicleSelection("jcb")
        }

        // Tempo category - Navigate based on booking mode
        tempoCard.setOnClickListener {
            navigateToVehicleSelection("tempo")
        }
        
        // REMOVED: Continue button click listener (button removed from layout)

        // Check location permissions
        checkLocationPermission()
    }
    
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        
        // Start hidden for entrance animation
        bottomSheet.visibility = android.view.View.INVISIBLE
        
        // Locked Bottom Sheet (Rapido Style) - Static, no drag
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isDraggable = false
        bottomSheetBehavior.isHideable = false
    }
    
    /**
     * Animates the bottom sheet sliding up from below the screen
     * Called after map is ready for smooth visual transition
     * Production-ready: uses hardware acceleration for butter-smooth 60fps
     */
    private fun animateBottomSheetEntrance() {
        bottomSheet.post {
            val slideUp = android.view.animation.AnimationUtils.loadAnimation(
                this, R.anim.slide_up_bottom_sheet
            )
            bottomSheet.visibility = android.view.View.VISIBLE
            bottomSheet.startAnimation(slideUp)
        }
    }
    
    /**
     * Adds a stop marker on the map using custom pin icon with visible banner
     * 
     * RAPIDO STYLE: Shows "Stop 1", "Stop 2" banner immediately visible on map
     * SCALABILITY: Uses custom InfoWindowAdapter for consistent styling
     * EASY UNDERSTANDING: Clear visual indication of stop sequence
     */
    private fun addStopMarker(location: LatLng, stopNumber: Int) {
        // Get stop address if available
        val stopAddress = if (stopNumber <= intermediateStops.size) {
            intermediateStops[stopNumber - 1]
        } else ""
        
        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Stop $stopNumber")
                .snippet(stopAddress)
                .icon(stopPinIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )
        
        // Show info window immediately so banner is visible
        marker?.showInfoWindow()
    }
    
    /**
     * Setup custom InfoWindowAdapter for styled stop banners
     * 
     * RAPIDO STYLE: Clean white banners with orange accent showing stop numbers
     * SCALABILITY: Single adapter handles all marker info windows
     * MODULARITY: Uses separate layout file (marker_stop_banner.xml)
     */
    private fun setupCustomInfoWindowAdapter() {
        googleMap.setInfoWindowAdapter(object : com.google.android.gms.maps.GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: com.google.android.gms.maps.model.Marker): android.view.View? {
                // Return null to use default frame with custom contents
                return null
            }
            
            override fun getInfoContents(marker: com.google.android.gms.maps.model.Marker): android.view.View {
                val view = layoutInflater.inflate(R.layout.marker_stop_banner, null)
                
                // Set stop label (e.g., "Stop 1", "Pickup", "Drop")
                view.findViewById<android.widget.TextView>(R.id.stopLabel).text = marker.title
                
                // Set address snippet if available
                val addressView = view.findViewById<android.widget.TextView>(R.id.stopAddress)
                val snippet = marker.snippet
                if (!snippet.isNullOrBlank()) {
                    // Show first part of address (before comma)
                    val shortAddress = snippet.split(",").firstOrNull()?.trim() ?: snippet
                    addressView.text = shortAddress
                    addressView.visibility = android.view.View.VISIBLE
                } else {
                    addressView.visibility = android.view.View.GONE
                }
                
                return view
            }
        })
    }

    /**
     * Load marker icons once at startup (Production Pattern: instant screen load)
     * Converts vector drawables to BitmapDescriptor for map markers
     * Uses Rapido-style circle markers instead of pin markers
     */
    private fun loadMarkerIcons() {
        pickupPinIcon = vectorToBitmap(R.drawable.ic_marker_pickup)
        dropPinIcon = vectorToBitmap(R.drawable.ic_marker_drop)
        stopPinIcon = vectorToBitmap(R.drawable.ic_marker_stop_base)
    }

    /**
     * Converts a vector drawable to BitmapDescriptor for Google Maps
     * Production Pattern: Caching prevents repeated conversions
     */
    private fun vectorToBitmap(drawableId: Int): com.google.android.gms.maps.model.BitmapDescriptor? {
        return try {
            val drawable = ContextCompat.getDrawable(this, drawableId) ?: return null
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error converting vector to bitmap")
            null
        }
    }
    
    /**
     * Initialize Weelo API Service for route calculation (AWS Location)
     */
    private fun initializeMapsService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        weeloApiService = retrofit.create(WeeloApiService::class.java)
    }
    
    /**
     * Fetch route from AWS Location Service via backend and draw on map
     * Uses multi-waypoint API for road-following routes through all stops
     *
     * REFACTORED: Uses /route-multi endpoint with pickup + stops + drop
     * Falls back to straight line if API fails
     */
    private fun fetchAndDrawRoute() {
        // Log coordinates for debugging
        timber.log.Timber.d("Route: From ${userLatLng.latitude},${userLatLng.longitude} to ${destinationLatLng.latitude},${destinationLatLng.longitude}")
        timber.log.Timber.d("Route: ${intermediateStopsLatLng.size} intermediate stops")

        // Always draw straight line first (instant feedback)
        drawStraightLineRoute()

        // Check network first
        if (!NetworkUtils.isNetworkAvailable(this)) {
            timber.log.Timber.w("No network - using straight line route")
            return
        }

        // Build points list: pickup → stops → drop
        val routePoints = mutableListOf<RoutePoint>()
        
        // Add pickup
        routePoints.add(RoutePoint(
            lat = userLatLng.latitude,
            lng = userLatLng.longitude,
            label = fromLocation.address
        ))
        
        // Add intermediate stops (if any)
        for ((index, stopLatLng) in intermediateStopsLatLng.withIndex()) {
            val stopLabel = if (index < intermediateStops.size) intermediateStops[index] else "Stop ${index + 1}"
            routePoints.add(RoutePoint(
                lat = stopLatLng.latitude,
                lng = stopLatLng.longitude,
                label = stopLabel
            ))
        }
        
        // Add drop
        routePoints.add(RoutePoint(
            lat = destinationLatLng.latitude,
            lng = destinationLatLng.longitude,
            label = toLocation.address
        ))

        val request = MultiPointRouteRequest(
            points = routePoints,
            truckMode = true,
            includePolyline = true
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = weeloApiService.calculateRouteMulti(request)
                
                withContext(Dispatchers.Main) {
                    val routeData = response.body()
                    if (response.isSuccessful && routeData?.success == true && routeData.data != null) {
                        val polylineData = routeData.data.polyline
                        if (polylineData != null && polylineData.isNotEmpty()) {
                            // Convert [[lat, lng], ...] to List<LatLng>
                            val roadPoints = polylineData.mapNotNull { coords ->
                                if (coords.size >= 2) {
                                    LatLng(coords[0], coords[1])
                                } else null
                            }
                            
                            if (roadPoints.isNotEmpty()) {
                                drawRoutePolyline(roadPoints)
                                timber.log.Timber.d("Multi-point route drawn: ${routeData.data.distanceKm} km via ${routeData.data.source} (${routePoints.size} points)")
                            } else {
                                // No valid route points - don't draw straight line, just log
                                timber.log.Timber.w("Route API returned empty polyline points")
                            }
                        } else {
                            // No polyline returned - don't draw straight line
                            timber.log.Timber.w("Route API returned no polyline data")
                        }
                    } else {
                        // API error - don't draw straight line, just log
                        timber.log.Timber.w("Route API error: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error fetching route from backend - no fallback line")
                // Don't draw straight line on error
            }
        }
    }
    
    /**
     * Draw the route polyline on the map
     */
    private fun drawRoutePolyline(routePoints: List<LatLng>) {
        // Remove old polyline if exists
        currentPolyline?.remove()

        // Draw new polyline with proper route - blue color for visibility
        val polylineOptions = PolylineOptions()
            .addAll(routePoints)
            .width(12f)  // Thicker for better visibility
            .color(android.graphics.Color.BLACK)  // Black color as per design
            .geodesic(true)

        currentPolyline = googleMap.addPolyline(polylineOptions)
        timber.log.Timber.d("Route polyline drawn with ${routePoints.size} points")
    }

    /**
     * Fallback: Draw straight line route when API fails or no internet
     */
    private fun drawStraightLineRoute() {
        // Remove old polyline if exists
        currentPolyline?.remove()

        val allPoints = mutableListOf(userLatLng)
        allPoints.addAll(intermediateStopsLatLng)
        allPoints.add(destinationLatLng)

        // Check if we have valid coordinates (not default 0,0)
        timber.log.Timber.d("Drawing straight line with ${allPoints.size} points")

        val polylineOptions = PolylineOptions()
            .addAll(allPoints)
            .width(10f)  // Visible width
            .color(android.graphics.Color.BLACK)  // Black color as per design
            .geodesic(true)

        currentPolyline = googleMap.addPolyline(polylineOptions)
    }
    
    /**
     * Redraws the route with all stops
     */
    private fun redrawRoute() {
        // Clear existing polylines
        googleMap.clear()
        currentPolyline = null
        
        // Re-add all markers
        googleMap.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("From: ${fromLocation.address}")
                .icon(pickupPinIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        
        // Add intermediate stop markers
        for (i in intermediateStopsLatLng.indices) {
            addStopMarker(intermediateStopsLatLng[i], i + 1)
        }
        
        googleMap.addMarker(
            MarkerOptions()
                .position(destinationLatLng)
                .title("To: ${toLocation.address}")
                .icon(dropPinIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        
        // Fetch and draw proper route
        fetchAndDrawRoute()
        
        // Adjust camera to show all points
        zoomToFitRoute()
    }

    /**
     * Adjusts the camera to show all route points with padding for the bottom sheet.
     */
    private fun zoomToFitRoute() {
        // Build bounds from all route points
        val allPoints = mutableListOf(userLatLng)
        allPoints.addAll(intermediateStopsLatLng)
        allPoints.add(destinationLatLng)
        
        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
        allPoints.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()
        
        // Set map padding to account for bottom sheet
        // This affects all subsequent camera movements
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomPadding = (screenHeight * 0.38).toInt() // 38% for bottom sheet
        val topPadding = (60 * displayMetrics.density).toInt() // 60dp for header
        
        googleMap.setPadding(0, topPadding, 0, bottomPadding)
        
        // Now animate with simple padding (map already has asymmetric padding)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            // Setup custom info window adapter for stop banners (RAPIDO STYLE)
            setupCustomInfoWindowAdapter()
            
            // FAST MAP LOADING OPTIMIZATIONS
            googleMap.apply {
                // Disable unnecessary features for faster load
                uiSettings.isRotateGesturesEnabled = false
                uiSettings.isTiltGesturesEnabled = false
                uiSettings.isIndoorLevelPickerEnabled = false
                uiSettings.isMapToolbarEnabled = false
                
                // Keep only essential controls
                uiSettings.isZoomControlsEnabled = false
                uiSettings.isMyLocationButtonEnabled = false
                uiSettings.isCompassEnabled = false
                
                // Use NORMAL map type (fastest)
                mapType = GoogleMap.MAP_TYPE_NORMAL
                
                // Optimize rendering
                setMinZoomPreference(4f)  // Allows viewing entire India for long routes
                setMaxZoomPreference(18f)

                // Apply Rapido-style Custom Map Style
                try {
                    val success = setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(
                            this@MapBookingActivity, R.raw.map_style_clean
                        )
                    )
                    if (!success) {
                        timber.log.Timber.e("Style parsing failed.")
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Can't find style. Error: ")
                }
            }
            
            // Check network before loading map
            if (!NetworkUtils.isNetworkAvailable(this)) {
                Toast.makeText(this, "No internet connection. Map may not load properly.", Toast.LENGTH_LONG).show()
            }

            // Add markers with custom pin icons (cached)
            googleMap.addMarker(
                MarkerOptions()
                    .position(userLatLng)
                    .title("From: ${fromLocation.address}")
                    .icon(pickupPinIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )

            // Add any intermediate stops passed from previous screen
            // CRITICAL FIX: Use actual coordinates if available, not fake calculated positions
            if (intermediateStops.isNotEmpty()) {
                for (i in intermediateStops.indices) {
                    val stopNumber = i + 1
                    val stopLatLng: LatLng
                    
                    // Check if we have actual coordinates from the intent
                    if (i < intermediateStopsLatLng.size) {
                        // Use actual geocoded coordinates
                        stopLatLng = intermediateStopsLatLng[i]
                    } else {
                        // Fallback: Calculate approximate position (only if no coordinates)
                        val fraction = stopNumber.toDouble() / (intermediateStops.size + 1)
                        val lat = userLatLng.latitude + (destinationLatLng.latitude - userLatLng.latitude) * fraction
                        val lng = userLatLng.longitude + (destinationLatLng.longitude - userLatLng.longitude) * fraction
                        stopLatLng = LatLng(lat, lng)
                        intermediateStopsLatLng.add(stopLatLng)
                    }
                    
                    addStopMarker(stopLatLng, stopNumber)
                }
            }

            googleMap.addMarker(
                MarkerOptions()
                    .position(destinationLatLng)
                    .title("To: ${toLocation.address}")
                    .icon(dropPinIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            // Draw route between markers
            fetchAndDrawRoute()

            // Move camera to show all markers with proper padding
            zoomToFitRoute()

            // Enable location if permission granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
            }

            // Map settings
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            googleMap.uiSettings.isCompassEnabled = true
            
            // Animate bottom sheet sliding up from below (Rapido-style entrance)
            animateBottomSheetEntrance()
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error loading map")
            Toast.makeText(this, "Error loading map. Please check your internet connection.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Check location permission silently — do NOT request here.
     * Permission is requested ONCE in LocationInputActivity (the entry point).
     * This avoids showing the permission dialog 3 times during the booking flow.
     */
    private fun checkLocationPermission() {
        // Only enable my-location layer if already granted — no dialog
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            if (::googleMap.isInitialized) {
                googleMap.isMyLocationEnabled = true
            }
        }
    }


    // ========== MapView Lifecycle Methods (REQUIRED for performance) ==========
    // REMOVED: MapView lifecycle methods - not needed for SupportMapFragment
    // The fragment manages its own lifecycle automatically
    
    // ========================================
    // Vehicle Selection Navigation
    // ========================================
    
    /**
     * Navigate to appropriate screen based on booking mode and vehicle category
     * 
     * SCALABILITY: Centralized navigation logic - easy to add new vehicle types
     * EASY UNDERSTANDING: Clear if-else for CUSTOM vs INSTANT mode
     * MODULARITY: Single function handles all vehicle navigation
     * CODING STANDARDS: Follows existing navigation patterns
     * 
     * @param vehicleCategory The category of vehicle selected (truck, tractor, jcb, tempo)
     */
    private fun navigateToVehicleSelection(vehicleCategory: String) {
        if (bookingMode == "CUSTOM") {
            // CUSTOM MODE: Skip instant booking flow, go directly to CustomBookingActivity
            // User only needs to select trucks for long-term contract
            navigateToCustomBooking(vehicleCategory)
        } else {
            // INSTANT MODE: Go to vehicle-specific types screen
            navigateToInstantBooking(vehicleCategory)
        }
    }
    
    /**
     * Navigate to CustomBookingActivity for long-term contracts
     * 
     * SCALABILITY: Passes pickup location for pre-filling form
     * EASY UNDERSTANDING: Direct navigation to custom booking form
     */
    private fun navigateToCustomBooking(vehicleCategory: String) {
        val intent = Intent(this, CustomBookingActivity::class.java).apply {
            // Pass pickup location for form pre-fill
            putExtra("PICKUP_LOCATION", fromLocation)
            // Pass vehicle category hint (optional - for UI customization)
            putExtra("VEHICLE_CATEGORY", vehicleCategory)
        }
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
        timber.log.Timber.d("Navigating to CustomBookingActivity: category=$vehicleCategory")
    }
    
    /**
     * Navigate to instant booking vehicle types screen
     * 
     * SCALABILITY: Supports multiple vehicle categories
     * MODULARITY: Each category has its own types activity
     */
    /**
     * Navigate to instant booking vehicle types screen
     * 
     * SCALABILITY: Supports multiple vehicle categories
     * MODULARITY: Each category has its own types activity
     * 
     * CRITICAL: Passes intermediate stops through to TruckTypesActivity
     * so they reach the order creation API
     */
    override fun onDestroy() {
        super.onDestroy()
        // Clean up map resources
        currentPolyline?.remove()
        currentPolyline = null
    }
    
    private fun navigateToInstantBooking(vehicleCategory: String) {
        val intent = when (vehicleCategory) {
            "truck" -> Intent(this, TruckTypesActivity::class.java).apply {
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
                // CRITICAL: Pass intermediate stops through to order creation
                if (intermediateStops.isNotEmpty()) {
                    putExtra("INTERMEDIATE_STOPS", intermediateStops.toTypedArray())
                    // Pass Location objects with coordinates
                    if (intermediateStopsLatLng.isNotEmpty()) {
                        val stopLocations = intermediateStops.mapIndexed { index, address ->
                            val latLng = if (index < intermediateStopsLatLng.size) intermediateStopsLatLng[index] else null
                            Location(address, latLng?.latitude ?: 0.0, latLng?.longitude ?: 0.0)
                        }.toTypedArray()
                        putExtra("INTERMEDIATE_STOPS_LOCATIONS", stopLocations)
                    }
                }
            }
            "tractor" -> Intent(this, TractorMachineryTypesActivity::class.java).apply {
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
                // Pass stops for tractor bookings too
                if (intermediateStops.isNotEmpty()) {
                    putExtra("INTERMEDIATE_STOPS", intermediateStops.toTypedArray())
                }
            }
            "jcb" -> {
                // JCB coming soon - show toast and return
                Toast.makeText(this, "JCB/Construction machinery - Coming soon!", Toast.LENGTH_SHORT).show()
                return
            }
            "tempo" -> {
                // Tempo coming soon - show toast and return
                Toast.makeText(this, "Tempo selection - Coming soon!", Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                Toast.makeText(this, "Vehicle type not supported", Toast.LENGTH_SHORT).show()
                return
            }
        }
        startActivity(intent)
        TransitionHelper.applySlideInLeftTransition(this)
        timber.log.Timber.d("Navigating to instant booking: category=$vehicleCategory")
    }
    
}
