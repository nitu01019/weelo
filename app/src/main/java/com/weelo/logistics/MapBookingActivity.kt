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
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.utils.NetworkUtils
import com.weelo.logistics.utils.PolylineDecoder
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.data.remote.api.GoogleMapsService
import com.weelo.logistics.data.remote.dto.DirectionsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * MapBookingActivity - Map & Booking Screen
 * Shows map with route, vehicle category selection
 */
class MapBookingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var backButton: CardView
    private lateinit var addStopButton: CardView
    private lateinit var truckCard: CardView
    private lateinit var tractorCard: CardView
    private lateinit var jcbCard: CardView
    private lateinit var tempoCard: CardView
    private lateinit var continueButton: android.widget.Button
    private lateinit var dragHandle: LinearLayout
    private lateinit var dragText: TextView
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var fromLocation: Location
    private lateinit var toLocation: Location
    private var intermediateStops = mutableListOf<String>()
    private val intermediateStopsLatLng = mutableListOf<LatLng>()
    
    // Default Jammu coordinates (fallback)
    private var userLatLng = LatLng(32.7266, 74.8570)
    private var destinationLatLng = LatLng(32.7355, 74.8702)

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val MAX_INTERMEDIATE_STOPS = 3
    
    // Google Maps API for Directions
    private lateinit var mapsService: GoogleMapsService
    private var currentPolyline: Polyline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_booking)

        // Initialize Retrofit for Google Maps API
        initializeMapsService()

        // Get data from intent using compatibility function
        fromLocation = intent.getParcelableExtraCompat<Location>("FROM_LOCATION") ?: Location("Jammu")
        toLocation = intent.getParcelableExtraCompat<Location>("TO_LOCATION") ?: Location("Destination")
        
        // Get intermediate stops from previous screen
        val stopsArray = intent.getStringArrayExtra("INTERMEDIATE_STOPS") ?: arrayOf()
        intermediateStops.addAll(stopsArray.toList())
        
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
        continueButton = findViewById(R.id.continueButton)
        dragHandle = findViewById(R.id.dragHandle)
        dragText = findViewById(R.id.dragText)
        bottomSheet = findViewById(R.id.bottomSheet)
        
        // Setup bottom sheet behavior
        setupBottomSheet()

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

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

        // Truck category - Navigate to Truck Types
        truckCard.setOnClickListener {
            val intent = Intent(this, TruckTypesActivity::class.java).apply {
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
            }
            startActivity(intent)
            TransitionHelper.applySlideInLeftTransition(this)
        }

        // Tractor category - Navigate to Tractor Machinery Types
        tractorCard.setOnClickListener {
            val intent = Intent(this, TractorMachineryTypesActivity::class.java).apply {
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
            }
            startActivity(intent)
            TransitionHelper.applySlideInLeftTransition(this)
        }

        // JCB category - Coming soon (will be implemented)
        jcbCard.setOnClickListener {
            Toast.makeText(this, "JCB/Construction machinery - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Tempo category - Coming soon
        tempoCard.setOnClickListener {
            Toast.makeText(this, "Tempo selection - Coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        // Continue button - Navigate to truck types (default)
        continueButton.setOnClickListener {
            val intent = Intent(this, TruckTypesActivity::class.java).apply {
                putExtra("FROM_LOCATION", fromLocation)
                putExtra("TO_LOCATION", toLocation)
            }
            startActivity(intent)
            TransitionHelper.applySlideInLeftTransition(this)
        }

        // Check location permissions
        checkLocationPermission()
    }
    
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        
        // Set initial state - EXPANDED by default
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetBehavior.peekHeight = 350 // Height when collapsed
        bottomSheetBehavior.isHideable = false
        
        // Add callback for state changes
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        dragText.text = getString(R.string.collapse)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        dragText.text = getString(R.string.expand)
                    }
                }
            }
            
            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                // Optional: animate something based on slide offset
            }
        })
        
        // Drag handle click to toggle
        dragHandle.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
    
    /**
     * Adds a stop marker on the map
     */
    private fun addStopMarker(location: LatLng, stopNumber: Int) {
        googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Stop $stopNumber")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )
    }
    
    /**
     * Initialize Google Maps Service for Directions API
     */
    private fun initializeMapsService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/maps/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        mapsService = retrofit.create(GoogleMapsService::class.java)
    }
    
    /**
     * Fetch route from Google Directions API and draw on map
     * This will show proper curved roads instead of straight lines
     * NOTE: Currently disabled until Google Directions API billing is enabled
     */
    private fun fetchAndDrawRoute() {
        // Silently skip route fetching - will be enabled when API is ready
        // No errors, no crashes, just shows markers
        // TODO: Enable this when Google Directions API billing is set up
    }
    
    /**
     * Draw the route polyline on the map
     */
    private fun drawRoutePolyline(routePoints: List<LatLng>) {
        // Remove old polyline if exists
        currentPolyline?.remove()
        
        // Draw new polyline with proper route
        val polylineOptions = PolylineOptions()
            .addAll(routePoints)
            .width(10f)
            .color(ContextCompat.getColor(this, android.R.color.black))
            .geodesic(true)
        
        currentPolyline = googleMap.addPolyline(polylineOptions)
    }
    
    /**
     * Fallback: Draw straight line route when API fails or no internet
     */
    private fun drawStraightLineRoute() {
        val allPoints = mutableListOf(userLatLng)
        allPoints.addAll(intermediateStopsLatLng)
        allPoints.add(destinationLatLng)
        
        val polylineOptions = PolylineOptions()
            .addAll(allPoints)
            .width(8f)
            .color(ContextCompat.getColor(this, android.R.color.black))
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
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        
        // Add intermediate stop markers
        for (i in intermediateStopsLatLng.indices) {
            addStopMarker(intermediateStopsLatLng[i], i + 1)
        }
        
        googleMap.addMarker(
            MarkerOptions()
                .position(destinationLatLng)
                .title("To: ${toLocation.address}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        
        // Fetch and draw proper route
        fetchAndDrawRoute()
        
        // Adjust camera to show all points
        val allPoints = mutableListOf(userLatLng)
        allPoints.addAll(intermediateStopsLatLng)
        allPoints.add(destinationLatLng)
        
        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
        allPoints.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            // Check network before loading map
            if (!NetworkUtils.isNetworkAvailable(this)) {
                Toast.makeText(this, "No internet connection. Map may not load properly.", Toast.LENGTH_LONG).show()
            }

            // Keep light map style (like reference app)
            // Removed dark style to match reference

            // Add markers
            googleMap.addMarker(
                MarkerOptions()
                    .position(userLatLng)
                    .title("From: ${fromLocation.address}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )

            // Add any intermediate stops passed from previous screen
            if (intermediateStops.isNotEmpty()) {
                for (i in intermediateStops.indices) {
                    val stopNumber = i + 1
                    // Calculate position between from and to for visual representation
                    val fraction = stopNumber.toDouble() / (intermediateStops.size + 1)
                    val lat = userLatLng.latitude + (destinationLatLng.latitude - userLatLng.latitude) * fraction
                    val lng = userLatLng.longitude + (destinationLatLng.longitude - userLatLng.longitude) * fraction
                    val stopLatLng = LatLng(lat, lng)
                    intermediateStopsLatLng.add(stopLatLng)
                    addStopMarker(stopLatLng, stopNumber)
                }
            }

            googleMap.addMarker(
                MarkerOptions()
                    .position(destinationLatLng)
                    .title("To: ${toLocation.address}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            // Route rendering will be enabled when Google Directions API is set up
            // For now, just show markers without route lines
            // Uncomment below when API billing is enabled:
            // fetchAndDrawRoute()

            // Move camera to show all markers
            val allPoints = mutableListOf(userLatLng)
            allPoints.addAll(intermediateStopsLatLng)
            allPoints.add(destinationLatLng)
            
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
            allPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))

            // Enable location if permission granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
            }

            // Map settings
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            googleMap.uiSettings.isCompassEnabled = true
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading map. Please try again.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (::googleMap.isInitialized) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        googleMap.isMyLocationEnabled = true
                    }
                }
            }
        }
    }
}
