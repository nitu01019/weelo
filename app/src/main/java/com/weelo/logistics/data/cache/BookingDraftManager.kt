package com.weelo.logistics.data.cache

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.weelo.logistics.data.remote.api.PlaceResult
import timber.log.Timber

/**
 * BookingDraftManager — Crash Recovery for Booking Flow
 * =====================================================
 *
 * Saves the multi-step booking draft (pickup, drop, vehicle type, quantity)
 * so that if the app crashes or is force-killed mid-booking, the user can
 * resume from where they left off.
 *
 * INDUSTRY STANDARD: Uber/Ola/Rapido all persist booking drafts locally.
 *
 * RULES:
 * - Draft expires after 15 minutes (stale data is worse than no data)
 * - Uses EncryptedSharedPreferences (AES256) for location privacy
 * - Cleared on successful order creation or explicit user cancel
 *
 * SCALABILITY: SharedPreferences is instant read/write, no DB overhead
 * MODULARITY: Single-responsibility class, no coupling to UI
 */
class BookingDraftManager(context: Context) {

    companion object {
        private const val TAG = "BookingDraftManager"
        private const val PREFS_NAME = "weelo_booking_draft_encrypted"
        private const val DRAFT_EXPIRY_MS = 15 * 60 * 1000L // 15 minutes

        // Keys
        private const val KEY_PICKUP_LABEL = "draft_pickup_label"
        private const val KEY_PICKUP_LAT = "draft_pickup_lat"
        private const val KEY_PICKUP_LNG = "draft_pickup_lng"
        private const val KEY_PICKUP_PLACE_ID = "draft_pickup_place_id"

        private const val KEY_DROP_LABEL = "draft_drop_label"
        private const val KEY_DROP_LAT = "draft_drop_lat"
        private const val KEY_DROP_LNG = "draft_drop_lng"
        private const val KEY_DROP_PLACE_ID = "draft_drop_place_id"

        private const val KEY_VEHICLE_TYPE = "draft_vehicle_type"
        private const val KEY_QUANTITY = "draft_quantity"
        private const val KEY_TIMESTAMP = "draft_timestamp"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to create encrypted prefs, falling back to regular")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Save pickup location to draft
     */
    fun savePickup(place: PlaceResult) {
        prefs.edit()
            .putString(KEY_PICKUP_LABEL, place.label)
            .putString(KEY_PICKUP_LAT, place.latitude.toString())
            .putString(KEY_PICKUP_LNG, place.longitude.toString())
            .putString(KEY_PICKUP_PLACE_ID, place.placeId)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Timber.d("$TAG: Pickup saved to draft")
    }

    /**
     * Save drop location to draft
     */
    fun saveDrop(place: PlaceResult) {
        prefs.edit()
            .putString(KEY_DROP_LABEL, place.label)
            .putString(KEY_DROP_LAT, place.latitude.toString())
            .putString(KEY_DROP_LNG, place.longitude.toString())
            .putString(KEY_DROP_PLACE_ID, place.placeId)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Timber.d("$TAG: Drop saved to draft")
    }

    /**
     * Save vehicle selection to draft
     */
    fun saveVehicleSelection(vehicleType: String, quantity: Int) {
        prefs.edit()
            .putString(KEY_VEHICLE_TYPE, vehicleType)
            .putInt(KEY_QUANTITY, quantity)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Timber.d("$TAG: Vehicle selection saved to draft")
    }

    /**
     * Get the saved draft. Returns null if expired or not found.
     */
    fun getDraft(): BookingDraft? {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp == 0L) return null

        // Check expiry — stale drafts are worse than no draft
        if (System.currentTimeMillis() - timestamp > DRAFT_EXPIRY_MS) {
            Timber.d("$TAG: Draft expired (${(System.currentTimeMillis() - timestamp) / 1000}s old)")
            clearDraft()
            return null
        }

        val pickupLabel = prefs.getString(KEY_PICKUP_LABEL, null)
        val pickupLat = prefs.getString(KEY_PICKUP_LAT, null)?.toDoubleOrNull()
        val pickupLng = prefs.getString(KEY_PICKUP_LNG, null)?.toDoubleOrNull()
        val pickupPlaceId = prefs.getString(KEY_PICKUP_PLACE_ID, null)

        val dropLabel = prefs.getString(KEY_DROP_LABEL, null)
        val dropLat = prefs.getString(KEY_DROP_LAT, null)?.toDoubleOrNull()
        val dropLng = prefs.getString(KEY_DROP_LNG, null)?.toDoubleOrNull()
        val dropPlaceId = prefs.getString(KEY_DROP_PLACE_ID, null)

        val vehicleType = prefs.getString(KEY_VEHICLE_TYPE, null)
        val quantity = prefs.getInt(KEY_QUANTITY, 0)

        // Build pickup PlaceResult if available
        val pickup = if (pickupLabel != null && pickupLat != null && pickupLng != null) {
            PlaceResult(
                placeId = pickupPlaceId ?: "",
                label = pickupLabel,
                latitude = pickupLat,
                longitude = pickupLng
            )
        } else null

        // Build drop PlaceResult if available
        val drop = if (dropLabel != null && dropLat != null && dropLng != null) {
            PlaceResult(
                placeId = dropPlaceId ?: "",
                label = dropLabel,
                latitude = dropLat,
                longitude = dropLng
            )
        } else null

        if (pickup == null && drop == null) return null

        return BookingDraft(
            pickup = pickup,
            drop = drop,
            vehicleType = vehicleType,
            quantity = if (quantity > 0) quantity else null,
            timestamp = timestamp
        )
    }

    /**
     * Clear draft — call on successful order creation or user cancel
     */
    fun clearDraft() {
        prefs.edit().clear().apply()
        Timber.d("$TAG: Draft cleared")
    }

    /**
     * Check if a non-expired draft exists
     */
    fun hasDraft(): Boolean {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp == 0L) return false
        if (System.currentTimeMillis() - timestamp > DRAFT_EXPIRY_MS) {
            clearDraft()
            return false
        }
        return true
    }
}

/**
 * Booking draft data class — holds the saved multi-step booking state
 */
data class BookingDraft(
    val pickup: PlaceResult?,
    val drop: PlaceResult?,
    val vehicleType: String?,
    val quantity: Int?,
    val timestamp: Long
)
