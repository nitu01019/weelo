package com.weelo.logistics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.adapters.SelectedTrucksAdapter
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.tutorial.OnboardingManager
import com.weelo.logistics.tutorial.TutorialCoordinator
import com.weelo.logistics.core.util.showToast
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.core.util.getParcelableExtraCompat
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.TruckConfig
import com.weelo.logistics.data.models.TruckSubtypesConfig
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.model.VehicleModel
import com.weelo.logistics.presentation.trucks.TruckTypesNavigationEvent
import com.weelo.logistics.presentation.trucks.TruckTypesViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint

/**
 * TruckTypesActivity - Production-Ready Truck Selection Screen
 */
@AndroidEntryPoint
class TruckTypesActivity : AppCompatActivity() {

    private val viewModel: TruckTypesViewModel by viewModels()

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var routeText: TextView
    private lateinit var continueButton: CardView
    
    // Toggle Components
    private lateinit var toggleTrucksButton: CardView
    private lateinit var toggleIcon: TextView
    private lateinit var trucksContainer: LinearLayout
    private var areTrucksVisible = true
    
    // Selected Trucks Section
    private lateinit var selectedTrucksSection: LinearLayout
    private lateinit var selectedTrucksRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var truckCountText: TextView
    private lateinit var selectedTrucksAdapter: SelectedTrucksAdapter
    private val selectedTrucksList = mutableListOf<SelectedTruckItem>()
    
    // Tutorial
    private var tutorialCoordinator: TutorialCoordinator? = null
    
    // Vehicle selection cards
    private var openCard: CardView? = null
    private var containerCard: CardView? = null
    private var lcvCard: CardView? = null
    private var miniCard: CardView? = null
    private var trailerCard: CardView? = null
    private var tipperCard: CardView? = null
    private var tankerCard: CardView? = null
    private var dumperCard: CardView? = null
    private var bulkerCard: CardView? = null
    
    private lateinit var fromLocation: Location
    private lateinit var toLocation: Location
    
    private val truckSelections = mutableMapOf<String, MutableMap<String, Int>>().apply {
        TruckSubtypesConfig.getAllDialogTruckTypes().forEach { truckType ->
            put(truckType, mutableMapOf())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_truck_types)
        
        fromLocation = intent.getParcelableExtraCompat<Location>("FROM_LOCATION") ?: Location("Unknown")
        toLocation = intent.getParcelableExtraCompat<Location>("TO_LOCATION") ?: Location("Unknown")

        setupViews()
        setupSelectedTrucksSection()
        observeViewModel()
        
        // Start tutorial on first launch (after a short delay for layout to settle)
        window.decorView.post {
            startTutorialIfNeeded()
        }
    }

    private fun setupViews() {
        // Initialize UI components
        backButton = findViewById(R.id.backButton)
        routeText = findViewById(R.id.routeText)
        continueButton = findViewById(R.id.continueButton)
        
        // Initialize toggle components
        toggleTrucksButton = findViewById(R.id.toggleTrucksButton)
        toggleIcon = findViewById(R.id.toggleIcon)
        trucksContainer = findViewById(R.id.trucksContainer)
        
        openCard = findViewById(R.id.openCard)
        containerCard = findViewById(R.id.containerCard)
        lcvCard = findViewById(R.id.lcvCard)
        miniCard = findViewById(R.id.miniCard)
        trailerCard = findViewById(R.id.trailerCard)
        tipperCard = findViewById(R.id.tipperCard)
        tankerCard = findViewById(R.id.tankerCard)
        dumperCard = findViewById(R.id.dumperCard)
        bulkerCard = findViewById(R.id.bulkerCard)

        backButton.setOnClickListener { 
            finish()
            TransitionHelper.applySlideOutRightTransition(this)
        }
        
        toggleTrucksButton.setOnClickListener {
            toggleTrucksVisibility()
        }
        
        setupVehicleCardClicks()

        continueButton.setOnClickListener {
            // Check if we have any selections
            val hasSelections = truckSelections.values.any { it.isNotEmpty() }
            
            if (hasSelections) {
                val details = mutableListOf<String>()
                var totalTrucks = 0
                var firstSelectedVehicleId = ""
                
                truckSelections.forEach { (truckType, subtypes) ->
                    val count = subtypes.values.sum()
                    if (count > 0) {
                        if (firstSelectedVehicleId.isEmpty()) {
                            firstSelectedVehicleId = truckType
                        }
                        totalTrucks += count
                        val displayName = TruckSubtypesConfig.getConfigById(truckType)?.displayName ?: truckType
                        details.add("$displayName: $count")
                    }
                }
                
                // Navigate to pricing screen
                navigateToPricing(firstSelectedVehicleId)
            } else {
                showToast("Please select a vehicle type")
            }
        }

        routeText.text = "${fromLocation.address} - ${toLocation.address}"
    }
    
