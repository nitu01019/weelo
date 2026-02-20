package com.weelo.logistics.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.api.PendingRatingData
import com.weelo.logistics.data.remote.api.SubmitRatingRequest
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * RatingBottomSheetFragment
 * =========================
 *
 * Reusable bottom sheet for customers to rate drivers after trip completion.
 *
 * FEATURES:
 * - 5-star rating bar
 * - Predefined tag chips (Polite, On Time, Safe Driving, etc.)
 * - Optional text comment (max 500 chars)
 * - ViewPager2 for multi-truck bookings (swipe between drivers)
 * - Auto-advance to next unrated driver after submit
 * - Loading overlay during API call
 * - Idempotent: double-submit returns existing rating
 *
 * USAGE:
 * ```kotlin
 * RatingBottomSheetFragment.newInstance(pendingRatings).apply {
 *     onAllRatingsComplete = { refreshBookingsList() }
 * }.show(supportFragmentManager, "rating_sheet")
 * ```
 *
 * MODULARITY: Works from both BookingTrackingActivity and MyBookingsActivity
 * SCALABILITY: Each rating is submitted individually, handles 1-N trucks
 */
class RatingBottomSheetFragment : BottomSheetDialogFragment() {

    // Callbacks — set by caller before showing
    var onAllRatingsComplete: (() -> Unit)? = null

    // Dependencies — injected via setter (fragment can't use @Inject easily)
    var apiService: WeeloApiService? = null
    var tokenManager: TokenManager? = null

    // UI Components
    private lateinit var tvTitle: TextView
    private lateinit var vpDriverCards: ViewPager2
    private lateinit var llPageIndicator: LinearLayout
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingHint: TextView
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var etComment: TextInputEditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var loadingOverlay: FrameLayout

    // Data
    private var pendingRatings: List<PendingRatingData> = emptyList()
    private var currentIndex = 0
    private val selectedTags = mutableSetOf<String>()
    private val ratedAssignments = mutableSetOf<String>()
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    // Predefined tags matching backend schema
    private val ratingTags = listOf(
        "polite" to "Polite",
        "on_time" to "On Time",
        "safe_driving" to "Safe Driving",
        "good_vehicle_condition" to "Good Vehicle",
        "professional" to "Professional",
        "helpful" to "Helpful"
    )

    private val negativeTags = listOf(
        "rude" to "Rude",
        "late" to "Late",
        "rash_driving" to "Rash Driving"
    )

    companion object {
        private const val TAG = "RatingBottomSheet"
        private const val ARG_RATINGS_JSON = "ratings_json"
        private val gson = Gson()

        fun newInstance(ratings: List<PendingRatingData>): RatingBottomSheetFragment {
            return RatingBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RATINGS_JSON, gson.toJson(ratings))
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (pendingRatings.isNotEmpty()) return

        val ratingsJson = arguments?.getString(ARG_RATINGS_JSON)
        if (!ratingsJson.isNullOrBlank()) {
            pendingRatings = try {
                val listType = object : TypeToken<List<PendingRatingData>>() {}.type
                gson.fromJson<List<PendingRatingData>>(ratingsJson, listType) ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to parse ratings arguments")
                emptyList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rating_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupBottomSheetBehavior()
        setupViewPager()
        setupRatingBar()
        setupTagChips()
        setupButtons()
        updateTitle()
    }

    override fun onDestroyView() {
        pageChangeCallback?.let { callback ->
            if (this::vpDriverCards.isInitialized) {
                vpDriverCards.unregisterOnPageChangeCallback(callback)
                vpDriverCards.adapter = null
            }
        }
        pageChangeCallback = null
        super.onDestroyView()
    }

    // =========================================================================
    // SETUP
    // =========================================================================

    private fun bindViews(view: View) {
        tvTitle = view.findViewById(R.id.tvRatingTitle)
        vpDriverCards = view.findViewById(R.id.vpDriverCards)
        llPageIndicator = view.findViewById(R.id.llPageIndicator)
        ratingBar = view.findViewById(R.id.ratingBar)
        tvRatingHint = view.findViewById(R.id.tvRatingHint)
        chipGroupTags = view.findViewById(R.id.chipGroupTags)
        etComment = view.findViewById(R.id.etComment)
        btnSubmit = view.findViewById(R.id.btnSubmitRating)
        btnSkip = view.findViewById(R.id.btnSkipRating)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
    }

    private fun setupBottomSheetBehavior() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = true
        }
    }

