package com.weelo.logistics.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.weelo.logistics.R
import timber.log.Timber

/**
 * CancellationBottomSheet
 * =======================
 * 
 * Reusable bottom sheet for collecting cancellation reason before cancelling an order.
 * 
 * FEATURES:
 * - Radio button list with predefined reasons
 * - "Other" option with free-text input
 * - Optional booking summary display (pickup, drop, vehicle, price)
 * - Confirm Cancel button (disabled until reason selected)
 * - Loading state while cancel API processes
 * - Callback interface for confirmed cancellation
 * 
 * USAGE:
 * ```kotlin
 * CancellationBottomSheet.newInstance(
 *     pickupAddress = "123 Main St",
 *     dropAddress = "456 Oak Ave",
 *     vehicleSummary = "2x Open 17ft",
 *     totalPrice = 15000
 * ).apply {
 *     onCancellationConfirmed = { reason -> 
 *         // Call cancel API with reason
 *     }
 * }.show(supportFragmentManager, "cancel_sheet")
 * ```
 * 
 * MODULARITY: Works for BOTH SearchingVehiclesDialog AND BookingTrackingActivity
 * SCALABILITY: Reason is sent to backend for analytics + driver notification
 * 
 * @author Weelo Team
 */
class CancellationBottomSheet : BottomSheetDialogFragment() {

    // Callback — set by caller before showing
    var onCancellationConfirmed: ((reason: String) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    // UI Components
    private lateinit var reasonRadioGroup: RadioGroup
    private lateinit var otherReasonLayout: TextInputLayout
    private lateinit var otherReasonInput: TextInputEditText
    private lateinit var goBackButton: MaterialButton
    private lateinit var confirmCancelButton: MaterialButton
    private lateinit var cancelProgressBar: ProgressBar
    
    // Optional summary views
    private var summaryPickup: TextView? = null
    private var summaryDrop: TextView? = null
    private var summaryVehicle: TextView? = null
    private var summaryPrice: TextView? = null
    private var bookingSummaryContainer: View? = null

    // Data
    private var pickupAddress: String? = null
    private var dropAddress: String? = null
    private var vehicleSummary: String? = null
    private var totalPrice: Int = 0

    // Predefined reasons (matches backend analytics)
    private val REASONS = mapOf(
        R.id.reasonFoundAnother to "Found another transporter",
        R.id.reasonPriceTooHigh to "Price too high",
        R.id.reasonChangeOfPlans to "Change of plans",
        R.id.reasonWrongLocation to "Wrong pickup/drop location",
        R.id.reasonTakingTooLong to "Taking too long",
        R.id.reasonOther to "Other"
    )

    companion object {
        private const val ARG_PICKUP = "pickup_address"
        private const val ARG_DROP = "drop_address"
        private const val ARG_VEHICLE = "vehicle_summary"
        private const val ARG_PRICE = "total_price"

        /**
         * Create new instance with optional booking summary data
         * 
         * @param pickupAddress Pickup location text (optional)
         * @param dropAddress Drop location text (optional)
         * @param vehicleSummary Vehicle summary text like "2x Open 17ft" (optional)
         * @param totalPrice Total price in ₹ (optional, 0 to hide)
         */
        fun newInstance(
            pickupAddress: String? = null,
            dropAddress: String? = null,
            vehicleSummary: String? = null,
            totalPrice: Int = 0
        ): CancellationBottomSheet {
            return CancellationBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PICKUP, pickupAddress)
                    putString(ARG_DROP, dropAddress)
                    putString(ARG_VEHICLE, vehicleSummary)
                    putInt(ARG_PRICE, totalPrice)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pickupAddress = it.getString(ARG_PICKUP)
            dropAddress = it.getString(ARG_DROP)
            vehicleSummary = it.getString(ARG_VEHICLE)
            totalPrice = it.getInt(ARG_PRICE, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_cancellation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        populateSummary()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isFitToContents = true
            }
        }
        return dialog
    }

