package com.weelo.logistics.presentation.location

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.domain.model.LocationModel
import timber.log.Timber
import java.util.Locale

/**
 * RecyclerView Adapter for Recent Locations (Rapido Style)
 * 
 * SCALABILITY: Uses ListAdapter with DiffUtil for efficient updates
 * EASY UNDERSTANDING: Simple flat layout, guaranteed click handling
 * MODULARITY: Reusable adapter with distance calculation
 * CODING STANDARDS: Follows Android RecyclerView best practices
 * 
 * @author Weelo Team
 */
/**
 * RecyclerView Adapter for Recent/Cached Locations (RAPIDO STYLE)
 * 
 * FEATURES:
 * - Distance shown BELOW icon (left column)
 * - Heart icon for favorites (red when active)
 * - Remove (X) button with circle background
 * - Click to select location
 * 
 * SCALABILITY: Uses ListAdapter with DiffUtil for efficient updates
 * EASY UNDERSTANDING: Simple flat layout, guaranteed click handling
 * MODULARITY: Reusable adapter with distance calculation
 * CODING STANDARDS: Follows Android RecyclerView best practices
 */
class RecentLocationsAdapter(
    private val onLocationClick: (LocationModel) -> Unit,
    private val onFavoriteClick: ((LocationModel) -> Unit)? = null,
    private val onRemoveClick: ((LocationModel) -> Unit)? = null
) : ListAdapter<LocationModel, RecentLocationsAdapter.LocationViewHolder>(LocationDiffCallback()) {

    // Current user location for distance calculation
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null

    /**
     * Update user location for distance calculation
     * SCALABILITY: Called once, used for all items
     */
    fun updateUserLocation(lat: Double, lng: Double) {
        userLatitude = lat
        userLongitude = lng
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val locationName: TextView? = itemView.findViewById(R.id.locationName)
        private val locationAddress: TextView? = itemView.findViewById(R.id.locationAddress)
        private val locationDistance: TextView? = itemView.findViewById(R.id.locationDistance)
        private val favoriteIcon: TextView? = itemView.findViewById(R.id.favoriteIcon)
        private val removeButton: TextView? = itemView.findViewById(R.id.removeButton)

        fun bind(location: LocationModel) {
            Timber.d("Binding location: ${location.address}")
            
            // Split address into name and subtitle (Rapido style)
            val parts = location.address.split(",", limit = 2)
            val name = parts.getOrNull(0)?.trim() ?: location.address
            val address = parts.getOrNull(1)?.trim() ?: ""

            // Set location name (Bold, Dark)
            locationName?.text = name

            // Set address subtitle (Slightly darker gray)
            locationAddress?.text = address
            locationAddress?.visibility = if (address.isNotEmpty()) View.VISIBLE else View.GONE

            // Calculate and display distance BELOW icon (RAPIDO STYLE)
            if (userLatitude != null && userLongitude != null) {
                val distanceText = calculateDistance(location)
                if (distanceText.isNotEmpty()) {
                    locationDistance?.text = distanceText
                    locationDistance?.visibility = View.VISIBLE
                } else {
                    locationDistance?.visibility = View.GONE
                }
            } else {
                locationDistance?.visibility = View.GONE
            }

            // Set favorite icon (Red filled for favorites, outline for others)
            favoriteIcon?.text = if (location.isFavorite) {
                itemView.context.getString(R.string.favorite_icon_filled)
            } else {
                itemView.context.getString(R.string.favorite_icon_empty)
            }

            // Click to select location
            itemView.setOnClickListener {
                Timber.d("Recent location selected: ${location.address}")
                onLocationClick(location)
            }

            // Favorite toggle - fills heart with red and moves to top priority
            favoriteIcon?.setOnClickListener {
                Timber.d("Favorite toggled: ${location.address}, was=${location.isFavorite}")
                onFavoriteClick?.invoke(location)
            }
            
            // Remove button with circle (RAPIDO STYLE) - deletes from cache
            if (onRemoveClick != null) {
                removeButton?.visibility = View.VISIBLE
                removeButton?.setOnClickListener {
                    Timber.d("Remove clicked: ${location.address}")
                    onRemoveClick.invoke(location)
                }
            } else {
                removeButton?.visibility = View.GONE
            }
        }

        /**
         * Calculate distance from user location
         * SCALABILITY: Native Android calculation, no API calls
         */
        private fun calculateDistance(location: LocationModel): String {
            val userLat = userLatitude ?: return ""
            val userLng = userLongitude ?: return ""

            val results = FloatArray(1)
            Location.distanceBetween(
                userLat, userLng,
                location.latitude, location.longitude,
                results
            )
            val distanceMeters = results[0]

            return if (distanceMeters < 1000) {
                "${distanceMeters.toInt()} m"
            } else {
                String.format(Locale.US, "%.1f km", distanceMeters / 1000)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     * SCALABILITY: Only updates changed items, not entire list
     */
    class LocationDiffCallback : DiffUtil.ItemCallback<LocationModel>() {
        override fun areItemsTheSame(oldItem: LocationModel, newItem: LocationModel): Boolean {
            return oldItem.id == newItem.id ||
                   (oldItem.address == newItem.address &&
                    oldItem.latitude == newItem.latitude &&
                    oldItem.longitude == newItem.longitude)
        }

        override fun areContentsTheSame(oldItem: LocationModel, newItem: LocationModel): Boolean {
            return oldItem == newItem
        }
    }
}
