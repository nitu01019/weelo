package com.weelo.logistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.weelo.logistics.adapters.CustomTruckTypeAdapter
import com.weelo.logistics.adapters.CustomTruckTypeItem
import com.weelo.logistics.adapters.SubtypeQuantityItem
import com.weelo.logistics.adapters.SubtypeWithQuantityAdapter
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.models.Location
import com.weelo.logistics.data.models.TruckSubtypesConfig
import com.weelo.logistics.data.remote.ApiConfig
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.CustomBookingRequest
import com.weelo.logistics.data.remote.api.CustomVehicleRequirement
import com.weelo.logistics.data.remote.api.WeeloApiService
import kotlinx.coroutines.launch
import timber.log.Timber
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * =============================================================================
 * CUSTOM BOOKING ACTIVITY
 * =============================================================================
 *
 * Handles long-term truck booking requests (weeks/months contracts).
 * User submits form -> Request goes to review queue in backend.
 *
 * SCALABILITY:
 * - Singleton Retrofit instance
 * - RecyclerView for efficient rendering
 * - Idempotency keys prevent duplicate submissions
 *
 * SECURITY:
 * - Input sanitization on all fields
 * - Quantity limits (max 100 per subtype, max 500 total)
 * - Date validation
 * - Double-click prevention
 *
 * MODULARITY:
 * - Uses TruckSubtypesConfig for truck data
 * - Separate adapters for grid and list views
 * - Clear separation of validation, UI, and API logic
 *
 * =============================================================================
 */
