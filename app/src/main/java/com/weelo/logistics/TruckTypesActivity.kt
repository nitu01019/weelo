package com.weelo.logistics

import timber.log.Timber
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
import androidx.recyclerview.widget.GridLayoutManager
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
import com.weelo.logistics.presentation.trucks.TruckPricingHelper
import com.weelo.logistics.presentation.trucks.TruckTypesNavigationEvent
import com.weelo.logistics.presentation.trucks.TruckTypesViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.weelo.logistics.data.repository.PricingRepository
import com.weelo.logistics.adapters.TruckSubtypeAdapter
import com.weelo.logistics.adapters.SubtypeItem
import com.weelo.logistics.adapters.TruckTypePickerAdapter
import com.weelo.logistics.adapters.TruckTypePickerItem
import com.weelo.logistics.presentation.booking.BookingConfirmationActivity
import com.weelo.logistics.presentation.booking.BookingTrackingActivity
import com.weelo.logistics.ui.dialogs.SearchingVehiclesDialog
import com.weelo.logistics.adapters.SubtypeWithQuantityAdapter
import com.weelo.logistics.adapters.SubtypeQuantityItem
import android.widget.ViewFlipper
import android.widget.EditText
import android.widget.ProgressBar

/**
 * TruckTypesActivity - Production-Ready Truck Selection Screen
 */
@AndroidEntryPoint
class TruckTypesActivity : AppCompatActivity() {

    @Inject
    lateinit var pricingRepository: PricingRepository
    
    private val viewModel: TruckTypesViewModel by viewModels()
    
    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var routeText: TextView
    // Continue button removed - flow now goes directly from bottom sheet CONFIRM to search dialog
    
    // Toggle Components
    private lateinit var toggleTrucksButton: CardView
    private lateinit var toggleIcon: TextView
    private lateinit var trucksContainer: LinearLayout
    private var areTrucksVisible = true
    
    // Selected Trucks Section - REMOVED (selection now handled in bottom sheet only)
    // Variables removed: selectedTrucksSection, selectedTrucksRecyclerView, emptyStateText, truckCountText, selectedTrucksAdapter
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
    
    // CRITICAL: Intermediate stops for order creation
    // SCALABILITY: Passed through the entire chain: LocationInput → Map → TruckTypes → SearchDialog → API
    private var intermediateStopLocations: Array<Location> = emptyArray()
    
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
        
        // CRITICAL: Receive intermediate stops from MapBookingActivity
        // SCALABILITY: Supports Location[] (with coordinates) or String[] (addresses only)
        @Suppress("DEPRECATION")
        val stopsFromIntent = intent.getParcelableArrayExtra("INTERMEDIATE_STOPS_LOCATIONS")
        if (stopsFromIntent != null && stopsFromIntent.isNotEmpty()) {
            intermediateStopLocations = stopsFromIntent.filterIsInstance<Location>().toTypedArray()
            timber.log.Timber.d("Received ${intermediateStopLocations.size} intermediate stops with coordinates")
        } else {
            // Fallback: Create Location objects from address strings (no coordinates)
            val addressStops = intent.getStringArrayExtra("INTERMEDIATE_STOPS")
            if (addressStops != null && addressStops.isNotEmpty()) {
                intermediateStopLocations = addressStops.map { Location(it) }.toTypedArray()
                timber.log.Timber.d("Received ${intermediateStopLocations.size} intermediate stops (addresses only)")
            }
        }

        setupViews()
        // setupSelectedTrucksSection() - REMOVED (selection now handled in bottom sheet only)
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
        
        // Setup Instant/Custom booking toggle
        setupBookingTypeToggle()

