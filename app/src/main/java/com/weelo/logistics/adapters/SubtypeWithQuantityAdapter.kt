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
 * Used in Step 2 of the Rapido-style truck type picker dialog
 * 
 * Features:
 * - Shows subtype image, name, and capacity
 * - Inline quantity selector (- / qty / +)
 * - Callback for quantity changes
 * - Clean, modular design
 * - Scalable for any number of subtypes
 */
class SubtypeWithQuantityAdapter(
    private val subtypes: List<SubtypeQuantityItem>,
    private val truckTypeIconRes: Int,
    private val quantities: MutableMap<String, Int>,
    private val onQuantityChanged: (subtypeId: String, newQuantity: Int) -> Unit
) : RecyclerView.Adapter<SubtypeWithQuantityAdapter.SubtypeViewHolder>() {

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

    override fun onBindViewHolder(holder: SubtypeViewHolder, position: Int) {
        val subtype = subtypes[position]
        val currentQty = quantities[subtype.id] ?: 0
        
        // Set subtype info
        holder.subtypeImage.setImageResource(truckTypeIconRes)
        holder.subtypeName.text = subtype.name
        holder.subtypeCapacity.text = subtype.capacity
        
        // Set quantity display
        holder.quantityText.text = currentQty.toString()
        
        // Update minus button appearance based on quantity
        holder.btnDecrease.alpha = if (currentQty > 0) 1f else 0.4f
        holder.btnDecrease.isEnabled = currentQty > 0
        
        // Decrease quantity
        holder.btnDecrease.setOnClickListener {
            val qty = quantities[subtype.id] ?: 0
            if (qty > 0) {
                val newQty = qty - 1
                quantities[subtype.id] = newQty
                holder.quantityText.text = newQty.toString()
                holder.btnDecrease.alpha = if (newQty > 0) 1f else 0.4f
                holder.btnDecrease.isEnabled = newQty > 0
                onQuantityChanged(subtype.id, newQty)
            }
        }
        
        // Increase quantity
        holder.btnIncrease.setOnClickListener {
            val qty = quantities[subtype.id] ?: 0
            val newQty = qty + 1
            quantities[subtype.id] = newQty
            holder.quantityText.text = newQty.toString()
            holder.btnDecrease.alpha = 1f
            holder.btnDecrease.isEnabled = true
            onQuantityChanged(subtype.id, newQty)
        }
    }

    override fun getItemCount(): Int = subtypes.size
}
