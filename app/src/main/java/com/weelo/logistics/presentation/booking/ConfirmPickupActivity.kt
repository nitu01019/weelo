package com.weelo.logistics.presentation.booking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import timber.log.Timber
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.weelo.logistics.R
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.core.util.getParcelableArrayListExtraCompat
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale

/**
 * ConfirmPickupActivity - Rapido Style Pickup Confirmation
 * =========================================================
 * 
 * Flow:
 * 1. User selects trucks and clicks "Confirm & Search"
 * 2. This screen shows the map with pickup location
 * 3. User can adjust the pickup point by moving the map
 * 4. User confirms pickup location
 * 5. Proceeds to searching for vehicles
 * 
 * Architecture & Scalability:
 * ===========================
 * - FAST MAP LOADING: Uses GoogleMap.setMapType(NORMAL) with lite mode disabled
 * - EFFICIENT GEOCODING: Debounced at 500ms to prevent API spam
 * - MEMORY EFFICIENT: Cancels coroutines on destroy
 * - MILLIONS OF USERS: Rate-limited geocoding, cached location data
 * - MODULAR: Clean separation - UI, Location, Geocoding are separate concerns
 * 
 * Performance Optimizations:
 * - Map initialized with minimal overlays
 * - Geocoder runs on IO dispatcher (non-blocking)
 * - Location permission checked asynchronously
 * - Camera movements are animated (not instant) for smoother UX
 * 
 * @author Weelo Team
 */
