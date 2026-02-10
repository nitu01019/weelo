package com.weelo.logistics.data.repository

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Home Feature Repository
 * =======================
 * Handles Live Truck Availability data
 * 
 * Backend Integration Points:
 * - WebSocket for live availability updates
 */
@Singleton
class HomeFeatureRepository @Inject constructor() {

    // Cache for availability (updates every 30 seconds)
    private var cachedAvailability: AvailabilitySummary? = null
    private var lastAvailabilityFetch: Long = 0

    /**
     * Get live truck availability near a location
     * @param lat latitude (reserved for location-based filtering)
     * @param lng longitude (reserved for location-based filtering)
     * @param forceRefresh bypass cache
     */
    @Suppress("UNUSED_PARAMETER") // lat/lng reserved for future location-based API
    suspend fun getLiveAvailability(
        lat: Double,
        lng: Double,
        forceRefresh: Boolean = false
    ): AvailabilitySummary {
        // Check cache (valid for 30 seconds) - safe access with let
        if (!forceRefresh && System.currentTimeMillis() - lastAvailabilityFetch < 30000) {
            cachedAvailability?.let { return it }
        }
        
        // TODO: Replace with Firebase Realtime DB listener
        // Path: /availability/{region}/trucks
        // Or API: GET /api/v1/trucks/availability?lat={lat}&lng={lng}
        
        delay(400) // Simulate network delay
        
        // Generate realistic availability data
        val truckTypes = listOf(
            LiveAvailability(
                truckTypeId = "open",
                truckTypeName = "Open",
                availableCount = Random.nextInt(8, 25),
                nearbyCount = Random.nextInt(3, 10),
                estimatedArrival = "${Random.nextInt(10, 20)} mins",
                isHighDemand = Random.nextBoolean()
            ),
            LiveAvailability(
                truckTypeId = "container",
                truckTypeName = "Container",
                availableCount = Random.nextInt(5, 15),
                nearbyCount = Random.nextInt(2, 6),
                estimatedArrival = "${Random.nextInt(15, 30)} mins",
                isHighDemand = false
            ),
            LiveAvailability(
                truckTypeId = "lcv",
                truckTypeName = "LCV",
                availableCount = Random.nextInt(10, 30),
                nearbyCount = Random.nextInt(5, 12),
                estimatedArrival = "${Random.nextInt(8, 15)} mins",
                isHighDemand = true
            ),
            LiveAvailability(
                truckTypeId = "mini",
                truckTypeName = "Mini/Pickup",
                availableCount = Random.nextInt(15, 40),
                nearbyCount = Random.nextInt(8, 20),
                estimatedArrival = "${Random.nextInt(5, 12)} mins",
                isHighDemand = false
            ),
            LiveAvailability(
                truckTypeId = "tipper",
                truckTypeName = "Tipper",
                availableCount = Random.nextInt(3, 10),
                nearbyCount = Random.nextInt(1, 4),
                estimatedArrival = "${Random.nextInt(20, 40)} mins",
                isHighDemand = false
            )
        )
        
        val totalAvailable = truckTypes.sumOf { it.availableCount }
        val nearbyTrucks = truckTypes.sumOf { it.nearbyCount }
        val fastestArrival = truckTypes.minByOrNull { 
            it.estimatedArrival.replace(" mins", "").toIntOrNull() ?: 999 
        }?.estimatedArrival ?: "10 mins"
        
        val availability = AvailabilitySummary(
            totalAvailable = totalAvailable,
            nearbyTrucks = nearbyTrucks,
            fastestArrival = fastestArrival,
            truckTypes = truckTypes
        )
        cachedAvailability = availability
        lastAvailabilityFetch = System.currentTimeMillis()
        
        return availability
    }

    /**
     * Clear caches (call on logout or refresh)
     */
    fun clearCache() {
        cachedAvailability = null
        lastAvailabilityFetch = 0
    }
}

/**
 * Data class for live availability of a truck type
 */
data class LiveAvailability(
    val truckTypeId: String,
    val truckTypeName: String,
    val availableCount: Int,
    val nearbyCount: Int,
    val estimatedArrival: String,
    val isHighDemand: Boolean
)

/**
 * Summary of availability near user
 */
data class AvailabilitySummary(
    val totalAvailable: Int,
    val nearbyTrucks: Int,
    val fastestArrival: String,
    val truckTypes: List<LiveAvailability>
)
