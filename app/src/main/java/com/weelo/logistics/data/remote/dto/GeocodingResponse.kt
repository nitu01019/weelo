package com.weelo.logistics.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Geocoding API Response DTOs
 */
data class GeocodingResponse(
    @SerializedName("results")
    val results: List<GeocodingResult>,
    @SerializedName("status")
    val status: String
)

data class GeocodingResult(
    @SerializedName("formatted_address")
    val formattedAddress: String,
    @SerializedName("geometry")
    val geometry: Geometry,
    @SerializedName("place_id")
    val placeId: String,
    @SerializedName("address_components")
    val addressComponents: List<AddressComponent>
)

data class Geometry(
    @SerializedName("location")
    val location: LatLng,
    @SerializedName("location_type")
    val locationType: String
)

data class LatLng(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double
)

data class AddressComponent(
    @SerializedName("long_name")
    val longName: String,
    @SerializedName("short_name")
    val shortName: String,
    @SerializedName("types")
    val types: List<String>
)
