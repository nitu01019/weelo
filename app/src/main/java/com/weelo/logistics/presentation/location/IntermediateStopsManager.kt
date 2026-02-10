package com.weelo.logistics.presentation.location

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.weelo.logistics.R
import com.weelo.logistics.core.util.gone
import com.weelo.logistics.core.util.visible
import timber.log.Timber

/**
 * Data class to store intermediate stop with address AND coordinates
 * CRITICAL: We need coordinates for accurate routing, not just address text
 */
data class StopLocation(
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    fun isValid(): Boolean = latitude != 0.0 && longitude != 0.0
}

/**
 * Manager for intermediate stops functionality
 * 
 * MODULARITY: Extracts stop management from Activity
 * SCALABILITY: Handles dynamic stop addition/removal
 * MAINTAINABILITY: Single responsibility for stops logic
 * 
 * @author Weelo Team
 */
class IntermediateStopsManager(
    private val context: Context,
    private val container: LinearLayout,
    private val bottomDottedLine: View,
    private val placesHelper: LocationPlacesHelper,
    private val maxStops: Int = DEFAULT_MAX_STOPS
) {

    // Changed: Store StopLocation with coordinates, not just strings
    private val stops = mutableListOf<StopLocation>()
    private var onStopsChangedListener: ((List<StopLocation>) -> Unit)? = null

    /**
     * Set listener for stops changes
     */
    fun setOnStopsChangedListener(listener: (List<StopLocation>) -> Unit) {
        onStopsChangedListener = listener
    }

    /**
     * Get current stops list (with coordinates)
     */
    fun getStops(): List<StopLocation> = stops.toList()
    
    /**
     * Get stops as address strings (for backward compatibility)
     */
    fun getStopAddresses(): List<String> = stops.map { it.address }

    /**
     * Get valid stops with coordinates
     */
    fun getValidStops(): List<StopLocation> = stops.filter { it.address.isNotBlank() }

    /**
     * Check if can add more stops
     */
    fun canAddStop(): Boolean = stops.size < maxStops

    /**
     * Get current stops count
     */
    fun getStopsCount(): Int = stops.size

    /**
     * Add a new intermediate stop
     * 
     * @return true if stop was added, false if max reached
     */
    fun addStop(): Boolean {
        if (!canAddStop()) {
            Timber.d("Cannot add stop - max $maxStops reached")
            return false
        }

        // Add empty StopLocation (coordinates will be filled when user selects a place)
        stops.add(StopLocation(""))
        bottomDottedLine.visible()

        val stopView = createStopView(stops.size)
        container.addView(stopView)

        // Focus on new input
        val input = stopView.findViewById<AutoCompleteTextView>(R.id.stopLocationInput)
        input?.requestFocus()

        notifyStopsChanged()
        return true
    }

    /**
     * Remove a stop at index
     */
    fun removeStop(index: Int, view: View) {
        if (index < 0 || index >= stops.size) return

        stops.removeAt(index)
        container.removeView(view)
        updateStopNumbers()

        if (stops.isEmpty()) {
            bottomDottedLine.gone()
        }

        notifyStopsChanged()
    }

    /**
     * Restore stops from saved state (address only - legacy)
     * Note: Coordinates will need to be fetched when the stop is edited
     */
    fun restoreStops(savedStops: Array<String>?) {
        if (savedStops.isNullOrEmpty()) return

        savedStops.forEach { stopName ->
            // Create StopLocation with address only (no coordinates yet)
            stops.add(StopLocation(stopName, 0.0, 0.0))
            val stopView = createStopView(stops.size)
            container.addView(stopView)
            
            val input = stopView.findViewById<AutoCompleteTextView>(R.id.stopLocationInput)
            input?.setText(stopName)
        }
        
        bottomDottedLine.visible()
        notifyStopsChanged()
    }

    /**
     * Clear all stops
     */
    fun clearStops() {
        stops.clear()
        container.removeAllViews()
        bottomDottedLine.gone()
        notifyStopsChanged()
    }

    /**
     * Create view for an intermediate stop
     */
    private fun createStopView(stopNumber: Int): View {
        val view = LayoutInflater.from(context).inflate(
            R.layout.item_intermediate_stop,
            container,
            false
        )

        val stopInput = view.findViewById<AutoCompleteTextView>(R.id.stopLocationInput)
        val stopNumberView = view.findViewById<TextView>(R.id.stopNumber)
        val removeButton = view.findViewById<ImageView>(R.id.removeStopButton)
        val dottedLineTop = view.findViewById<View>(R.id.dottedLineTop)

        // Set stop number
        stopNumberView?.text = stopNumber.toString()

        // Setup autocomplete - CRITICAL: Save coordinates, not just address
        stopInput?.let { input ->
            placesHelper.setupAutocomplete(input) { address, lat, lng ->
                // Save complete location data (address + coordinates)
                updateStopLocation(stopNumber - 1, address, lat, lng)
            }

            // Handle text changes (only for manual edits without coordinates)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Only update address text, keep existing coordinates
                    val index = stopNumber - 1
                    if (index >= 0 && index < stops.size) {
                        val currentStop = stops[index]
                        updateStopLocation(index, s?.toString() ?: "", currentStop.latitude, currentStop.longitude)
                    }
                }
            })
        }

        // Remove button
        removeButton?.setOnClickListener {
            removeStop(stopNumber - 1, view)
        }

        // Show top dotted line
        dottedLineTop?.visible()

        return view
    }

    /**
     * Update stop with complete location data (address + coordinates)
     * CRITICAL: Stores lat/lng for accurate routing
     */
    private fun updateStopLocation(index: Int, address: String, lat: Double, lng: Double) {
        if (index >= 0 && index < stops.size) {
            stops[index] = StopLocation(address, lat, lng)
            notifyStopsChanged()
        }
    }

    /**
     * Update stop numbers after removal
     */
    private fun updateStopNumbers() {
        for (i in 0 until container.childCount) {
            val stopView = container.getChildAt(i)
            val numberView = stopView.findViewById<TextView>(R.id.stopNumber)
            numberView?.text = (i + 1).toString()
        }
    }

    /**
     * Notify listener of stops change
     */
    private fun notifyStopsChanged() {
        onStopsChangedListener?.invoke(stops.toList())
    }

    companion object {
        const val DEFAULT_MAX_STOPS = 3
    }
}
