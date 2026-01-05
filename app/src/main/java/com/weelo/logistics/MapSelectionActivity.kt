package com.weelo.logistics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.models.Location

/**
 * MapSelectionActivity - Select location by dragging pin on map
 */
class MapSelectionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var backButton: ImageView
    private lateinit var confirmButton: Button
    private lateinit var centerPin: ImageView
    private lateinit var addressText: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLatLng: LatLng? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selection)

        // Initialize views
        backButton = findViewById(R.id.backButton)
        confirmButton = findViewById(R.id.confirmButton)
        centerPin = findViewById(R.id.centerPin)
        addressText = findViewById(R.id.addressText)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Back button
        backButton.setOnClickListener {
            finish()
        }

        // Confirm button
        confirmButton.setOnClickListener {
            selectedLatLng?.let { latLng ->
                val location = Location(
                    address = addressText.text.toString(),
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                val resultIntent = Intent().apply {
                    putExtra("SELECTED_LOCATION", location)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } ?: run {
                Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            }
        }

        // Check location permissions
        checkLocationPermission()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            // Enable location if permission granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
                
                // Move to current location
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                        selectedLatLng = currentLatLng
                        updateAddressText(currentLatLng)
                    }
                }
            }

            // Map settings
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true

            // Listen for camera movements (dragging)
            googleMap.setOnCameraIdleListener {
                val centerLatLng = googleMap.cameraPosition.target
                selectedLatLng = centerLatLng
                updateAddressText(centerLatLng)
            }

            // Initial address text
            addressText.text = "Move map to select location"

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading map: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun updateAddressText(latLng: LatLng) {
        // For now, show coordinates
        // TODO: Use Geocoder to get actual address
        addressText.text = "Lat: ${String.format("%.4f", latLng.latitude)}, Lng: ${String.format("%.4f", latLng.longitude)}"
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
