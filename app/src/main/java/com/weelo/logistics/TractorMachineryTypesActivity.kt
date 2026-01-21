package com.weelo.logistics

import android.os.Bundle
import android.widget.Toast
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.TractorMachinerySubtypesConfig
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * TractorMachineryTypesActivity - Tractor Machinery Selection Screen
 * SEPARATE from TruckTypesActivity - handles agricultural/construction machinery
 */
class TractorMachineryTypesActivity : AppCompatActivity() {

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var routeText: TextView
    private lateinit var continueButton: CardView
    
    // Toggle Components for Second Row
    private lateinit var machineryTypesToggleCard: CardView
    private lateinit var toggleButtonText: TextView
    private lateinit var toggleArrow: TextView
    private lateinit var secondRowMachinery: LinearLayout
    
    // State Management
    private var isSecondRowVisible = true
    
    // Agricultural machinery selection cards
    private var tractorCard: CardView? = null
    private var trolleyCard: CardView? = null
    private var thresherCard: CardView? = null
    private var harvesterCard: CardView? = null
    private var cultivatorCard: CardView? = null
    private var seederCard: CardView? = null
    private var sprayerCard: CardView? = null
    private var rotavatorCard: CardView? = null
    private var ploughCard: CardView? = null
    private var balerCard: CardView? = null
    
    private lateinit var fromLocation: Location
    private lateinit var toLocation: Location
    
    private val machinerySelections = mutableMapOf<String, MutableMap<String, Int>>().apply {
        TractorMachinerySubtypesConfig.getAllDialogMachineryTypes().forEach { machineryType ->
            put(machineryType, mutableMapOf())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tractor_machinery_types)
        
        fromLocation = intent.getParcelableExtraCompat<Location>("FROM_LOCATION") ?: Location("Unknown")
        toLocation = intent.getParcelableExtraCompat<Location>("TO_LOCATION") ?: Location("Unknown")

        setupViews()
    }

    private fun setupViews() {
        // Initialize UI components
        backButton = findViewById(R.id.backButton)
        routeText = findViewById(R.id.routeText)
        continueButton = findViewById(R.id.continueButton)
        
        // Initialize toggle components
        machineryTypesToggleCard = findViewById(R.id.machineryTypesToggleCard)
        toggleButtonText = findViewById(R.id.toggleButtonText)
        toggleArrow = findViewById(R.id.toggleArrow)
        secondRowMachinery = findViewById(R.id.secondRowMachinery)
        
        tractorCard = findViewById(R.id.tractorCard)
        trolleyCard = findViewById(R.id.trolleyCard)
        thresherCard = findViewById(R.id.thresherCard)
        harvesterCard = findViewById(R.id.harvesterCard)
        cultivatorCard = findViewById(R.id.cultivatorCard)
        seederCard = findViewById(R.id.seederCard)
        sprayerCard = findViewById(R.id.sprayerCard)
        rotavatorCard = findViewById(R.id.rotavatorCard)
        ploughCard = findViewById(R.id.ploughCard)
        balerCard = findViewById(R.id.balerCard)

        backButton.setOnClickListener { 
            finish()
            TransitionHelper.applySlideOutRightTransition(this)
        }
        
        machineryTypesToggleCard.setOnClickListener {
            toggleSecondRow()
        }
        
        setupMachineryCardClicks()

        continueButton.setOnClickListener {
            // Check if we have any selections
            val hasSelections = machinerySelections.values.any { it.isNotEmpty() }
            
            if (hasSelections) {
                val details = mutableListOf<String>()
                var totalMachinery = 0
                var firstSelectedMachineryId = ""
                
                machinerySelections.forEach { (machineryType, subtypes) ->
                    val count = subtypes.values.sum()
                    if (count > 0) {
                        if (firstSelectedMachineryId.isEmpty()) {
                            firstSelectedMachineryId = machineryType
                        }
                        totalMachinery += count
                        val displayName = TractorMachinerySubtypesConfig.getConfigById(machineryType)?.displayName ?: machineryType
                        details.add("$displayName: $count")
                    }
                }
                
                // Navigate to pricing screen
                navigateToPricing(firstSelectedMachineryId)
            } else {
                showToast("Please select a machinery type")
            }
        }

        routeText.text = "${fromLocation.address} - ${toLocation.address}"
    }
    
