package com.weelo.logistics.ui.dialogs

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

    @Inject
    lateinit var bookingRepository: BookingApiRepository

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

    // Timer & State
    private var countDownTimer: CountDownTimer? = null
    private var skipWaitTimer: CountDownTimer? = null
    private var bookingJob: Job? = null
    private var createdBookingId: String? = null
    private val timeoutSeconds = 60L
    private val showSkipWaitAfterSeconds = 15L
    private var skipWaitShown = false
    
    // Status
    private enum class SearchStatus {
        SEARCHING,
        BOOKING_CREATED,
        DRIVER_FOUND,
        TIMEOUT,
        CANCELLED,
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

        /**
         * Create new instance of SearchingVehiclesDialog
         * 
         * @param fromLocation Pickup location
         * @param toLocation Drop location
         * @param selectedTrucks List of selected trucks with quantities
         * @param totalPrice Total calculated price
         * @param distanceKm Distance in kilometers
         */
        fun newInstance(
            fromLocation: Location,
            toLocation: Location,
            selectedTrucks: ArrayList<SelectedTruckItem>,
            totalPrice: Int,
            distanceKm: Int
        ): SearchingVehiclesDialog {
            return SearchingVehiclesDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FROM_LOCATION, fromLocation)
                    putParcelable(ARG_TO_LOCATION, toLocation)
                    putParcelableArrayList(ARG_SELECTED_TRUCKS, selectedTrucks)
                    putInt(ARG_TOTAL_PRICE, totalPrice)
                    putInt(ARG_DISTANCE_KM, distanceKm)
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
        totalPriceText.text = "₹${String.format("%,d", totalPrice)}"
        
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
        cancelButton.setOnClickListener {
            cancelSearch()
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
    }
    
    private fun showTripDetails() {
        // Show Trip Details as a proper bottom sheet
        val tripDetailsSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_trip_details, null)
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
        totalFareText.text = "₹ ${String.format("%,d", finalPrice)}"
        
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
        
        groupedTrucks.forEach { (truckType, items) ->
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
        android.util.Log.d("SearchingDialog", "Applied boost: +₹$amount")
        
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

    private fun startBookingProcess() {
        android.util.Log.d("SearchingDialog", "Starting booking process...")
        
        // Start countdown timer
        startCountdownTimer()
        
        // Create booking via API
        createBooking()
    }

    private fun startCountdownTimer() {
        progressBar.max = 100
        progressBar.progress = 0
        
        countDownTimer = object : CountDownTimer(timeoutSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                val progress = ((timeoutSeconds - secondsRemaining) * 100 / timeoutSeconds).toInt()
                
                progressBar.progress = progress
                timerText.text = "Timeout in ${secondsRemaining}s"
                
                // Update progress steps based on time elapsed
                val elapsedSeconds = timeoutSeconds - secondsRemaining
                when {
                    elapsedSeconds >= 45 -> updateProgressSteps(4)
                    elapsedSeconds >= 30 -> updateProgressSteps(3)
                    elapsedSeconds >= 15 -> updateProgressSteps(2)
                    else -> updateProgressSteps(1)
                }
            }

            override fun onFinish() {
                if (currentStatus == SearchStatus.SEARCHING || currentStatus == SearchStatus.BOOKING_CREATED) {
                    handleTimeout()
                }
            }
        }.start()
    }

    private fun createBooking() {
        bookingJob = lifecycleScope.launch {
            try {
                updateStatus(SearchStatus.SEARCHING, "Creating booking...", "Connecting to server...")
                
                // Convert Location to LocationRequest
                val pickupRequest = fromLocation!!.toLocationRequest()
                val dropRequest = toLocation!!.toLocationRequest()
                
                // Calculate price per truck
                val trucksCount = selectedTrucks?.sumOf { it.quantity } ?: 1
                val pricePerTruck = if (trucksCount > 0) totalPrice / trucksCount else 0
                
                val result = bookingRepository.createBookingSimple(
                    pickup = pickupRequest,
                    drop = dropRequest,
                    vehicleType = selectedTrucks?.firstOrNull()?.truckTypeId ?: "open",
                    vehicleSubtype = selectedTrucks?.firstOrNull()?.specification ?: "",
                    trucksNeeded = trucksCount,
                    distanceKm = distanceKm,
                    pricePerTruck = pricePerTruck
                )
                
                when (result) {
                    is Result.Success -> {
                        createdBookingId = result.data
                        updateStatus(
                            SearchStatus.BOOKING_CREATED,
                            "Searching for Vehicles",
                            "Booking created! Waiting for drivers..."
                        )
                        
                        // Notify listener
                        onBookingCreatedListener?.onBookingCreated(result.data)
                        
                        android.util.Log.d("SearchingDialog", "Booking created: ${result.data}")
                    }
                    is Result.Error -> {
                        updateStatus(
                            SearchStatus.ERROR,
                            "Booking Failed",
                            result.exception.message ?: "Failed to create booking"
                        )
                        cancelButton.text = "CLOSE"
                    }
                    is Result.Loading -> {
                        // Do nothing, still loading
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SearchingDialog", "Error creating booking: ${e.message}", e)
                updateStatus(
                    SearchStatus.ERROR,
                    "Error",
                    e.message ?: "Something went wrong"
                )
                cancelButton.text = "CLOSE"
            }
        }
    }

    private fun updateStatus(status: SearchStatus, title: String, subtitle: String) {
        currentStatus = status
        statusTitle.text = title
        statusSubtitle.text = subtitle
        
        // Update animation based on status
        when (status) {
            SearchStatus.DRIVER_FOUND -> {
                animationView.setAnimation(R.raw.success_animation)
                animationView.playAnimation()
                cancelButton.text = "VIEW DETAILS"
            }
            SearchStatus.ERROR, SearchStatus.TIMEOUT -> {
                animationView.pauseAnimation()
            }
            else -> { /* Keep searching animation */ }
        }
    }

    private fun handleTimeout() {
        currentStatus = SearchStatus.TIMEOUT
        countDownTimer?.cancel()
        
        statusTitle.text = "No Drivers Found"
        statusSubtitle.text = "No transporters available right now. Try again later."
        animationView.pauseAnimation()
        cancelButton.text = "CLOSE"
        
        onSearchTimeoutListener?.onSearchTimeout(createdBookingId)
    }

    private fun cancelSearch() {
        currentStatus = SearchStatus.CANCELLED
        countDownTimer?.cancel()
        bookingJob?.cancel()
        
        onSearchCancelledListener?.onSearchCancelled()
        dismiss()
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
        currentStatus = SearchStatus.DRIVER_FOUND
        countDownTimer?.cancel()
        
        updateStatus(
            SearchStatus.DRIVER_FOUND,
            "Driver Found!",
            "$driverName is on the way"
        )
        
        createdBookingId?.let { bookingId ->
            onDriverFoundListener?.onDriverFound(bookingId, driverName, vehicleNumber)
        }
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        skipWaitTimer?.cancel()
        bookingJob?.cancel()
        super.onDestroyView()
    }
}
