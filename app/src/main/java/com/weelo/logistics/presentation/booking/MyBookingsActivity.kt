package com.weelo.logistics.presentation.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.core.util.TransitionHelper
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.api.BookingData
import com.weelo.logistics.data.remote.api.BookingsListResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * =============================================================================
 * MY BOOKINGS ACTIVITY — Active & Recent Bookings List
 * =============================================================================
 *
 * Shows the customer's bookings grouped by status:
 * - 🟢 Active: In-progress bookings with "Track" button
 * - 🟡 Pending: Waiting for truck assignment
 * - ⚪ Completed: Past deliveries
 * - 🔴 Cancelled: Cancelled bookings
 *
 * NAVIGATION:
 *   - Tap active booking → BookingTrackingActivity (multi-truck map)
 *   - Tap pending booking → BookingRequestActivity (assignment progress)
 *   - Tap completed booking → Details (future feature)
 *
 * SCALABILITY:
 *   - DiffUtil adapter for efficient list updates
 *   - Pagination-ready (limit/offset in API)
 *   - SwipeRefreshLayout for pull-to-refresh
 *
 * MODULARITY:
 *   - Reuses existing BookingData model from WeeloApiService
 *   - Standalone Activity — no coupling to MainActivity
 *   - Uses Hilt for dependency injection
 *
 * =============================================================================
 */
@AndroidEntryPoint
class MyBookingsActivity : AppCompatActivity() {

