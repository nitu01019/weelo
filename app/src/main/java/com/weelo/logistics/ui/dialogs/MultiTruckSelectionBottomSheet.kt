package com.weelo.logistics.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.weelo.logistics.R
import com.weelo.logistics.adapters.MultiTruckSelectionAdapter
import com.weelo.logistics.adapters.SubtypeItem
import com.weelo.logistics.adapters.TruckTypeSection
import com.weelo.logistics.adapters.TruckTypeSelection
import com.weelo.logistics.data.models.TruckSubtypesConfig
import java.text.NumberFormat
import java.util.Locale

/**
 * Multi-Truck Selection Bottom Sheet (Rapido-Style)
 * ==================================================
 * 
 * A draggable bottom sheet that allows users to:
 * 1. Select multiple truck types
 * 2. For each truck type, select subtypes and quantities
 * 3. See real-time total price
 * 4. Confirm and proceed to search
 * 
 * Scalable: Uses efficient RecyclerView with nested adapters
 * Modular: Easy to add new truck types
 * Secure: All data validation before confirmation
 */
class MultiTruckSelectionBottomSheet : BottomSheetDialogFragment() {

    // UI Components
    private lateinit var closeButton: ImageView
    private lateinit var selectionSummaryBar: LinearLayout
    private lateinit var selectedCountText: TextView
    private lateinit var totalPriceText: TextView
    private lateinit var truckTypesRecyclerView: RecyclerView
    private lateinit var addTruckTypeButton: MaterialButton
    private lateinit var clearAllButton: MaterialButton
    private lateinit var confirmButton: MaterialButton

    // Adapter
    private lateinit var multiTruckAdapter: MultiTruckSelectionAdapter

    // Data
    private var initialTruckTypeId: String? = null
    private var distanceKm: Int = 0
    private val availableTruckTypes = mutableListOf<String>()
    private val addedTruckTypeIds = mutableSetOf<String>()

    // Callbacks
    private var onConfirmListener: ((List<TruckTypeSelection>, Int) -> Unit)? = null
    private var onAddTruckTypeListener: ((List<String>) -> Unit)? = null

    companion object {
        private const val ARG_INITIAL_TRUCK_TYPE = "initial_truck_type"
        private const val ARG_DISTANCE_KM = "distance_km"

        fun newInstance(
            initialTruckTypeId: String,
            distanceKm: Int
        ): MultiTruckSelectionBottomSheet {
            return MultiTruckSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_TRUCK_TYPE, initialTruckTypeId)
                    putInt(ARG_DISTANCE_KM, distanceKm)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialTruckTypeId = it.getString(ARG_INITIAL_TRUCK_TYPE)
            distanceKm = it.getInt(ARG_DISTANCE_KM, 0)
        }
        
        // Get all available truck types
        availableTruckTypes.addAll(TruckSubtypesConfig.getAllDialogTruckTypes())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_multi_truck_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapter()
        setupClickListeners()
        
