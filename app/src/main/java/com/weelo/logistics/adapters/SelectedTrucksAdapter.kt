package com.weelo.logistics.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R
import com.weelo.logistics.data.models.SelectedTruckItem

/**
 * Adapter for displaying selected trucks in a horizontal RecyclerView
 * 
 * Uses ListAdapter with DiffUtil for efficient updates
 * Designed for scalability with proper ViewHolder pattern
 */
class SelectedTrucksAdapter(
    private val onQuantityChanged: (SelectedTruckItem, Int) -> Unit,
    private val onRemove: (SelectedTruckItem) -> Unit
) : ListAdapter<SelectedTruckItem, SelectedTrucksAdapter.SelectedTruckViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedTruckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_truck, parent, false)
        return SelectedTruckViewHolder(view, onQuantityChanged, onRemove)
    }

    override fun onBindViewHolder(holder: SelectedTruckViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for selected truck items
     */
    class SelectedTruckViewHolder(
        itemView: View,
        private val onQuantityChanged: (SelectedTruckItem, Int) -> Unit,
        private val onRemove: (SelectedTruckItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val card: CardView = itemView.findViewById(R.id.selectedTruckCard)
        private val truckTypeName: TextView = itemView.findViewById(R.id.truckTypeName)
        private val truckIcon: ImageView = itemView.findViewById(R.id.truckIcon)
        private val truckSpecification: TextView = itemView.findViewById(R.id.truckSpecification)
        private val quantityText: TextView = itemView.findViewById(R.id.quantityText)
        private val plusButton: TextView = itemView.findViewById(R.id.plusButton)
        private val minusButton: TextView = itemView.findViewById(R.id.minusButton)
        private val removeButton: ImageView = itemView.findViewById(R.id.removeButton)

        fun bind(item: SelectedTruckItem) {
            truckTypeName.text = item.truckTypeName
            truckIcon.setImageResource(item.iconResource)
            truckSpecification.text = item.specification
            quantityText.text = item.quantity.toString()

            // Plus button - increase quantity
            plusButton.setOnClickListener {
                val newQuantity = item.quantity + 1
                onQuantityChanged(item, newQuantity)
            }

            // Minus button - decrease quantity (minimum 1)
            minusButton.setOnClickListener {
                if (item.quantity > 1) {
                    val newQuantity = item.quantity - 1
                    onQuantityChanged(item, newQuantity)
                }
            }

            // Remove button - delete this truck
            removeButton.setOnClickListener {
                onRemove(item)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class DiffCallback : DiffUtil.ItemCallback<SelectedTruckItem>() {
        override fun areItemsTheSame(oldItem: SelectedTruckItem, newItem: SelectedTruckItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SelectedTruckItem, newItem: SelectedTruckItem): Boolean {
            return oldItem == newItem
        }
    }
}