    /**
     * Setup the Selected Trucks section with RecyclerView
     * Always visible with vertical list layout
     */
    private fun setupSelectedTrucksSection() {
        selectedTrucksSection = findViewById(R.id.selectedTrucksSection)
        selectedTrucksRecyclerView = findViewById(R.id.selectedTrucksRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        truckCountText = findViewById(R.id.truckCountText)
        
        // Setup adapter with callbacks
        selectedTrucksAdapter = SelectedTrucksAdapter(
            onQuantityChanged = { item, newQuantity ->
                // FAST UPDATE - Immediate UI refresh
                val index = selectedTrucksList.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    // Update the mutable item
                    selectedTrucksList[index].quantity = newQuantity
                    
                    // Update the main selection map IMMEDIATELY
                    val key = if (item.specification.contains("|")) {
                        item.specification // LCV format: "LCV Open|17 Feet"
                    } else {
                        item.specification
                    }
                    truckSelections[item.truckTypeId]?.put(key, newQuantity)
                    
                    // IMMEDIATE refresh - create NEW list with copies to trigger DiffUtil
                    val newList = selectedTrucksList.map { it.copy() }
                    selectedTrucksAdapter.submitList(newList) {
                        // After list is submitted, update count
                        updateTruckCount()
                    }
                }
            },
            onRemove = { item ->
                // Remove from list
                selectedTrucksList.removeIf { it.id == item.id }
                selectedTrucksAdapter.submitList(ArrayList(selectedTrucksList))
                
                // Remove from main selection map
                val key = if (item.specification.contains("|")) {
                    item.specification
                } else {
                    item.specification
                }
                truckSelections[item.truckTypeId]?.remove(key)
                
                // Update vehicle card background
                val vehicleCard = getVehicleCardById(item.truckTypeId)
                val hasRemaining = truckSelections[item.truckTypeId]?.isNotEmpty() == true
                if (!hasRemaining) {
                    vehicleCard?.setCardBackgroundColor(getColor(android.R.color.darker_gray))
                }
                
                // Show empty state if no trucks
                updateEmptyState()
                updateTruckCount()
                
                if (selectedTrucksList.isEmpty()) {
                    continueButton.visibility = View.GONE
                }
                
                showToast("${item.truckTypeName} removed")
            }
        )
        
        // Setup RecyclerView with VERTICAL layout
        selectedTrucksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TruckTypesActivity, LinearLayoutManager.VERTICAL, false)
            adapter = selectedTrucksAdapter
        }
        
        // Section is always visible, show empty state initially
        updateEmptyState()
    }
    
    /**
     * Update empty state visibility
     * Shows "No trucks selected" when list is empty
     */
    private fun updateEmptyState() {
        if (selectedTrucksList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            selectedTrucksRecyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            selectedTrucksRecyclerView.visibility = View.VISIBLE
        }
    }
    
    /**
     * Update truck count display
     * Shows total number of trucks selected (just the number)
     */
    private fun updateTruckCount() {
        val totalCount = selectedTrucksList.sumOf { it.quantity }
        truckCountText.text = totalCount.toString()
    }
    
    /**
     * Start tutorial if this is the first time user sees this screen
     */
    private fun startTutorialIfNeeded() {
        val onboardingManager = OnboardingManager.getInstance(this)
        
        tutorialCoordinator = TutorialCoordinator(
            activity = this,
            onboardingManager = onboardingManager,
            onComplete = {
                // Tutorial completed or skipped
                tutorialCoordinator = null
            }
        )
        
        tutorialCoordinator?.startTruckSelectionTutorial()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up tutorial if activity is destroyed during tutorial
        tutorialCoordinator = null
    }
    
    /**
     * Add selected trucks to the "Selected Trucks" section
     * Called when user confirms selection in the bottom sheet
     */
    private fun addToSelectedTrucks(truckTypeId: String, selectedSubtypes: Map<String, Int>) {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId) ?: return
        val iconResource = getTruckIconResource(truckTypeId)
        
        selectedSubtypes.forEach { (specification, quantity) ->
            val uniqueId = "${truckTypeId}_${specification}_${System.currentTimeMillis()}"
            
            // Check if this truck already exists (update quantity instead of adding new)
            val existingIndex = selectedTrucksList.indexOfFirst { 
                it.truckTypeId == truckTypeId && it.specification == specification 
            }
            
            if (existingIndex != -1) {
                // Update existing truck quantity
                selectedTrucksList[existingIndex].quantity = quantity
            } else {
                // Add new truck
                val selectedTruck = SelectedTruckItem(
                    id = uniqueId,
                    truckTypeId = truckTypeId,
                    truckTypeName = config.displayName,
                    specification = specification,
                    iconResource = iconResource,
                    quantity = quantity
                )
                selectedTrucksList.add(selectedTruck)
            }
        }
        
        // Update adapter and empty state
        selectedTrucksAdapter.submitList(ArrayList(selectedTrucksList))
        updateEmptyState()
        updateTruckCount()
    }
    
    private fun setupVehicleCardClicks() {
        openCard?.setOnClickListener { selectVehicle("open") }
        containerCard?.setOnClickListener { selectVehicle("container") }
        lcvCard?.setOnClickListener { selectVehicle("lcv") }
        miniCard?.setOnClickListener { selectVehicle("mini") }
        trailerCard?.setOnClickListener { selectVehicle("trailer") }
        tipperCard?.setOnClickListener { selectVehicle("tipper") }
        tankerCard?.setOnClickListener { selectVehicle("tanker") }
        dumperCard?.setOnClickListener { selectVehicle("dumper") }
        bulkerCard?.setOnClickListener { selectVehicle("bulker") }
    }
    
    private fun selectVehicle(vehicleId: String) {
        TruckSubtypesConfig.getConfigById(vehicleId)?.let {
            showTruckSubtypesDialog(vehicleId)
        }
    }
    
    /**
     * Toggles visibility of second row trucks (Trailer, Tipper, Tanker, Dumper, Bulker)
     * Uses smooth slide animation for better UX
     * 
     * @see hideSecondRow for hiding animation
     * @see showSecondRow for showing animation
     */
    private fun toggleTrucksVisibility() {
        if (areTrucksVisible) {
            // Hide trucks - slide up behind row 1
            trucksContainer.animate()
                .translationY(-trucksContainer.height.toFloat())
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    trucksContainer.visibility = View.GONE
                }
                .start()
            toggleIcon.text = "▼"
            areTrucksVisible = false
        } else {
            // Show trucks - slide down from behind row 1
            trucksContainer.visibility = View.VISIBLE
            trucksContainer.translationY = -trucksContainer.height.toFloat()
            trucksContainer.alpha = 0f
            trucksContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .start()
            toggleIcon.text = "▲"
            areTrucksVisible = true
        }
    }
    
    /**
     * Universal dialog function for all truck types
     * Uses configuration from TruckSubtypesConfig
     */
    private fun showTruckSubtypesDialog(truckTypeId: String) {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId) ?: return
        val selectedSubtypes = truckSelections[truckTypeId] ?: return
        val vehicleCard = getVehicleCardById(truckTypeId)
        
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.behavior.isDraggable = true
        bottomSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        
        val view = layoutInflater.inflate(R.layout.bottom_sheet_truck_subtypes, null)
        bottomSheet.setContentView(view)
        
        view.findViewById<TextView>(R.id.bottomSheetTitle)?.text = config.displayName
        
        // Set the truck icon based on truck type
        val truckIcon = view.findViewById<ImageView>(R.id.bottomSheetTruckIcon)
        truckIcon?.setImageResource(getTruckIconResource(truckTypeId))
        
        view.findViewById<ImageView>(R.id.closeButton)?.setOnClickListener {
            resetBottomSheetSelections(view, truckTypeId)
            bottomSheet.dismiss()
        }
        
        val subtypesContainer = view.findViewById<LinearLayout>(R.id.subtypesContainer)
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)
        
        // Check if this is LCV - handle specially as category headers
        if (truckTypeId == "lcv" && config.subtypeLengths.isNotEmpty()) {
            setupLcvCategoryDialog(view, config, selectedSubtypes, vehicleCard, scrollView)
        } else {
            // Standard truck types - direct selection with 4 columns like reference
            subtypesContainer?.let {
                // Clear any existing views first to prevent duplicates
                it.removeAllViews()
                
                val gridLayout = GridLayout(this)
                gridLayout.columnCount = 4  // 4 columns like reference design
                gridLayout.rowCount = (config.subtypes.size + 3) / 4
                
                config.subtypes.forEach { subtype ->
                    addSubtypeCard(gridLayout, subtype, truckTypeId, selectedSubtypes, vehicleCard, view)
                }
                
                it.addView(gridLayout)
            }
            
            // Add length subtypes if configured (for non-LCV trucks)
            if (config.lengthSubtypes.isNotEmpty()) {
                view.findViewById<TextView>(R.id.lengthSectionTitle)?.visibility = View.VISIBLE
                val lengthContainer = view.findViewById<LinearLayout>(R.id.lengthSubtypesContainer)
                lengthContainer?.visibility = View.VISIBLE
                
                lengthContainer?.let {
                    // Clear any existing views first to prevent duplicates
                    it.removeAllViews()
                    
                    val gridLayout = GridLayout(this)
                    gridLayout.columnCount = 4
                    gridLayout.rowCount = (config.lengthSubtypes.size + 3) / 4
                    
                    config.lengthSubtypes.forEach { lengthSubtype ->
                        addSubtypeCard(gridLayout, lengthSubtype, truckTypeId, selectedSubtypes, vehicleCard, view)
                    }
                    
                    it.addView(gridLayout)
                }
            }
        }
        
        view.findViewById<Button>(R.id.confirmSelectionButton)?.setOnClickListener {
            if (selectedSubtypes.isNotEmpty()) {
                vehicleCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
                
                // Add to selected trucks section
                addToSelectedTrucks(truckTypeId, selectedSubtypes)
                
                bottomSheet.dismiss()
                showToast("${selectedSubtypes.values.sum()} ${config.displayName} trucks selected")
            } else {
                showToast("Please select at least one truck")
            }
        }
        
        view.findViewById<Button>(R.id.clearAllButton)?.setOnClickListener {
            resetBottomSheetSelections(view, truckTypeId)
        }
        
        bottomSheet.show()
    }
    
    /**
     * Setup LCV dialog with category headers (LCV Open, LCV Container)
     * Clicking a category expands to show its length subtypes
     */
    private fun setupLcvCategoryDialog(
        view: View,
        config: TruckConfig,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        scrollView: androidx.core.widget.NestedScrollView?
    ) {
        val subtypesContainer = view.findViewById<LinearLayout>(R.id.subtypesContainer)
        
        // LCV Open section references
        val lcvOpenTitle = view.findViewById<TextView>(R.id.lcvOpenSectionTitle)
        val lcvOpenContainer = view.findViewById<LinearLayout>(R.id.lcvOpenSubtypesContainer)
        
        // LCV Container section references
        val lcvContainerTitle = view.findViewById<TextView>(R.id.lcvContainerSectionTitle)
        val lcvContainerContainer = view.findViewById<LinearLayout>(R.id.lcvContainerSubtypesContainer)
        
        // Track which category is expanded
        var expandedCategory: String? = null
        
        // Build category cards grid
        subtypesContainer?.let { container ->
            // Clear any existing views first to prevent duplicates
            container.removeAllViews()
            
            val gridLayout = GridLayout(this)
            gridLayout.columnCount = 2
            gridLayout.rowCount = 1
            
            config.subtypes.forEach { categoryName ->
                val card = layoutInflater.inflate(R.layout.item_truck_subtype, null)
                card.findViewById<TextView>(R.id.subtypeName)?.text = categoryName
                card.findViewById<ImageView>(R.id.subtypeIcon)?.setImageResource(getTruckIconResource("lcv"))
                
                // Hide quantity controls - these are category headers, not selectable
                card.findViewById<LinearLayout>(R.id.quantityControls)?.visibility = View.GONE
                
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(4, 4, 4, 4)
                }
                card.layoutParams = params
                
                val cardContent = card.findViewById<LinearLayout>(R.id.cardContent)
                val subtypeCard = card.findViewById<CardView>(R.id.subtypeCard)
                
                // Category click handler - expand to show subtypes
                subtypeCard?.isClickable = true
                subtypeCard?.isFocusable = true
                subtypeCard?.setOnClickListener {
                    val isCurrentlyExpanded = expandedCategory == categoryName
                    
                    // Collapse all sections first
                    lcvOpenTitle?.visibility = View.GONE
                    lcvOpenContainer?.visibility = View.GONE
                    lcvContainerTitle?.visibility = View.GONE
                    lcvContainerContainer?.visibility = View.GONE
                    
                    // Clear containers when collapsing to ensure fresh state
                    lcvOpenContainer?.removeAllViews()
                    lcvContainerContainer?.removeAllViews()
                    
                    // Reset all category card backgrounds
                    for (i in 0 until gridLayout.childCount) {
                        val categoryCard = gridLayout.getChildAt(i)
                        categoryCard.findViewById<LinearLayout>(R.id.cardContent)
                            ?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
                    }
                    
                    if (!isCurrentlyExpanded) {
                        // Expand this category
                        expandedCategory = categoryName
                        cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
                        
                        when (categoryName) {
                            "LCV Open" -> {
                                lcvOpenTitle?.visibility = View.VISIBLE
                                lcvOpenContainer?.visibility = View.VISIBLE
                                
                                // Always refresh the container to ensure proper state
                                lcvOpenContainer?.removeAllViews()
                                val lengths = config.subtypeLengths["LCV Open"] ?: emptyList()
                                // Pass category name to create unique keys
                                addLengthSubtypesToContainer(lcvOpenContainer, lengths, "lcv", "LCV Open", selectedSubtypes, vehicleCard, view)
                                
                                // Scroll to show the subtypes
                                scrollView?.post {
                                    scrollView.smoothScrollTo(0, lcvOpenTitle?.top ?: 0)
                                }
                            }
                            "LCV Container" -> {
                                lcvContainerTitle?.visibility = View.VISIBLE
                                lcvContainerContainer?.visibility = View.VISIBLE
                                
                                // Always refresh the container to ensure proper state
                                lcvContainerContainer?.removeAllViews()
                                val lengths = config.subtypeLengths["LCV Container"] ?: emptyList()
                                // Pass category name to create unique keys
                                addLengthSubtypesToContainer(lcvContainerContainer, lengths, "lcv", "LCV Container", selectedSubtypes, vehicleCard, view)
                                
                                // Scroll to show the subtypes
                                scrollView?.post {
                                    scrollView.smoothScrollTo(0, lcvContainerTitle?.top ?: 0)
                                }
                            }
                        }
                    } else {
                        // Collapse - already done above
                        expandedCategory = null
                    }
                }
                
                gridLayout.addView(card)
            }
            
            container.addView(gridLayout)
        }
    }
    
    /**
     * Add length subtype cards to a container
     * @param categoryPrefix Optional prefix for unique key generation (e.g., "LCV Open" or "LCV Container")
     */
    private fun addLengthSubtypesToContainer(
        container: LinearLayout?,
        lengths: List<String>,
        truckTypeId: String,
        categoryPrefix: String,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        parentView: View
    ) {
        // Safety check for null container
        if (container == null) return
        
        // Clear container first to prevent stacking issues causing touch problems
        container.removeAllViews()
        
        val gridLayout = GridLayout(this)
        gridLayout.columnCount = 3 // 3 columns for lengths
        gridLayout.rowCount = (lengths.size + 2) / 3
        
        lengths.forEach { lengthSubtype ->
            // Use unique key format: "CategoryPrefix|Length" to prevent mixing
            addLengthSubtypeCard(gridLayout, lengthSubtype, truckTypeId, categoryPrefix, selectedSubtypes, vehicleCard, parentView)
        }
        
        container.addView(gridLayout)
    }
    
    /**
     * Add a length subtype card with unique key based on category prefix
     * This prevents LCV Open and LCV Container from sharing selections
     */
    private fun addLengthSubtypeCard(
        gridLayout: GridLayout,
        displayName: String,
        truckTypeId: String,
        categoryPrefix: String,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        parentView: View
    ) {
        // Unique key: "LCV Open|17 Feet" or "LCV Container|17 Feet"
        val uniqueKey = "$categoryPrefix|$displayName"
        
        val card = layoutInflater.inflate(R.layout.item_truck_subtype, null)
        card.findViewById<TextView>(R.id.subtypeName)?.text = displayName // Show just the length to user
        
        // Set the truck icon based on truck type
        val iconView = card.findViewById<ImageView>(R.id.subtypeIcon)
        iconView?.setImageResource(getTruckIconResource(truckTypeId))
        
        // Adjust image padding based on truck type for better visibility
        // Negative padding makes image 50% larger for specific types
        val paddingDp = when (truckTypeId) {
            "dumper" -> -10   // Dumper: 50% larger (negative padding)
            "tanker" -> -10   // Tanker: 50% larger
            "container" -> -10 // Container: 50% larger
            "mini" -> -10     // Mini: 50% larger
            "lcv" -> 1        // LCV: keep current size (slightly smaller)
            else -> 0         // All others: maximum size (no padding)
        }
        val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
        iconView?.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(4, 4, 4, 4)
        }
        card.layoutParams = params
        
        val cardContent = card.findViewById<LinearLayout>(R.id.cardContent)
        val quantityControls = card.findViewById<LinearLayout>(R.id.quantityControls)
        val quantityText = card.findViewById<TextView>(R.id.quantityText)
        val plusButton = card.findViewById<TextView>(R.id.plusButton)
        val minusButton = card.findViewById<TextView>(R.id.minusButton)
        val subtypeCard = card.findViewById<CardView>(R.id.subtypeCard)
        
        // Restore already-selected state using unique key
        val existingQty = selectedSubtypes[uniqueKey]
        if (existingQty != null && existingQty > 0) {
            quantityControls?.visibility = View.VISIBLE
            quantityText?.text = existingQty.toString()
            cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
        }
        
        // Ensure card is clickable and focusable
        subtypeCard?.isClickable = true
        subtypeCard?.isFocusable = true
        
        // Card click handler
        subtypeCard?.setOnClickListener {
            val currentQty = selectedSubtypes[uniqueKey] ?: 0
            if (currentQty == 0) {
                selectedSubtypes[uniqueKey] = 1
                quantityControls?.visibility = View.VISIBLE
                quantityText?.text = "1"
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
                vehicleCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
            } else {
                selectedSubtypes[uniqueKey] = currentQty + 1
                quantityText?.text = "${currentQty + 1}"
            }
            updateConfirmButton(parentView, selectedSubtypes)
        }
        
        // Plus button - ensure clickable
        plusButton?.isClickable = true
        plusButton?.isFocusable = true
        plusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[uniqueKey] ?: 0
            if (currentQty == 0) {
                // First click on plus - initialize selection
                selectedSubtypes[uniqueKey] = 1
                quantityControls?.visibility = View.VISIBLE
                quantityText?.text = "1"
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
                vehicleCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
            } else {
                selectedSubtypes[uniqueKey] = currentQty + 1
                quantityText?.text = "${currentQty + 1}"
            }
            updateConfirmButton(parentView, selectedSubtypes)
        }
        
        // Minus button - ensure clickable
        minusButton?.isClickable = true
        minusButton?.isFocusable = true
        minusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[uniqueKey] ?: 0
            if (currentQty > 1) {
                selectedSubtypes[uniqueKey] = currentQty - 1
                quantityText?.text = "${currentQty - 1}"
                updateConfirmButton(parentView, selectedSubtypes)
            } else if (currentQty == 1) {
                selectedSubtypes.remove(uniqueKey)
                quantityControls?.visibility = View.GONE
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
                if (selectedSubtypes.isEmpty()) {
                    vehicleCard?.setCardBackgroundColor(getColor(android.R.color.white))
                    if (truckSelections.values.all { it.isEmpty() }) {
                        continueButton.visibility = View.GONE
                    }
                }
                updateConfirmButton(parentView, selectedSubtypes)
            }
        }
        
        gridLayout.addView(card)
    }
    
    /**
     * Helper function to add a subtype card to a grid
     * Handles selection styling with proper background drawables
     */
    private fun addSubtypeCard(
        gridLayout: GridLayout,
        subtype: String,
        truckTypeId: String,
        selectedSubtypes: MutableMap<String, Int>,
        vehicleCard: CardView?,
        parentView: View
    ) {
        val card = layoutInflater.inflate(R.layout.item_truck_subtype, null)
        card.findViewById<TextView>(R.id.subtypeName)?.text = subtype
        
        // Set the truck icon based on truck type
        val iconView = card.findViewById<ImageView>(R.id.subtypeIcon)
        iconView?.setImageResource(getTruckIconResource(truckTypeId))
        
        // Adjust image padding based on truck type for better visibility
        // Negative padding makes image 50% larger for specific types
        val paddingDp = when (truckTypeId) {
            "dumper" -> -10   // Dumper: 50% larger (negative padding)
            "tanker" -> -10   // Tanker: 50% larger
            "container" -> -10 // Container: 50% larger
            "mini" -> -10     // Mini: 50% larger
            "lcv" -> 1        // LCV: keep current size (slightly smaller)
            else -> 0         // All others: maximum size (no padding)
        }
        val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
        iconView?.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(4, 4, 4, 4)
        }
        card.layoutParams = params
        
        val cardContent = card.findViewById<LinearLayout>(R.id.cardContent)
        val quantityControls = card.findViewById<LinearLayout>(R.id.quantityControls)
        val quantityText = card.findViewById<TextView>(R.id.quantityText)
        val plusButton = card.findViewById<TextView>(R.id.plusButton)
        val minusButton = card.findViewById<TextView>(R.id.minusButton)
        val subtypeCard = card.findViewById<CardView>(R.id.subtypeCard)
        
        // IMPORTANT: Restore already-selected state when card is created
        val existingQty = selectedSubtypes[subtype]
        if (existingQty != null && existingQty > 0) {
            quantityControls?.visibility = View.VISIBLE
            quantityText?.text = existingQty.toString()
            cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
        }
        
        // Ensure card is clickable and focusable for proper touch handling
        subtypeCard?.isClickable = true
        subtypeCard?.isFocusable = true
        
        // Card click handler - works for both new selection AND increment
        subtypeCard?.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            if (currentQty == 0) {
                // New selection
                selectedSubtypes[subtype] = 1
                quantityControls?.visibility = View.VISIBLE
                quantityText?.text = "1"
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
                vehicleCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
            } else {
                // Already selected - increment quantity
                selectedSubtypes[subtype] = currentQty + 1
                quantityText?.text = "${currentQty + 1}"
            }
            updateConfirmButton(parentView, selectedSubtypes)
        }
        
        // Plus button - ensure clickable
        plusButton?.isClickable = true
        plusButton?.isFocusable = true
        plusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            if (currentQty == 0) {
                // First click on plus - initialize selection
                selectedSubtypes[subtype] = 1
                quantityControls?.visibility = View.VISIBLE
                quantityText?.text = "1"
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_selected)
                vehicleCard?.setCardBackgroundColor(getColor(android.R.color.holo_blue_light))
                continueButton.visibility = View.VISIBLE
            } else {
                selectedSubtypes[subtype] = currentQty + 1
                quantityText?.text = "${currentQty + 1}"
            }
            updateConfirmButton(parentView, selectedSubtypes)
        }
        
        // Minus button - ensure clickable
        minusButton?.isClickable = true
        minusButton?.isFocusable = true
        minusButton?.setOnClickListener {
            val currentQty = selectedSubtypes[subtype] ?: 0
            if (currentQty > 1) {
                selectedSubtypes[subtype] = currentQty - 1
                quantityText?.text = "${currentQty - 1}"
                updateConfirmButton(parentView, selectedSubtypes)
            } else if (currentQty == 1) {
                selectedSubtypes.remove(subtype)
                quantityControls?.visibility = View.GONE
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
                if (selectedSubtypes.isEmpty()) {
                    vehicleCard?.setCardBackgroundColor(getColor(android.R.color.white))
                    if (truckSelections.values.all { it.isEmpty() }) {
                        continueButton.visibility = View.GONE
                    }
                }
                updateConfirmButton(parentView, selectedSubtypes)
            }
        }
        
        gridLayout.addView(card)
    }
    
    /**
     * Helper function to get vehicle card by truck type ID
     */
    private fun getVehicleCardById(truckTypeId: String): CardView? {
        return when (truckTypeId) {
            "open" -> openCard
            "container" -> containerCard
            "lcv" -> lcvCard
            "mini" -> miniCard
            "trailer" -> trailerCard
            "tipper" -> tipperCard
            "tanker" -> tankerCard
            "dumper" -> dumperCard
            "bulker" -> bulkerCard
            else -> null
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            if (state.vehicles.isNotEmpty()) {
                displayVehicles(state.vehicles)
            }

            state.errorMessage?.let { error ->
                showToast(error)
                viewModel.clearError()
            }
        }

        viewModel.navigationEvent.observe(this) { event ->
            when (event) {
                is TruckTypesNavigationEvent.NavigateToPricing -> {
                    // Payment/Booking screen removed - will be added later
                    Toast.makeText(
                        this,
                        "Booking confirmed! Payment feature coming soon.",
                        Toast.LENGTH_LONG
                    ).show()
                    // TODO: Navigate to payment/booking confirmation screen when ready
                }
            }
        }
    }
    
    private fun displayVehicles(vehicles: List<VehicleModel>) {
        vehicles.forEach { vehicle ->
            when (vehicle.id) {
                "open" -> openCard?.let { updateCardInfo(it, vehicle) }
                "container" -> containerCard?.let { updateCardInfo(it, vehicle) }
                "lcv" -> lcvCard?.let { updateCardInfo(it, vehicle) }
                "mini" -> miniCard?.let { updateCardInfo(it, vehicle) }
                "trailer" -> trailerCard?.let { updateCardInfo(it, vehicle) }
                "tipper" -> tipperCard?.let { updateCardInfo(it, vehicle) }
                "tanker" -> tankerCard?.let { updateCardInfo(it, vehicle) }
                "dumper" -> dumperCard?.let { updateCardInfo(it, vehicle) }
                "bulker" -> bulkerCard?.let { updateCardInfo(it, vehicle) }
            }
        }
        
        listOf(openCard, containerCard, lcvCard, miniCard, trailerCard,
               tipperCard, tankerCard, dumperCard, bulkerCard).forEach { card ->
            card?.visibility = View.VISIBLE
        }
    }
    
    private fun updateCardInfo(card: CardView, vehicle: VehicleModel) {
        // Update card info if needed - IDs might vary based on layout
        // This is a helper function for future use
    }
    
    private fun updateConfirmButton(view: View, subtypesMap: Map<String, Int>) {
        val totalCount = subtypesMap.values.sum()
        view.findViewById<Button>(R.id.confirmSelectionButton)?.text = 
            if (totalCount > 0) "CONFIRM ($totalCount)" else "CONFIRM"
    }
    
    private fun resetBottomSheetSelections(view: View, vehicleType: String) {
        val selectedSubtypes = truckSelections[vehicleType] ?: return
        val vehicleCard = getVehicleCardById(vehicleType)
        
        selectedSubtypes.clear()
        vehicleCard?.setCardBackgroundColor(getColor(android.R.color.white))
        
        if (truckSelections.values.all { it.isEmpty() }) {
            continueButton.visibility = View.GONE
        }
        
        // Reset main subtypes container
        resetGridInContainer(view.findViewById(R.id.subtypesContainer))
        
        // Reset length subtypes container
        resetGridInContainer(view.findViewById(R.id.lengthSubtypesContainer))
        
        // Reset LCV Open and LCV Container containers
        view.findViewById<LinearLayout>(R.id.lcvOpenSubtypesContainer)?.let { container ->
            container.removeAllViews()
            container.visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.lcvOpenSectionTitle)?.visibility = View.GONE
        
        view.findViewById<LinearLayout>(R.id.lcvContainerSubtypesContainer)?.let { container ->
            container.removeAllViews()
            container.visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.lcvContainerSectionTitle)?.visibility = View.GONE
        
        updateConfirmButton(view, selectedSubtypes)
    }
    
    /**
     * Helper function to reset all cards in a grid container
     */
    private fun resetGridInContainer(container: LinearLayout?) {
        val gridLayout = container?.getChildAt(0) as? GridLayout
        gridLayout?.let { grid ->
            for (i in 0 until grid.childCount) {
                val card = grid.getChildAt(i)
                val cardContent = card.findViewById<LinearLayout>(R.id.cardContent)
                val quantityControls = card.findViewById<LinearLayout>(R.id.quantityControls)
                
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
                quantityControls?.visibility = View.GONE
            }
        }
    }
    
    /**
     * Navigate to pricing screen with selected vehicle
     * REMOVED: Payment screen temporarily removed
     */
    private fun navigateToPricing(vehicleId: String) {
        // Payment/Booking screen removed - will be added later
        Toast.makeText(
            this,
            "Booking confirmed! Payment feature coming soon.",
            Toast.LENGTH_LONG
        ).show()
        // TODO: Navigate to payment/booking confirmation screen when ready
        // val intent = Intent(this, PricingActivity::class.java)
    }
    
    /**
     * Get the truck icon resource based on truck type ID
     * Returns clean vector/png truck icons for subtypes dialog
     * Uses the new uploaded clean line-art icons
     */
    private fun getTruckIconResource(truckTypeId: String): Int {
        return when (truckTypeId) {
            "open" -> R.drawable.ic_truck_open
            "container" -> R.drawable.ic_truck_container_subtype  // New clean container icon
            "lcv" -> R.drawable.ic_lcv_clean
            "mini" -> R.drawable.ic_truck_mini_subtype            // NEW: Updated mini subtype image
            "trailer" -> R.drawable.ic_truck_trailer_subtype      // New clean trailer icon
            "tipper" -> R.drawable.ic_truck_tipper_subtype        // NEW: Updated tipper subtype image
            "tanker" -> R.drawable.ic_truck_tanker_subtype        // NEW: Updated tanker subtype image
            "dumper" -> R.drawable.ic_truck_dumper_subtype        // New clean dumper icon
            "bulker" -> R.drawable.ic_truck_bulker_subtype        // New clean bulker icon
            else -> R.drawable.ic_truck_open
        }
    }
}