    private fun setupViewPager() {
        if (pendingRatings.isEmpty()) {
            dismiss()
            return
        }

        vpDriverCards.adapter = DriverCardAdapter(pendingRatings)

        // Show page indicator only for multi-truck
        if (pendingRatings.size > 1) {
            llPageIndicator.visibility = View.VISIBLE
            buildPageIndicator()
            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentIndex = position
                    updatePageIndicator(position)
                    resetRatingForm()
                    updateTitle()
                }
            }
            pageChangeCallback?.let { callback ->
                vpDriverCards.registerOnPageChangeCallback(callback)
            }
        }
    }

    private fun setupRatingBar() {
        ratingBar.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { _, rating, _ ->
            val stars = rating.toInt()
            btnSubmit.isEnabled = stars >= 1
            tvRatingHint.text = when (stars) {
                1 -> "Poor"
                2 -> "Fair"
                3 -> "Good"
                4 -> "Very Good"
                5 -> "Excellent!"
                else -> "Tap to rate"
            }
            // Show negative tags for low ratings, positive for high
            updateTagVisibility(stars)
        }
    }

    private fun setupTagChips() {
        chipGroupTags.removeAllViews()
        val allTags = ratingTags + negativeTags
        for ((key, label) in allTags) {
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = false
                tag = key
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (selectedTags.size < 5) {
                            selectedTags.add(key)
                        } else {
                            this.isChecked = false
                            Toast.makeText(context, "Maximum 5 tags", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        selectedTags.remove(key)
                    }
                }
            }
            chipGroupTags.addView(chip)
        }
    }

    private fun updateTagVisibility(stars: Int) {
        for (i in 0 until chipGroupTags.childCount) {
            val chip = chipGroupTags.getChildAt(i) as? Chip ?: continue
            val key = chip.tag as? String ?: continue
            val isNegative = negativeTags.any { it.first == key }
            // Show negative tags for 1-2 stars, positive for 3-5
            val shouldHide = (stars <= 2 && !isNegative) || (stars >= 4 && isNegative)
            if (shouldHide) {
                // Clear selection so hidden chips don't get submitted
                chip.isChecked = false
                selectedTags.remove(key)
                chip.visibility = View.GONE
            } else {
                chip.visibility = View.VISIBLE
            }
        }
    }

    private fun setupButtons() {
        btnSubmit.setOnClickListener { submitCurrentRating() }
        btnSkip.setOnClickListener {
            if (currentIndex < pendingRatings.size - 1) {
                // Skip to next driver
                vpDriverCards.currentItem = currentIndex + 1
            } else {
                // All done or skipped
                dismiss()
                onAllRatingsComplete?.invoke()
            }
        }
    }

    // =========================================================================
    // RATING SUBMISSION
    // =========================================================================

    private fun submitCurrentRating() {
        val rating = pendingRatings.getOrNull(currentIndex) ?: return
        val stars = ratingBar.rating.toInt()
        if (stars < 1) return

        val comment = etComment.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val request = SubmitRatingRequest(
            assignmentId = rating.assignmentId,
            stars = stars,
            comment = comment,
            tags = selectedTags.toList()
        )

        val service = apiService
        val accessToken = tokenManager?.getAccessToken()
        if (service == null || accessToken.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Session expired. Please reopen ratings.", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    service.submitRating("Bearer $accessToken", request)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    ratedAssignments.add(rating.assignmentId)
                    val data = response.body()?.data
                    Timber.i("$TAG: Rating submitted for ${rating.assignmentId}, avg=${data?.driverAvgRating}")

                    context?.let {
                        Toast.makeText(
                            it,
                            data?.message ?: "Thank you for your feedback!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Auto-advance to next unrated driver or dismiss
                    advanceToNextOrDismiss()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to submit rating"
                    Timber.w("$TAG: Rating submit failed: $errorMsg")
                    context?.let { Toast.makeText(it, "Could not submit rating. Try again.", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "$TAG: Rating submit error")
                context?.let { Toast.makeText(it, "Network error. Please try again.", Toast.LENGTH_SHORT).show() }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun advanceToNextOrDismiss() {
        // Find next unrated assignment
        val nextIndex = (currentIndex + 1 until pendingRatings.size)
            .firstOrNull { pendingRatings[it].assignmentId !in ratedAssignments }

        if (nextIndex != null) {
            vpDriverCards.currentItem = nextIndex
            resetRatingForm()
        } else {
            // All rated or end of list
            dismiss()
            onAllRatingsComplete?.invoke()
        }
    }

    // =========================================================================
    // UI HELPERS
    // =========================================================================

    private fun resetRatingForm() {
        ratingBar.rating = 0f
        tvRatingHint.text = "Tap to rate"
        btnSubmit.isEnabled = false
        etComment.setText("")
        selectedTags.clear()
        for (i in 0 until chipGroupTags.childCount) {
            (chipGroupTags.getChildAt(i) as? Chip)?.isChecked = false
        }
        // Reset tag visibility
        for (i in 0 until chipGroupTags.childCount) {
            chipGroupTags.getChildAt(i).visibility = View.VISIBLE
        }
    }

    private fun updateTitle() {
        tvTitle.text = if (pendingRatings.size > 1) {
            "Rate Driver ${currentIndex + 1} of ${pendingRatings.size}"
        } else {
            "Rate Your Experience"
        }
    }

    private fun setLoading(loading: Boolean) {
        loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading && ratingBar.rating >= 1
        btnSkip.isEnabled = !loading
        isCancelable = !loading
    }

    private fun buildPageIndicator() {
        llPageIndicator.removeAllViews()
        for (i in pendingRatings.indices) {
            val dot = View(requireContext()).apply {
                val size = if (i == 0) 10 else 8
                layoutParams = LinearLayout.LayoutParams(
                    (size * resources.displayMetrics.density).toInt(),
                    (size * resources.displayMetrics.density).toInt()
                ).apply { setMargins(4, 0, 4, 0) }
                setBackgroundResource(
                    if (i == 0) R.drawable.bg_drag_handle
                    else R.drawable.bg_circle_grey
                )
            }
            llPageIndicator.addView(dot)
        }
    }

    private fun updatePageIndicator(position: Int) {
        for (i in 0 until llPageIndicator.childCount) {
            val dot = llPageIndicator.getChildAt(i)
            val isActive = i == position
            val size = if (isActive) 10 else 8
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                width = (size * resources.displayMetrics.density).toInt()
                height = (size * resources.displayMetrics.density).toInt()
            }
            dot.setBackgroundResource(
                if (isActive) R.drawable.bg_drag_handle
                else R.drawable.bg_circle_grey
            )
        }
    }

    // =========================================================================
    // DRIVER CARD ADAPTER (ViewPager2)
    // =========================================================================

    private inner class DriverCardAdapter(
        private val drivers: List<PendingRatingData>
    ) : RecyclerView.Adapter<DriverCardAdapter.DriverViewHolder>() {

        inner class DriverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: ImageView = view.findViewById(R.id.ivDriverPhoto)
            val tvName: TextView = view.findViewById(R.id.tvDriverName)
            val tvVehicleNumber: TextView = view.findViewById(R.id.tvVehicleInfo)
            val tvVehicleType: TextView = view.findViewById(R.id.tvVehicleType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rating_driver_card, parent, false)
            return DriverViewHolder(view)
        }

        override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
            val driver = drivers[position]

            holder.tvName.text = driver.driverName
            holder.tvVehicleNumber.text = driver.vehicleNumber
            holder.tvVehicleType.text = driver.vehicleType

            // Load driver photo with Glide (cached, circular)
            if (!driver.driverProfilePhotoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(driver.driverProfilePhotoUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(holder.ivPhoto)
            } else {
                // Explicitly clear to avoid recycled views showing previous driver's photo
                Glide.with(holder.itemView.context).clear(holder.ivPhoto)
                holder.ivPhoto.setImageResource(R.drawable.ic_person)
            }
        }

        override fun getItemCount(): Int = drivers.size
    }
}
