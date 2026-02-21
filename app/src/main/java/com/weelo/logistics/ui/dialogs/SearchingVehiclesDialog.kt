package com.weelo.logistics.ui.dialogs

import timber.log.Timber
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton
import com.weelo.logistics.R
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.util.getParcelableCompat
import com.weelo.logistics.core.util.getParcelableArrayListCompat
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.data.repository.BookingApiRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SearchingVehiclesDialog
 * =======================
 * 
 * Production-ready dialog for searching/booking vehicles.
 * 
 * FEATURES:
 * - Lottie animation for visual feedback
 * - Real-time countdown timer
 * - Progress bar showing search progress
 * - Booking summary display
 * - Cancel functionality
 * - Backend integration for booking creation
 * 
 * USAGE:
 * ```kotlin
 * SearchingVehiclesDialog.newInstance(
 *     fromLocation = pickupLocation,
 *     toLocation = dropLocation,
 *     selectedTrucks = trucksList,
 *     totalPrice = calculatedPrice,
 *     distanceKm = distance
 * ).show(supportFragmentManager, "searching_dialog")
 * ```
 * 
 * CALLBACKS:
 * - OnBookingCreatedListener: Called when booking is successfully created
 * - OnSearchCancelledListener: Called when user cancels the search
 * - OnSearchTimeoutListener: Called when search times out
 * 
 * @author Weelo Team
 * @version 1.0
 */
@AndroidEntryPoint
class SearchingVehiclesDialog : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {
    
    // ============================================================
    // MODULARITY: SharedPreferences Helper for Order Persistence
    // ============================================================
    // SCALABILITY: Separate object for clean state management
    // EASY UNDERSTANDING: Single source of truth for active order state
    // ============================================================
    private object ActiveOrderPrefs {
        private const val PREFS_NAME = "weelo_active_order"
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_EXPIRES_AT = "expires_at"
        
        fun save(context: android.content.Context, orderId: String, expiresAtMs: Long) {
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER_ID, orderId)
                .putLong(KEY_EXPIRES_AT, expiresAtMs)
                .apply()
        }
        
        fun get(context: android.content.Context): Pair<String?, Long> {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val orderId = prefs.getString(KEY_ORDER_ID, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
            return Pair(orderId, expiresAt)
        }
        
        fun clear(context: android.content.Context) {
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    @Inject
    lateinit var bookingRepository: BookingApiRepository

    @Inject
    lateinit var webSocketService: com.weelo.logistics.data.remote.WebSocketService

    // UI Components
    private lateinit var animationView: LottieAnimationView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var pickupLocationText: TextView
    private lateinit var dropLocationText: TextView
    private lateinit var selectedTrucksText: TextView
    private lateinit var totalPriceText: TextView
    private lateinit var cancelButton: MaterialButton
    private lateinit var retrySearchButton: MaterialButton
    
    // New UI Components for Rapido-style design
    private lateinit var tripDetailsButton: MaterialButton
    private lateinit var animationContainer: View
    private lateinit var skipWaitCard: View
    private lateinit var boost50Button: MaterialButton
    private lateinit var boost100Button: MaterialButton
    private lateinit var boost150Button: MaterialButton
    private lateinit var boost200Button: MaterialButton
    
    // Progress dots
    private lateinit var progressDot1: View
    private lateinit var progressDot2: View
    private lateinit var progressDot3: View
    private lateinit var progressDot4: View
    private lateinit var progressLine1: View
    private lateinit var progressLine2: View
    private lateinit var progressLine3: View

    // Data
    private var fromLocation: Location? = null
    private var toLocation: Location? = null
    private var selectedTrucks: ArrayList<SelectedTruckItem>? = null
    private var totalPrice: Int = 0
    private var distanceKm: Int = 0
    private var currentBoostAmount: Int = 0
    
    // CRITICAL: Intermediate stops for order creation
    // SCALABILITY: Passed through entire chain to BookingApiRepository.createOrder()
    private var intermediateStops: ArrayList<Location> = arrayListOf()

    // Timer & State
    private var countDownTimer: CountDownTimer? = null
    private var skipWaitTimer: CountDownTimer? = null
    private var bookingJob: Job? = null
    private var createdBookingId: String? = null

    // WebSocket collectors (must be started only after socket is connected + room joined)
    private var orderExpiredJob: Job? = null
    private var orderCancelledJob: Job? = null
    private var broadcastStateChangedJob: Job? = null
    private var trucksRemainingUpdateJob: Job? = null
    private var bookingFullyFilledJob: Job? = null
    private var reconnectPollJob: Job? = null
    // REMOVE hardcoded timeout - now comes from backend
    // private val timeoutSeconds = 60L  // OLD: Hardcoded
    // NEW: Timer duration from backend response (expiresIn)
    private val showSkipWaitAfterSeconds = 15L
    private var skipWaitShown = false
    
    // Status
    private enum class SearchStatus {
        SEARCHING,
        BOOKING_CREATED,
        DRIVER_FOUND,
        TIMEOUT,
        CANCELLED,
        CANCELLED_CONFIRMED,
        ERROR
    }
    private var currentStatus = SearchStatus.SEARCHING
    private var currentStep = 1

    // Callbacks
    private var onBookingCreatedListener: OnBookingCreatedListener? = null
    private var onSearchCancelledListener: OnSearchCancelledListener? = null
    private var onSearchTimeoutListener: OnSearchTimeoutListener? = null
    private var onDriverFoundListener: OnDriverFoundListener? = null

    interface OnBookingCreatedListener {
        fun onBookingCreated(bookingId: String)
    }

    interface OnSearchCancelledListener {
        fun onSearchCancelled()
    }

    interface OnSearchTimeoutListener {
        fun onSearchTimeout(bookingId: String?)
    }

    interface OnDriverFoundListener {
        fun onDriverFound(bookingId: String, driverName: String, vehicleNumber: String)
    }

    companion object {
        private const val ARG_FROM_LOCATION = "arg_from_location"
        private const val ARG_TO_LOCATION = "arg_to_location"
        private const val ARG_SELECTED_TRUCKS = "arg_selected_trucks"
        private const val ARG_TOTAL_PRICE = "arg_total_price"
        private const val ARG_DISTANCE_KM = "arg_distance_km"
        private const val ARG_INTERMEDIATE_STOPS = "arg_intermediate_stops"

        /**
         * Create new instance of SearchingVehiclesDialog
         * 
         * SCALABILITY: Supports intermediate stops for multi-waypoint orders
         * EASY UNDERSTANDING: All booking data passed in Bundle
         * 
         * @param fromLocation Pickup location
         * @param toLocation Drop location
         * @param selectedTrucks List of selected trucks with quantities
         * @param totalPrice Total calculated price
         * @param distanceKm Distance in kilometers
         * @param intermediateStops List of intermediate stop locations (optional)
         */
        fun newInstance(
            fromLocation: Location,
            toLocation: Location,
            selectedTrucks: ArrayList<SelectedTruckItem>,
            totalPrice: Int,
            distanceKm: Int,
            intermediateStops: ArrayList<Location> = arrayListOf()
        ): SearchingVehiclesDialog {
            return SearchingVehiclesDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FROM_LOCATION, fromLocation)
                    putParcelable(ARG_TO_LOCATION, toLocation)
                    putParcelableArrayList(ARG_SELECTED_TRUCKS, selectedTrucks)
                    putInt(ARG_TOTAL_PRICE, totalPrice)
                    putInt(ARG_DISTANCE_KM, distanceKm)
                    putParcelableArrayList(ARG_INTERMEDIATE_STOPS, intermediateStops)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Weelo_Dialog_Transparent)
        
        // Extract arguments
        arguments?.let { args ->
            fromLocation = args.getParcelableCompat(ARG_FROM_LOCATION)
            toLocation = args.getParcelableCompat(ARG_TO_LOCATION)
            selectedTrucks = args.getParcelableArrayListCompat(ARG_SELECTED_TRUCKS)
            totalPrice = args.getInt(ARG_TOTAL_PRICE, 0)
            distanceKm = args.getInt(ARG_DISTANCE_KM, 0)
            // CRITICAL: Receive intermediate stops
            intermediateStops = args.getParcelableArrayListCompat(ARG_INTERMEDIATE_STOPS) ?: arrayListOf()
            timber.log.Timber.d("SearchingVehiclesDialog: ${intermediateStops.size} intermediate stops received")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_searching_vehicles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupUI()
        setupClickListeners()
        startBookingProcess()
    }

    override fun onDestroyView() {
        orderExpiredJob?.cancel()
        orderExpiredJob = null
        orderCancelledJob?.cancel()
        orderCancelledJob = null
        broadcastStateChangedJob?.cancel()
        broadcastStateChangedJob = null
        trucksRemainingUpdateJob?.cancel()
        trucksRemainingUpdateJob = null
        bookingFullyFilledJob?.cancel()
        bookingFullyFilledJob = null
        reconnectPollJob?.cancel()
        reconnectPollJob = null
        countDownTimer?.cancel()
        skipWaitTimer?.cancel()
        bookingJob?.cancel()

        // Leave WebSocket room on dismiss — clean up subscription
        leaveBookingRoomSafely(createdBookingId)

        super.onDestroyView()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bottom_sheet_rounded_bg)
                
                // Compact bottom sheet - wrap content, attached to bottom
                (sheet.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { params ->
                    params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    params.gravity = android.view.Gravity.BOTTOM
                    sheet.layoutParams = params
                }
                
                // Configure behavior - compact, fits content
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false  // Don't allow dragging for search dialog
                behavior.isFitToContents = true
                behavior.isHideable = false
                
                sheet.requestLayout()
            }
        }
        
        return dialog
    }

