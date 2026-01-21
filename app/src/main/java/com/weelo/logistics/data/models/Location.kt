package com.weelo.logistics.data.models

import android.os.Parcelable
import com.weelo.logistics.data.remote.api.CoordinatesRequest
import com.weelo.logistics.data.remote.api.LocationRequest
import kotlinx.parcelize.Parcelize

/**
 * Location data model
 * Represents a location with address and coordinates
 */
@Parcelize
data class Location(
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val city: String = "",
    val state: String = ""
) : Parcelable {
    
    companion object {
        fun empty() = Location("", 0.0, 0.0, "", "")
    }
    
    fun isValid(): Boolean = address.isNotBlank()
    
    /**
     * Convert Location to LocationRequest for API calls
     */
    fun toLocationRequest(): LocationRequest {
        return LocationRequest(
            coordinates = CoordinatesRequest(
                latitude = latitude,
                longitude = longitude
            ),
            address = address,
            city = city.ifBlank { null },
            state = state.ifBlank { null },
            pincode = null
        )
    }
}
