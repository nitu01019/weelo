package com.weelo.logistics.adapters

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.api.PlaceResult
import java.util.Locale

/**
 * RecyclerView Adapter for Places Search Results (Rapido Style)
 * 
 * FEATURES:
 * - Distance shown BELOW icon (left column)
 * - Heart icon for favorites (click to add to cache as favorite)
 * - Click item to select location
 * 
 * SCALABILITY: Efficient view binding with distance calculation
 * EASY UNDERSTANDING: Separate click handlers for item and heart
 * MODULARITY: Reusable adapter with callbacks
 * CODING STANDARDS: Follows Android RecyclerView best practices
 */
class WeeloPlacesRecyclerAdapter(
    private var biasLat: Double? = null,
    private var biasLng: Double? = null,
    private val onPlaceSelected: (PlaceResult) -> Unit,
    private val onFavoriteClick: ((PlaceResult) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val places = ArrayList<PlaceResult>()
    
    // Callback for "Use Current Location"
    private var onCurrentLocationSelected: (() -> Unit)? = null

    fun setOnCurrentLocationListener(listener: () -> Unit) {
        onCurrentLocationSelected = listener
    }

    fun updatePlaces(newPlaces: List<PlaceResult>) {
        val oldSize = places.size
        places.clear()
        places.addAll(newPlaces)
        
        // Use efficient notifications instead of notifyDataSetChanged
        // Positions are offset by 1 for the header at position 0, so no +1 on count
        if (oldSize > newPlaces.size) {
            notifyItemRangeChanged(1, newPlaces.size)
            notifyItemRangeRemoved(newPlaces.size + 1, oldSize - newPlaces.size)
        } else if (oldSize < newPlaces.size) {
            notifyItemRangeChanged(1, oldSize)
            notifyItemRangeInserted(oldSize + 1, newPlaces.size - oldSize)
        } else {
            notifyItemRangeChanged(1, newPlaces.size)
        }
    }

    fun clear() {
        val oldSize = places.size
        places.clear()
        if (oldSize > 0) {
            notifyItemRangeRemoved(1, oldSize)
        }
    }

    fun updateBias(lat: Double, lng: Double) {
        biasLat = lat
        biasLng = lng
        // Only update distance display, no need to rebind all items
        notifyItemRangeChanged(1, places.size, "distance")
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_autocomplete_place, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_autocomplete_place, parent, false)
            PlaceViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_HEADER) {
            (holder as HeaderViewHolder).bind()
        } else {
            // Adjust position for header
            (holder as PlaceViewHolder).bind(places[position - 1])
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            // Partial update for distance only
            if (payloads.contains("distance") && holder is PlaceViewHolder) {
                holder.updateDistance(places[position - 1])
            } else {
                // Unknown payload — fall back to full bind
                super.onBindViewHolder(holder, position, payloads)
            }
        }
    }

    override fun getItemCount(): Int = places.size + 1 // Always show header

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    /**
     * ViewHolder for "Use Current Location" header (Rapido style)
     * 
     * SCALABILITY: First item in search results for quick location access
     * EASY UNDERSTANDING: Clear "Use Current Location" option
     * MODULARITY: Separate header logic from search results
     */
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val placeName: TextView = itemView.findViewById(R.id.placeName)
        private val placeAddress: TextView = itemView.findViewById(R.id.placeAddress)
        private val placeDistance: TextView = itemView.findViewById(R.id.placeDistance)
        private val icon: android.widget.ImageView = itemView.findViewById(R.id.iconLocation)
        private val heart: android.widget.ImageView = itemView.findViewById(R.id.iconFavorite) 

        fun bind() {
            itemView.setOnClickListener { onCurrentLocationSelected?.invoke() }
            
            placeName.text = "Use Current Location"
            placeName.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            placeAddress.text = "Using GPS"
            
            // Icon - current location style
            icon.setImageResource(R.drawable.ic_current_location)
            icon.imageTintList = null
            
            // Hide distance and heart for header
            placeDistance.visibility = View.GONE
            heart.visibility = View.GONE
        }
    }

    /**
     * ViewHolder for search result items (Rapido style)
     * 
     * SCALABILITY: Efficient view binding with distance calculation
     * EASY UNDERSTANDING: Distance shown UNDER icon (left column)
     * MODULARITY: Matches item_autocomplete_place.xml structure
     * CODING STANDARDS: Follows ViewHolder pattern best practices
     * 
     * HEART BUTTON: Separate click handler - adds to favorites without selecting
     */
    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val placeName: TextView = itemView.findViewById(R.id.placeName)
        private val placeAddress: TextView = itemView.findViewById(R.id.placeAddress)
        private val placeDistance: TextView = itemView.findViewById(R.id.placeDistance)
        private val iconFavorite: android.widget.ImageView = itemView.findViewById(R.id.iconFavorite)

        fun bind(place: PlaceResult) {
            // ITEM CLICK: Select location
            itemView.setOnClickListener { 
                timber.log.Timber.d("Search result selected: ${place.label}")
                onPlaceSelected(place) 
            }
            
            // HEART CLICK: Add to favorites (separate from item click)
            iconFavorite.visibility = View.VISIBLE
            iconFavorite.setOnClickListener {
                timber.log.Timber.d("Heart clicked - adding to favorites: ${place.label}")
                onFavoriteClick?.invoke(place)
            }

            // Segregate Name and Address (Rapido style)
            val fullLabel = place.label
            val parts = fullLabel.split(", ", limit = 2)

            if (parts.size >= 2) {
                placeName.text = parts[0]
                placeAddress.text = parts[1]
            } else {
                placeName.text = fullLabel
                placeAddress.text = place.city ?: ""
            }

            updateDistance(place)
        }
        
        fun updateDistance(place: PlaceResult) {
            // Calculate Distance (shown UNDER icon - Rapido style)
            if (biasLat != null && biasLng != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    biasLat!!, biasLng!!,
                    place.latitude, place.longitude,
                    results
                )
                val distanceMeters = results[0]

                val distanceText = if (distanceMeters < 1000) {
                    "${distanceMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.1f km", distanceMeters / 1000)
                }

                placeDistance.text = distanceText
                placeDistance.visibility = View.VISIBLE
            } else {
                placeDistance.visibility = View.GONE
            }
        }
    }
}
