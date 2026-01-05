package com.weelo.logistics.presentation.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Driver Dashboard
 * Manages driver availability, trip data, and summary statistics
 */
class DriverDashboardViewModel : ViewModel() {
    
    // Driver availability status
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    // Driver name
    private val _driverName = MutableStateFlow("Driver")
    val driverName: StateFlow<String> = _driverName.asStateFlow()
    
    // Today's summary
    private val _todaySummary = MutableStateFlow(
        TodaySummary(
            tripCount = 0,
            distance = 0,
            earnings = 0.0
        )
    )
    val todaySummary: StateFlow<TodaySummary> = _todaySummary.asStateFlow()
    
    // Current active trip
    private val _currentTrip = MutableStateFlow<ActiveTrip?>(null)
    val currentTrip: StateFlow<ActiveTrip?> = _currentTrip.asStateFlow()
    
    init {
        loadDriverData()
        loadTodaySummary()
        loadActiveTrip()
    }
    
    fun updateAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            _isAvailable.value = isAvailable
            // TODO: Call API to update driver availability status
            // api.updateDriverAvailability(isAvailable)
        }
    }
    
    fun sendSOSAlert() {
        viewModelScope.launch {
            // TODO: Send SOS alert to backend
            // api.sendSOSAlert(driverId, currentLocation, currentTripId)
        }
    }
    
    fun reportIssue(issueType: String) {
        viewModelScope.launch {
            // TODO: Report issue to backend
            // api.reportIssue(driverId, currentTripId, issueType, timestamp)
        }
    }
    
    private fun loadDriverData() {
        viewModelScope.launch {
            // TODO: Load driver profile data from repository
            // For now, using mock data
            _driverName.value = "Rajesh"
        }
    }
    
    private fun loadTodaySummary() {
        viewModelScope.launch {
            // TODO: Load today's summary from repository
            // For now, using mock data
            _todaySummary.value = TodaySummary(
                tripCount = 2,
                distance = 450,
                earnings = 8500.0
            )
        }
    }
    
    private fun loadActiveTrip() {
        viewModelScope.launch {
            // TODO: Load active trip from repository
            // For now, using mock data
            _currentTrip.value = ActiveTrip(
                tripId = "TRP12345",
                vehicleNumber = "GJ-01-AB-1234",
                vehicleType = "Open Truck - 20 Feet",
                pickup = "Mumbai",
                delivery = "Delhi",
                pickupLat = 19.0760,
                pickupLng = 72.8777,
                deliveryLat = 28.7041,
                deliveryLng = 77.1025,
                status = "In Progress",
                startTime = "10:30 AM"
            )
        }
    }
}

/**
 * Data class for today's summary statistics
 */
data class TodaySummary(
    val tripCount: Int,
    val distance: Int, // in kilometers
    val earnings: Double // in rupees
)

/**
 * Data class for active trip information
 */
data class ActiveTrip(
    val tripId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val pickup: String,
    val delivery: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val deliveryLat: Double,
    val deliveryLng: Double,
    val status: String,
    val startTime: String
)
