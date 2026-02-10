package com.weelo.logistics.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R

/**
 * Data class for custom booking truck type card
 *
 * @param id Unique identifier matching TruckSubtypesConfig IDs
 * @param displayName User-friendly name
 * @param iconResId Drawable resource for truck icon
 * @param subtypesCount Number of available subtypes
 * @param totalQuantity Total trucks selected for this type (mutable for updates)
 */
data class CustomTruckTypeItem(
    val id: String,
    val displayName: String,
    val iconResId: Int,
    val subtypesCount: Int,
    var totalQuantity: Int = 0
)

/**
 * Adapter for displaying truck type cards in a grid for Custom Booking
 *
 * SCALABILITY:
 * - RecyclerView with ViewHolder pattern for smooth scrolling
 * - Efficient partial updates with notifyItemChanged()
 *
 * SECURITY:
 * - Immutable list reference (only quantities are mutable)
 * - Null-safe view binding
 *
 * MODULARITY:
 * - Clean callback interface for click handling
 * - Self-contained quantity management
 *
 * EASY UNDERSTANDING:
 * - Clear visual feedback for selected items (background color + badge)
 * - Intuitive quantity display
 */
class CustomTruckTypeAdapter(
    private val truckTypes: List<CustomTruckTypeItem>,
    private val onTruckTypeClick: (CustomTruckTypeItem) -> Unit
) : RecyclerView.Adapter<CustomTruckTypeAdapter.TruckTypeViewHolder>() {

    inner class TruckTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val truckCard: CardView? = itemView.findViewById(R.id.truckCard)
        val truckIcon: ImageView? = itemView.findViewById(R.id.truckIcon)
        val truckName: TextView? = itemView.findViewById(R.id.truckName)
        val subtypesCount: TextView? = itemView.findViewById(R.id.subtypesCount)
        val quantityBadge: TextView? = itemView.findViewById(R.id.quantityBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TruckTypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_truck_card, parent, false)
        return TruckTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TruckTypeViewHolder, position: Int) {
        if (position < 0 || position >= truckTypes.size) return

        val truckType = truckTypes[position]
        val context = holder.itemView.context

        // Set truck info with null safety
        holder.truckIcon?.setImageResource(truckType.iconResId)
        holder.truckName?.text = truckType.displayName
        holder.subtypesCount?.text = "${truckType.subtypesCount} options"

        // Show/hide quantity badge based on selection
        val quantity = truckType.totalQuantity.coerceAtLeast(0)

        if (quantity > 0) {
            holder.quantityBadge?.visibility = View.VISIBLE
            holder.quantityBadge?.text = quantity.toString()

            // Highlight card when has selections
            holder.truckCard?.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.selected_card_bg)
            )
        } else {
            holder.quantityBadge?.visibility = View.GONE
            holder.truckCard?.setCardBackgroundColor(
                ContextCompat.getColor(context, android.R.color.white)
            )
        }

        // Click handler
        holder.truckCard?.setOnClickListener {
            onTruckTypeClick(truckType)
        }
    }

    override fun getItemCount(): Int = truckTypes.size

    /**
     * Update the quantity for a specific truck type and refresh the badge
     * Thread-safe: only updates if index is valid
     *
     * @param truckTypeId ID of the truck type to update
     * @param quantity New total quantity (will be coerced to >= 0)
     */
    fun updateQuantity(truckTypeId: String, quantity: Int) {
        val index = truckTypes.indexOfFirst { it.id == truckTypeId }
        if (index >= 0 && index < truckTypes.size) {
            truckTypes[index].totalQuantity = quantity.coerceAtLeast(0)
            notifyItemChanged(index)
        }
    }

    /**
     * Get total trucks selected across all types
     */
    fun getTotalSelectedTrucks(): Int {
        return truckTypes.sumOf { it.totalQuantity.coerceAtLeast(0) }
    }

    /**
     * Reset all quantities to zero
     */
    fun resetAllQuantities() {
        truckTypes.forEach { it.totalQuantity = 0 }
        notifyDataSetChanged()
    }

    /**
     * Get list of selected truck types with quantities > 0
     */
    fun getSelectedTruckTypes(): List<CustomTruckTypeItem> {
        return truckTypes.filter { it.totalQuantity > 0 }
    }
}
