package com.weelo.logistics.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R

/**
 * Data class for subtype item in the quantity picker
 * @param id unique identifier for the subtype
 * @param name display name of the subtype
 * @param capacity capacity description (e.g., "7-8 Tons")
 */
data class SubtypeQuantityItem(
    val id: String,
    val name: String,
    val capacity: String
)

/**
 * Adapter for displaying truck subtypes with inline quantity selectors
 *
 * SCALABILITY:
 * - RecyclerView for efficient rendering with many subtypes
 * - Debounce-friendly callback design
 *
 * SECURITY:
 * - Enforces maximum quantity per subtype (100)
 * - Prevents negative quantities
 *
 * MODULARITY:
 * - Generic design works with any truck type
 * - Clear callback interface for quantity changes
 */
class SubtypeWithQuantityAdapter(
    private val subtypes: List<SubtypeQuantityItem>,
    private val truckTypeIconRes: Int,
    private val quantities: MutableMap<String, Int>,
    private val onQuantityChanged: (subtypeId: String, newQuantity: Int) -> Unit
) : RecyclerView.Adapter<SubtypeWithQuantityAdapter.SubtypeViewHolder>() {

    companion object {
        private const val MAX_QUANTITY = 100
        private const val MIN_QUANTITY = 0
    }

    inner class SubtypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subtypeImage: ImageView = itemView.findViewById(R.id.subtypeImage)
        val subtypeName: TextView = itemView.findViewById(R.id.subtypeName)
        val subtypeCapacity: TextView = itemView.findViewById(R.id.subtypeCapacity)
        val btnDecrease: TextView = itemView.findViewById(R.id.btnDecrease)
        val quantityText: TextView = itemView.findViewById(R.id.quantityText)
        val btnIncrease: TextView = itemView.findViewById(R.id.btnIncrease)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subtype_with_quantity, parent, false)
        return SubtypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtypeViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains("quantity")) {
            // Partial update: only refresh quantity display and button states
            val subtype = subtypes[position]
            val currentQty = quantities[subtype.id]?.coerceIn(MIN_QUANTITY, MAX_QUANTITY) ?: 0
            holder.quantityText.text = currentQty.toString()
            updateButtonStates(holder, currentQty)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: SubtypeViewHolder, position: Int) {
        val subtype = subtypes[position]
        val currentQty = quantities[subtype.id]?.coerceIn(MIN_QUANTITY, MAX_QUANTITY) ?: 0

        // Set subtype info
        holder.subtypeImage.setImageResource(truckTypeIconRes)
        holder.subtypeName.text = subtype.name
        holder.subtypeCapacity.text = subtype.capacity

        // Set quantity display
        holder.quantityText.text = currentQty.toString()

        // Update button states
        updateButtonStates(holder, currentQty)

        // Decrease quantity
        holder.btnDecrease.setOnClickListener {
            val qty = quantities[subtype.id] ?: 0
            if (qty > MIN_QUANTITY) {
                val newQty = (qty - 1).coerceIn(MIN_QUANTITY, MAX_QUANTITY)
                quantities[subtype.id] = newQty
                holder.quantityText.text = newQty.toString()
                updateButtonStates(holder, newQty)
                onQuantityChanged(subtype.id, newQty)
            }
        }

        // Increase quantity
        holder.btnIncrease.setOnClickListener {
            val qty = quantities[subtype.id] ?: 0
            if (qty < MAX_QUANTITY) {
                val newQty = (qty + 1).coerceIn(MIN_QUANTITY, MAX_QUANTITY)
                quantities[subtype.id] = newQty
                holder.quantityText.text = newQty.toString()
                updateButtonStates(holder, newQty)
                onQuantityChanged(subtype.id, newQty)
            }
        }
    }

    private fun updateButtonStates(holder: SubtypeViewHolder, quantity: Int) {
        // Decrease button
        holder.btnDecrease.alpha = if (quantity > MIN_QUANTITY) 1f else 0.4f
        holder.btnDecrease.isEnabled = quantity > MIN_QUANTITY

        // Increase button
        holder.btnIncrease.alpha = if (quantity < MAX_QUANTITY) 1f else 0.4f
        holder.btnIncrease.isEnabled = quantity < MAX_QUANTITY
    }

    override fun getItemCount(): Int = subtypes.size

    /**
     * Get total quantity selected across all subtypes
     */
    fun getTotalQuantity(): Int {
        return quantities.values.sum()
    }

    /**
     * Reset all quantities to zero
     */
    fun resetAllQuantities() {
        quantities.clear()
        notifyItemRangeChanged(0, subtypes.size, "quantity")
    }
}
