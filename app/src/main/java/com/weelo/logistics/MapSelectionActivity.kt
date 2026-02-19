package com.weelo.logistics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.remote.ApiConfig
import com.weelo.logistics.data.remote.api.ReverseGeocodeRequest
import com.weelo.logistics.data.remote.api.WeeloApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * MapSelectionActivity - Select location by dragging pin on map
 *
 * SCALABILITY:
 * - Singleton Retrofit instance to prevent memory leaks
 * - Debounced API calls (300ms) to reduce server load
 * - Coroutine-based async operations
 *
 * SECURITY:
 * - Coordinate validation before API calls
 * - Graceful fallback on API failures
 *
 * MODULARITY:
 * - Clear separation of map, geocoding, and UI logic
 * - Reusable for both FROM and TO field selection
 */
class MapSelectionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var backButton: ImageView
    private lateinit var confirmButton: Button
    private lateinit var centerPin: ImageView
    private lateinit var addressText: TextView
    private lateinit var titleText: TextView
    private lateinit var changeButton: TextView
    private lateinit var pinIndicator: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLatLng: LatLng? = null
    private var inputType: String = "FROM"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_CODE_CHANGE_LOCATION = 3001

    // Reverse geocoding
    private var reverseGeocodeJob: Job? = null
    private var currentCity: String? = null
    private var isConfirmInProgress = false  // Prevent double-click

    companion object {
        // Singleton Retrofit instance for scalability
        @Volatile
        private var apiService: WeeloApiService? = null

        private fun getApiService(): WeeloApiService {
            return apiService ?: synchronized(this) {
                apiService ?: createApiService().also { apiService = it }
            }
        }

        private fun createApiService(): WeeloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeeloApiService::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selection)

        initViews()
        setupClickListeners()
        checkLocationPermission()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        confirmButton = findViewById(R.id.confirmButton)
        centerPin = findViewById(R.id.centerPin)
        addressText = findViewById(R.id.addressText)
        titleText = findViewById(R.id.titleText)
        changeButton = findViewById(R.id.changeButton)
        pinIndicator = findViewById(R.id.pinIndicator)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get input type from intent (FROM or TO) - sanitized
        inputType = when (intent.getStringExtra("INPUT_TYPE")) {
            "TO" -> "TO"
            else -> "FROM"
        }
        
        // =====================================================
        // RAPIDO STYLE: Green/Red pins, title, button text
        // =====================================================
        // SCALABILITY: All UI driven by INPUT_TYPE, no hardcoding
        // EASY UNDERSTANDING: Visual distinction between pickup and drop
        if (inputType == "TO") {
            centerPin.setImageResource(R.drawable.ic_pin_drop)        // Red pin 📍
            confirmButton.text = "Select Drop"
            titleText.text = "Select your location"
            pinIndicator.setBackgroundResource(R.drawable.bg_red_dot)  // Red dot
        } else {
            centerPin.setImageResource(R.drawable.ic_pin_pickup)      // Green pin 📍
            confirmButton.text = "Select Pickup"
            titleText.text = "Select your location"
            pinIndicator.setBackgroundResource(R.drawable.bg_green_dot) // Green dot
        }

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        confirmButton.setOnClickListener {
            handleConfirmClick()
        }
        
        // =====================================================
        // RAPIDO STYLE: "Change" opens MapSearchActivity
        // =====================================================
        // Opens a dedicated search page with cached locations
        // User searches, selects → map jumps to that location
        // EASY UNDERSTANDING: Separate page for searching
        // MODULARITY: Standalone MapSearchActivity
        changeButton.setOnClickListener {
            val intent = Intent(this, MapSearchActivity::class.java).apply {
                putExtra("INPUT_TYPE", inputType)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_CHANGE_LOCATION)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left)
        }
    }
    
    /**
     * Handle result from MapSearchActivity (Change page)
     * 
     * RAPIDO STYLE: User selected location from Change page → map jumps to it
     * SCALABILITY: Simple intent extras, no Parcelable complexity
     * EASY UNDERSTANDING: Read lat/lng/address → animate map → update UI
     */
    @Deprecated("Using legacy API for backward compatibility", ReplaceWith("ActivityResultLauncher"))
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_CHANGE_LOCATION && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("SELECTED_LAT", 0.0)
            val lng = data.getDoubleExtra("SELECTED_LNG", 0.0)
            val address = data.getStringExtra("SELECTED_ADDRESS") ?: ""
            val city = data.getStringExtra("SELECTED_CITY") ?: ""
            
            if (isValidCoordinate(lat, lng)) {
                val latLng = LatLng(lat, lng)
                selectedLatLng = latLng
                addressText.text = address
                currentCity = city
                
                // Animate map to selected location with zoom
                if (::googleMap.isInitialized) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
                
                timber.log.Timber.d("Map jumped to: $address ($lat, $lng)")
            }
        }
    }

    private fun handleConfirmClick() {
        // Prevent double-click
        if (isConfirmInProgress) return

        val latLng = selectedLatLng
        if (latLng == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate coordinates
        if (!isValidCoordinate(latLng.latitude, latLng.longitude)) {
            Toast.makeText(this, "Invalid location selected", Toast.LENGTH_SHORT).show()
            return
        }

        isConfirmInProgress = true
        confirmButton.isEnabled = false

        val addressString = addressText.text.toString()
            .take(500)  // Limit address length for security
            .trim()

        val location = Location(
            address = if (addressString.isNotBlank()) addressString else "Selected Location",
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            city = currentCity?.take(100) ?: ""  // Limit city length, default to empty
        )

        val resultIntent = Intent().apply {
            putExtra("SELECTED_LOCATION", location)
            putExtra("INPUT_TYPE", inputType)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Validate coordinates are within valid range
     */
    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true

                // Auto-center on current location with street-level zoom (Rapido style)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        if (isValidCoordinate(it.latitude, it.longitude)) {
                            val currentLatLng = LatLng(it.latitude, it.longitude)
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                            selectedLatLng = currentLatLng
                            fetchAddressForLocation(currentLatLng)
                        }
                    }
                }
            }

            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            
            // =====================================================
            // RAPIDO STYLE: Pin Lift Animation when map moves
            // =====================================================
            // When user starts dragging, lift the pin UP (floating effect)
            googleMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    // Lift pin UP with smooth animation
                    centerPin.animate()
                        .translationY(-40f)  // Lift 40dp
                        .setDuration(200)
                        .start()
                    addressText.text = "Move map to select location"
                }
            }

            // =====================================================
            // RAPIDO STYLE: Pin Drop Animation when map stops
            // =====================================================
            // When map stops moving, drop pin DOWN with bounce effect
            googleMap.setOnCameraIdleListener {
                // Drop pin DOWN with bounce (Rapido style)
                centerPin.animate()
                    .translationY(0f)  // Back to original position
                    .setDuration(300)
                    .setInterpolator(android.view.animation.BounceInterpolator())
                    .start()
                
                val centerLatLng = googleMap.cameraPosition.target
                if (isValidCoordinate(centerLatLng.latitude, centerLatLng.longitude)) {
                    selectedLatLng = centerLatLng
                    fetchAddressForLocation(centerLatLng)
                }
            }

            addressText.text = "Move map to select location"

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading map", Toast.LENGTH_SHORT).show()
            logError("Map loading error", e)
        }
    }

    /**
     * Fetch address using debounced API call
     * SCALABILITY: 300ms debounce prevents API spam during rapid map dragging
     */
    private fun fetchAddressForLocation(latLng: LatLng) {
        reverseGeocodeJob?.cancel()

        addressText.text = "Finding address..."

        reverseGeocodeJob = lifecycleScope.launch {
            delay(300)  // Debounce

            try {
                val request = ReverseGeocodeRequest(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                val response = getApiService().reverseGeocode(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null && data.address.isNotBlank()) {
                        addressText.text = data.address.take(500)  // Limit display length
                        currentCity = data.city?.take(100)
                    } else {
                        showCoordinatesFallback(latLng)
                    }
                } else {
                    showCoordinatesFallback(latLng)
                }
            } catch (e: Exception) {
                logError("Reverse geocode error", e)
                showCoordinatesFallback(latLng)
            }
        }
    }

    private fun showCoordinatesFallback(latLng: LatLng) {
        addressText.text = String.format("%.6f, %.6f", latLng.latitude, latLng.longitude)
        currentCity = null
    }

    private fun checkLocationPermission() {
        // Permission is requested ONCE in LocationInputActivity (the entry point).
        // Here we only enable my-location if already granted — no dialog.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            if (::googleMap.isInitialized) {
                googleMap.isMyLocationEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reverseGeocodeJob?.cancel()
    }

    private fun logError(message: String, e: Exception) {
        timber.log.Timber.e(e, message)
        if (!BuildConfig.DEBUG) {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .recordException(e)
            } catch (_: Exception) {
                // Timber already logged above
            }
        }
    }
}
