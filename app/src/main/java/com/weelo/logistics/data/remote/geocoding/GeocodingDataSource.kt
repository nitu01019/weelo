package com.weelo.logistics.data.remote.geocoding

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.remote.api.*
import com.weelo.logistics.domain.model.LocationModel
import timber.log.Timber
import javax.inject.Inject

/**
 * Data source for geocoding operations
 * 
 * REFACTORED: Now uses AWS Location Service via Weelo Backend
 * instead of direct Google Maps API calls.
 * 
 * Benefits:
 * - Scalable: Backend handles rate limiting, caching
 * - Cost-effective: AWS Location is cheaper than Google
 * - Consistent: Same data source for all clients
 * - Secure: No API keys exposed in app
 * 
 * @see WeeloApiService for endpoint definitions
 */
class GeocodingDataSource @Inject constructor(
    private val weeloApiService: WeeloApiService
) {
    
    /**
     * Search for places by text query
     * Uses AWS Location Service Place Index via backend
     */
    suspend fun searchPlaces(
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null,
        maxResults: Int = 5
    ): Result<List<LocationModel>> {
        return try {
            val request = PlaceSearchRequest(
                query = query,
                biasLat = biasLat,
                biasLng = biasLng,
                maxResults = maxResults
            )
            
            val response = weeloApiService.searchPlaces(request)
            val body = response.body()
            
            if (response.isSuccessful && body?.success == true && body.data != null) {
                val locations = body.data.map { place ->
                    LocationModel(
                        address = place.label,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        city = place.city ?: "",
                        state = "",
                        pincode = ""
                    )
                }
                Timber.d("Place search: '${query}' returned ${locations.size} results")
                Result.Success(locations)
            } else {
                val errorMsg = body?.error?.message ?: "Search failed"
                Timber.e("Place search error: $errorMsg")
                Result.Error(WeeloException.LocationException(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Place search exception")
            Result.Error(WeeloException.NetworkException("Network error during place search"))
        }
    }
    
    /**
     * Convert address to coordinates
     * Uses AWS Location Service via backend
     */
    suspend fun geocodeAddress(address: String): Result<LocationModel> {
        // Use searchPlaces and take first result
        return when (val result = searchPlaces(address, maxResults = 1)) {
            is Result.Success -> {
                if (result.data.isNotEmpty()) {
                    Result.Success(result.data.first())
                } else {
                    Result.Error(WeeloException.LocationException("Location not found"))
                }
            }
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }
    
    /**
     * Convert coordinates to address
     * Uses AWS Location Service via backend
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<LocationModel> {
        return try {
            val request = ReverseGeocodeRequest(latitude, longitude)
            val response = weeloApiService.reverseGeocode(request)
            val body = response.body()
            
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.Success(
                    LocationModel(
                        address = body.data.address,
                        latitude = latitude,
                        longitude = longitude,
                        city = body.data.city ?: "",
                        state = body.data.state ?: "",
                        pincode = body.data.postalCode ?: ""
                    )
                )
            } else {
                Result.Error(WeeloException.LocationException("Address not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Reverse geocoding exception")
            Result.Error(WeeloException.NetworkException("Network error during reverse geocoding"))
        }
    }
    
    /**
     * Calculate distance between two locations
     * Uses AWS Location Service Route Calculator via backend
     * Falls back to Haversine for long routes (>400km)
     */
    suspend fun calculateDistance(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        truckMode: Boolean = true
    ): Result<Int> {
        return try {
            val request = RouteCalculationRequest(
                from = RouteCoordinates(fromLat, fromLng),
                to = RouteCoordinates(toLat, toLng),
                truckMode = truckMode
            )
            
            val response = weeloApiService.calculateRoute(request)
            val body = response.body()
            
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Timber.d("Route calculated: ${body.data.distanceKm} km via ${body.data.source}")
                Result.Success(body.data.distanceKm)
            } else {
                Result.Error(WeeloException.LocationException("Route not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Distance calculation exception")
            Result.Error(WeeloException.NetworkException("Network error during distance calculation"))
        }
    }
    
    /**
     * Calculate route with full details (distance + duration)
     */
    suspend fun calculateRoute(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        truckMode: Boolean = true
    ): Result<RouteCalculationData> {
        return try {
            val request = RouteCalculationRequest(
                from = RouteCoordinates(fromLat, fromLng),
                to = RouteCoordinates(toLat, toLng),
                truckMode = truckMode
            )
            
            val response = weeloApiService.calculateRoute(request)
            val body = response.body()
            
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Timber.d("Route: ${body.data.distanceKm} km, ${body.data.durationFormatted}")
                Result.Success(body.data)
            } else {
                Result.Error(WeeloException.LocationException("Route not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Route calculation exception")
            Result.Error(WeeloException.NetworkException("Network error during route calculation"))
        }
    }
    
    /**
     * Check if AWS Location Service is available
     */
    suspend fun isServiceAvailable(): Boolean {
        return try {
            val response = weeloApiService.getGeocodingStatus()
            response.body()?.data?.available ?: false
        } catch (e: Exception) {
            Timber.e(e, "Failed to check geocoding status")
            false
        }
    }
}

