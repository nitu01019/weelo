package com.weelo.logistics.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.weelo.logistics.R
import com.weelo.logistics.data.models.TruckSubtypesConfig

/**
 * MODULARITY: Bottom sheet for truck subtype selection
 * Shows subtypes (sizes) for selected truck type
 * Includes quantity picker and capacity information
 * 
 * SCALABILITY: Efficient chip selection, minimal views
 * CODING STANDARDS: Material Design 3 bottom sheet pattern
 */
class SubtypeSelectionBottomSheet : BottomSheetDialogFragment() {

    private lateinit var truckTypeId: String
    private lateinit var truckTypeName: String
    private var truckImageRes: Int = 0
    
    // UI Components
    private lateinit var truckTypeNameView: TextView
    private lateinit var subtypeChipGroup: ChipGroup
    private lateinit var quantityText: TextView
    private lateinit var decreaseButton: MaterialButton
    private lateinit var increaseButton: MaterialButton
    private lateinit var confirmButton: MaterialButton
    private lateinit var truckImage: ImageView
    private lateinit var capacityInfo: TextView
    
    // State
    private var selectedSubtype: String = ""
    private var quantity: Int = 1
    
    // Callback for confirmation
    private var onConfirmedListener: ((String, String, Int) -> Unit)? = null
    
    companion object {
        private const val ARG_TRUCK_TYPE_ID = "truck_type_id"
        private const val ARG_TRUCK_TYPE_NAME = "truck_type_name"
        private const val ARG_TRUCK_IMAGE_RES = "truck_image_res"
        
        /**
         * EASY UNDERSTANDING: Factory method to create subtype selection bottom sheet
         */
        fun newInstance(
            truckTypeId: String,
            truckTypeName: String,
            truckImageRes: Int
        ): SubtypeSelectionBottomSheet {
            return SubtypeSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TRUCK_TYPE_ID, truckTypeId)
                    putString(ARG_TRUCK_TYPE_NAME, truckTypeName)
                    putInt(ARG_TRUCK_IMAGE_RES, truckImageRes)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let {
            truckTypeId = it.getString(ARG_TRUCK_TYPE_ID) ?: "open"
            truckTypeName = it.getString(ARG_TRUCK_TYPE_NAME) ?: "Open Truck"
            truckImageRes = it.getInt(ARG_TRUCK_IMAGE_RES, R.drawable.ic_open_main)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_subtype_selection, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupHeader()
        setupSubtypeChips()
        setupQuantityControls()
        setupConfirmButton()
    }
    
    /**
     * MODULARITY: Initialize all UI components
     */
    private fun initializeViews(view: View) {
        truckTypeNameView = view.findViewById(R.id.truckTypeName)
        subtypeChipGroup = view.findViewById(R.id.subtypeChipGroup)
        quantityText = view.findViewById(R.id.quantityText)
        decreaseButton = view.findViewById(R.id.decreaseButton)
        increaseButton = view.findViewById(R.id.increaseButton)
        confirmButton = view.findViewById(R.id.confirmButton)
        truckImage = view.findViewById(R.id.truckImage)
        capacityInfo = view.findViewById(R.id.capacityInfo)
    }
    
    /**
     * EASY UNDERSTANDING: Setup header with truck name and image
     */
    private fun setupHeader() {
        truckTypeNameView.text = truckTypeName
        truckImage.setImageResource(truckImageRes)
    }
    
    /**
     * MODULARITY: Create chips for all available subtypes
     * Uses TruckSubtypesConfig for centralized subtype management
     */
    private fun setupSubtypeChips() {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId)
        val subtypes = config?.subtypes ?: listOf("Standard")
        
        subtypeChipGroup.removeAllViews()
        
        subtypes.forEachIndexed { index, subtype ->
            val chip = Chip(requireContext()).apply {
                text = subtype
                isCheckable = true
                isChecked = index == 0 // Select first by default
                
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSubtype = subtype
                        updateCapacityInfo()
                    }
                }
            }
            subtypeChipGroup.addView(chip)
            
            // Set first as default selected
            if (index == 0) {
                selectedSubtype = subtype
                updateCapacityInfo()
            }
        }
    }
    
    /**
     * SCALABILITY: Update capacity information based on selected subtype
     */
    private fun updateCapacityInfo() {
        val capacity = TruckSubtypesConfig.getCapacityInfo(truckTypeId, selectedSubtype)
        capacityInfo.text = if (capacity != null) {
            val tonnage = capacity.capacityKg / 1000.0
            val length = if (capacity.lengthFeet != null) "${capacity.lengthFeet}ft" else ""
            "Capacity: ${tonnage}T $length".trim()
        } else {
            "Capacity information not available"
        }
    }
    
    /**
     * EASY UNDERSTANDING: Setup quantity increase/decrease controls
     */
    private fun setupQuantityControls() {
        quantityText.text = quantity.toString()
        
        decreaseButton.setOnClickListener {
            if (quantity > 1) {
                quantity--
                quantityText.text = quantity.toString()
            }
        }
        
        increaseButton.setOnClickListener {
            if (quantity < 99) { // Max 99 trucks
                quantity++
                quantityText.text = quantity.toString()
            }
        }
    }
    
    /**
     * MODULARITY: Handle confirm button click
     */
    private fun setupConfirmButton() {
        confirmButton.setOnClickListener {
            if (selectedSubtype.isNotEmpty()) {
                onConfirmedListener?.invoke(truckTypeId, selectedSubtype, quantity)
                dismiss()
            }
        }
    }
    
    /**
     * SCALABILITY: Set callback for confirmation
     */
    fun setOnConfirmedListener(listener: (truckTypeId: String, subtype: String, quantity: Int) -> Unit) {
        onConfirmedListener = listener
    }
    
    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }
}
