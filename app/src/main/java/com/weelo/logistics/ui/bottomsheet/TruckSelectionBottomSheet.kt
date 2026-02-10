package com.weelo.logistics.ui.bottomsheet

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.weelo.logistics.R
import com.weelo.logistics.data.models.Location

/**
 * MODULARITY: Bottom sheet for truck type selection
 * Shows truck categories (Open, Container, LCV, etc.)
 * Replaces TruckTypesActivity navigation for better UX
 * 
 * SCALABILITY: Lightweight bottom sheet, no heavy RecyclerView
 * CODING STANDARDS: Follows Material Design bottom sheet pattern
 */
class TruckSelectionBottomSheet : BottomSheetDialogFragment() {

    private lateinit var fromLocation: Location
    private lateinit var toLocation: Location
    
    // UI Components - All truck type cards
    private lateinit var openCard: CardView
    private lateinit var containerCard: CardView
    private lateinit var lcvCard: CardView
    private lateinit var miniCard: CardView
    private lateinit var trailerCard: CardView
    private lateinit var tipperCard: CardView
    private lateinit var tankerCard: CardView
    private lateinit var dumperCard: CardView
    private lateinit var bulkerCard: CardView
    private lateinit var routeText: TextView
    
    // Callback for truck type selection
    private var onTruckTypeSelectedListener: ((String) -> Unit)? = null
    
    companion object {
        private const val ARG_FROM_LOCATION = "from_location"
        private const val ARG_TO_LOCATION = "to_location"
        
        /**
         * EASY UNDERSTANDING: Factory method to create bottom sheet with locations
         */
        fun newInstance(fromLocation: Location, toLocation: Location): TruckSelectionBottomSheet {
            return TruckSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FROM_LOCATION, fromLocation)
                    putParcelable(ARG_TO_LOCATION, toLocation)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract locations from arguments
        arguments?.let {
            fromLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_FROM_LOCATION, Location::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_FROM_LOCATION)
            } ?: Location("Unknown")
            
            toLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_TO_LOCATION, Location::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_TO_LOCATION)
            } ?: Location("Unknown")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_truck_selection, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRouteDisplay()
        setupClickListeners()
    }
    
    /**
     * MODULARITY: Initialize all UI components
     */
    private fun initializeViews(view: View) {
        routeText = view.findViewById(R.id.routeText)
        openCard = view.findViewById(R.id.openCard)
        containerCard = view.findViewById(R.id.containerCard)
        lcvCard = view.findViewById(R.id.lcvCard)
        miniCard = view.findViewById(R.id.miniCard)
        trailerCard = view.findViewById(R.id.trailerCard)
        tipperCard = view.findViewById(R.id.tipperCard)
        tankerCard = view.findViewById(R.id.tankerCard)
        dumperCard = view.findViewById(R.id.dumperCard)
        bulkerCard = view.findViewById(R.id.bulkerCard)
    }
    
    /**
     * EASY UNDERSTANDING: Display route information
     */
    private fun setupRouteDisplay() {
        val fromCity = fromLocation.city.ifEmpty { "Pickup" }
        val toCity = toLocation.city.ifEmpty { "Drop" }
        routeText.text = "$fromCity → $toCity"
    }
    
    /**
     * MODULARITY: Setup click listeners for all truck types
     */
    private fun setupClickListeners() {
        openCard.setOnClickListener { onTruckTypeClicked("open") }
        containerCard.setOnClickListener { onTruckTypeClicked("container") }
        lcvCard.setOnClickListener { onTruckTypeClicked("lcv") }
        miniCard.setOnClickListener { onTruckTypeClicked("mini") }
        trailerCard.setOnClickListener { onTruckTypeClicked("trailer") }
        tipperCard.setOnClickListener { onTruckTypeClicked("tipper") }
        tankerCard.setOnClickListener { onTruckTypeClicked("tanker") }
        dumperCard.setOnClickListener { onTruckTypeClicked("dumper") }
        bulkerCard.setOnClickListener { onTruckTypeClicked("bulker") }
    }
    
    /**
     * EASY UNDERSTANDING: Handle truck type selection
     * Navigate to TruckTypesActivity with pre-selected truck type
     */
    private fun onTruckTypeClicked(truckTypeId: String) {
        dismiss() // Close bottom sheet first
        onTruckTypeSelectedListener?.invoke(truckTypeId)
    }
    
    /**
     * SCALABILITY: Set callback for truck type selection
     */
    fun setOnTruckTypeSelectedListener(listener: (String) -> Unit) {
        onTruckTypeSelectedListener = listener
    }
    
    override fun getTheme(): Int {
        // Use rounded corner bottom sheet theme
        return R.style.BottomSheetDialogTheme
    }
}
