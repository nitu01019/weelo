package com.weelo.logistics.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.weelo.logistics.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Rapido-Style Adapter for truck subtypes
 * ========================================
 * Features:
 * - Clean Material Design cards
 * - Yellow selection highlight with border
 * - Smooth quantity selector
 * - Price display in green
 * 
 * Scalable: Uses efficient ViewHolder pattern
 * Modular: Easy to customize appearance via XML
 */
class TruckSubtypeAdapter(
    private val subtypes: List<SubtypeItem>,
    private val onQuantityChanged: (String, Int, Int) -> Unit  // subtypeId, quantity, price
) : RecyclerView.Adapter<TruckSubtypeAdapter.ViewHolder>() {

    private val quantities = mutableMapOf<String, Int>()
    
    init {
        // Initialize quantities from subtypes
        subtypes.forEach { quantities[it.id] = it.initialQuantity }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_truck_subtype_rapido, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(subtypes[position])
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else if (payloads.contains("quantity")) {
            // Partial update for quantity only
            holder.updateQuantity(subtypes[position])
        } else {
            // Unknown payload — fall back to full bind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = subtypes.size

    fun getSelectedItems(): Map<String, Int> {
        return quantities.filter { it.value > 0 }
    }
    
    fun getTotalPrice(): Int {
        return subtypes.sumOf { subtype ->
            (quantities[subtype.id] ?: 0) * subtype.price
        }
    }
    
    fun getTotalCount(): Int {
        return quantities.values.sum()
    }
    
    fun clearAll() {
        quantities.keys.forEach { quantities[it] = 0 }
        notifyItemRangeChanged(0, subtypes.size, "quantity")
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.subtypeCard)
        private val icon: ImageView = itemView.findViewById(R.id.subtypeIcon)
        private val name: TextView = itemView.findViewById(R.id.subtypeName)
        private val capacity: TextView = itemView.findViewById(R.id.subtypeCapacity)
        private val price: TextView = itemView.findViewById(R.id.subtypePrice)
        private val minusBtn: TextView = itemView.findViewById(R.id.minusButton)
        private val plusBtn: TextView = itemView.findViewById(R.id.plusButton)
        private val quantityText: TextView = itemView.findViewById(R.id.quantityText)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        fun bind(subtype: SubtypeItem) {
            // Set content
            name.text = subtype.name
            capacity.text = subtype.capacity
            price.text = formatPrice(subtype.price)
            icon.setImageResource(subtype.iconRes)
            
            updateQuantity(subtype)
            setupClickListeners(subtype)
        }
        
        fun updateQuantity(subtype: SubtypeItem) {
            val qty = quantities[subtype.id] ?: 0
            quantityText.text = qty.toString()
            
            // Update card highlight based on selection
            updateCardStyle(qty > 0)
        }
        
        private fun setupClickListeners(subtype: SubtypeItem) {
            
            // Minus button click
            minusBtn.setOnClickListener {
                val currentQty = quantities[subtype.id] ?: 0
                if (currentQty > 0) {
                    quantities[subtype.id] = currentQty - 1
                    quantityText.text = (currentQty - 1).toString()
                    updateCardStyle(currentQty - 1 > 0)
                    onQuantityChanged(subtype.id, currentQty - 1, subtype.price)
                }
            }
            
            // Plus button click
            plusBtn.setOnClickListener {
                val currentQty = quantities[subtype.id] ?: 0
                if (currentQty < 10) { // Max 10 trucks per subtype
                    quantities[subtype.id] = currentQty + 1
                    quantityText.text = (currentQty + 1).toString()
                    updateCardStyle(true)
                    onQuantityChanged(subtype.id, currentQty + 1, subtype.price)
                }
            }
            
            // Tap card to increment (like Rapido)
            card.setOnClickListener {
                val currentQty = quantities[subtype.id] ?: 0
                if (currentQty < 10) {
                    quantities[subtype.id] = currentQty + 1
                    quantityText.text = (currentQty + 1).toString()
                    updateCardStyle(true)
                    onQuantityChanged(subtype.id, currentQty + 1, subtype.price)
                }
            }
        }
        
        /**
         * Update card appearance based on selection state
         * Selected: Yellow background, orange border, visible indicator
         * Unselected: White background, gray border
         */
        private fun updateCardStyle(isSelected: Boolean) {
            if (isSelected) {
                // Selected state - Rapido yellow theme
                card.setCardBackgroundColor(0xFFFFFDE7.toInt()) // Light yellow
                card.strokeColor = 0xFFFFA000.toInt() // Orange border
                card.strokeWidth = 2
                card.cardElevation = 4f
                selectionIndicator.visibility = View.VISIBLE
            } else {
                // Unselected state
                card.setCardBackgroundColor(0xFFFFFFFF.toInt()) // White
                card.strokeColor = 0xFFE0E0E0.toInt() // Gray border
                card.strokeWidth = 1
                card.cardElevation = 2f
                selectionIndicator.visibility = View.GONE
            }
        }
        
        private fun formatPrice(price: Int): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            formatter.maximumFractionDigits = 0
            return formatter.format(price)
        }
    }
}

/**
 * Data class for subtype item
 */
data class SubtypeItem(
    val id: String,           // e.g., "17_feet" 
    val name: String,         // e.g., "17 Feet"
    val capacity: String,     // e.g., "7.5 - 10 Ton"
    val price: Int,           // Price from backend
    val iconRes: Int,         // Drawable resource
    val initialQuantity: Int = 0
)
