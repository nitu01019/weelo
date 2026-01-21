package com.weelo.logistics.presentation.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.api.AssignedTruckData

/**
 * Adapter for displaying assigned trucks in booking request screen
 */
class AssignedTrucksAdapter(
    private val onTruckClick: (AssignedTruckData) -> Unit
) : ListAdapter<AssignedTruckData, AssignedTrucksAdapter.TruckViewHolder>(TruckDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TruckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assigned_truck, parent, false)
        return TruckViewHolder(view)
    }

    override fun onBindViewHolder(holder: TruckViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TruckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvVehicleNumber: TextView = itemView.findViewById(R.id.tvVehicleNumber)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvDriverPhone: TextView = itemView.findViewById(R.id.tvDriverPhone)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
        private val btnCall: View = itemView.findViewById(R.id.btnCall)
        private val btnTrack: View = itemView.findViewById(R.id.btnTrack)

        fun bind(truck: AssignedTruckData) {
            tvVehicleNumber.text = truck.vehicleNumber
            tvDriverName.text = truck.driverName
            tvDriverPhone.text = truck.driverPhone

            // Status display
            when (truck.status.lowercase()) {
                "pending" -> {
                    tvStatus.text = "Waiting for driver"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.warning_orange))
                    ivStatusIcon.setImageResource(R.drawable.ic_pending)
                    btnTrack.visibility = View.GONE
                }
                "driver_accepted" -> {
                    tvStatus.text = "Driver accepted"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    ivStatusIcon.setImageResource(R.drawable.ic_check)
                    btnTrack.visibility = View.VISIBLE
                }
                "en_route_pickup" -> {
                    tvStatus.text = "Coming to pickup"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.primary_blue))
                    ivStatusIcon.setImageResource(R.drawable.ic_truck)
                    btnTrack.visibility = View.VISIBLE
                }
                "at_pickup" -> {
                    tvStatus.text = "At pickup location"
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
                    tvStatus.text = "Delivered"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.success_green))
                    ivStatusIcon.setImageResource(R.drawable.ic_check)
                    btnTrack.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = truck.status
                    tvStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
                }
            }

            // Click handlers
            itemView.setOnClickListener { onTruckClick(truck) }
            
            btnCall.setOnClickListener {
                // Intent to call driver
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:${truck.driverPhone}")
                }
                itemView.context.startActivity(intent)
            }

            btnTrack.setOnClickListener {
                onTruckClick(truck)
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
}