    private fun setupMachineryCardClicks() {
        tractorCard?.setOnClickListener { selectMachinery("tractor") }
        trolleyCard?.setOnClickListener { selectMachinery("trolley") }
        thresherCard?.setOnClickListener { selectMachinery("thresher") }
        harvesterCard?.setOnClickListener { selectMachinery("harvester") }
        cultivatorCard?.setOnClickListener { selectMachinery("cultivator") }
        seederCard?.setOnClickListener { selectMachinery("seeder") }
        sprayerCard?.setOnClickListener { selectMachinery("sprayer") }
        rotavatorCard?.setOnClickListener { selectMachinery("rotavator") }
        ploughCard?.setOnClickListener { selectMachinery("plough") }
        balerCard?.setOnClickListener { selectMachinery("baler") }
    }
    
    private fun selectMachinery(machineryId: String) {
        TractorMachinerySubtypesConfig.getConfigById(machineryId)?.let {
            showMachinerySubtypesDialog(machineryId)
        }
    }
    
    /**
     * Toggles visibility of second row machinery
     * Uses smooth slide animation for better UX
     */
    private fun toggleSecondRow() {
        if (isSecondRowVisible) {
            hideSecondRow()
        } else {
            showSecondRow()
        }
    }
    
    /**
     * Hides second row with slide-up animation
     * Duration: 300ms with fade effect
     */
    private fun hideSecondRow() {
        secondRowMachinery.animate()
            .alpha(0f)
            .translationY(-secondRowMachinery.height.toFloat())
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                secondRowMachinery.visibility = View.GONE
            }
            .start()
        