        routeText.text = "${capitalizeFirstWord(fromLocation.address)} → ${capitalizeFirstWord(toLocation.address)}"
    }
    
    /**
     * Setup Instant/Custom booking toggle handlers
     * - Instant tab: Stays on this screen (default)
     * - Custom tab: Opens CustomBookingActivity for long-term contracts
     */
    private fun setupBookingTypeToggle() {
        val instantTab = findViewById<TextView>(R.id.instantTab)
        val customTab = findViewById<TextView>(R.id.customTab)
        
        // Instant tab click - just update UI (already on instant)
        instantTab?.setOnClickListener {
            instantTab.setBackgroundResource(R.drawable.bg_toggle_selected)
            instantTab.setTextColor(resources.getColor(android.R.color.white, null))
            customTab?.background = null
            customTab?.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        
        // Custom tab click - navigate to LocationInputActivity for custom booking
        // FIXED: Previously just called finish() which closed to wrong screen
        customTab?.setOnClickListener {
            // Show dialog explaining custom booking flow
            android.app.AlertDialog.Builder(this)
                .setTitle("Start Custom Booking?")
                .setMessage("Custom booking requires selecting your pickup location first. Go to location page?")
                .setPositiveButton("Go to Location") { _, _ ->
                    // FIXED: Navigate TO LocationInputActivity explicitly with CUSTOM mode
                    val intent = Intent(this, LocationInputActivity::class.java).apply {
                        putExtra("BOOKING_MODE", "CUSTOM")
                        // Clear back stack so user doesn't return to TruckTypes
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    TransitionHelper.applySlideOutRightTransition(this)
                    finish()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    /**
     * Capitalize the first letter of each word in an address
     */
    private fun capitalizeFirstWord(address: String): String {
        return address.trim().split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
        }
    }
    
    // setupSelectedTrucksSection() - REMOVED (selection now handled in bottom sheet only)
    
    // updateEmptyState() and updateTruckCount() - REMOVED (selection now handled in bottom sheet only)
    
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
        
        // Update adapter and empty state - REMOVED (selection now handled in bottom sheet only)
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
            showMultiTruckSelectionSheet(vehicleId)
        }
    }
    
    /**
     * Show the new Rapido-style multi-truck selection bottom sheet
     */
    private fun showMultiTruckSelectionSheet(initialTruckTypeId: String) {
        val distanceKm = calculateDistanceKm()
        
        val bottomSheet = com.weelo.logistics.ui.dialogs.MultiTruckSelectionBottomSheet.newInstance(
            initialTruckTypeId = initialTruckTypeId,
            distanceKm = distanceKm
        )
        
        // Handle confirmation - show search dialog
        bottomSheet.setOnConfirmListener { selections, totalPrice ->
            // Convert selections to SelectedTruckItem list for search dialog
            val trucksForDialog = ArrayList<SelectedTruckItem>()
            selections.forEach { truckType ->
                truckType.subtypes.forEach { subtype ->
                    trucksForDialog.add(SelectedTruckItem(
                        id = "${truckType.truckTypeId}_${subtype.subtypeId}",
                        truckTypeId = truckType.truckTypeId,
                        truckTypeName = truckType.truckTypeName,
                        specification = subtype.subtypeName,
                        iconResource = truckType.iconRes,
                        quantity = subtype.quantity
                    ))
                }
            }
            
            // Show the search dialog
            showSearchingVehiclesDialog(trucksForDialog, totalPrice, distanceKm)
        }
        
        // Handle add truck type - show simple picker dialog (no Step 2)
        bottomSheet.setOnAddTruckTypeListener { availableTypes ->
            showSimpleTruckTypePickerDialog(availableTypes) { selectedType ->
                bottomSheet.addTruckType(selectedType)
            }
        }
        
        bottomSheet.show(supportFragmentManager, "multi_truck_selection")
    }
    
    /**
     * Shows a SIMPLE truck type picker dialog - just grid selection
     * Used when adding truck types from MultiTruckSelectionBottomSheet
     * No Step 2 - just selects the truck type and closes
     */
    private fun showSimpleTruckTypePickerDialog(availableTypes: List<String>, onSelected: (String) -> Unit) {
        val dialog = BottomSheetDialog(this)
        
        val view = layoutInflater.inflate(R.layout.dialog_truck_type_picker, findViewById(android.R.id.content), false)
        dialog.setContentView(view)
        
        // Configure bottom sheet using helper for consistency and scalability
        com.weelo.logistics.core.util.BottomSheetHelper.configureBottomSheet(
            dialog = dialog,
            style = com.weelo.logistics.core.util.BottomSheetHelper.Style.FIXED_LARGE,
            isDismissable = true
        )
        
        // Hide Step 2 completely - we only need Step 1
        view.findViewById<View>(R.id.step2SubtypeSelection)?.visibility = View.GONE
        
        // Build truck type items with icons
        val allTruckTypeItems = availableTypes.map { typeId ->
            val config = TruckSubtypesConfig.getConfigById(typeId)
            TruckTypePickerItem(
                truckTypeId = typeId,
                displayName = config?.displayName ?: typeId,
                description = getTruckTypeDescription(typeId),
                iconRes = getTruckIconResource(typeId)
            )
        }
        
        // Setup grid with optimizations
        val gridRecyclerView = view.findViewById<RecyclerView>(R.id.truckTypesGridRecyclerView)
        gridRecyclerView.layoutManager = GridLayoutManager(this, 4) // 4-column grid
        
        val gridAdapter = TruckTypePickerAdapter(allTruckTypeItems) { selectedTypeId ->
            // Directly select and close - no Step 2
            dialog.dismiss()
            onSelected(selectedTypeId)
        }
        gridRecyclerView.adapter = gridAdapter
        // Do NOT setHasFixedSize(true) here: RecyclerView height is wrap_content in the bottom sheet.
        // Setting fixed size would be incorrect and triggers lint InvalidSetHasFixedSize.
        gridRecyclerView.setHasFixedSize(false)
        gridRecyclerView.itemAnimator = null // Disable animations for instant search
        
        // Search functionality
        val searchInput = view.findViewById<EditText>(R.id.searchTruckType)
        val clearSearchBtn = view.findViewById<ImageView>(R.id.clearSearchButton)
        
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                clearSearchBtn?.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                val filteredItems = if (query.isEmpty()) {
                    allTruckTypeItems
                } else {
                    allTruckTypeItems.filter { 
                        it.displayName.lowercase().contains(query) ||
                        it.description.lowercase().contains(query)
                    }
                }
                gridAdapter.updateItems(filteredItems)
            }
        })
        
        clearSearchBtn?.setOnClickListener {
            searchInput?.text?.clear()
        }
        
        dialog.show()
    }

    /**
     * Shows the Rapido-style multi-step truck type picker dialog
     * Step 1: Grid of truck types with search
     * Step 2: Subtype selection with quantity selectors
     * 
     * Features:
     * - Horizontal slide transitions between steps
     * - Search functionality to filter truck types
     * - Inline quantity selectors for subtypes
     * - All selection done within the dialog (no page navigation)
     */
    private fun showTruckTypePickerDialog(availableTypes: List<String>, onSelected: (String) -> Unit) {
        val dialog = BottomSheetDialog(this)
        
        val view = layoutInflater.inflate(R.layout.dialog_truck_type_picker, findViewById(android.R.id.content), false)
        dialog.setContentView(view)
        
        // Configure bottom sheet using helper for consistency and scalability
        com.weelo.logistics.core.util.BottomSheetHelper.configureBottomSheet(
            dialog = dialog,
            style = com.weelo.logistics.core.util.BottomSheetHelper.Style.FIXED_LARGE,
            isDismissable = true
        )
        
        // Get ViewFlipper for step transitions
        val viewFlipper = view.findViewById<ViewFlipper>(R.id.dialogViewFlipper)
        
        // Build truck type items with icons
        val allTruckTypeItems = availableTypes.map { typeId ->
            val config = TruckSubtypesConfig.getConfigById(typeId)
            TruckTypePickerItem(
                truckTypeId = typeId,
                displayName = config?.displayName ?: typeId,
                description = getTruckTypeDescription(typeId),
                iconRes = getTruckIconResource(typeId)
            )
        }
        
        // ==================== STEP 1: Truck Type Grid ====================
        val gridRecyclerView = view.findViewById<RecyclerView>(R.id.truckTypesGridRecyclerView)
        gridRecyclerView.layoutManager = GridLayoutManager(this, 4) // 4-column grid
        // Do NOT setHasFixedSize(true) here: RecyclerView height is wrap_content in the bottom sheet.
        // Setting fixed size would be incorrect and triggers lint InvalidSetHasFixedSize.
        gridRecyclerView.setHasFixedSize(false)
        gridRecyclerView.itemAnimator = null // Disable animations
        
        val gridAdapter = TruckTypePickerAdapter(allTruckTypeItems) { selectedTypeId ->
            // Transition to Step 2 with selected truck type
            showStep2SubtypeSelection(view, viewFlipper, selectedTypeId, dialog, onSelected)
        }
        gridRecyclerView.adapter = gridAdapter
        
        // Search functionality
        val searchInput = view.findViewById<EditText>(R.id.searchTruckType)
        val clearSearchBtn = view.findViewById<ImageView>(R.id.clearSearchButton)
        
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                clearSearchBtn?.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                // Filter truck types based on search query
                val filteredItems = if (query.isEmpty()) {
                    allTruckTypeItems
                } else {
                    allTruckTypeItems.filter { 
                        it.displayName.lowercase().contains(query) ||
                        it.description.lowercase().contains(query)
                    }
                }
                gridAdapter.updateItems(filteredItems)
            }
        })
        
        clearSearchBtn?.setOnClickListener {
            searchInput?.text?.clear()
        }
        
        dialog.show()
    }
    
    /**
     * Shows Step 2 of the dialog - Subtype selection with quantity
     * Slides in from right with animation
     */
    private fun showStep2SubtypeSelection(
        dialogView: View,
        viewFlipper: ViewFlipper,
        truckTypeId: String,
        dialog: BottomSheetDialog,
        onSelected: (String) -> Unit
    ) {
        // Set animations for forward navigation (slide left)
        viewFlipper.inAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_from_right)
        viewFlipper.outAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_to_left)
        
        // Update header with selected truck type name
        val config = TruckSubtypesConfig.getConfigById(truckTypeId)
        dialogView.findViewById<TextView>(R.id.selectedTruckTypeName)?.text = "${config?.displayName ?: truckTypeId} Trucks"
        
        // Setup subtypes RecyclerView with optimizations
        val subtypesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.subtypesRecyclerView)
        subtypesRecyclerView.layoutManager = LinearLayoutManager(this)
        // Do NOT setHasFixedSize(true) here: height is wrap_content in a bottom sheet.
        subtypesRecyclerView.setHasFixedSize(false)
        subtypesRecyclerView.itemAnimator = null // Disable animations for performance
        
        // Get subtypes for selected truck type and convert to SubtypeQuantityItem
        val subtypeStrings = config?.subtypes ?: emptyList()
        val subtypeItems = subtypeStrings.map { subtypeName ->
            // Get capacity info if available
            val capacityInfo = TruckSubtypesConfig.getCapacityInfo(truckTypeId, subtypeName)
            val capacityText = if (capacityInfo != null) {
                "${capacityInfo.minTonnage.toInt()}-${capacityInfo.maxTonnage.toInt()} Tons"
            } else {
                "Standard capacity"
            }
            SubtypeQuantityItem(
                id = subtypeName, // Use name as ID
                name = subtypeName,
                capacity = capacityText
            )
        }
        
        // Track quantities for each subtype
        val subtypeQuantities = mutableMapOf<String, Int>()
        subtypeItems.forEach { subtypeQuantities[it.id] = 0 }
        
        // Selection summary views
        val summaryLayout = dialogView.findViewById<LinearLayout>(R.id.selectionSummaryLayout)
        val selectedCountText = dialogView.findViewById<TextView>(R.id.selectedCountText)
        val addToBookingBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addToBookingButton)
        
        // Function to update summary
        fun updateSummary() {
            val totalSelected = subtypeQuantities.values.sum()
            if (totalSelected > 0) {
                summaryLayout?.visibility = View.VISIBLE
                selectedCountText?.text = "$totalSelected truck${if (totalSelected > 1) "s" else ""}"
                addToBookingBtn?.isEnabled = true
                addToBookingBtn?.alpha = 1f
            } else {
                summaryLayout?.visibility = View.GONE
                addToBookingBtn?.isEnabled = false
                addToBookingBtn?.alpha = 0.5f
            }
        }
        
        // Setup subtypes adapter with quantity callbacks
        subtypesRecyclerView.adapter = SubtypeWithQuantityAdapter(
            subtypes = subtypeItems,
            truckTypeIconRes = getTruckIconResource(truckTypeId),
            quantities = subtypeQuantities,
            onQuantityChanged = { subtypeId, newQty ->
                subtypeQuantities[subtypeId] = newQty
                updateSummary()
            }
        )
        
        // Initialize summary state
        updateSummary()
        
        // Back button - go back to Step 1
        dialogView.findViewById<ImageView>(R.id.backToStep1Button)?.setOnClickListener {
            // Set animations for back navigation (slide right)
            viewFlipper.inAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_from_left)
            viewFlipper.outAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_to_right)
            viewFlipper.showPrevious()
        }
        
        // Close button
        dialogView.findViewById<ImageView>(R.id.closeDialogButton)?.setOnClickListener {
            dialog.dismiss()
        }
        
        // Add to Booking button
        addToBookingBtn?.setOnClickListener {
            // Add selected trucks to the booking with correct quantity
            subtypeQuantities.forEach { (subtypeName, quantity) ->
                if (quantity > 0) {
                    // Find the subtype item for capacity info
                    val subtypeItem = subtypeItems.find { it.id == subtypeName }
                    addTruckToSelectionWithQuantity(truckTypeId, subtypeName, subtypeItem?.capacity ?: "", quantity)
                }
            }
            
            // Update empty state after adding - REMOVED (selection now handled in bottom sheet only)
            
            dialog.dismiss()
            onSelected(truckTypeId)
            
            // Show confirmation toast
            val totalAdded = subtypeQuantities.values.sum()
            if (totalAdded > 0) {
                showToast("$totalAdded truck${if (totalAdded > 1) "s" else ""} added to booking")
            }
        }
        
        // Show Step 2
        viewFlipper.showNext()
    }
    
    /**
     * Helper function to add a truck to the current selection
     * Uses the existing selectedTrucksList and updates the UI
     */
    private fun addTruckToSelection(truckTypeId: String, subtypeName: String, capacity: String) {
        addTruckToSelectionWithQuantity(truckTypeId, subtypeName, capacity, 1)
    }
    
    /**
     * Helper function to add a truck with specific quantity to the current selection
     * Uses the existing selectedTrucksList and updates the UI
     */
    @Suppress("UNUSED_PARAMETER") // capacity reserved for future use
    private fun addTruckToSelectionWithQuantity(truckTypeId: String, subtypeName: String, capacity: String, quantity: Int) {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId)
        val displayName = config?.displayName ?: truckTypeId
        
        // Check if same truck type + specification already exists
        val existingIndex = selectedTrucksList.indexOfFirst { 
            it.truckTypeId == truckTypeId && it.specification == subtypeName 
        }
        
        if (existingIndex >= 0) {
            // Update quantity of existing item
            selectedTrucksList[existingIndex].quantity = quantity
        } else {
            // Add new item with specified quantity
            val newItem = SelectedTruckItem(
                id = "${truckTypeId}_${subtypeName}_${System.currentTimeMillis()}",
                truckTypeId = truckTypeId,
                truckTypeName = displayName,
                specification = subtypeName,
                iconResource = getTruckIconResource(truckTypeId),
                quantity = quantity
            )
            selectedTrucksList.add(newItem)
        }
        
        // Update the adapter with new list - REMOVED (selection now handled in bottom sheet only)
    }
    
    /**
     * Get description text for each truck type - delegates to TruckPricingHelper
     */
    private fun getTruckTypeDescription(truckTypeId: String): String {
        return TruckPricingHelper.getTruckTypeDescription(truckTypeId)
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
        val distanceKm = calculateDistanceKm()
        
        val bottomSheet = BottomSheetDialog(this)
        
        val view = layoutInflater.inflate(R.layout.bottom_sheet_truck_subtypes, findViewById(android.R.id.content), false)
        bottomSheet.setContentView(view)
        
        // Configure bottom sheet using helper for consistency and scalability
        com.weelo.logistics.core.util.BottomSheetHelper.configureBottomSheet(
            dialog = bottomSheet,
            style = com.weelo.logistics.core.util.BottomSheetHelper.Style.FIXED_LARGE,
            isDismissable = true
        )
        
        // Setup header
        view.findViewById<TextView>(R.id.bottomSheetTitle)?.text = config.displayName
        
        // Set header icon - same as truck type card icon
        val headerIconRes = getTruckIconResource(truckTypeId)
        view.findViewById<ImageView>(R.id.bottomSheetTruckIcon)?.setImageResource(headerIconRes)
        
        // Close button
        view.findViewById<ImageView>(R.id.closeButton)?.setOnClickListener {
            bottomSheet.dismiss()
        }
        
        // Get UI components
        val recyclerView = view.findViewById<RecyclerView>(R.id.subtypesRecyclerView)
        val selectionSummary = view.findViewById<LinearLayout>(R.id.selectionSummary)
        val selectedCountText = view.findViewById<TextView>(R.id.selectedCountText)
        val totalPriceText = view.findViewById<TextView>(R.id.totalPriceText)
        val confirmButton = view.findViewById<Button>(R.id.confirmSelectionButton)
        val clearAllButton = view.findViewById<Button>(R.id.clearAllButton)
        val deselectAllButton = view.findViewById<TextView>(R.id.deselectAllButton)
        
        // Build subtype items with pricing
        val subtypeItems = mutableListOf<SubtypeItem>()
        val iconRes = getTruckIconResource(truckTypeId)
        
        // Get capacity info for subtypes
        val capacities = TruckSubtypesConfig.getAllCapacitiesForType(truckTypeId)
        
        config.subtypes.forEach { subtypeName ->
            // subtypeName is a String like "17 Feet", "19 Feet" etc.
            val subtypeId = subtypeName.lowercase().replace(" ", "_")
            val capacityInfo = capacities[subtypeName]
            val capacityText = if (capacityInfo != null) {
                "${capacityInfo.minTonnage} - ${capacityInfo.maxTonnage} Ton"
            } else {
                ""
            }
            
            // Calculate base price for this subtype
            val basePrice = calculateBasePrice(truckTypeId, subtypeId, distanceKm)
            
            subtypeItems.add(
                SubtypeItem(
                    id = subtypeId,
                    name = subtypeName,
                    capacity = capacityText,
                    price = basePrice,
                    iconRes = iconRes,
                    initialQuantity = selectedSubtypes[subtypeName] ?: 0
                )
            )
        }
        
        // Create adapter with quantity change callback
        var adapter: TruckSubtypeAdapter? = null
        adapter = TruckSubtypeAdapter(subtypeItems) { subtypeId, quantity, _ ->
            // Find the original subtype name
            val subtypeName = subtypeItems.find { it.id == subtypeId }?.name ?: subtypeId
            selectedSubtypes[subtypeName] = quantity
            
            // Update UI (safe null check - adapter is guaranteed non-null in callback)
            adapter?.let { safeAdapter ->
                updateSelectionSummary(
                    selectionSummary, 
                    selectedCountText, 
                    totalPriceText,
                    confirmButton,
                    deselectAllButton,
                    safeAdapter
                )
            }
        }
        
        // Hide old scrollView/subtypesContainer, show RecyclerView
        view.findViewById<View>(R.id.scrollView)?.visibility = View.GONE
        recyclerView?.visibility = View.VISIBLE
        
        // Setup RecyclerView with optimizations
        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = adapter
        // Do NOT setHasFixedSize(true) here: height is wrap_content in a bottom sheet.
        recyclerView?.setHasFixedSize(false)
        recyclerView?.itemAnimator = null // Disable animations for performance
        
        // Initial UI state
        updateSelectionSummary(selectionSummary, selectedCountText, totalPriceText, confirmButton, deselectAllButton, adapter)
        
        // Confirm button
        confirmButton?.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                // Map back to original subtype names and save selections
                val namedSelections = mutableMapOf<String, Int>()
                selected.forEach { (subtypeId, qty) ->
                    val subtypeName = subtypeItems.find { it.id == subtypeId }?.name ?: subtypeId
                    namedSelections[subtypeName] = qty
                    selectedSubtypes[subtypeName] = qty
                }
                
                // Add to selected trucks list
                addToSelectedTrucks(truckTypeId, namedSelections)
                
                // Update vehicle card checkmark
                vehicleCard?.let { card ->
                    card.findViewById<View>(R.id.selectedBadge)?.visibility = View.VISIBLE
                }
                
                bottomSheet.dismiss()
                
                @Suppress("UNUSED_VARIABLE") // Reserved for future analytics
                val totalTrucks = selected.values.sum()
                
                // Build trucks list for search dialog
                val trucksForDialog = ArrayList<SelectedTruckItem>()
                selected.forEach { (subtypeId, qty) ->
                    val subtypeInfo = subtypeItems.find { it.id == subtypeId }
                    trucksForDialog.add(SelectedTruckItem(
                        id = "${truckTypeId}_${subtypeId}",
                        truckTypeId = truckTypeId,
                        truckTypeName = config.displayName,
                        specification = subtypeInfo?.name ?: subtypeId,
                        iconResource = getTruckIconResource(truckTypeId),
                        quantity = qty
                    ))
                }
                
                // Calculate total price
                val totalPrice = adapter.getTotalPrice()
                
                // Show searching vehicles dialog directly
                showSearchingVehiclesDialog(trucksForDialog, totalPrice, distanceKm)
                
            } else {
                showToast("Please select at least one truck")
            }
        }
        
        // Clear All button
        clearAllButton?.setOnClickListener {
            adapter.clearAll()
            config.subtypes.forEach { selectedSubtypes[it] = 0 }
            updateSelectionSummary(selectionSummary, selectedCountText, totalPriceText, confirmButton, deselectAllButton, adapter)
        }
        
        // Deselect All in header
        deselectAllButton?.setOnClickListener {
            adapter.clearAll()
            config.subtypes.forEach { selectedSubtypes[it] = 0 }
            updateSelectionSummary(selectionSummary, selectedCountText, totalPriceText, confirmButton, deselectAllButton, adapter)
        }
        
        // Fetch real pricing from backend
        fetchPricingForSubtypes(truckTypeId, subtypeItems, adapter, distanceKm)
        
        bottomSheet.show()
    }
    
    private fun updateSelectionSummary(
        summaryLayout: LinearLayout?,
        countText: TextView?,
        priceText: TextView?,
        confirmButton: Button?,
        deselectButton: TextView?,
        adapter: TruckSubtypeAdapter
    ) {
        val totalCount = adapter.getTotalCount()
        val totalPrice = adapter.getTotalPrice()
        
        if (totalCount > 0) {
            summaryLayout?.visibility = View.VISIBLE
            deselectButton?.visibility = View.VISIBLE
            countText?.text = "$totalCount truck${if (totalCount > 1) "s" else ""} selected"
            priceText?.text = "₹${String.format("%,d", totalPrice)}"
            confirmButton?.isEnabled = true
            confirmButton?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
        } else {
            summaryLayout?.visibility = View.GONE
            deselectButton?.visibility = View.GONE
            confirmButton?.isEnabled = false
            confirmButton?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFAAAAAA.toInt())
        }
    }
    
    private fun calculateBasePrice(truckTypeId: String, subtypeId: String, distanceKm: Int): Int {
        return TruckPricingHelper.calculateBasePrice(truckTypeId, subtypeId, distanceKm)
    }
    
    private fun fetchPricingForSubtypes(
        truckTypeId: String,
        subtypeItems: MutableList<SubtypeItem>,
        adapter: TruckSubtypeAdapter,
        distanceKm: Int
    ) {
        lifecycleScope.launch {
            subtypeItems.forEachIndexed { index, item ->
                try {
                    val result = pricingRepository.getEstimate(
                        vehicleType = truckTypeId,
                        vehicleSubtype = item.name,
                        distanceKm = distanceKm,
                        trucksNeeded = 1
                    )
                    
                    if (result is com.weelo.logistics.core.common.Result.Success) {
                        // Update price in the item
                        subtypeItems[index] = item.copy(price = result.data.totalPrice)
                        adapter.notifyItemChanged(index)
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to fetch pricing for ${item.name}")
                }
            }
        }
    }

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
                val card = layoutInflater.inflate(R.layout.item_truck_subtype, gridLayout, false)
                card.findViewById<TextView>(R.id.subtypeName)?.text = categoryName
                card.findViewById<ImageView>(R.id.subtypeIcon)?.setImageResource(getTruckIconResource("lcv"))
                
                // Hide quantity controls - these are category headers, not selectable
                card.findViewById<LinearLayout>(R.id.quantitySelector)?.visibility = View.GONE
                
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
        
        val card = layoutInflater.inflate(R.layout.item_truck_subtype, gridLayout, false)
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
        val quantityControls = card.findViewById<LinearLayout>(R.id.quantitySelector)
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
        val card = layoutInflater.inflate(R.layout.item_truck_subtype, gridLayout, false)
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
        val quantityControls = card.findViewById<LinearLayout>(R.id.quantitySelector)
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
                    // Payment flow: Completes via backend API
                    // Future enhancement: Add dedicated payment/confirmation screen
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
    
    @Suppress("UNUSED_PARAMETER") // Parameters reserved for future implementation
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
                val quantityControls = card.findViewById<LinearLayout>(R.id.quantitySelector)
                
                cardContent?.setBackgroundResource(R.drawable.bg_subtype_card_unselected)
                quantityControls?.visibility = View.GONE
            }
        }
    }
    
    /**
     * Show searching vehicles dialog with selected trucks
     * This is the main entry point after user confirms truck selection
     */
    // Store pending data for after pickup confirmation
    private var pendingTrucksForDialog: ArrayList<SelectedTruckItem>? = null
    private var pendingTotalPrice: Int = 0
    private var pendingDistanceKm: Int = 0
    
    // Activity result launcher for Confirm Pickup
    private val confirmPickupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Get confirmed pickup location
                val confirmedPickup = data.getParcelableExtraCompat<Location>(
                    com.weelo.logistics.presentation.booking.ConfirmPickupActivity.RESULT_CONFIRMED_PICKUP
                )
                
                if (confirmedPickup != null) {
                    // Update fromLocation with confirmed pickup
                    fromLocation = confirmedPickup
                    
                    // Now show the searching dialog
                    pendingTrucksForDialog?.let { trucks ->
                        showSearchingDialogInternal(trucks, pendingTotalPrice, pendingDistanceKm)
                    }
                }
            }
        }
        // Clear pending data
        pendingTrucksForDialog = null
    }
    
    /**
     * Show Confirm Pickup Activity first, then search dialog
     * Flow: Select trucks -> Confirm Pickup -> Search for vehicles
     */
    private fun showSearchingVehiclesDialog(
        trucksForDialog: ArrayList<SelectedTruckItem>,
        totalPrice: Int,
        distanceKm: Int
    ) {
        Timber.d( "Launching confirm pickup: trucks=${trucksForDialog.size}, price=$totalPrice, distance=${distanceKm}km")
        
        // Store data for after confirmation
        pendingTrucksForDialog = trucksForDialog
        pendingTotalPrice = totalPrice
        pendingDistanceKm = distanceKm
        
        // Launch Confirm Pickup Activity
        val intent = Intent(this, com.weelo.logistics.presentation.booking.ConfirmPickupActivity::class.java).apply {
            putExtra(com.weelo.logistics.presentation.booking.ConfirmPickupActivity.EXTRA_PICKUP_LOCATION, fromLocation)
            putExtra(com.weelo.logistics.presentation.booking.ConfirmPickupActivity.EXTRA_DROP_LOCATION, toLocation)
            putParcelableArrayListExtra(com.weelo.logistics.presentation.booking.ConfirmPickupActivity.EXTRA_SELECTED_TRUCKS, trucksForDialog)
            putExtra(com.weelo.logistics.presentation.booking.ConfirmPickupActivity.EXTRA_TOTAL_PRICE, totalPrice)
            putExtra(com.weelo.logistics.presentation.booking.ConfirmPickupActivity.EXTRA_DISTANCE_KM, distanceKm)
        }
        
        confirmPickupLauncher.launch(intent)
    }
    
    /**
     * Internal function to show the actual searching dialog
     * Called after pickup location is confirmed
     */
    private fun showSearchingDialogInternal(
        trucksForDialog: ArrayList<SelectedTruckItem>,
        totalPrice: Int,
        distanceKm: Int
    ) {
        Timber.d( "Showing search dialog: trucks=${trucksForDialog.size}, price=$totalPrice, distance=${distanceKm}km")
        
        try {
            // CRITICAL: Pass intermediate stops to SearchingVehiclesDialog for order creation
            // SCALABILITY: Converts Array<Location> to ArrayList<Location> for Bundle
            val stopsArrayList = ArrayList(intermediateStopLocations.toList())
            
            val searchDialog = SearchingVehiclesDialog.newInstance(
                fromLocation = fromLocation,
                toLocation = toLocation,
                selectedTrucks = trucksForDialog,
                totalPrice = totalPrice,
                distanceKm = distanceKm,
                intermediateStops = stopsArrayList
            )
            
            // Set callbacks
            // Store active order ID so retry/cancel flows work correctly
            var activeOrderId: String? = null

            searchDialog.setOnBookingCreatedListener(object : SearchingVehiclesDialog.OnBookingCreatedListener {
                override fun onBookingCreated(bookingId: String) {
                    Timber.d("Order created: $bookingId")
                    activeOrderId = bookingId
                }
            })

            // [Cancel] on timeout bottom sheet OR manual cancel success → go back to map/booking screen
            searchDialog.setOnSearchCancelledListener(object : SearchingVehiclesDialog.OnSearchCancelledListener {
                override fun onSearchCancelled() {
                    Timber.d("Search cancelled — returning to map. orderId=$activeOrderId")
                    activeOrderId = null
                    // Finish TruckTypesActivity so customer lands back on the map/booking screen
                    finish()
                }
            })

            // Timeout is handled fully inside SearchingVehiclesDialog (Retry/Cancel buttons).
            // This callback fires only if the dialog is dismissed without retry — go back to map.
            searchDialog.setOnSearchTimeoutListener(object : SearchingVehiclesDialog.OnSearchTimeoutListener {
                override fun onSearchTimeout(bookingId: String?) {
                    Timber.d("Search timed out, orderId=$bookingId — returning to map")
                    activeOrderId = null
                    finish()
                }
            })

            // All trucks filled (booking_fully_filled) → navigate to BookingTrackingActivity
            searchDialog.setOnDriverFoundListener(object : SearchingVehiclesDialog.OnDriverFoundListener {
                override fun onDriverFound(bookingId: String, driverName: String, vehicleNumber: String) {
                    Timber.d("Booking fully filled: orderId=$bookingId")
                    val intent = Intent(this@TruckTypesActivity, BookingTrackingActivity::class.java).apply {
                        putExtra("booking_id", bookingId)
                        // Clear back-stack so customer cannot go back to the truck-selection screen
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    searchDialog.dismiss()
                    finish()
                }
            })
            
            searchDialog.show(supportFragmentManager, "searching_vehicles_dialog")
            
        } catch (e: Exception) {
            Timber.e( "ERROR showing search dialog: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Navigate to booking confirmation screen with selected vehicles
     */
    @Suppress("UNUSED_PARAMETER") // vehicleId reserved for single-vehicle booking flow
    private fun navigateToPricing(vehicleId: String) {
        Timber.e( "=== navigateToPricing START ===")
        
        // Build trucks list from truckSelections if selectedTrucksList is empty
        val trucksToSend = if (selectedTrucksList.isNotEmpty()) {
            ArrayList(selectedTrucksList.map { it.copy() })
        } else {
            // Build from truckSelections map as fallback
            val fallbackList = ArrayList<SelectedTruckItem>()
            truckSelections.forEach { (truckTypeId, subtypes) ->
                if (subtypes.isNotEmpty()) {
                    val config = TruckSubtypesConfig.getConfigById(truckTypeId)
                    val iconResource = getTruckIconResource(truckTypeId)
                    
                    subtypes.forEach { (specification, quantity) ->
                        if (quantity > 0) {
                            fallbackList.add(
                                SelectedTruckItem(
                                    id = "${truckTypeId}_${specification}_${System.currentTimeMillis()}",
                                    truckTypeId = truckTypeId,
                                    truckTypeName = config?.displayName ?: truckTypeId,
                                    specification = specification,
                                    iconResource = iconResource,
                                    quantity = quantity
                                )
                            )
                        }
                    }
                }
            }
            fallbackList
        }
        
        Timber.e( "trucksToSend size: ${trucksToSend.size}")
        
        if (trucksToSend.isEmpty()) {
            showToast("Please select at least one truck")
            return
        }
        
        val distanceKm = calculateDistanceKm()
        
        Timber.e( "Distance: ${distanceKm}km")
        Timber.e( "From: ${fromLocation.address}")
        Timber.e( "To: ${toLocation.address}")
        
        // SIMPLE DIRECT NAVIGATION
        try {
            Timber.e( "Creating Intent...")
            
            val intent = Intent(this, com.weelo.logistics.presentation.pricing.PricingActivity::class.java)
            intent.putExtra("from_location", fromLocation)
            intent.putExtra("to_location", toLocation)
            intent.putParcelableArrayListExtra("selected_trucks", trucksToSend)
            intent.putExtra("distance_km", distanceKm)
            
            startActivity(intent)
            TransitionHelper.applyCustomTransition(this, R.anim.slide_in_right, R.anim.slide_out_left)
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error navigating to pricing")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculateDistanceKm(): Int {
        return TruckPricingHelper.calculateDistanceKm(fromLocation, toLocation)
    }
    
    /**
     * Get truck icon resource - delegates to TruckPricingHelper
     */
    private fun getTruckIconResource(truckTypeId: String): Int {
        return TruckPricingHelper.getTruckIconResource(truckTypeId)
    }
}