    private fun initViews(view: View) {
        animationView = view.findViewById(R.id.searchingAnimation)
        statusTitle = view.findViewById(R.id.statusTitle)
        statusSubtitle = view.findViewById(R.id.statusSubtitle)
        progressBar = view.findViewById(R.id.searchProgressBar)
        timerText = view.findViewById(R.id.timerText)
        pickupLocationText = view.findViewById(R.id.pickupLocationText)
        dropLocationText = view.findViewById(R.id.dropLocationText)
        selectedTrucksText = view.findViewById(R.id.selectedTrucksText)
        totalPriceText = view.findViewById(R.id.totalPriceText)
        cancelButton = view.findViewById(R.id.cancelButton)
        retrySearchButton = view.findViewById(R.id.retrySearchButton)
        
        // New Rapido-style components
        tripDetailsButton = view.findViewById(R.id.tripDetailsButton)
        animationContainer = view.findViewById(R.id.animationContainer)
        skipWaitCard = view.findViewById(R.id.skipWaitCard)
        boost50Button = view.findViewById(R.id.boost50Button)
        boost100Button = view.findViewById(R.id.boost100Button)
        boost150Button = view.findViewById(R.id.boost150Button)
        boost200Button = view.findViewById(R.id.boost200Button)
        
        // Progress dots and lines
        progressDot1 = view.findViewById(R.id.progressDot1)
        progressDot2 = view.findViewById(R.id.progressDot2)
        progressDot3 = view.findViewById(R.id.progressDot3)
        progressDot4 = view.findViewById(R.id.progressDot4)
        progressLine1 = view.findViewById(R.id.progressLine1)
        progressLine2 = view.findViewById(R.id.progressLine2)
        progressLine3 = view.findViewById(R.id.progressLine3)
    }

    private fun setupUI() {
        // Set location texts (hidden but used for data)
        pickupLocationText.text = fromLocation?.address ?: "Pickup Location"
        dropLocationText.text = toLocation?.address ?: "Drop Location"
        
        // Set trucks summary
        val trucksSummary = buildTrucksSummary()
        selectedTrucksText.text = trucksSummary
        
        // Set price
        totalPriceText.text = "₹${String.format(java.util.Locale.getDefault(), "%,d", totalPrice)}"
        
        // Set title based on selected truck type
        val vehicleType = selectedTrucks?.firstOrNull()?.truckTypeName ?: "Vehicle"
        statusSubtitle.text = "Looking for your"
        statusTitle.text = vehicleType
        
        // Start animation
        animationView.playAnimation()
        
        // Initialize progress to step 1
        updateProgressSteps(1)
        
        // Start skip wait timer (shows after 15 seconds)
        startSkipWaitTimer()
    }