        // Add initial truck type
        initialTruckTypeId?.let { addTruckType(it) }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bottom_sheet_rounded_bg)
                
                val screenHeight = resources.displayMetrics.heightPixels
                val maxHeight = (screenHeight * 0.75).toInt()  // 75% of screen - good size for Your Selection
                
                // Set fixed height and gravity to BOTTOM on parent FrameLayout
                (sheet.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { params ->
                    params.height = maxHeight
                    params.gravity = android.view.Gravity.BOTTOM
                    sheet.layoutParams = params
                }
                
                // Configure behavior - fixed height, no more expansion
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.peekHeight = maxHeight
                behavior.maxHeight = maxHeight  // Limit max height
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                behavior.isFitToContents = true  // Fit to content, won't expand beyond maxHeight
                behavior.isHideable = true
                
                // Force layout update
                sheet.requestLayout()
            }
        }
        
        return dialog
    }

    private fun initViews(view: View) {
        closeButton = view.findViewById(R.id.closeButton)
        selectionSummaryBar = view.findViewById(R.id.selectionSummaryBar)
        selectedCountText = view.findViewById(R.id.selectedCountText)
        totalPriceText = view.findViewById(R.id.totalPriceText)
        truckTypesRecyclerView = view.findViewById(R.id.truckTypesRecyclerView)
        addTruckTypeButton = view.findViewById(R.id.addTruckTypeButton)
        clearAllButton = view.findViewById(R.id.clearAllButton)
        confirmButton = view.findViewById(R.id.confirmButton)
    }

    private fun setupAdapter() {
        multiTruckAdapter = MultiTruckSelectionAdapter { selections ->
            updateSummary(selections)
        }
        
        truckTypesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = multiTruckAdapter
        }
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            dismiss()
        }

        addTruckTypeButton.setOnClickListener {
            // Show available truck types that haven't been added yet
            val availableToAdd = availableTruckTypes.filter { it !in addedTruckTypeIds }
            if (availableToAdd.isNotEmpty()) {
                onAddTruckTypeListener?.invoke(availableToAdd)
            }
        }

        clearAllButton.setOnClickListener {
            multiTruckAdapter.clearAll()
            updateSummary(emptyList())
        }

        confirmButton.setOnClickListener {
            val selections = multiTruckAdapter.getAllSelections()
            val totalPrice = multiTruckAdapter.getTotalPrice()
            
            if (selections.isNotEmpty()) {
                onConfirmListener?.invoke(selections, totalPrice)
                dismiss()
            }
        }
    }

    /**
     * Add a truck type to the selection
     * 
     * Behavior:
     * - Old truck types are COLLAPSED (keep their selections)
     * - New truck type is EXPANDED
     * - RecyclerView SCROLLS to the new truck type
     */
    fun addTruckType(truckTypeId: String) {
        if (truckTypeId in addedTruckTypeIds) return

        val config = TruckSubtypesConfig.getConfigById(truckTypeId) ?: return
        val capacities = TruckSubtypesConfig.getAllCapacitiesForType(truckTypeId)
        val iconRes = getTruckIconResource(truckTypeId)

        val subtypes = config.subtypes.map { subtypeName: String ->
            val subtypeId = subtypeName.lowercase().replace(" ", "_")
            val capacityInfo = capacities[subtypeName]
            val capacityText = if (capacityInfo != null) {
                "${capacityInfo.minTonnage} - ${capacityInfo.maxTonnage} Ton"
            } else {
                ""
            }
            val price = calculateBasePrice(truckTypeId, subtypeId, distanceKm)

            SubtypeItem(
                id = subtypeId,
                name = subtypeName,
                capacity = capacityText,
                price = price,
                iconRes = iconRes
            )
        }

        val section = TruckTypeSection(
            truckTypeId = truckTypeId,
            displayName = config.displayName,
            iconRes = iconRes,
            subtypes = subtypes
        )

        // Add truck type - adapter will collapse old ones and expand new one
        val newPosition = multiTruckAdapter.addTruckType(section)
        addedTruckTypeIds.add(truckTypeId)
        
        // Scroll to the newly added truck type with smooth animation
        if (newPosition >= 0) {
            truckTypesRecyclerView.postDelayed({
                truckTypesRecyclerView.smoothScrollToPosition(newPosition)
            }, 100) // Small delay to let the layout settle
        }
        
        // Update add button visibility
        updateAddButtonVisibility()
    }

    private fun updateSummary(selections: List<TruckTypeSelection>) {
        val totalCount = selections.sumOf { it.getTotalCount() }
        val totalPrice = selections.sumOf { it.getTotalPrice() }

        if (totalCount > 0) {
            selectionSummaryBar.visibility = View.VISIBLE
            selectedCountText.text = "$totalCount truck${if (totalCount > 1) "s" else ""} selected"
            totalPriceText.text = formatPrice(totalPrice)
            confirmButton.isEnabled = true
            confirmButton.alpha = 1.0f
        } else {
            selectionSummaryBar.visibility = View.GONE
            confirmButton.isEnabled = false
            confirmButton.alpha = 0.5f
        }
    }

    private fun updateAddButtonVisibility() {
        val availableToAdd = availableTruckTypes.filter { it !in addedTruckTypeIds }
        addTruckTypeButton.visibility = if (availableToAdd.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Get truck icon resource - uses the SAME PNG icons as main truck type cards
     * This ensures visual consistency across the entire app
     */
    private fun getTruckIconResource(truckTypeId: String): Int {
        return when (truckTypeId.lowercase()) {
            "open" -> R.drawable.ic_open_main
            "container" -> R.drawable.ic_container_main
            "lcv" -> R.drawable.ic_lcv_main
            "mini" -> R.drawable.ic_mini_main
            "trailer" -> R.drawable.ic_trailer_main
            "tipper" -> R.drawable.ic_tipper_main
            "tanker" -> R.drawable.ic_tanker_main
            "dumper" -> R.drawable.ic_dumper_main
            "bulker" -> R.drawable.ic_bulker_main
            else -> R.drawable.ic_open_main
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun calculateBasePrice(truckTypeId: String, subtypeId: String, distanceKm: Int): Int {
        // Base pricing logic - can be replaced with API call
        val baseRate = when (truckTypeId.lowercase()) {
            "open" -> 15
            "container" -> 20
            "trailer" -> 25
            "tipper" -> 18
            "tanker" -> 22
            "dumper" -> 20
            "bulker" -> 23
            "lcv" -> 12
            "mini" -> 10
            else -> 15
        }
        
        val minPrice = 1500
        val calculatedPrice = baseRate * distanceKm
        return maxOf(minPrice, calculatedPrice)
    }

    private fun formatPrice(price: Int): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        formatter.maximumFractionDigits = 0
        return formatter.format(price)
    }

    // Public methods for callbacks
    fun setOnConfirmListener(listener: (List<TruckTypeSelection>, Int) -> Unit) {
        onConfirmListener = listener
    }

    fun setOnAddTruckTypeListener(listener: (List<String>) -> Unit) {
        onAddTruckTypeListener = listener
    }
}
