package com.weelo.logistics.data.remote.geocoding

import android.content.Context
import com.weelo.logistics.R
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.remote.api.GoogleMapsService
import com.weelo.logistics.domain.model.LocationModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Data source for geocoding operations
 */
class GeocodingDataSource @Inject constructor(
    private val googleMapsService: GoogleMapsService,
    @ApplicationContext private val context: Context
) {
    
    private val apiKey: String by lazy {
        com.weelo.logistics.BuildConfig.MAPS_API_KEY
    }
    
    /**
     * Convert address to coordinates
     */
    suspend fun geocodeAddress(address: String): Result<LocationModel> {
        return try {
            val response = googleMapsService.geocodeAddress(address, apiKey)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                
                if (body.status == "OK" && body.results.isNotEmpty()) {
                    val result = body.results.first()
                    val location = result.geometry.location
                    
                    // Extract city and state from address components
                    val city = result.addressComponents
                        .find { it.types.contains("locality") }
                        ?.longName ?: ""
                    
                    val state = result.addressComponents
                        .find { it.types.contains("administrative_area_level_1") }
                        ?.longName ?: ""
                    
                    val pincode = result.addressComponents
                        .find { it.types.contains("postal_code") }
                        ?.longName ?: ""
                    
                    Result.Success(
                        LocationModel(
                            address = result.formattedAddress,
                            latitude = location.lat,
                            longitude = location.lng,
                            city = city,
                            state = state,
                            pincode = pincode
                        )
                    )
                } else {
                    Timber.e("Geocoding failed with status: ${body.status}")
                    Result.Error(WeeloException.LocationException("Location not found"))
                }
            } else {
                Timber.e("Geocoding API error: ${response.code()}")
                Result.Error(WeeloException.NetworkException("Failed to geocode address"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Geocoding exception")
            Result.Error(WeeloException.NetworkException("Network error during geocoding"))
        }
    }
    
    /**
     * Convert coordinates to address
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<LocationModel> {
        return try {
            val latLng = "$latitude,$longitude"
            val response = googleMapsService.reverseGeocode(latLng, apiKey)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                
                if (body.status == "OK" && body.results.isNotEmpty()) {
                    val result = body.results.first()
                    
                    // Extract city and state
                    val city = result.addressComponents
                        .find { it.types.contains("locality") }
                        ?.longName ?: ""
                    
                    val state = result.addressComponents
                        .find { it.types.contains("administrative_area_level_1") }
                        ?.longName ?: ""
                    
                    val pincode = result.addressComponents
                        .find { it.types.contains("postal_code") }
                        ?.longName ?: ""
                    
                    Result.Success(
                        LocationModel(
                            address = result.formattedAddress,
                            latitude = latitude,
                            longitude = longitude,
                            city = city,
                            state = state,
                            pincode = pincode
                        )
                    )
                } else {
                    Result.Error(WeeloException.LocationException("Address not found"))
                }
            } else {
                Result.Error(WeeloException.NetworkException("Failed to reverse geocode"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Reverse geocoding exception")
            Result.Error(WeeloException.NetworkException("Network error during reverse geocoding"))
        }
    }
    
    /**
     * Calculate distance between two locations
     */
    suspend fun calculateDistance(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Result<Int> {
        return try {
            val origin = "$fromLat,$fromLng"
            val destination = "$toLat,$toLng"
            
            val response = googleMapsService.getDirections(origin, destination, "driving", apiKey)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                
                if (body.status == "OK" && body.routes.isNotEmpty()) {
                    val route = body.routes.first()
                    val leg = route.legs.first()
                    
                    // Distance in kilometers
                    val distanceKm = (leg.distance.value / 1000.0).toInt()
                    
                    Timber.d("Calculated distance: $distanceKm km")
                    Result.Success(distanceKm)
                } else {
                    Result.Error(WeeloException.LocationException("Route not found"))
                }
            } else {
                Result.Error(WeeloException.NetworkException("Failed to calculate distance"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Distance calculation exception")
            Result.Error(WeeloException.NetworkException("Network error during distance calculation"))
        }
    }
}
