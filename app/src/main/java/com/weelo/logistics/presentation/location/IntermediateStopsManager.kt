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

    private val stops = mutableListOf<String>()
    private var onStopsChangedListener: ((List<String>) -> Unit)? = null

    /**
     * Set listener for stops changes
     */
    fun setOnStopsChangedListener(listener: (List<String>) -> Unit) {
        onStopsChangedListener = listener
    }

    /**
     * Get current stops list
     */
    fun getStops(): List<String> = stops.toList()

    /**
     * Get valid (non-empty) stops
     */
    fun getValidStops(): List<String> = stops.filter { it.isNotBlank() }

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

        stops.add("")
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
     * Restore stops from saved state
     */
    fun restoreStops(savedStops: Array<String>?) {
        if (savedStops.isNullOrEmpty()) return

        savedStops.forEach { stopName ->
            stops.add(stopName)
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

        // Setup autocomplete
        stopInput?.let { input ->
            placesHelper.setupAutocomplete(input) { address ->
                updateStopAddress(stopNumber - 1, address)
            }

            // Handle text changes
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateStopAddress(stopNumber - 1, s?.toString() ?: "")
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
     * Update stop address at index
     */
    private fun updateStopAddress(index: Int, address: String) {
        if (index >= 0 && index < stops.size) {
            stops[index] = address
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
