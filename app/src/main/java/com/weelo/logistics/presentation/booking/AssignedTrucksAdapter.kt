package com.weelo.logistics.presentation.booking

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.api.AssignedTruckData

/**
 * =============================================================================
 * ASSIGNED TRUCKS ADAPTER — Horizontal Scrollable Truck Cards
 * =============================================================================
 *
 * Displays assigned trucks in a horizontal RecyclerView on the tracking screen.
 * Each card shows: vehicle number, driver name, status badge, ETA, call/track buttons.
 *
 * SCALABILITY:
 * - Uses ListAdapter + DiffUtil for efficient updates (O(n) diff)
 * - Only re-binds changed items — smooth even with 50+ trucks
 *
 * MODULARITY:
 * - Callbacks for track/call — activity decides what happens
 * - Status display logic is self-contained
 * - ETA map stored externally, passed via updateEta()
 *
 * =============================================================================
 */
class AssignedTrucksAdapter(
    private val onTrackClick: (AssignedTruckData) -> Unit,
    private val onCallClick: ((AssignedTruckData) -> Unit)? = null
) : ListAdapter<AssignedTruckData, AssignedTrucksAdapter.TruckViewHolder>(TruckDiffCallback()) {

    // ETA values keyed by assignmentId — updated externally
    private val etaMap = mutableMapOf<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TruckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assigned_truck, parent, false)
        return TruckViewHolder(view)
    }

    override fun onBindViewHolder(holder: TruckViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Update ETA for a specific truck (called when location updates arrive).
     * Only re-binds the affected item — no full list refresh.
     */
    fun updateEta(assignmentId: String, eta: String) {
        etaMap[assignmentId] = eta
        val index = currentList.indexOfFirst { it.assignmentId == assignmentId }
        if (index >= 0) {
            notifyItemChanged(index, PAYLOAD_ETA_UPDATE)
        }
    }

    /**
     * Update status for a specific truck (called on assignment_status_changed).
     * Creates a new list with the updated item for proper DiffUtil handling.
     */
    fun updateTruckStatus(assignmentId: String, newStatus: String) {
        val updatedList = currentList.map { truck ->
            if (truck.assignmentId == assignmentId) {
                truck.copy(status = newStatus)
            } else {
                truck
            }
        }
        submitList(updatedList)
    }

    override fun onBindViewHolder(holder: TruckViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_ETA_UPDATE)) {
            // Partial bind — only update ETA text
            val truck = getItem(position)
            holder.updateEta(etaMap[truck.assignmentId])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class TruckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvVehicleNumber: TextView = itemView.findViewById(R.id.tvVehicleNumber)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvDriverPhone: TextView = itemView.findViewById(R.id.tvDriverPhone)
        private val tvDriverRating: TextView = itemView.findViewById(R.id.tvDriverRating)
        private val layoutRating: LinearLayout = itemView.findViewById(R.id.layoutRating)
        private val ivDriverPhoto: ImageView = itemView.findViewById(R.id.ivDriverPhoto)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvEta: TextView = itemView.findViewById(R.id.tvEta)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
        private val btnCall: View = itemView.findViewById(R.id.btnCall)
        private val btnTrack: View = itemView.findViewById(R.id.btnTrack)

        fun bind(truck: AssignedTruckData) {
            tvVehicleNumber.text = truck.vehicleNumber
            tvDriverName.text = truck.driverName
            tvDriverPhone.text = truck.driverPhone
            
            // Driver Rating — real avg from DB, "New" for unrated drivers
            if (truck.driverRating != null) {
                tvDriverRating.text = String.format(java.util.Locale.US, "%.1f", truck.driverRating)
                layoutRating.visibility = View.VISIBLE
            } else {
                tvDriverRating.text = itemView.context.getString(R.string.rating_new_driver)
                layoutRating.visibility = View.VISIBLE
            }
            
            // Driver Profile Photo (Glide — cached, circular, with placeholder)
            if (!truck.driverProfilePhotoUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(truck.driverProfilePhotoUrl)
                    .apply(RequestOptions()
                        .transform(CircleCrop())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person))
                    .sizeMultiplier(0.5f)  // Load at half resolution for fast preview
                    .into(ivDriverPhoto)
            } else {
                ivDriverPhoto.setImageResource(R.drawable.ic_person)
            }

            // ETA
            updateEta(etaMap[truck.assignmentId])

            // Status display with color-coded badges
            bindStatus(truck.status)

            // Click handlers
            itemView.setOnClickListener { onTrackClick(truck) }

            btnCall.setOnClickListener {
                if (onCallClick != null) {
                    onCallClick.invoke(truck)
                } else {
                    // Default: launch system dialler — guard against blank phone
                    val phone = truck.driverPhone.trim()
                    if (phone.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phone")
                        }
                        itemView.context.startActivity(intent)
                    }
                }
            }

            btnTrack.setOnClickListener {
                onTrackClick(truck)
            }
        }

        fun updateEta(eta: String?) {
            if (eta != null) {
                tvEta.text = "ETA: $eta"
                tvEta.visibility = View.VISIBLE
            } else {
                tvEta.visibility = View.GONE
            }
        }

        private fun bindStatus(status: String) {
            when (status.lowercase(java.util.Locale.ROOT)) {
                "pending" -> {
                    tvStatus.text = "Waiting"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.warning_orange))
                    ivStatusIcon.setImageResource(R.drawable.ic_pending)
                    btnTrack.visibility = View.GONE
                }
                "driver_accepted", "heading_to_pickup" -> {
                    tvStatus.text = "Coming to pickup"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.warning_orange))
                    ivStatusIcon.setImageResource(R.drawable.ic_truck)
                    btnTrack.visibility = View.VISIBLE
                }
                "at_pickup", "loading_complete" -> {
                    tvStatus.text = "At pickup"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.primary_blue))
                    ivStatusIcon.setImageResource(R.drawable.ic_location)
                    btnTrack.visibility = View.VISIBLE
                }
                "in_transit" -> {
                    tvStatus.text = "In transit"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    ivStatusIcon.setImageResource(R.drawable.ic_truck)
                    btnTrack.visibility = View.VISIBLE
                }
                "completed" -> {
                    tvStatus.text = "Delivered ✓"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    ivStatusIcon.setImageResource(R.drawable.ic_check)
                    btnTrack.visibility = View.GONE
                }
                "driver_declined" -> {
                    tvStatus.text = "Declined"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.error_red))
                    ivStatusIcon.setImageResource(R.drawable.ic_error)
                    btnTrack.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = status.replace("_", " ")
                    tvStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
                    ivStatusIcon.setImageResource(R.drawable.ic_truck)
                    btnTrack.visibility = View.GONE
                }
            }
        }
    }

    class TruckDiffCallback : DiffUtil.ItemCallback<AssignedTruckData>() {
        override fun areItemsTheSame(oldItem: AssignedTruckData, newItem: AssignedTruckData): Boolean {
            return oldItem.assignmentId == newItem.assignmentId
        }

        override fun areContentsTheSame(oldItem: AssignedTruckData, newItem: AssignedTruckData): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val PAYLOAD_ETA_UPDATE = "eta_update"
    }
}