@dagger.hilt.android.AndroidEntryPoint
class CustomBookingActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var injectedApiService: WeeloApiService

    // Form fields
    private lateinit var pickupCityInput: EditText
    private lateinit var pickupStateInput: EditText
    private lateinit var startDatePicker: TextView
    private lateinit var endDatePicker: TextView
    private lateinit var flexibleDatesCheckbox: CheckBox
    private lateinit var goodsTypeInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var specialRequestsInput: EditText
    private lateinit var submitButton: CardView
    private lateinit var backButton: ImageView

    // New truck selection UI
    private lateinit var truckTypesGrid: RecyclerView
    private lateinit var selectedSummaryContainer: LinearLayout
    private lateinit var selectedSummaryText: TextView
    private lateinit var totalTrucksText: TextView
    private lateinit var truckTypeAdapter: CustomTruckTypeAdapter

    // Date values
    private var startDate: String = ""
    private var endDate: String = ""
    private var startDateMillis: Long = 0
    private var endDateMillis: Long = 0

    // Selected trucks: Map<TruckTypeId, Map<SubtypeId, Quantity>>
    private val selectedTrucks = mutableMapOf<String, MutableMap<String, Int>>()

    // Prevent double submission
    private var isSubmitting = false

    // Constants for validation
    companion object {
        private const val MAX_QUANTITY_PER_SUBTYPE = 100
        private const val MAX_TOTAL_TRUCKS = 500
        private const val MAX_CITY_LENGTH = 100
        private const val MAX_TEXT_LENGTH = 500

        // Singleton Retrofit instance
        @Volatile
        private var apiService: WeeloApiService? = null

        private fun getApiService(): WeeloApiService {
            return apiService ?: synchronized(this) {
                apiService ?: createApiService().also { apiService = it }
            }
        }

        private fun createApiService(): WeeloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(Constants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeeloApiService::class.java)
        }
    }

    // PRODUCTION: Use detailed truck images matching Instant booking
    // Images: ic_*_main.png (512x512px optimized PNGs, ~40KB each)
    // Total memory: ~360KB for 9 images (negligible at scale)
    // SCALABILITY: Reuses Android's drawable cache for efficiency
    private val truckTypeIcons = mapOf(
        "open" to R.drawable.ic_open_main,
        "container" to R.drawable.ic_container_main,
        "lcv" to R.drawable.ic_lcv_main,
        "mini" to R.drawable.ic_mini_main,
        "trailer" to R.drawable.ic_trailer_main,
        "tipper" to R.drawable.ic_tipper_main,
        "tanker" to R.drawable.ic_tanker_main,
        "dumper" to R.drawable.ic_dumper_main,
        "bulker" to R.drawable.ic_bulker_main
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_booking)

        initViews()
        setupTruckTypesGrid()
        setupDatePickers()
        setupClickListeners()
        prefillPickupLocation()
    }
    
    /**
     * Pre-fill pickup location if passed from LocationInputActivity
     * 
     * SCALABILITY: Simple intent extra parsing, O(1) operation
     * MODULARITY: Isolated function for location pre-fill
     * UNDERSTANDING: Clear intent extra handling with fallback
     */
    private fun prefillPickupLocation() {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Location>("PICKUP_LOCATION")?.let { location ->
            // Extract city and state from location address
            val addressParts = location.address.split(",").map { it.trim() }
            
            when {
                addressParts.size >= 2 -> {
                    // Address format: "City, State" or "Area, City, State"
                    pickupCityInput.setText(addressParts[0]) // First part is city/area
                    pickupStateInput.setText(addressParts.getOrNull(1) ?: "") // Second part is state
                }
                location.city.isNotBlank() -> {
                    // Use city from Location object
                    pickupCityInput.setText(location.city)
                }
                else -> {
                    // Fallback: Use full address
                    pickupCityInput.setText(location.address)
                }
            }
            
            // Store coordinates for future use (if needed for distance calculation)
            pickupCityInput.tag = location
            
            Timber.d("Pre-filled pickup location: ${location.address}")
        }
    }

    private fun initViews() {
        pickupCityInput = findViewById(R.id.pickupCityInput)
        pickupStateInput = findViewById(R.id.pickupStateInput)
        startDatePicker = findViewById(R.id.startDatePicker)
        endDatePicker = findViewById(R.id.endDatePicker)
        flexibleDatesCheckbox = findViewById(R.id.flexibleDatesCheckbox)
        goodsTypeInput = findViewById(R.id.goodsTypeInput)
        weightInput = findViewById(R.id.weightInput)
        specialRequestsInput = findViewById(R.id.specialRequestsInput)
        submitButton = findViewById(R.id.submitButton)
        backButton = findViewById(R.id.backButton)

        // New UI elements
        truckTypesGrid = findViewById(R.id.truckTypesGrid)
        selectedSummaryContainer = findViewById(R.id.selectedSummaryContainer)
        selectedSummaryText = findViewById(R.id.selectedSummaryText)
        totalTrucksText = findViewById(R.id.totalTrucksText)
    }

    private fun setupTruckTypesGrid() {
        val truckTypeItems = listOf(
            CustomTruckTypeItem("open", "Open", truckTypeIcons["open"] ?: R.drawable.ic_truck_open, TruckSubtypesConfig.OPEN.subtypes.size),
            CustomTruckTypeItem("container", "Container", truckTypeIcons["container"] ?: R.drawable.ic_truck_container, TruckSubtypesConfig.CONTAINER.subtypes.size),
            CustomTruckTypeItem("lcv", "LCV", truckTypeIcons["lcv"] ?: R.drawable.ic_truck_lcv, TruckSubtypesConfig.LCV.subtypes.size),
            CustomTruckTypeItem("mini", "Mini/Pickup", truckTypeIcons["mini"] ?: R.drawable.ic_truck_mini, TruckSubtypesConfig.MINI.subtypes.size),
            CustomTruckTypeItem("trailer", "Trailer", truckTypeIcons["trailer"] ?: R.drawable.ic_truck_trailer, TruckSubtypesConfig.TRAILER.subtypes.size),
            CustomTruckTypeItem("tipper", "Tipper", truckTypeIcons["tipper"] ?: R.drawable.ic_truck_tipper, TruckSubtypesConfig.TIPPER.subtypes.size),
            CustomTruckTypeItem("tanker", "Tanker", truckTypeIcons["tanker"] ?: R.drawable.ic_truck_tanker, TruckSubtypesConfig.TANKER.subtypes.size),
            CustomTruckTypeItem("dumper", "Dumper", truckTypeIcons["dumper"] ?: R.drawable.ic_truck_dumper, TruckSubtypesConfig.DUMPER.subtypes.size),
            CustomTruckTypeItem("bulker", "Bulker", truckTypeIcons["bulker"] ?: R.drawable.ic_truck_bulker, TruckSubtypesConfig.BULKER.subtypes.size)
        )

        truckTypeAdapter = CustomTruckTypeAdapter(truckTypeItems) { truckType ->
            showSubtypeSelectionBottomSheet(truckType)
        }

        truckTypesGrid.layoutManager = GridLayoutManager(this, 3)
        truckTypesGrid.adapter = truckTypeAdapter
    }

    private fun showSubtypeSelectionBottomSheet(truckType: CustomTruckTypeItem) {
        val bottomSheet = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_custom_subtype_selection, bottomSheet.window?.decorView as? ViewGroup, false)
        bottomSheet.setContentView(view)

        val config = TruckSubtypesConfig.getConfigById(truckType.id) ?: return

        // Setup header
        val headerIcon = view.findViewById<ImageView>(R.id.headerIcon)
        val headerTitle = view.findViewById<TextView>(R.id.headerTitle)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        headerIcon.setImageResource(truckType.iconResId)
        headerTitle.text = getString(R.string.select_truck_options, truckType.displayName)
        closeButton.setOnClickListener { bottomSheet.dismiss() }

        // Get or create quantities map for this truck type
        val quantities = selectedTrucks.getOrPut(truckType.id) { mutableMapOf() }

        // Create subtype items with capacity info
        val capacities = TruckSubtypesConfig.getAllCapacitiesForType(truckType.id)
        val subtypeItems = config.subtypes.map { subtypeName ->
            val capacity = capacities[subtypeName]
            val capacityText = if (capacity != null) {
                "${capacity.minTonnage.toInt()}-${capacity.maxTonnage.toInt()} Tons"
            } else {
                ""
            }
            SubtypeQuantityItem(
                id = subtypeName,
                name = subtypeName,
                capacity = capacityText
            )
        }

        // Setup RecyclerView
        val subtypesRecyclerView = view.findViewById<RecyclerView>(R.id.subtypesRecyclerView)
        val totalContainer = view.findViewById<LinearLayout>(R.id.totalContainer)
        val totalQuantity = view.findViewById<TextView>(R.id.totalQuantity)
        val doneButton = view.findViewById<CardView>(R.id.doneButton)

        fun updateTotalDisplay() {
            val total = quantities.values.sum()
            if (total > 0) {
                totalContainer.visibility = View.VISIBLE
                totalQuantity.text = resources.getQuantityString(R.plurals.truck_count, total, total)
            } else {
                totalContainer.visibility = View.GONE
            }
        }

        val subtypeAdapter = SubtypeWithQuantityAdapter(
            subtypes = subtypeItems,
            truckTypeIconRes = truckType.iconResId,
            quantities = quantities
        ) { subtypeId, newQuantity ->
            // Enforce maximum quantity per subtype — clamp before applying
            val clampedQuantity = newQuantity.coerceAtMost(MAX_QUANTITY_PER_SUBTYPE)
            quantities[subtypeId] = clampedQuantity
            if (newQuantity > MAX_QUANTITY_PER_SUBTYPE) {
                Toast.makeText(this, getString(R.string.max_quantity_per_type, MAX_QUANTITY_PER_SUBTYPE), Toast.LENGTH_SHORT).show()
            }

            // Check total trucks limit
            val totalTrucks = selectedTrucks.values.sumOf { it.values.sum() }
            if (totalTrucks > MAX_TOTAL_TRUCKS) {
                Toast.makeText(this, getString(R.string.max_trucks_total, MAX_TOTAL_TRUCKS), Toast.LENGTH_SHORT).show()
            }

            updateTotalDisplay()
        }

        subtypesRecyclerView.layoutManager = LinearLayoutManager(this)
        subtypesRecyclerView.adapter = subtypeAdapter

        updateTotalDisplay()

        doneButton.setOnClickListener {
            val total = quantities.values.sum()
            truckTypeAdapter.updateQuantity(truckType.id, total)
            updateSelectedSummary()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun updateSelectedSummary() {
        val summaryLines = mutableListOf<String>()
        var totalTrucks = 0

        selectedTrucks.forEach { (truckTypeId, subtypes) ->
            subtypes.forEach { (subtypeName, quantity) ->
                if (quantity > 0) {
                    val config = TruckSubtypesConfig.getConfigById(truckTypeId)
                    val typeName = config?.displayName ?: truckTypeId.replaceFirstChar { it.uppercase() }
                    summaryLines.add("$quantity x $typeName - $subtypeName")
                    totalTrucks += quantity
                }
            }
        }

        if (totalTrucks > 0) {
            selectedSummaryContainer.visibility = View.VISIBLE
            selectedSummaryText.text = summaryLines.joinToString("\n")
            totalTrucksText.text = getString(R.string.total_trucks, totalTrucks)
        } else {
            selectedSummaryContainer.visibility = View.GONE
        }
    }

    private fun setupDatePickers() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        startDatePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day, 0, 0, 0)
                    startDate = dateFormat.format(calendar.time)
                    startDateMillis = calendar.timeInMillis
                    startDatePicker.text = displayFormat.format(calendar.time)

                    // Validate end date if already selected
                    if (endDateMillis in 1 until startDateMillis) {
                        endDate = ""
                        endDateMillis = 0
                        endDatePicker.text = getString(R.string.select_date)
                        Toast.makeText(this, getString(R.string.end_date_after_start), Toast.LENGTH_SHORT).show()
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
                show()
            }
        }

        endDatePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day, 23, 59, 59)
                    val selectedMillis = calendar.timeInMillis

                    // Validate: end date must be after start date
                    if (startDateMillis > 0 && selectedMillis < startDateMillis) {
                        Toast.makeText(this, getString(R.string.end_date_after_start), Toast.LENGTH_SHORT).show()
                        return@DatePickerDialog
                    }

                    endDate = dateFormat.format(calendar.time)
                    endDateMillis = selectedMillis
                    endDatePicker.text = displayFormat.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // Set minimum date to start date if selected, otherwise today
                datePicker.minDate = if (startDateMillis > 0) startDateMillis else System.currentTimeMillis()
                show()
            }
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        submitButton.setOnClickListener {
            handleSubmit()
        }
    }

    private fun handleSubmit() {
        // Prevent double-click
        if (isSubmitting) return

        if (validateForm()) {
            submitRequest()
        }
    }

    /**
     * Sanitize text input to prevent injection attacks
     */
    private fun sanitizeInput(input: String, maxLength: Int = MAX_TEXT_LENGTH): String {
        return input
            .trim()
            .take(maxLength)
            .replace(Regex("""[<>"';&|`$]"""), "")  // Remove potentially dangerous characters
    }

    private fun validateForm(): Boolean {
        val pickupCity = sanitizeInput(pickupCityInput.text.toString(), MAX_CITY_LENGTH)
        if (pickupCity.isBlank()) {
            pickupCityInput.error = "Pickup city is required"
            pickupCityInput.requestFocus()
            return false
        }

        // Drop city validation removed - custom booking only needs pickup location

        // Check if at least one truck is selected
        val totalTrucks = selectedTrucks.values.sumOf { it.values.sum() }
        if (totalTrucks == 0) {
            Toast.makeText(this, "Please select at least one truck type", Toast.LENGTH_SHORT).show()
            return false
        }

        if (totalTrucks > MAX_TOTAL_TRUCKS) {
            Toast.makeText(this, "Maximum $MAX_TOTAL_TRUCKS trucks allowed", Toast.LENGTH_SHORT).show()
            return false
        }

        if (startDate.isEmpty()) {
            Toast.makeText(this, "Please select start date", Toast.LENGTH_SHORT).show()
            return false
        }

        if (endDate.isEmpty()) {
            Toast.makeText(this, "Please select end date", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate end date is after start date
        if (endDateMillis <= startDateMillis) {
            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun submitRequest() {
        isSubmitting = true
        submitButton.isEnabled = false
        submitButton.alpha = 0.6f

        // Build vehicle requirements list with sanitized data
        val vehicleRequirements = mutableListOf<CustomVehicleRequirement>()

        selectedTrucks.forEach { (truckTypeId, subtypes) ->
            subtypes.forEach { (subtypeName, quantity) ->
                if (quantity > 0) {
                    // Validate truck type ID against known types
                    if (TruckSubtypesConfig.getConfigById(truckTypeId) != null) {
                        vehicleRequirements.add(CustomVehicleRequirement(
                            type = truckTypeId,
                            subtype = subtypeName.take(50),  // Limit subtype name length
                            quantity = quantity.coerceIn(1, MAX_QUANTITY_PER_SUBTYPE)
                        ))
                    }
                }
            }
        }

        // Build request with sanitized inputs
        val request = CustomBookingRequest(
            pickupCity = sanitizeInput(pickupCityInput.text.toString(), MAX_CITY_LENGTH),
            pickupState = sanitizeInput(pickupStateInput.text.toString(), MAX_CITY_LENGTH).takeIf { it.isNotBlank() },
            dropCity = "",  // Custom booking only needs pickup location
            dropState = null,
            vehicleRequirements = vehicleRequirements,
            startDate = startDate,
            endDate = endDate,
            isFlexible = flexibleDatesCheckbox.isChecked,
            goodsType = sanitizeInput(goodsTypeInput.text.toString(), 100).takeIf { it.isNotBlank() },
            estimatedWeight = sanitizeInput(weightInput.text.toString(), 50).takeIf { it.isNotBlank() },
            specialRequests = sanitizeInput(specialRequestsInput.text.toString(), MAX_TEXT_LENGTH).takeIf { it.isNotBlank() }
        )

        // Generate idempotency key to prevent duplicate submissions
        val idempotencyKey = UUID.randomUUID().toString()

        lifecycleScope.launch {
            try {
                val tokenManager = TokenManager(this@CustomBookingActivity)
                val token = tokenManager.getAccessToken()

                if (token == null) {
                    showError("Please login again")
                    resetSubmitButton()
                    return@launch
                }

                val response = injectedApiService.submitCustomBooking(
                    token = "Bearer $token",
                    request = request,
                    idempotencyKey = idempotencyKey
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val successMessage = response.body()?.message ?: getString(R.string.custom_booking_success)
                    Toast.makeText(
                        this@CustomBookingActivity,
                        successMessage,
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val errorMsg = response.body()?.error?.message
                        ?: getString(R.string.custom_booking_error)
                    showError(errorMsg)
                    resetSubmitButton()
                }
            } catch (e: Exception) {
                logError("Submit request error", e)
                showError("Network error. Please check your connection.")
                resetSubmitButton()
            }
        }
    }

    private fun resetSubmitButton() {
        isSubmitting = false
        submitButton.isEnabled = true
        submitButton.alpha = 1f
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun logError(errorMessage: String, exception: Exception) {
        // Log error with Timber for debugging
        Timber.e(exception, errorMessage)
        
        // Send to Crashlytics in production
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                log(errorMessage)
                recordException(exception)
            }
        } catch (crashlyticsError: Exception) {
            Timber.w(crashlyticsError, "Failed to log to Crashlytics")
        }
    }
}