    @Inject lateinit var apiService: WeeloApiService
    @Inject lateinit var tokenManager: TokenManager

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var skeletonContainer: View
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var adapter: BookingsListAdapter

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, MyBookingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_bookings)

        setupViews()
        setupAdapter()
        loadBookings()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.rvBookings)
        emptyView = findViewById(R.id.tvEmpty)
        skeletonContainer = findViewById(R.id.skeletonContainer)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
            TransitionHelper.applySlideOutRightTransition(this)
        }

        swipeRefresh.setOnRefreshListener { loadBookings() }
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> recyclerView.canScrollVertically(-1) }
    }

    private fun setupAdapter() {
        adapter = BookingsListAdapter(
            onBookingClick = { booking -> navigateToBooking(booking) },
            onRateClick = { booking -> showRatingForBooking(booking) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    /**
     * Fetch pending ratings for this booking, then show the rating bottom sheet.
     * Flow: Rate button → API call → RatingBottomSheetFragment
     */
    private fun showRatingForBooking(booking: BookingData) {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: run {
                    Toast.makeText(this@MyBookingsActivity, "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // IO thread: network call must not run on main thread
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    apiService.getPendingRatings("Bearer $token")
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val allPending = response.body()?.data ?: emptyList()

                    // Filter to only this booking's unrated trips
                    val bookingRatings = allPending.filter { rating -> rating.bookingId == booking.id }

                    if (bookingRatings.isNotEmpty()) {
                        val fragment = com.weelo.logistics.ui.bottomsheet.RatingBottomSheetFragment
                            .newInstance(bookingRatings).apply {
                                onAllRatingsComplete = {
                                    // Refresh the bookings list to update the Rate badge
                                    loadBookings()
                                }
                            }
                        fragment.show(supportFragmentManager, "rating_sheet")
                    } else {
                        Toast.makeText(this@MyBookingsActivity, "All trips already rated!", Toast.LENGTH_SHORT).show()
                        loadBookings() // Refresh to remove stale Rate badge
                    }
                } else {
                    Toast.makeText(this@MyBookingsActivity, "Failed to load rating data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch pending ratings for booking ${booking.id}")
                Toast.makeText(this@MyBookingsActivity, "Something went wrong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBookings() {
        lifecycleScope.launch {
            try {
                // Show skeleton shimmer only on first load (not on pull-to-refresh)
                if (adapter.currentList.isEmpty()) {
                    skeletonContainer.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
                emptyView.visibility = View.GONE

                val accessToken = tokenManager.getAccessToken()
                if (accessToken.isNullOrBlank()) {
                    swipeRefresh.isRefreshing = false
                    skeletonContainer.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    if (adapter.currentList.isEmpty()) {
                        emptyView.text = "Session expired. Please login again."
                        emptyView.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    apiService.getMyBookings("Bearer $accessToken", page = 1, limit = 50)
                }

                swipeRefresh.isRefreshing = false
                skeletonContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (response.isSuccessful && response.body()?.success == true) {
                    val bookings = response.body()?.data?.bookings ?: emptyList()

                    // Sort: active first, then pending, then completed, then cancelled
                    val sorted = bookings.sortedWith(compareBy {
                        when (it.status.lowercase(java.util.Locale.ROOT)) { // already fixed
                            "in_progress", "in_transit" -> 0
                            "fully_filled", "confirmed", "driver_assigned" -> 1
                            "active", "pending", "partially_filled" -> 2
                            "completed" -> 3
                            "cancelled", "expired" -> 4
                            else -> 5
                        }
                    })

                    adapter.submitList(sorted)
                    emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    if (adapter.currentList.isEmpty()) {
                        emptyView.text = "Failed to load bookings"
                        emptyView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load bookings")
                swipeRefresh.isRefreshing = false
                skeletonContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                if (adapter.currentList.isEmpty()) {
                    emptyView.text = "Network error. Pull to retry."
                    emptyView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun navigateToBooking(booking: BookingData) {
        when (booking.status.lowercase(java.util.Locale.ROOT)) {
            "in_progress", "in_transit", "fully_filled", "confirmed", "driver_assigned" -> {
                // Active/confirmed → tracking screen
                startActivity(BookingTrackingActivity.newIntent(this, booking.id))
            }
            "active", "pending", "partially_filled" -> {
                // Pending → request/assignment screen
                startActivity(BookingRequestActivity.newIntent(this, booking.id))
            }
            else -> {
                // Completed/cancelled → show toast (future: details screen)
                Toast.makeText(this, "Booking ${booking.status}", Toast.LENGTH_SHORT).show()
            }
        }
        TransitionHelper.applySlideInLeftTransition(this)
    }
}

/**
 * =============================================================================
 * BOOKINGS LIST ADAPTER — Efficient booking cards with DiffUtil
 * =============================================================================
 */
class BookingsListAdapter(
    private val onBookingClick: (BookingData) -> Unit,
    private val onRateClick: (BookingData) -> Unit = {}
) : ListAdapter<BookingData, BookingsListAdapter.BookingViewHolder>(BookingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookingId: TextView = itemView.findViewById(R.id.tvBookingId)
        private val tvRoute: TextView = itemView.findViewById(R.id.tvRoute)
        private val tvVehicleInfo: TextView = itemView.findViewById(R.id.tvVehicleInfo)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val btnAction: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.btnAction)

        fun bind(booking: BookingData) {
            tvBookingId.text = "#${booking.id.takeLast(6).uppercase()}"
            tvRoute.text = try {
                "${booking.pickup.address} → ${booking.drop.address}"
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse route for booking ${booking.id}")
                "Route details"
            }
            tvVehicleInfo.text = "${booking.vehicleType} • ${itemView.context.resources.getQuantityString(R.plurals.truck_count, booking.trucksNeeded, booking.trucksNeeded)}"
            tvDate.text = booking.createdAt
            tvPrice.text = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(booking.totalAmount)
            var actionHandler: (() -> Unit)? = { onBookingClick(booking) }

            // Status badge
            when (booking.status.lowercase(java.util.Locale.ROOT)) {
                "in_progress", "in_transit" -> {
                    tvStatus.text = "🟢 In Progress"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    btnAction.text = "Track"
                    btnAction.visibility = View.VISIBLE
                }
                "fully_filled", "confirmed", "driver_assigned" -> {
                    tvStatus.text = "🟢 Trucks Assigned"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    btnAction.text = "Track"
                    btnAction.visibility = View.VISIBLE
                }
                "active", "pending" -> {
                    tvStatus.text = "🟡 Finding Trucks"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.warning_orange))
                    btnAction.text = "View"
                    btnAction.visibility = View.VISIBLE
                }
                "partially_filled" -> {
                    tvStatus.text = "🟡 Partially Filled"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.warning_orange))
                    btnAction.text = "View"
                    btnAction.visibility = View.VISIBLE
                }
                "completed" -> {
                    tvStatus.text = "✅ Completed"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
                    // Show "⭐ Rate" button if booking has unrated trips
                    if (booking.hasUnratedTrips == true) {
                        btnAction.text = "⭐ Rate"
                        btnAction.visibility = View.VISIBLE
                        actionHandler = { onRateClick(booking) }
                    } else {
                        btnAction.visibility = View.GONE
                        actionHandler = null
                    }
                }
                "cancelled" -> {
                    tvStatus.text = "❌ Cancelled"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.error_red))
                    btnAction.visibility = View.GONE
                    actionHandler = null
                }
                "expired" -> {
                    tvStatus.text = "⏰ Expired"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.error_red))
                    btnAction.visibility = View.GONE
                    actionHandler = null
                }
                else -> {
                    tvStatus.text = booking.status
                    tvStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
                    btnAction.visibility = View.GONE
                    actionHandler = null
                }
            }

            itemView.setOnClickListener { onBookingClick(booking) }
            btnAction.setOnClickListener {
                actionHandler?.invoke()
            }
            btnAction.isEnabled = actionHandler != null
        }
    }

    class BookingDiffCallback : DiffUtil.ItemCallback<BookingData>() {
        override fun areItemsTheSame(oldItem: BookingData, newItem: BookingData) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BookingData, newItem: BookingData) = oldItem == newItem
    }
}
