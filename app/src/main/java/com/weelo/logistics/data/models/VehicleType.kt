package com.weelo.logistics.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Vehicle type data model
 * Represents different types of vehicles available for logistics
 */
@Parcelize
data class VehicleType(
    val id: String,
    val name: String,
    val iconResource: Int,  // Drawable resource ID for professional icons
    val capacityRange: String,
    val description: String = "",
    val priceMultiplier: Double = 1.0
) : Parcelable

/**
 * Vehicle category for main screen
 */
@Parcelize
data class VehicleCategory(
    val id: String,
    val name: String,
    val iconResource: Int,
    val nearbyCount: Int = 0
) : Parcelable
