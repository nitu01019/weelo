package com.weelo.logistics.data.remote.api

import com.weelo.logistics.data.remote.dto.DirectionsResponse
import com.weelo.logistics.data.remote.dto.GeocodingResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Maps API Service
 * Provides geocoding and directions APIs
 */
interface GoogleMapsService {
    
    /**
     * Geocoding API - Convert address to lat/lng
     */
    @GET("geocode/json")
    suspend fun geocodeAddress(
        @Query("address") address: String,
        @Query("key") apiKey: String
    ): Response<GeocodingResponse>
    
    /**
     * Reverse Geocoding - Convert lat/lng to address
     */
    @GET("geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng") latLng: String,
        @Query("key") apiKey: String
    ): Response<GeocodingResponse>
    
    /**
     * Directions API - Get route between two points
     */
    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "driving",
        @Query("key") apiKey: String
    ): Response<DirectionsResponse>
    
    /**
     * Directions API with waypoints - Get route through multiple stops
     */
    @GET("directions/json")
    suspend fun getDirectionsWithWaypoints(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String,
        @Query("mode") mode: String = "driving",
        @Query("key") apiKey: String
    ): Response<DirectionsResponse>
}
