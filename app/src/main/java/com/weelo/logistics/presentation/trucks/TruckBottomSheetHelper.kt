package com.weelo.logistics.presentation.trucks

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.weelo.logistics.R
import com.weelo.logistics.adapters.SubtypeItem
import com.weelo.logistics.adapters.TruckSubtypeAdapter

/**
 * TruckBottomSheetHelper - Utility functions for bottom sheet dialogs
 * 
 * Extracted from TruckTypesActivity for better modularity.
 * Contains:
 * - Subtype card creation
 * - Selection summary updates
 * - Grid layout helpers
 * 
 * Note: The actual bottom sheet dialog logic remains in TruckTypesActivity
 * since it has complex dependencies on activity context and layout resources.
 */
class TruckBottomSheetHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {

    /**
     * Update selection summary UI in bottom sheet
     */
    fun updateSelectionSummary(
        summaryLayout: LinearLayout?,
        countText: TextView?,
        priceText: TextView?,
        confirmButton: Button?,
        deselectButton: TextView?,
        totalCount: Int,
        totalPrice: Int
    ) {
        if (totalCount > 0) {
            summaryLayout?.visibility = View.VISIBLE
            deselectButton?.visibility = View.VISIBLE
            countText?.text = "$totalCount truck${if (totalCount > 1) "s" else ""} selected"
            priceText?.text = "₹${String.format("%,d", totalPrice)}"
            confirmButton?.isEnabled = true
            confirmButton?.backgroundTintList = ColorStateList.valueOf(0xFFFF9800.toInt())
        } else {
            summaryLayout?.visibility = View.GONE
            deselectButton?.visibility = View.GONE
            confirmButton?.isEnabled = false
            confirmButton?.backgroundTintList = ColorStateList.valueOf(0xFFAAAAAA.toInt())
        }
    }

    /**
     * Add a subtype card to a GridLayout
     */
    fun addSubtypeCardToGrid(
        gridLayout: GridLayout,
        subtype: String,
        truckTypeId: String,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        parentView: View,
        onSelectionChanged: () -> Unit
    ) {
        val card = layoutInflater.inflate(R.layout.item_truck_subtype, null)
        card.findViewById<TextView>(R.id.subtypeName)?.text = subtype
        
        // Set the truck icon based on truck type
        val iconView = card.findViewById<ImageView>(R.id.subtypeIcon)
        iconView?.setImageResource(TruckPricingHelper.getTruckIconResource(truckTypeId))
        
        // Adjust image padding
        val paddingDp = TruckPricingHelper.getIconPaddingDp(truckTypeId)
        val paddingPx = (paddingDp * context.resources.displayMetrics.density).toInt()
        iconView?.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(4, 4, 4, 4)
        }
        card.layoutParams = params
        
        // Setup click handling
        setupSubtypeCardClickHandlers(
            card, 
            subtype, 
            selectedSubtypes, 
            vehicleCard,
            onSelectionChanged
        )
        
        gridLayout.addView(card)
    }

    /**
     * Setup click handlers for subtype card
     */
    private fun setupSubtypeCardClickHandlers(
        card: View,
        subtype: String,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        onSelectionChanged: () -> Unit
    ) {
        val cardContent = card.findViewById<View>(R.id.cardContent)
        val quantityControls = card.findViewById<View>(R.id.quantityControls)
        val quantityText = card.findViewById<TextView>(R.id.quantityText)
        val plusButton = card.findViewById<View>(R.id.plusButton)
        val minusButton = card.findViewById<View>(R.id.minusButton)

        // Initialize state based on existing selection
        val initialQty = selectedSubtypes[subtype] ?: 0
        if (initialQty > 0) {
            quantityControls?.visibility = View.VISIBLE
            quantityText?.text = initialQty.toString()
            cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
        }

        // Card click handler
        card.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            if (currentQty == 0) {
                selectedSubtypes[subtype] = 1
                quantityControls?.visibility = View.VISIBLE
                quantityText?.text = "1"
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
            } else {
                selectedSubtypes[subtype] = currentQty + 1
                quantityText?.text = "${currentQty + 1}"
            }
            onSelectionChanged()
        }

        // Plus button
        plusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            selectedSubtypes[subtype] = currentQty + 1
            if (currentQty == 0) {
                quantityControls?.visibility = View.VISIBLE
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
            }
            quantityText?.text = "${currentQty + 1}"
            onSelectionChanged()
        }

        // Minus button
        minusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            if (currentQty > 1) {
                selectedSubtypes[subtype] = currentQty - 1
                quantityText?.text = "${currentQty - 1}"
            } else if (currentQty == 1) {
                selectedSubtypes.remove(subtype)
                quantityControls?.visibility = View.GONE
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
            }
            onSelectionChanged()
        }
    }

    companion object {
        private const val TAG = "TruckBottomSheetHelper"
    }
}