    private fun buildTrucksSummary(): String {
        val trucks = selectedTrucks ?: return "No trucks selected"
        
        if (trucks.isEmpty()) return "No trucks selected"
        
        val summaryParts = trucks.map { truck ->
            "${truck.quantity}x ${truck.truckTypeName}"
        }
        
        return summaryParts.joinToString(", ")
    }

    private fun setupClickListeners() {
        // Cancel/Close button - dismiss dialog and notify listener
        // MODULARITY: Handles both cancel and close scenarios
        // EASY UNDERSTANDING: Clear behavior based on dialog state
        cancelButton.setOnClickListener {
            when (currentStatus) {
                SearchStatus.ERROR -> {
                    // Error state — just close
                    dismiss()
                }
                SearchStatus.TIMEOUT -> {
                    // PRD 4.1: On timeout, secondary action = go back to map screen (no new order)
                    // Retry Search button handles the retry flow separately
                    retrySearchButton.visibility = View.GONE
                    onSearchTimeoutListener?.onSearchTimeout(createdBookingId)
                    ActiveOrderPrefs.clear(requireContext())
                    createdBookingId = null
                    dismiss()
                }
                SearchStatus.CANCELLED_CONFIRMED -> {
                    retrySearchButton.visibility = View.GONE
                    onSearchCancelledListener?.onSearchCancelled()
                    createdBookingId = null
                    dismiss()
                }
                SearchStatus.DRIVER_FOUND -> {
                    dismiss()
                }
                else -> {
                    // During search, cancel the search
                    cancelSearch()
                }
            }
        }
        
        // Trip Details button - show booking summary
        tripDetailsButton.setOnClickListener {
            showTripDetails()
        }
        
        // Boost price buttons
        boost50Button.setOnClickListener { applyBoost(50) }
        boost100Button.setOnClickListener { applyBoost(100) }
        boost150Button.setOnClickListener { applyBoost(150) }
        boost200Button.setOnClickListener { applyBoost(200) }

        // WebSocket order events are registered only after connect()+joinBookingRoom().
        // Registering listeners before socket init would be a no-op (socket == null).
        // See: startOrderWebSocketCollectorsIfPossible()
    }
    
    private fun showTripDetails() {
        // Show Trip Details as a proper bottom sheet
        val tripDetailsSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_trip_details, 
            requireView().parent as? android.view.ViewGroup, false)
        tripDetailsSheet.setContentView(view)
        
        // Set vehicle icon and type - show summary of all trucks
        val vehicleIcon = view.findViewById<android.widget.ImageView>(R.id.vehicleIcon)
        val vehicleTypeText = view.findViewById<TextView>(R.id.vehicleTypeText)
        
        // Build summary text (e.g., "2x Open, 1x Container")
        val vehicleSummary = buildVehicleSummary()
        vehicleTypeText.text = vehicleSummary
        
        // Set icon based on first/primary vehicle type
        val primaryVehicle = selectedTrucks?.firstOrNull()?.truckTypeName ?: "Vehicle"
        val iconRes = getVehicleIconRes(primaryVehicle)
        vehicleIcon.setImageResource(iconRes)
        
        // Populate trucks list container with all selected trucks
        populateTrucksList(view)
        
        // Set progress bars based on current step
        updateTripDetailsProgress(view)
        
        // Set location details
        val pickupTitle = view.findViewById<TextView>(R.id.pickupTitle)
        val pickupAddress = view.findViewById<TextView>(R.id.pickupAddress)
        val dropTitle = view.findViewById<TextView>(R.id.dropTitle)
        val dropAddress = view.findViewById<TextView>(R.id.dropAddress)
        
        // Extract short name and full address
        val pickupFull = fromLocation?.address ?: "Pickup Location"
        val dropFull = toLocation?.address ?: "Drop Location"
        
        pickupTitle.text = pickupFull.split(",").firstOrNull() ?: pickupFull
        pickupAddress.text = pickupFull
        dropTitle.text = dropFull.split(",").firstOrNull() ?: dropFull
        dropAddress.text = dropFull
        
        // Set total fare
        val totalFareText = view.findViewById<TextView>(R.id.totalFareText)
        val finalPrice = totalPrice + currentBoostAmount
        totalFareText.text = "₹ ${String.format(java.util.Locale.getDefault(), "%,d", finalPrice)}"
        
        // Set payment method
        val paymentMethodText = view.findViewById<TextView>(R.id.paymentMethodText)
        paymentMethodText.text = "Paying via cash"
        
