package com.weelo.logistics.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Directions API Response DTOs
 */
data class DirectionsResponse(
    @SerializedName("routes")
    val routes: List<Route>,
    @SerializedName("status")
    val status: String
)

data class Route(
    @SerializedName("legs")
    val legs: List<Leg>,
    @SerializedName("overview_polyline")
    val overviewPolyline: Polyline,
    @SerializedName("summary")
    val summary: String
)

data class Leg(
    @SerializedName("distance")
    val distance: Distance,
    @SerializedName("duration")
    val duration: Duration,
    @SerializedName("start_address")
    val startAddress: String,
    @SerializedName("end_address")
    val endAddress: String,
    @SerializedName("steps")
    val steps: List<Step>
)

data class Distance(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Int // in meters
)

data class Duration(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Int // in seconds
)

data class Step(
    @SerializedName("distance")
    val distance: Distance,
    @SerializedName("duration")
    val duration: Duration,
    @SerializedName("html_instructions")
    val htmlInstructions: String,
    @SerializedName("polyline")
    val polyline: Polyline
)

data class Polyline(
    @SerializedName("points")
    val points: String
)
