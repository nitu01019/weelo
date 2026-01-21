package com.weelo.logistics.adapters

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.weelo.logistics.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Multi-Truck Selection Adapter (Rapido-Style)
 * =============================================
 * Expandable accordion adapter for selecting multiple truck types
 * 
 * Features:
 * - Expandable/collapsible truck type sections
 * - Nested RecyclerView for subtypes
 * - Real-time price calculation
 * - Visual selection feedback
 * 
 * Scalable: Efficient ViewHolder pattern with nested adapters
 * Modular: Each truck type is self-contained
 */
class MultiTruckSelectionAdapter(
    private val onSelectionChanged: (List<TruckTypeSelection>) -> Unit
) : RecyclerView.Adapter<MultiTruckSelectionAdapter.TruckTypeViewHolder>() {

    private val truckTypes = mutableListOf<TruckTypeSection>()
    private val expandedStates = mutableMapOf<String, Boolean>()
    
    fun setTruckTypes(types: List<TruckTypeSection>) {
        truckTypes.clear()
        truckTypes.addAll(types)
        // Initially expand first item if has selections, otherwise all collapsed
        types.forEachIndexed { index, section ->
            expandedStates[section.truckTypeId] = section.hasSelections() || index == 0
        }
        notifyDataSetChanged()
    }
    
    /**
     * Add a new truck type to the selection
     * 
     * Behavior:
     * - Collapses ALL existing truck types (they keep their data)
     * - Expands ONLY the new truck type
     * - Scrolls to the new truck type (handled by caller via callback)
     * 
     * @param section The truck type section to add
     * @return The position where the new truck was added (for scrolling)
     */
    fun addTruckType(section: TruckTypeSection): Int {
        if (truckTypes.none { it.truckTypeId == section.truckTypeId }) {
            // Collapse ALL existing truck types (keep their data)
            val previouslyExpanded = mutableListOf<Int>()
            truckTypes.forEachIndexed { index, existingSection ->
                if (expandedStates[existingSection.truckTypeId] == true) {
                    expandedStates[existingSection.truckTypeId] = false
                    previouslyExpanded.add(index)
                }
            }
            
            // Add new truck type
            truckTypes.add(section)
            val newPosition = truckTypes.size - 1
            
            // Expand ONLY the new truck type
            expandedStates[section.truckTypeId] = true
            
            // Notify changes - collapsed items first, then inserted
            previouslyExpanded.forEach { notifyItemChanged(it) }
            notifyItemInserted(newPosition)
            
            return newPosition
        }
        return -1
    }
    
    fun removeTruckType(truckTypeId: String) {
        val index = truckTypes.indexOfFirst { it.truckTypeId == truckTypeId }
        if (index >= 0) {
            truckTypes.removeAt(index)
            expandedStates.remove(truckTypeId)
            notifyItemRemoved(index)
        }
    }
    
    fun getAllSelections(): List<TruckTypeSelection> {
        return truckTypes.mapNotNull { section ->
            val selections = section.getSelectedSubtypes()
            if (selections.isNotEmpty()) {
                TruckTypeSelection(
                    truckTypeId = section.truckTypeId,
                    truckTypeName = section.displayName,
                    iconRes = section.iconRes,
                    subtypes = selections
                )
            } else null
        }
    }
    
    fun getTotalPrice(): Int {
        return truckTypes.sumOf { it.getTotalPrice() }
    }
    
    fun getTotalTruckCount(): Int {
        return truckTypes.sumOf { it.getTotalCount() }
    }
    
    fun clearAll() {
        truckTypes.forEach { it.clearSelections() }
        notifyDataSetChanged()
        onSelectionChanged(emptyList())
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TruckTypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_truck_type_expandable, parent, false)
        return TruckTypeViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TruckTypeViewHolder, position: Int) {
        holder.bind(truckTypes[position])
    }
    
    override fun getItemCount() = truckTypes.size
    
    inner class TruckTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerCard: MaterialCardView = itemView.findViewById(R.id.headerCard)
        private val truckIcon: ImageView = itemView.findViewById(R.id.truckTypeIcon)
        private val truckName: TextView = itemView.findViewById(R.id.truckTypeName)
        private val selectionCount: TextView = itemView.findViewById(R.id.selectionCount)
        private val totalPrice: TextView = itemView.findViewById(R.id.totalPrice)
        private val expandArrow: ImageView = itemView.findViewById(R.id.expandArrow)
        private val subtypesContainer: LinearLayout = itemView.findViewById(R.id.subtypesContainer)
        private val subtypesRecyclerView: RecyclerView = itemView.findViewById(R.id.subtypesRecyclerView)
        private val removeButton: ImageView = itemView.findViewById(R.id.removeButton)
        
        private var subtypeAdapter: TruckSubtypeAdapter? = null
        
        fun bind(section: TruckTypeSection) {
            // Set header content
            truckIcon.setImageResource(section.iconRes)
            truckName.text = section.displayName
            updateSelectionSummary(section)
            
            // Setup expand/collapse
            val isExpanded = expandedStates[section.truckTypeId] ?: false
            subtypesContainer.isVisible = isExpanded
            expandArrow.rotation = if (isExpanded) 180f else 0f
            
            // Header click to expand/collapse
            headerCard.setOnClickListener {
                val newState = !(expandedStates[section.truckTypeId] ?: false)
                expandedStates[section.truckTypeId] = newState
                
                // Animate arrow
                ObjectAnimator.ofFloat(expandArrow, "rotation", if (newState) 180f else 0f)
                    .setDuration(200)
                    .start()
                
                // Show/hide subtypes
                subtypesContainer.isVisible = newState
            }
            
            // Remove button
            removeButton.setOnClickListener {
                section.clearSelections()
                removeTruckType(section.truckTypeId)
                onSelectionChanged(getAllSelections())
            }
            
            // Setup subtypes RecyclerView
            setupSubtypesRecyclerView(section)
            
            // Update header style based on selection
            updateHeaderStyle(section.hasSelections())
        }
        
        private fun setupSubtypesRecyclerView(section: TruckTypeSection) {
            if (subtypeAdapter == null) {
                subtypesRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            }
            
            subtypeAdapter = TruckSubtypeAdapter(section.subtypes) { subtypeId, quantity, price ->
                section.updateQuantity(subtypeId, quantity)
                updateSelectionSummary(section)
                updateHeaderStyle(section.hasSelections())
                onSelectionChanged(getAllSelections())
            }
            
            subtypesRecyclerView.adapter = subtypeAdapter
        }
        
        private fun updateSelectionSummary(section: TruckTypeSection) {
            val count = section.getTotalCount()
            val price = section.getTotalPrice()
            
            if (count > 0) {
                selectionCount.text = "$count selected"
                selectionCount.isVisible = true
                totalPrice.text = formatPrice(price)
                totalPrice.isVisible = true
            } else {
                selectionCount.text = "Tap to select"
                selectionCount.isVisible = true
                totalPrice.isVisible = false
            }
        }
        
        private fun updateHeaderStyle(hasSelections: Boolean) {
            if (hasSelections) {
                headerCard.setCardBackgroundColor(0xFFFFFDE7.toInt())
                headerCard.strokeColor = 0xFFFFA000.toInt()
                headerCard.strokeWidth = 2
            } else {
                headerCard.setCardBackgroundColor(0xFFFFFFFF.toInt())
                headerCard.strokeColor = 0xFFE0E0E0.toInt()
                headerCard.strokeWidth = 1
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
 * Data class for a truck type section in the accordion
 */
data class TruckTypeSection(
    val truckTypeId: String,
    val displayName: String,
    val iconRes: Int,
    val subtypes: List<SubtypeItem>
) {
    private val quantities = mutableMapOf<String, Int>()
    
    init {
        subtypes.forEach { quantities[it.id] = it.initialQuantity }
    }
    
    fun updateQuantity(subtypeId: String, quantity: Int) {
        quantities[subtypeId] = quantity
    }
    
    fun getQuantity(subtypeId: String): Int = quantities[subtypeId] ?: 0
    
    fun hasSelections(): Boolean = quantities.values.any { it > 0 }
    
    fun getTotalCount(): Int = quantities.values.sum()
    
    fun getTotalPrice(): Int {
        return subtypes.sumOf { subtype ->
            (quantities[subtype.id] ?: 0) * subtype.price
        }
    }
    
    fun getSelectedSubtypes(): List<SubtypeSelection> {
        return subtypes.mapNotNull { subtype ->
            val qty = quantities[subtype.id] ?: 0
            if (qty > 0) {
                SubtypeSelection(
                    subtypeId = subtype.id,
                    subtypeName = subtype.name,
                    quantity = qty,
                    pricePerUnit = subtype.price
                )
            } else null
        }
    }
    
    fun clearSelections() {
        quantities.keys.forEach { quantities[it] = 0 }
    }
}

/**
 * Data class for truck type selection result
 */
data class TruckTypeSelection(
    val truckTypeId: String,
    val truckTypeName: String,
    val iconRes: Int,
    val subtypes: List<SubtypeSelection>
) {
    fun getTotalCount(): Int = subtypes.sumOf { it.quantity }
    fun getTotalPrice(): Int = subtypes.sumOf { it.quantity * it.pricePerUnit }
}

/**
 * Data class for subtype selection
 */
data class SubtypeSelection(
    val subtypeId: String,
    val subtypeName: String,
    val quantity: Int,
    val pricePerUnit: Int
)