    private fun initViews(view: View) {
        reasonRadioGroup = view.findViewById(R.id.reasonRadioGroup)
        otherReasonLayout = view.findViewById(R.id.otherReasonLayout)
        otherReasonInput = view.findViewById(R.id.otherReasonInput)
        goBackButton = view.findViewById(R.id.goBackButton)
        confirmCancelButton = view.findViewById(R.id.confirmCancelButton)
        cancelProgressBar = view.findViewById(R.id.cancelProgressBar)
        
        // Optional summary views
        summaryPickup = view.findViewById(R.id.summaryPickup)
        summaryDrop = view.findViewById(R.id.summaryDrop)
        summaryVehicle = view.findViewById(R.id.summaryVehicle)
        summaryPrice = view.findViewById(R.id.summaryPrice)
        bookingSummaryContainer = view.findViewById(R.id.bookingSummaryContainer)
    }

    private fun setupListeners() {
        // Radio group — enable confirm button when reason selected
        reasonRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            confirmCancelButton.isEnabled = true
            
            // Show/hide "Other" text input
            if (checkedId == R.id.reasonOther) {
                otherReasonLayout.visibility = View.VISIBLE
                otherReasonInput.requestFocus()
            } else {
                otherReasonLayout.visibility = View.GONE
            }
        }

        // Go Back — dismiss without cancelling
        goBackButton.setOnClickListener {
            dismiss()
            onDismissed?.invoke()
        }

        // Confirm Cancel — collect reason and invoke callback
        confirmCancelButton.setOnClickListener {
            val reason = getSelectedReason()
            if (reason.isNotBlank()) {
                Timber.d("Cancellation confirmed with reason: $reason")
                showLoading(true)
                onCancellationConfirmed?.invoke(reason)
            }
        }
    }

    private fun populateSummary() {
        val hasSummary = !pickupAddress.isNullOrBlank() || !dropAddress.isNullOrBlank()
        
        if (hasSummary) {
            bookingSummaryContainer?.visibility = View.VISIBLE
            summaryPickup?.text = "📍 ${pickupAddress ?: ""}"
            summaryDrop?.text = "📌 ${dropAddress ?: ""}"
            summaryVehicle?.text = vehicleSummary ?: ""
            summaryPrice?.text = if (totalPrice > 0) "₹${String.format("%,d", totalPrice)}" else ""
        } else {
            bookingSummaryContainer?.visibility = View.GONE
        }
    }

    /**
     * Get the selected cancellation reason
     * 
     * If "Other" is selected, returns the custom text input.
     * Otherwise returns the predefined reason string.
     */
    private fun getSelectedReason(): String {
        val checkedId = reasonRadioGroup.checkedRadioButtonId
        if (checkedId == -1) return ""
        
        if (checkedId == R.id.reasonOther) {
            val customReason = otherReasonInput.text?.toString()?.trim() ?: ""
            return if (customReason.isNotBlank()) customReason else "Other"
        }
        
        return REASONS[checkedId] ?: "Cancelled by customer"
    }

    /**
     * Show/hide loading state
     * Called when cancel API is processing
     */
    fun showLoading(loading: Boolean) {
        cancelProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        confirmCancelButton.isEnabled = !loading
        goBackButton.isEnabled = !loading
        confirmCancelButton.text = if (loading) "CANCELLING..." else "CONFIRM CANCEL"
        isCancelable = !loading
    }

    /**
     * Call this after cancel API completes (success or failure)
     * On success: dismiss. On failure: re-enable buttons.
     */
    fun onCancelComplete(success: Boolean, errorMessage: String? = null) {
        showLoading(false)
        if (success) {
            dismiss()
        } else {
            val ctx = context ?: return  // Fragment may be detached before async response
            android.widget.Toast.makeText(
                ctx,
                errorMessage ?: "Failed to cancel. Please try again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