        // Back button - just dismiss the trip details sheet
        val backButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.backButton)
        backButton.setOnClickListener {
            tripDetailsSheet.dismiss()
        }
        
        // Cancel Ride button - cancel the search
        val cancelRideButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelRideButton)
        cancelRideButton.setOnClickListener {
            tripDetailsSheet.dismiss()
            cancelSearch()
        }
        
        // Configure bottom sheet using helper for consistency across app
        com.weelo.logistics.core.util.BottomSheetHelper.configureBottomSheet(
            dialog = tripDetailsSheet,
            style = com.weelo.logistics.core.util.BottomSheetHelper.Style.COMPACT,
            isDismissable = true
        )
        
        tripDetailsSheet.show()
    }
    
    /**
     * Build a summary of all selected vehicles (e.g., "2x Open, 1x Container")
     */
    private fun buildVehicleSummary(): String {
        val trucks = selectedTrucks ?: return "Vehicle"
        if (trucks.isEmpty()) return "Vehicle"
        
        // Group by truck type and count
        val grouped = trucks.groupBy { it.truckTypeName }
        return grouped.entries.joinToString(", ") { (type, items) ->
            val totalQty = items.sumOf { it.quantity }
            "${totalQty}x $type"
        }
    }
    
    /**
     * Populate the trucks list container with all selected trucks and their details
     */
    private fun populateTrucksList(view: View) {
        val container = view.findViewById<android.widget.LinearLayout>(R.id.trucksListContainer)
        container.removeAllViews()
        
        val trucks = selectedTrucks ?: return
        
        // Group trucks by type for cleaner display
        val groupedTrucks = trucks.groupBy { it.truckTypeName }
        
        groupedTrucks.forEach { (_, items) ->
            items.forEach { truck ->
                // Create a row for each truck subtype
                val rowView = createTruckRowView(truck)
                container.addView(rowView)
            }
        }
    }
    
    /**
     * Create a view row for a single truck item
     */
    private fun createTruckRowView(truck: SelectedTruckItem): View {
        val context = requireContext()
        
        // Horizontal LinearLayout for the row
        val row = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        }
        
        // Truck icon - use iconResource from truck item or fallback to type-based icon
        val icon = android.widget.ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx())
            setImageResource(if (truck.iconResource != 0) truck.iconResource else getVehicleIconRes(truck.truckTypeName))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        row.addView(icon)
        
        // Truck details (name + specification)
        val detailsLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
        }
        
        // Truck type name with quantity
        val nameText = TextView(context).apply {
            text = "${truck.quantity}x ${truck.truckTypeName}"
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detailsLayout.addView(nameText)
        
        // Specification (e.g., "8-11 Ton", "32 Feet")
        if (truck.specification.isNotEmpty()) {
            val specText = TextView(context).apply {
                text = truck.specification
                setTextColor(android.graphics.Color.parseColor("#666666"))
                textSize = 12f
            }
            detailsLayout.addView(specText)
        }
        
        row.addView(detailsLayout)
        
        return row
    }
    
    /**
     * Extension function to convert dp to pixels
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun getVehicleIconRes(vehicleType: String): Int {
        return when (vehicleType.lowercase()) {
            "open", "open truck" -> R.drawable.ic_open_main
            "container" -> R.drawable.ic_container_main
            "lcv" -> R.drawable.ic_lcv_main
            "mini", "mini/pickup" -> R.drawable.ic_mini_main
            "tipper" -> R.drawable.ic_tipper_main
            "tanker" -> R.drawable.ic_tanker_main
            "trailer" -> R.drawable.ic_trailer_main
            "dumper" -> R.drawable.ic_dumper_main
            "bulker" -> R.drawable.ic_bulker_main
            else -> R.drawable.ic_truck
        }
    }
    
    private fun updateTripDetailsProgress(view: View) {
        val activeColor = android.graphics.Color.parseColor("#3366CC")
        val inactiveColor = android.graphics.Color.parseColor("#E0E0E0")
        
        view.findViewById<View>(R.id.progressBar1)?.setBackgroundColor(if (currentStep >= 1) activeColor else inactiveColor)
        view.findViewById<View>(R.id.progressBar2)?.setBackgroundColor(if (currentStep >= 2) activeColor else inactiveColor)
        view.findViewById<View>(R.id.progressBar3)?.setBackgroundColor(if (currentStep >= 3) activeColor else inactiveColor)
        view.findViewById<View>(R.id.progressBar4)?.setBackgroundColor(if (currentStep >= 4) activeColor else inactiveColor)
    }
    
    private fun applyBoost(amount: Int) {
        currentBoostAmount = amount
        
        // Update button states - highlight selected
        resetBoostButtons()
        when (amount) {
            50 -> highlightBoostButton(boost50Button)
            100 -> highlightBoostButton(boost100Button)
            150 -> highlightBoostButton(boost150Button)
            200 -> highlightBoostButton(boost200Button)
        }
        
        // TODO: Send boost request to backend
        Timber.d( "Applied boost: +₹$amount")
        
        // Show confirmation
        android.widget.Toast.makeText(
            requireContext(),
            "Added +₹$amount to get faster response!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun resetBoostButtons() {
        val defaultColor = android.graphics.Color.parseColor("#333333")
        val defaultStroke = android.graphics.Color.parseColor("#DDDDDD")
        
        listOf(boost50Button, boost100Button, boost150Button, boost200Button).forEach { btn ->
            btn.setTextColor(defaultColor)
            btn.strokeColor = android.content.res.ColorStateList.valueOf(defaultStroke)
            btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    
    private fun highlightBoostButton(button: MaterialButton) {
        val highlightColor = android.graphics.Color.parseColor("#FFA000")
        button.setTextColor(android.graphics.Color.WHITE)
        button.strokeColor = android.content.res.ColorStateList.valueOf(highlightColor)
        button.setBackgroundColor(highlightColor)
    }
    
    private fun updateProgressSteps(step: Int) {
        currentStep = step
        val activeColor = android.graphics.Color.parseColor("#3366CC")
        val inactiveColor = android.graphics.Color.parseColor("#E0E0E0")
        
        // Update dots - use drawable resources for proper circular shape
        progressDot1.setBackgroundResource(if (step >= 1) R.drawable.bg_green_dot else R.drawable.bg_red_dot)
        progressDot2.setBackgroundResource(if (step >= 2) R.drawable.bg_green_dot else R.drawable.bg_red_dot)
        progressDot3.setBackgroundResource(if (step >= 3) R.drawable.bg_green_dot else R.drawable.bg_red_dot)
        progressDot4.setBackgroundResource(if (step >= 4) R.drawable.bg_green_dot else R.drawable.bg_red_dot)
        
        // For inactive dots, just use gray color
        if (step < 1) progressDot1.setBackgroundColor(inactiveColor)
        if (step < 2) progressDot2.setBackgroundColor(inactiveColor)
        if (step < 3) progressDot3.setBackgroundColor(inactiveColor)
        if (step < 4) progressDot4.setBackgroundColor(inactiveColor)
        
        // Update lines
        progressLine1.setBackgroundColor(if (step >= 2) activeColor else inactiveColor)
        progressLine2.setBackgroundColor(if (step >= 3) activeColor else inactiveColor)
        progressLine3.setBackgroundColor(if (step >= 4) activeColor else inactiveColor)
    }
    
    private fun startSkipWaitTimer() {
        skipWaitTimer = object : CountDownTimer(showSkipWaitAfterSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Nothing to do while waiting
            }
            
            override fun onFinish() {
                if (!skipWaitShown && currentStatus == SearchStatus.SEARCHING) {
                    showSkipWaitCard()
                }
            }
        }.start()
    }
    
    private fun showSkipWaitCard() {
        skipWaitShown = true
        skipWaitCard.visibility = View.VISIBLE
        
        // Animate card appearance
        skipWaitCard.alpha = 0f
        skipWaitCard.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    private fun hideSkipWaitCard() {
        skipWaitCard.visibility = View.GONE
    }

    private fun isSearchingState(): Boolean {
        return currentStatus == SearchStatus.SEARCHING || currentStatus == SearchStatus.BOOKING_CREATED
    }

    private fun leaveBookingRoomSafely(orderId: String?) {
        if (orderId.isNullOrBlank()) return
        try {
            webSocketService.leaveBookingRoom(orderId)
        } catch (e: Exception) {
            timber.log.Timber.w("SearchingVehiclesDialog: leaveBookingRoom failed: ${e.message}")
        }
    }

    private fun canTransitionToTimeout(): Boolean {
        return currentStatus != SearchStatus.TIMEOUT &&
            currentStatus != SearchStatus.CANCELLED &&
            currentStatus != SearchStatus.CANCELLED_CONFIRMED &&
            currentStatus != SearchStatus.DRIVER_FOUND &&
            currentStatus != SearchStatus.ERROR
    }

    private fun canTransitionToCancelledConfirmed(): Boolean {
        return currentStatus != SearchStatus.CANCELLED_CONFIRMED &&
            currentStatus != SearchStatus.DRIVER_FOUND &&
            currentStatus != SearchStatus.ERROR
    }

    private fun canTransitionToDriverFound(): Boolean {
        return currentStatus != SearchStatus.DRIVER_FOUND &&
            currentStatus != SearchStatus.ERROR
    }

    private fun wireSearchAgainButton() {
        retrySearchButton.setOnClickListener {
            retrySearchButton.isEnabled = false
            retrySearchButton.text = getString(R.string.searching_in_progress)
            executeRetrySearch()
        }
    }

    private fun showCancelledConfirmedState(reason: String) {
        if (!canTransitionToCancelledConfirmed()) return

        currentStatus = SearchStatus.CANCELLED_CONFIRMED
        countDownTimer?.cancel()
        leaveBookingRoomSafely(createdBookingId)
        ActiveOrderPrefs.clear(requireContext())

        statusTitle.text = getString(R.string.search_cancelled_title)
        statusSubtitle.text = if (reason.isNotBlank()) {
            getString(R.string.search_cancelled_subtitle_with_reason, reason)
        } else {
            getString(R.string.search_cancelled_subtitle_no_reason)
        }

        animationView.pauseAnimation()
        timerText.visibility = View.GONE
        progressBar.visibility = View.GONE
        hideSkipWaitCard()

        retrySearchButton.visibility = View.VISIBLE
        retrySearchButton.isEnabled = true
        retrySearchButton.text = getString(R.string.search_again)
        wireSearchAgainButton()

        cancelButton.text = getString(R.string.go_back)
    }

    private fun handleBookingFullyFilled(orderId: String) {
        if (!canTransitionToDriverFound()) return

        Timber.i("SearchingVehiclesDialog: booking_fully_filled received for $orderId")
        countDownTimer?.cancel()
        leaveBookingRoomSafely(orderId)
        ActiveOrderPrefs.clear(requireContext())

        updateStatus(
            SearchStatus.DRIVER_FOUND,
            getString(R.string.search_trucks_assigned_title),
            getString(R.string.search_trucks_assigned_subtitle)
        )

        onDriverFoundListener?.onDriverFound(orderId, "", "")
    }

    private fun startOrderWebSocketCollectorsIfPossible() {
        // Cancel any previous collectors (e.g., retry flow creates a new order)
        orderExpiredJob?.cancel()
        orderCancelledJob?.cancel()
        broadcastStateChangedJob?.cancel()
        trucksRemainingUpdateJob?.cancel()
        bookingFullyFilledJob?.cancel()

        // PRD 4.2: Listen for backend order_expired event — triggers timeout UI.
        orderExpiredJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.onOrderExpired().collect { event ->
                val activeOrderId = createdBookingId
                if (!activeOrderId.isNullOrEmpty() && event.orderId == activeOrderId) {
                    Timber.i("SearchingVehiclesDialog: order_expired received from backend for $activeOrderId")
                    if (canTransitionToTimeout()) {
                        handleTimeout()
                    }
                }
            }
        }

        // PRD 4.2: Listen for backend order_cancelled event — backend confirmed cancel.
        orderCancelledJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.onOrderCancelled().collect { event ->
                val activeOrderId = createdBookingId
                if (!activeOrderId.isNullOrEmpty() && event.orderId == activeOrderId) {
                    Timber.i("SearchingVehiclesDialog: order_cancelled received from backend for $activeOrderId")
                    if (canTransitionToCancelledConfirmed()) {
                        showCancelledConfirmedState(event.reason)
                    }
                }
            }
        }

        // PRD 4.2: Order lifecycle state updates
        broadcastStateChangedJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.onBroadcastStateChanged().collect { event ->
                val activeOrderId = createdBookingId
                if (!activeOrderId.isNullOrEmpty() && event.orderId == activeOrderId) {
                    when (event.status.lowercase()) {
                        "created" -> statusSubtitle.text = getString(R.string.search_lifecycle_created)
                        "broadcasting" -> statusSubtitle.text = getString(R.string.search_lifecycle_broadcasting)
                        "active" -> statusSubtitle.text = getString(R.string.search_lifecycle_active)
                        "partially_filled" -> statusSubtitle.text = getString(R.string.search_lifecycle_partially_filled)
                        "fully_filled" -> handleBookingFullyFilled(activeOrderId)
                    }
                }
            }
        }

        // PRD 4.2: Real-time fill progress
        trucksRemainingUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.onTrucksRemainingUpdate().collect { event ->
                val activeOrderId = createdBookingId
                if (!activeOrderId.isNullOrEmpty() && event.orderId == activeOrderId) {
                    if (isSearchingState()) {
                        statusSubtitle.text = getString(
                            R.string.search_lifecycle_progress,
                            event.trucksFilled,
                            event.trucksNeeded
                        )
                    }
                    if (event.trucksNeeded > 0 && event.trucksFilled >= event.trucksNeeded) {
                        handleBookingFullyFilled(activeOrderId)
                    }
                }
            }
        }

        // PRD 4.2: All trucks assigned
        bookingFullyFilledJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.onBookingFullyFilled().collect { event ->
                val activeOrderId = createdBookingId
                if (!activeOrderId.isNullOrEmpty() && event.orderId == activeOrderId) {
                    handleBookingFullyFilled(activeOrderId)
                }
            }
        }
    }

    private fun startReconnectOrderStatusPoll() {
        // Poll order status when socket reconnects, to catch missed expiry/cancel while offline.
        reconnectPollJob?.cancel()
        reconnectPollJob = viewLifecycleOwner.lifecycleScope.launch {
            webSocketService.connectionState
                .collect { state ->
                    if (state == com.weelo.logistics.data.remote.WebSocketService.ConnectionState.CONNECTED) {
                        val activeOrderId = createdBookingId
                        if (!activeOrderId.isNullOrBlank()) {
                            when (val statusResult = bookingRepository.getOrderStatus(activeOrderId)) {
                                is Result.Success -> {
                                    val status = statusResult.data.status.lowercase()
                                    if (!statusResult.data.isActive || status == "expired") {
                                        Timber.i("SearchingVehiclesDialog: reconnect poll detected expired order $activeOrderId")
                                        if (canTransitionToTimeout()) {
                                            handleTimeout()
                                        }
                                    } else if (status == "cancelled") {
                                        Timber.i("SearchingVehiclesDialog: reconnect poll detected cancelled order $activeOrderId")
                                        showCancelledConfirmedState("Cancelled by customer")
                                    } else if (status == "fully_filled") {
                                        handleBookingFullyFilled(activeOrderId)
                                    }
                                }
                                is Result.Error -> {
                                    Timber.w("SearchingVehiclesDialog: reconnect order status poll failed: ${statusResult.exception.message}")
                                }
                                Result.Loading -> Unit
                            }
                        }
                    }
                }
        }
    }

    private fun startBookingProcess() {
        Timber.d("Starting booking process...")

        // Create booking first - timer starts when we get response with expiresIn
        createBooking()
    }

    /**
     * Start countdown timer with backend-provided duration
     * 
     * SCALABILITY: Uses backend TTL - can change duration without app update
     * EASY UNDERSTANDING: Timer always matches backend expiry
     * MODULARITY: Duration parameter makes this reusable
     */
    private fun startCountdownTimer(durationSeconds: Int) {
        progressBar.max = 100
        progressBar.progress = 0
        
        Timber.d( "Starting timer with ${durationSeconds}s from backend")
        
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                val progress = ((durationSeconds - secondsRemaining) * 100 / durationSeconds)
                
                progressBar.progress = progress
                timerText.text = getString(R.string.search_timeout_in_seconds, secondsRemaining)
                
                // Update progress steps based on time elapsed (proportional to duration)
                val elapsedSeconds = durationSeconds - secondsRemaining
                when {
                    elapsedSeconds >= (durationSeconds * 0.75).toInt() -> updateProgressSteps(4)
                    elapsedSeconds >= (durationSeconds * 0.50).toInt() -> updateProgressSteps(3)
                    elapsedSeconds >= (durationSeconds * 0.25).toInt() -> updateProgressSteps(2)
                    else -> updateProgressSteps(1)
                }
            }

            override fun onFinish() {
                if (canTransitionToTimeout()) {
                    handleTimeout()
                }
            }
        }.start()
    }

    private fun createBooking() {
        bookingJob = lifecycleScope.launch {
            try {
                // CRASH PREVENTION: Validate required data before proceeding
                val pickup = fromLocation
                val drop = toLocation
                
                if (pickup == null || drop == null) {
                    updateStatus(
                        SearchStatus.ERROR,
                        getString(R.string.search_location_error_title),
                        getString(R.string.search_location_missing_subtitle)
                    )
                    return@launch
                }
                
                updateStatus(
                    SearchStatus.SEARCHING,
                    getString(R.string.search_creating_booking_title),
                    getString(R.string.search_connecting_to_server_subtitle)
                )
                
                // Convert selectedTrucks to TruckSelection list for multi-truck order
                val trucksCount = selectedTrucks?.sumOf { it.quantity } ?: 1
                val avgPricePerTruck = if (trucksCount > 0) totalPrice / trucksCount else 0
                
                val truckSelections = selectedTrucks?.map { truck ->
                    com.weelo.logistics.data.repository.TruckSelection(
                        vehicleType = truck.truckTypeId,
                        vehicleSubtype = truck.specification,
                        quantity = truck.quantity,
                        pricePerTruck = avgPricePerTruck  // Evenly distribute price for now
                    )
                } ?: emptyList()
                
                Timber.d( "Creating order with ${truckSelections.size} truck types: ${truckSelections.map { "${it.vehicleType}/${it.vehicleSubtype}:${it.quantity}" }}")
                
                // Convert Location to LocationModel for createOrder API (safe - already null checked)
                val pickupModel = com.weelo.logistics.domain.model.LocationModel(
                    address = pickup.address,
                    latitude = pickup.latitude,
                    longitude = pickup.longitude,
                    city = pickup.city,
                    state = pickup.state
                )
                val dropModel = com.weelo.logistics.domain.model.LocationModel(
                    address = drop.address,
                    latitude = drop.latitude,
                    longitude = drop.longitude,
                    city = drop.city,
                    state = drop.state
                )
                
                // CRITICAL: Convert intermediate stops to LocationModel for API
                // SCALABILITY: Supports unlimited stops with coordinates
                val intermediateStopModels = intermediateStops.map { stop ->
                    com.weelo.logistics.domain.model.LocationModel(
                        address = stop.address,
                        latitude = stop.latitude,
                        longitude = stop.longitude
                    )
                }
                
                Timber.d( "Creating order with ${intermediateStopModels.size} intermediate stops")
                
                // Use createOrder for multi-truck support (each subtype is separate)
                val result = bookingRepository.createOrder(
                    pickup = pickupModel,
                    drop = dropModel,
                    trucks = truckSelections,
                    distanceKm = distanceKm,
                    goodsType = "General", // TODO: Get from user selection
                    intermediateStops = intermediateStopModels  // CRITICAL: Pass stops to API
                )
                
                when (result) {
                    is Result.Success -> {
                        val orderResult = result.data
                        createdBookingId = orderResult.orderId
                        
                        // SCALABILITY: Get TTL from backend (not hardcoded)
                        // EASY UNDERSTANDING: Backend controls timer duration
                        val expiresIn = orderResult.expiresIn ?: 60 // Fallback to 60s if not provided
                        
                        // MODULARITY: Persist order state for app resume
                        val expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)
                        val ctx = context ?: return@launch  // Guard: dialog may be detached
                        ActiveOrderPrefs.save(ctx, orderResult.orderId, expiresAtMs)
                        
                        // Start timer with backend duration
                        startCountdownTimer(expiresIn)
                        
                        updateStatus(
                            SearchStatus.BOOKING_CREATED,
                            getString(R.string.search_order_created_title),
                            getString(R.string.search_order_created_subtitle, orderResult.totalTrucks)
                        )
                        
                        // PRD 4.2: Connect WebSocket and join order room so we receive
                        // order_expired / order_cancelled events from backend in real-time
                        try {
                            webSocketService.connect()
                            webSocketService.joinBookingRoom(orderResult.orderId)
                            Timber.i("SearchingVehiclesDialog: WebSocket connected, joined room ${orderResult.orderId}")

                            // GAP FIX: Only now register Socket.IO listeners, because socket is initialized.
                            startOrderWebSocketCollectorsIfPossible()
                            startReconnectOrderStatusPoll()
                        } catch (e: Exception) {
                            Timber.w("SearchingVehiclesDialog: WebSocket connect failed (non-critical): ${e.message}")
                            // Non-critical — local CountDownTimer still handles timeout
                        }

                        // Notify listener
                        onBookingCreatedListener?.onBookingCreated(orderResult.orderId)

                        Timber.d( "Order created: ${orderResult.orderId}, expires in ${expiresIn}s")
                    }
                    Result.Loading -> {
                        // Should not happen in this context (already handled by bookingJob)
                        Timber.d( "Unexpected Loading state")
                    }
                    is Result.Error -> {
                        // EASY UNDERSTANDING: Show user-friendly error messages
                        // SCALABILITY: Handles different error types gracefully
                        val errorMessage = result.exception.message ?: "Failed to create booking"
                        
                        // Check if it's an active order error
                        val isActiveOrderError = errorMessage.contains("active order", ignoreCase = true) ||
                                               errorMessage.contains("ACTIVE_ORDER", ignoreCase = true)
                        
                        updateStatus(
                            SearchStatus.ERROR,
                            if (isActiveOrderError) {
                                getString(R.string.search_active_order_exists_title)
                            } else {
                                getString(R.string.search_booking_failed_title)
                            },
                            errorMessage
                        )
                        cancelButton.text = getString(R.string.search_close)
                        
                        Timber.e( "Booking error: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e( "Error creating booking: ${e.message}", e)
                updateStatus(
                    SearchStatus.ERROR,
                    getString(R.string.search_generic_error_title),
                    e.message ?: getString(R.string.search_generic_error_subtitle)
                )
                cancelButton.text = getString(R.string.search_close)
            }
        }
    }

    private fun updateStatus(status: SearchStatus, title: String, subtitle: String) {
        currentStatus = status
        statusTitle.text = title
        statusSubtitle.text = subtitle
        
        // EASY UNDERSTANDING: Update UI based on status
        // MODULARITY: Clean separation of status handling
        when (status) {
            SearchStatus.DRIVER_FOUND -> {
                animationView.setAnimation(R.raw.success_animation)
                animationView.playAnimation()
                cancelButton.text = getString(R.string.search_view_details)
                // Hide timer and progress when driver found
                timerText.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
            SearchStatus.ERROR -> {
                animationView.pauseAnimation()
                animationView.visibility = View.GONE
                cancelButton.text = getString(R.string.search_close)
                // Hide timer and progress on error
                timerText.visibility = View.GONE
                progressBar.visibility = View.GONE
                // Hide boost cards on error
                skipWaitCard.visibility = View.GONE
            }
            SearchStatus.TIMEOUT -> {
                // PRD 4.1: TIMEOUT uses handleTimeout() directly — do NOT set button text here
                // retrySearchButton + cancelButton are managed by handleTimeout()
                animationView.pauseAnimation()
                timerText.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
            SearchStatus.CANCELLED_CONFIRMED -> {
                animationView.pauseAnimation()
                timerText.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
            else -> { 
                /* Keep searching animation and UI visible */ 
            }
        }
    }

    private fun handleTimeout() {
        if (!canTransitionToTimeout()) return

        currentStatus = SearchStatus.TIMEOUT
        countDownTimer?.cancel()
        leaveBookingRoomSafely(createdBookingId)

        // SCALABILITY: Clear persisted order state
        ActiveOrderPrefs.clear(requireContext())

        // PRD 4.1: Timeout UI — show Retry + Cancel dual buttons
        statusTitle.text = getString(R.string.search_timeout_title)
        statusSubtitle.text = getString(R.string.search_timeout_subtitle)
        animationView.pauseAnimation()

        // Hide timer and progress
        timerText.visibility = View.GONE
        progressBar.visibility = View.GONE

        // Show Retry Search button (primary — brand yellow)
        retrySearchButton.visibility = View.VISIBLE
        retrySearchButton.isEnabled = true
        retrySearchButton.text = getString(R.string.retry_search)

        // Secondary action: go back
        cancelButton.text = getString(R.string.go_back)

        // Wire Retry button — PRD Retry flow: cancel old order → create fresh order same params
        wireSearchAgainButton()
    }

    /**
     * Retry search — PRD 4.1 Retry flow:
     * 1. Cancel previous expired order (idempotent — already expired server-side)
     * 2. Generate fresh UUID idempotency key (NEVER reuse old one)
     * 3. Call POST /orders with same params + fresh key
     * 4. On success: reset timer → show searching UI again
     */
    private fun executeRetrySearch() {
        Timber.i("SearchingVehiclesDialog: Executing retry search")

        lifecycleScope.launch {
            // Step 1: Cancel old order if it exists (idempotent — backend handles already-expired)
            val oldOrderId = createdBookingId
            if (oldOrderId != null) {
                leaveBookingRoomSafely(oldOrderId)
                try {
                    bookingRepository.cancelOrder(oldOrderId, "retry_after_timeout")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.w("Retry: cancel old order failed (non-critical): ${e.message}")
                    // Non-critical — order may already be expired server-side
                }
            }

            // Step 2: Reset UI to searching state
            createdBookingId = null
            currentStatus = SearchStatus.SEARCHING
            retrySearchButton.visibility = View.GONE
            cancelButton.text = getString(R.string.search_cancel_button)
            statusTitle.text = getString(R.string.search_status_searching_title)
            statusSubtitle.text = getString(R.string.search_status_searching_subtitle)
            animationView.resumeAnimation()
            timerText.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            ActiveOrderPrefs.clear(requireContext())

            // Step 3: Create fresh order with new idempotency key (same params)
            startBookingProcess()
        }
    }

    /**
     * Cancel search — opens CancellationBottomSheet for reason selection,
     * then calls backend with the selected reason.
     * 
     * SCALABILITY: Backend handles Redis cleanup, DB update, transporter/driver notifications
     * EASY UNDERSTANDING: User picks reason → UI confirms → backend processes
     * MODULARITY: CancellationBottomSheet is reusable across the app
     */
    private fun cancelSearch() {
        val orderId = createdBookingId
        
        if (orderId == null) {
            // No order created yet, just dismiss
            dismiss()
            return
        }
        
        Timber.d("Opening cancellation reason sheet for order: $orderId")
        
        // Build vehicle summary for the bottom sheet
        val vehicleSummary = selectedTrucks?.joinToString(", ") { 
            "${it.quantity}x ${it.truckTypeName}" 
        } ?: "Vehicle"
        
        // Show CancellationBottomSheet for reason selection
        val cancelSheet = com.weelo.logistics.ui.bottomsheet.CancellationBottomSheet.newInstance(
            pickupAddress = fromLocation?.address,
            dropAddress = toLocation?.address,
            vehicleSummary = vehicleSummary,
            totalPrice = totalPrice
        )
        
        cancelSheet.onCancellationConfirmed = { reason ->
            // User confirmed — call backend with reason
            executeCancelWithReason(orderId, reason, cancelSheet)
        }
        
        cancelSheet.show(childFragmentManager, "cancel_reason")
    }
    
    /**
     * Execute the actual cancellation with the selected reason
     * 
     * SCALABILITY: Optimistic UI + backend confirmation pattern
     * EASY UNDERSTANDING: Loading state on bottom sheet, rollback on failure
     * MODULARITY: Separated from reason collection for clean architecture
     */
    private fun executeCancelWithReason(
        orderId: String, 
        reason: String,
        cancelSheet: com.weelo.logistics.ui.bottomsheet.CancellationBottomSheet
    ) {
        val previousStatus = currentStatus
        currentStatus = SearchStatus.CANCELLED
        countDownTimer?.cancel()
        bookingJob?.cancel()
        
        lifecycleScope.launch {
            try {
                val result = bookingRepository.cancelOrder(orderId, reason)
                
                when (result) {
                    is Result.Success -> {
                        // Backend confirmed — clear everything
                        leaveBookingRoomSafely(orderId)
                        ActiveOrderPrefs.clear(context?.applicationContext ?: requireContext())
                        
                        Timber.d("Order cancelled: $orderId, reason: $reason, " +
                            "${result.data.transportersNotified} transporters, " +
                            "${result.data.driversNotified} drivers notified")
                        
                        cancelSheet.onCancelComplete(true)
                        showCancelledConfirmedState(reason)
                    }
                    Result.Loading -> {
                        Timber.d("Unexpected Loading state during cancel")
                    }
                    is Result.Error -> {
                        Timber.w("Cancel failed: ${result.exception.message}")
                        
                        // Rollback
                        currentStatus = previousStatus
                        cancelSheet.onCancelComplete(false, result.exception.message)
                        
                        // Restart timer with remaining time
                        val (_, expiresAtMs) = ActiveOrderPrefs.get(requireContext())
                        val remainingMs = expiresAtMs - System.currentTimeMillis()
                        if (remainingMs > 0) {
                            val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(1)
                            startCountdownTimer(remainingSeconds)
                        } else {
                            handleTimeout()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Cancel error")
                currentStatus = previousStatus
                cancelSheet.onCancelComplete(false, e.message)
            }
        }
    }

    // Public methods for setting listeners
    fun setOnBookingCreatedListener(listener: OnBookingCreatedListener) {
        this.onBookingCreatedListener = listener
    }

    fun setOnSearchCancelledListener(listener: OnSearchCancelledListener) {
        this.onSearchCancelledListener = listener
    }

    fun setOnSearchTimeoutListener(listener: OnSearchTimeoutListener) {
        this.onSearchTimeoutListener = listener
    }

    fun setOnDriverFoundListener(listener: OnDriverFoundListener) {
        this.onDriverFoundListener = listener
    }

    /**
     * Call this when a driver accepts the booking (from WebSocket/Push notification)
     */
    fun onDriverAccepted(driverName: String, vehicleNumber: String) {
        if (!canTransitionToDriverFound()) return

        currentStatus = SearchStatus.DRIVER_FOUND
        countDownTimer?.cancel()
        
        updateStatus(
            SearchStatus.DRIVER_FOUND,
            getString(R.string.search_driver_found_title),
            getString(R.string.search_driver_found_subtitle, driverName)
        )
        
        createdBookingId?.let { bookingId ->
            onDriverFoundListener?.onDriverFound(bookingId, driverName, vehicleNumber)
        }
    }

}
