package com.weelo.logistics.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.R

/**
 * Adapter for Truck Type Picker Dialog - Rapido Style Grid
 * =========================================================
 * Displays available truck types in a 3-column grid with icons and names
 * for the "Add Another Truck Type" dialog
 * 
 * Features:
 * - Grid layout with truck icons on top, names below
 * - Same truck icons as main truck type cards
 * - Search/filter support via updateItems()
 * - Clean tap feedback
 * - Modular and scalable design
 */
class TruckTypePickerAdapter(
    private var truckTypes: List<TruckTypePickerItem>,
    private val onTruckTypeSelected: (String) -> Unit
) : RecyclerView.Adapter<TruckTypePickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_truck_type_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(truckTypes[position])
    }

    override fun getItemCount() = truckTypes.size

    /**
     * Update the list of truck types (used for search filtering)
     * @param newItems filtered list of truck type items
     */
    fun updateItems(newItems: List<TruckTypePickerItem>) {
        truckTypes = newItems
        // Always use notifyDataSetChanged() for filter swaps:
        // notifyItemRangeChanged with no payload skips rebind for same-position items,
        // causing stale visuals when filtering changes item content but not count.
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: FrameLayout = itemView.findViewById(R.id.truckTypePickerCard)
        private val icon: ImageView = itemView.findViewById(R.id.truckTypePickerIcon)
        private val name: TextView = itemView.findViewById(R.id.truckTypePickerName)
        private val nameInCard: TextView? = itemView.findViewById(R.id.truckNameInCard)
        private val tonnageInCard: TextView? = itemView.findViewById(R.id.truckTonnageInCard)

        fun bind(item: TruckTypePickerItem) {
            name.text = item.displayName
            icon.setImageResource(item.iconRes)
            
            // Set name and tonnage inside card
            nameInCard?.text = item.displayName
            tonnageInCard?.text = getTonnageForTruckType(item.truckTypeId)
            
            cardContainer.setOnClickListener {
                onTruckTypeSelected(item.truckTypeId)
            }
        }
        
        private fun getTonnageForTruckType(truckTypeId: String): String {
            return when (truckTypeId.lowercase()) {
                "open" -> "7.5 - 43 Ton"
                "container" -> "7.5 - 30 Ton"
                "lcv" -> "2.5 - 7 Ton"
                "mini" -> "0.75 - 2 Ton"
                "trailer" -> "20 - 40 Ton"
                "tipper" -> "6 - 30 Ton"
                "tanker" -> "12 - 30 Ton"
                "dumper" -> "17 - 25 Ton"
                "bulker" -> "20 - 36 Ton"
                else -> ""
            }
        }
    }
}

/**
 * Data class for truck type picker item
 * @param truckTypeId unique identifier for the truck type (e.g., "open", "container")
 * @param displayName user-friendly name to display (e.g., "Open Body", "Container")
 * @param description brief description of the truck type (used for search filtering)
 * @param iconRes drawable resource ID for the truck icon
 */
data class TruckTypePickerItem(
    val truckTypeId: String,
    val displayName: String,
    val description: String,
    val iconRes: Int
)
