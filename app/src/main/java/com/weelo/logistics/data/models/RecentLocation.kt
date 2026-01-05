package com.weelo.logistics.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Recent location model for location history
 */
@Parcelize
data class RecentLocation(
    val location: Location,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : Parcelable
