package com.weelo.logistics.core.util

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import timber.log.Timber

/**
 * Helper class for Google Places API integration
 */
object PlacesHelper {

    private var isInitialized = false

    /**
     * Initialize Places API
     * @param context Application context
     * @param apiKey Google Maps API key
     */
    fun initialize(context: android.content.Context, apiKey: String) {
        if (!isInitialized && apiKey.isNotEmpty()) {
            try {
                Places.initialize(context.applicationContext, apiKey)
                isInitialized = true
                Timber.d("Places API initialized")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Places API")
            }
        }
    }

    /**
     * Launch autocomplete activity
     * Initializes Places API if needed
     */
    fun launchAutocomplete(
        activity: Activity,
        requestCode: Int,
        hint: String = "Enter location"
    ) {
        try {
            // Initialize Places API if not already done
            if (!isInitialized) {
                try {
                    if (!Places.isInitialized()) {
                        Places.initialize(activity.applicationContext, activity.getString(com.weelo.logistics.R.string.google_maps_key))
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize Places API")
                    throw e
                }
            }
            
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS_COMPONENTS
            )

            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
            )
                .setHint(hint)
                .setCountries(listOf("IN")) // Restrict to India
                .build(activity)

            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch autocomplete")
            // Show user-friendly error
            activity.runOnUiThread {
                android.widget.Toast.makeText(
                    activity,
                    "Unable to open location picker. Please type your location.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Handle autocomplete result
     */
    fun handleAutocompleteResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        expectedRequestCode: Int,
        onSuccess: (Place) -> Unit,
        onError: (Status) -> Unit
    ) {
        if (requestCode == expectedRequestCode) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(it)
                        Timber.d("Place selected: ${place.name}, ${place.address}")
                        onSuccess(place)
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(it)
                        Timber.e("Autocomplete error: ${status.statusMessage}")
                        onError(status)
                    }
                }
                Activity.RESULT_CANCELED -> {
                    Timber.d("Autocomplete cancelled")
                }
            }
        }
    }

    /**
     * Convert Place to LocationModel
     */
    fun placeToLocationModel(place: Place): com.weelo.logistics.domain.model.LocationModel {
        val latLng = place.latLng
        val addressComponents = place.addressComponents?.asList()

        // Extract city
        val city = addressComponents
            ?.find { it.types.contains("locality") }
            ?.name ?: ""

        // Extract state
        val state = addressComponents
            ?.find { it.types.contains("administrative_area_level_1") }
            ?.name ?: ""

        // Extract pincode
        val pincode = addressComponents
            ?.find { it.types.contains("postal_code") }
            ?.name ?: ""

        return com.weelo.logistics.domain.model.LocationModel(
            address = place.address ?: place.name ?: "",
            latitude = latLng?.latitude ?: 0.0,
            longitude = latLng?.longitude ?: 0.0,
            city = city,
            state = state,
            pincode = pincode
        )
    }

    /**
     * Request codes for autocomplete
     */
    object RequestCodes {
        const val AUTOCOMPLETE_FROM_LOCATION = 1001
        const val AUTOCOMPLETE_TO_LOCATION = 1002
        const val AUTOCOMPLETE_MAP_LOCATION = 1003
    }
}