        updateToggleButton(isVisible = false)
        isSecondRowVisible = false
    }
    
    /**
     * Shows second row with slide-down animation
     * Duration: 300ms with fade effect
     */
    private fun showSecondRow() {
        secondRowMachinery.visibility = View.VISIBLE
        secondRowMachinery.alpha = 0f
        secondRowMachinery.translationY = -secondRowMachinery.height.toFloat()
        
        secondRowMachinery.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        
        updateToggleButton(isVisible = true)
        isSecondRowVisible = true
    }
    
    /**
     * Updates toggle button text and arrow based on visibility state
     */
    private fun updateToggleButton(isVisible: Boolean) {
        if (isVisible) {
            toggleButtonText.text = "Hide\nMachinery"
            toggleArrow.text = "▲"
        } else {
            toggleButtonText.text = "Show\nMachinery"
            toggleArrow.text = "▼"
        }
    }
    
    companion object {
        private const val ANIMATION_DURATION = 300L
    }
    
    /**
     * Universal dialog function for all machinery types
     * Uses configuration from TractorMachinerySubtypesConfig
     */
    private fun showMachinerySubtypesDialog(machineryTypeId: String) {
        val config = TractorMachinerySubtypesConfig.getConfigById(machineryTypeId) ?: return
        val selectedSubtypes = machinerySelections[machineryTypeId] ?: return
        val machineryCard = getMachineryCardById(machineryTypeId)
        
        val bottomSheet = BottomSheetDialog(this)
        
        val view = layoutInflater.inflate(R.layout.bottom_sheet_machinery_subtypes, null)
        bottomSheet.setContentView(view)
        
        // Configure bottom sheet using helper for consistency and scalability
        com.weelo.logistics.core.util.BottomSheetHelper.configureBottomSheet(
            dialog = bottomSheet,
            style = com.weelo.logistics.core.util.BottomSheetHelper.Style.FIXED_LARGE,
            isDismissable = true
        )
        
        view.findViewById<TextView>(R.id.bottomSheetTitle)?.text = config.displayName
        view.findViewById<ImageView>(R.id.closeButton)?.setOnClickListener {
            resetBottomSheetSelections(view, machineryTypeId)
            bottomSheet.dismiss()
        }
        
        val subtypesContainer = view.findViewById<LinearLayout>(R.id.subtypesContainer)
        
        subtypesContainer?.let {
            val gridLayout = GridLayout(this)
            gridLayout.columnCount = config.gridColumns
            gridLayout.rowCount = (config.subtypes.size + config.gridColumns - 1) / config.gridColumns
            
            config.subtypes.forEach { subtype ->
                val card = layoutInflater.inflate(R.layout.item_machinery_subtype, null)
                card.findViewById<TextView>(R.id.subtypeName)?.text = subtype
                
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(4, 4, 4, 4)
                }
                card.layoutParams = params
                
                val quantityControls = card.findViewById<LinearLayout>(R.id.quantityControls)
                val quantityText = card.findViewById<TextView>(R.id.quantityText)
                val plusButton = card.findViewById<TextView>(R.id.plusButton)
                val minusButton = card.findViewById<TextView>(R.id.minusButton)
                val subtypeCard = card.findViewById<CardView>(R.id.subtypeCard)
                
                subtypeCard?.setOnClickListener {
                    if (selectedSubtypes[subtype] == null) {
                        selectedSubtypes[subtype] = 1
                        quantityControls?.visibility = View.VISIBLE
                        quantityText?.text = "1"
                        subtypeCard.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                        machineryCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                        continueButton.visibility = View.VISIBLE
                        updateConfirmButton(view, selectedSubtypes)
                    }
                }
                
                plusButton?.setOnClickListener {
                    val currentQty = selectedSubtypes[subtype] ?: 1
                    selectedSubtypes[subtype] = currentQty + 1
                    quantityText?.text = "${currentQty + 1}"
                    updateConfirmButton(view, selectedSubtypes)
                }
                
                minusButton?.setOnClickListener {
                    val currentQty = selectedSubtypes[subtype] ?: 1
                    if (currentQty > 1) {
                        selectedSubtypes[subtype] = currentQty - 1
                        quantityText?.text = "${currentQty - 1}"
                        updateConfirmButton(view, selectedSubtypes)
                    } else {
                        selectedSubtypes.remove(subtype)
                        quantityControls?.visibility = View.GONE
                        subtypeCard?.setCardBackgroundColor(getColor(android.R.color.white))
                        if (selectedSubtypes.isEmpty()) {
                            machineryCard?.setCardBackgroundColor(getColor(android.R.color.white))
                            if (machinerySelections.values.all { it.isEmpty() }) {
                                continueButton.visibility = View.GONE
                            }
                        }
                        updateConfirmButton(view, selectedSubtypes)
                    }
                }
                
                gridLayout.addView(card)
            }
            
            it.addView(gridLayout)
        }
        
        view.findViewById<Button>(R.id.confirmSelectionButton)?.setOnClickListener {
            if (selectedSubtypes.isNotEmpty()) {
                machineryCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
                bottomSheet.dismiss()
                showToast("${selectedSubtypes.values.sum()} ${config.displayName} selected - Click Continue to proceed")
            } else {
                showToast("Please select at least one machinery")
            }
        }
        
        view.findViewById<Button>(R.id.clearAllButton)?.setOnClickListener {
            resetBottomSheetSelections(view, machineryTypeId)
        }
        
        bottomSheet.show()
    }
    
    /**
     * Helper function to get machinery card by machinery type ID
     */
    private fun getMachineryCardById(machineryTypeId: String): CardView? {
        return when (machineryTypeId) {
            "tractor" -> tractorCard
            "trolley" -> trolleyCard
            "thresher" -> thresherCard
            "harvester" -> harvesterCard
            "cultivator" -> cultivatorCard
            "seeder" -> seederCard
            "sprayer" -> sprayerCard
            "rotavator" -> rotavatorCard
            "plough" -> ploughCard
            "baler" -> balerCard
            else -> null
        }
    }
    
    private fun updateConfirmButton(view: View, subtypesMap: Map<String, Int>) {
        val totalCount = subtypesMap.values.sum()
        view.findViewById<Button>(R.id.confirmSelectionButton)?.text = 
            if (totalCount > 0) "CONFIRM ($totalCount)" else "CONFIRM"
    }
    
    private fun resetBottomSheetSelections(view: View, machineryType: String) {
        val selectedSubtypes = machinerySelections[machineryType] ?: return
        val machineryCard = getMachineryCardById(machineryType)
        
        selectedSubtypes.clear()
        machineryCard?.setCardBackgroundColor(getColor(android.R.color.white))
        
        if (machinerySelections.values.all { it.isEmpty() }) {
            continueButton.visibility = View.GONE
        }
        
        val subtypesContainer = view.findViewById<LinearLayout>(R.id.subtypesContainer)
        val gridLayout = subtypesContainer?.getChildAt(0) as? GridLayout
        
        gridLayout?.let { grid ->
            for (i in 0 until grid.childCount) {
                val card = grid.getChildAt(i)
                val subtypeCard = card.findViewById<CardView>(R.id.subtypeCard)
                val quantityControls = card.findViewById<LinearLayout>(R.id.quantityControls)
                
                subtypeCard?.setCardBackgroundColor(getColor(android.R.color.white))
                quantityControls?.visibility = View.GONE
            }
        }
        
        updateConfirmButton(view, selectedSubtypes)
    }
    
    /**
     * Navigate to pricing screen with selected machinery
     */
    private fun navigateToPricing(machineryId: String) {
        Toast.makeText(
            this,
            "Booking confirmed! Payment feature coming soon.",
            Toast.LENGTH_LONG
        ).show()
    }
}
