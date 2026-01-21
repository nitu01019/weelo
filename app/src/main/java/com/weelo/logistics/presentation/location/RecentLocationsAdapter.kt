package com.weelo.logistics.presentation.location

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.domain.model.LocationModel

/**
 * RecyclerView Adapter for Recent Locations
 * 
 * SCALABILITY: Uses ListAdapter with DiffUtil for efficient updates
 * PERFORMANCE: Only updates changed items, not entire list
 * MEMORY: ViewHolder pattern for memory efficiency
 * 
 * @author Weelo Team
 */
class RecentLocationsAdapter(
    private val onLocationClick: (LocationModel, Boolean) -> Unit,
    private val onFavoriteClick: ((LocationModel) -> Unit)? = null
) : ListAdapter<LocationModel, RecentLocationsAdapter.LocationViewHolder>(LocationDiffCallback()) {

    private var isFromLocationMode = true

    /**
     * Set mode - selecting FROM or TO location
     */
    fun setFromLocationMode(isFrom: Boolean) {
        isFromLocationMode = isFrom
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
        private val locationText: TextView? = itemView.findViewById(R.id.locationText)
        private val favoriteIcon: TextView? = itemView.findViewById(R.id.favoriteIcon)

        fun bind(location: LocationModel) {
            // Set location text with safe fallback
            locationText?.text = location.toShortString()

            // Set favorite icon
            favoriteIcon?.text = if (location.isFavorite) "❤️" else "🤍"

            // Click to select location
            itemView.setOnClickListener {
                onLocationClick(location, isFromLocationMode)
            }

            // Long press or icon click to toggle favorite
            favoriteIcon?.setOnClickListener {
                onFavoriteClick?.invoke(location)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class LocationDiffCallback : DiffUtil.ItemCallback<LocationModel>() {
        override fun areItemsTheSame(oldItem: LocationModel, newItem: LocationModel): Boolean {
            return oldItem.address == newItem.address &&
                   oldItem.latitude == newItem.latitude &&
                   oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: LocationModel, newItem: LocationModel): Boolean {
            return oldItem == newItem
        }
    }
}