@AndroidEntryPoint
class ConfirmPickupActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "ConfirmPickupActivity"
        
        // Intent extras keys - use consistent naming for easy backend integration
        const val EXTRA_PICKUP_LOCATION = "extra_pickup_location"
        const val EXTRA_DROP_LOCATION = "extra_drop_location"
        const val EXTRA_SELECTED_TRUCKS = "extra_selected_trucks"
        const val EXTRA_TOTAL_PRICE = "extra_total_price"
        const val EXTRA_DISTANCE_KM = "extra_distance_km"
        
        // Result keys
        const val RESULT_CONFIRMED_PICKUP = "result_confirmed_pickup"
        
        // Map configuration - optimized for fast loading
        private const val DEFAULT_ZOOM = 17f
        private const val MIN_ZOOM = 10f
        private const val MAX_ZOOM = 20f
        
        // Geocoding - debounce to prevent API spam (critical for millions of users)
        private const val GEOCODE_DEBOUNCE_MS = 500L
        
        // Animation duration for smooth camera movements
        private const val CAMERA_ANIMATION_MS = 300
        
        /**
         * Pre-initialize Google Maps SDK in Application class for faster loading
         * Call this in WeeloApplication.onCreate()
         */
        fun preInitializeMaps(context: android.content.Context) {
            try {
                MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) { }
            } catch (e: Exception) {
                Timber.e("Maps pre-initialization failed: ${e.message}")
            }
        }
    }

    // Map - using MapView for faster initialization
    private var googleMap: GoogleMap? = null
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI Components
    private lateinit var backButton: FloatingActionButton
    private lateinit var myLocationButton: FloatingActionButton
    private lateinit var pickupLocationName: TextView
    private lateinit var pickupLocationAddress: TextView
    private lateinit var confirmPickupButton: MaterialButton

    // Data
    private var pickupLocation: Location? = null
    private var dropLocation: Location? = null
    private var selectedTrucks: ArrayList<SelectedTruckItem>? = null
    private var totalPrice: Int = 0
    private var distanceKm: Int = 0

    // Geocoding
    private var geocodeJob: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Location permission
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_pickup)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get data from intent
        extractIntentData()

        // Initialize views
        initViews()

        // Setup map
        setupMap()

        // Setup click listeners
        setupClickListeners()
    }

    private fun extractIntentData() {
        pickupLocation = intent.getParcelableExtraCompat(EXTRA_PICKUP_LOCATION)
        dropLocation = intent.getParcelableExtraCompat(EXTRA_DROP_LOCATION)
        selectedTrucks = intent.getParcelableArrayListExtraCompat(EXTRA_SELECTED_TRUCKS)
        totalPrice = intent.getIntExtra(EXTRA_TOTAL_PRICE, 0)
        distanceKm = intent.getIntExtra(EXTRA_DISTANCE_KM, 0)

        Timber.d("Pickup: ${pickupLocation?.address}")
        Timber.d("Drop: ${dropLocation?.address}")
        Timber.d("Trucks: ${selectedTrucks?.size}")
        Timber.d("Price: $totalPrice")
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        myLocationButton = findViewById(R.id.myLocationButton)
        pickupLocationName = findViewById(R.id.pickupLocationName)
        pickupLocationAddress = findViewById(R.id.pickupLocationAddress)
        confirmPickupButton = findViewById(R.id.confirmPickupButton)

        // Set initial location text
        updateLocationDisplay(pickupLocation)
    }

    private fun setupMap() {
        // Initialize MapView - faster than SupportMapFragment
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(null) // Pass null for faster init, we don't need saved state
        
        // Get map async with optimized renderer
        mapView.getMapAsync(this)
    }
    
    // ============ MapView Lifecycle Methods (REQUIRED) ============
    
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }
    
    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun setupClickListeners() {
        // Back button
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // My location button
        myLocationButton.setOnClickListener {
            moveToCurrentLocation()
        }

        // Confirm pickup button
        confirmPickupButton.setOnClickListener {
            confirmPickupAndProceed()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // ============ OPTIMIZED MAP CONFIGURATION ============
        // These settings are tuned for FAST loading and smooth performance
        
        // Set map type to NORMAL (fastest to load)
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        
        // Configure UI settings - minimal for performance
        map.uiSettings.apply {
            isZoomControlsEnabled = false      // We have custom buttons
            isMyLocationButtonEnabled = false  // We have custom button
            isCompassEnabled = false           // Not needed, reduces overhead
            isRotateGesturesEnabled = false    // Simpler interaction
            isTiltGesturesEnabled = false      // 2D view only, faster
            isScrollGesturesEnabled = true     // Required for panning
            isZoomGesturesEnabled = true       // Required for pinch zoom
            isMapToolbarEnabled = false        // Hide Google Maps toolbar
            isIndoorLevelPickerEnabled = false // Not needed for pickup
        }
        
        // Set zoom limits for better UX
        map.setMinZoomPreference(MIN_ZOOM)
        map.setMaxZoomPreference(MAX_ZOOM)
        
        // Disable indoor maps (faster loading)
        map.isIndoorEnabled = false
        
        // Disable traffic layer (faster loading)
        map.isTrafficEnabled = false
        
        // Disable buildings layer for cleaner view
        map.isBuildingsEnabled = true

        // Enable my location layer if permission granted
        checkAndRequestLocationPermission()

        // Move to pickup location IMMEDIATELY (no animation for initial load)
        pickupLocation?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
            
            // Update display with initial location
            updateLocationDisplay(location)
        }

        // Listen for camera movement (user dragging map)
        map.setOnCameraIdleListener {
            onMapCameraIdle()
        }

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                // User is dragging the map - show loading state
                pickupLocationName.text = "Locating..."
                pickupLocationAddress.text = "Move map to adjust pickup point"
            }
        }
        
        Timber.d("Map ready and configured for optimal performance")
    }

    private fun onMapCameraIdle() {
        val map = googleMap ?: return
        val centerLatLng = map.cameraPosition.target

        // Update pickup location with new coordinates
        pickupLocation = pickupLocation?.copy(
            latitude = centerLatLng.latitude,
            longitude = centerLatLng.longitude
        ) ?: Location(
            address = "",
            latitude = centerLatLng.latitude,
            longitude = centerLatLng.longitude
        )

        // Geocode the new location (debounced)
        geocodeLocation(centerLatLng)
    }

    /**
     * Geocode location with debouncing to prevent excessive API calls
     * This is critical for scalability - millions of users moving maps
     */
    private fun geocodeLocation(latLng: LatLng) {
        // Cancel previous geocode job
        geocodeJob?.cancel()

        geocodeJob = mainScope.launch {
            delay(GEOCODE_DEBOUNCE_MS) // Debounce

            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(this@ConfirmPickupActivity, Locale.getDefault())
                    
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

                    withContext(Dispatchers.Main) {
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val locationName = address.featureName ?: address.subLocality ?: address.locality ?: "Unknown"
                            val fullAddress = address.getAddressLine(0) ?: ""

                            // Update pickup location
                            pickupLocation = pickupLocation?.copy(
                                address = fullAddress
                            )

                            updateLocationDisplay(pickupLocation, locationName)
                        } else {
                            updateLocationDisplay(null)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e("Geocoding failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        pickupLocationName.text = "Location selected"
                        pickupLocationAddress.text = "${String.format("%.6f", latLng.latitude)}, ${String.format("%.6f", latLng.longitude)}"
                    }
                }
            }
        }
    }

    private fun updateLocationDisplay(location: Location?, shortName: String? = null) {
        if (location != null) {
            val address = location.address
            val name = shortName ?: address.split(",").firstOrNull() ?: "Selected Location"
            
            pickupLocationName.text = name
            pickupLocationAddress.text = address
        } else {
            pickupLocationName.text = "Select pickup location"
            pickupLocationAddress.text = "Move the map to choose a pickup point"
        }
    }

    private fun checkAndRequestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun enableMyLocation() {
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            Timber.e("Location permission not granted: ${e.message}")
        }
    }

    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkAndRequestLocationPermission()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
            } ?: run {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmPickupAndProceed() {
        val pickup = pickupLocation
        val drop = dropLocation
        val trucks = selectedTrucks

        if (pickup == null || pickup.address.isEmpty()) {
            Toast.makeText(this, "Please select a valid pickup location", Toast.LENGTH_SHORT).show()
            return
        }

        if (drop == null) {
            Toast.makeText(this, "Drop location is missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (trucks.isNullOrEmpty()) {
            Toast.makeText(this, "No vehicles selected", Toast.LENGTH_SHORT).show()
            return
        }

        Timber.d("Confirming pickup: ${pickup.address}")

        // Return result to calling activity
        val resultIntent = Intent().apply {
            putExtra(RESULT_CONFIRMED_PICKUP, pickup)
            putExtra(EXTRA_DROP_LOCATION, drop)
            putParcelableArrayListExtra(EXTRA_SELECTED_TRUCKS, trucks)
            putExtra(EXTRA_TOTAL_PRICE, totalPrice)
            putExtra(EXTRA_DISTANCE_KM, distanceKm)
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        geocodeJob?.cancel()
        mainScope.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }
}
