package com.weelo.logistics.presentation.trucks

import com.weelo.logistics.data.models.SelectedTruckItem
import com.weelo.logistics.data.models.TruckSubtypesConfig

/**
 * TruckSelectionHelper - Manages truck selection state and operations
 * 
 * Extracted from TruckTypesActivity for better modularity.
 * Contains:
 * - Selection state management
 * - Adding/removing trucks from selection
 * - Converting selections for dialogs
 */
class TruckSelectionHelper {

    private val selectedTrucksList = mutableListOf<SelectedTruckItem>()
    
    private val truckSelections = mutableMapOf<String, MutableMap<String, Int>>().apply {
        TruckSubtypesConfig.getAllDialogTruckTypes().forEach { truckType ->
            put(truckType, mutableMapOf())
        }
    }

    /**
     * Get current selected trucks list
     */
    fun getSelectedTrucks(): List<SelectedTruckItem> = selectedTrucksList.toList()

    /**
     * Get mutable selected trucks list
     */
    fun getSelectedTrucksMutable(): MutableList<SelectedTruckItem> = selectedTrucksList

    /**
     * Get selections map for a specific truck type
     */
    fun getSelectionsForType(truckTypeId: String): MutableMap<String, Int>? {
        return truckSelections[truckTypeId]
    }

    /**
     * Get all truck selections
     */
    fun getAllSelections(): Map<String, MutableMap<String, Int>> = truckSelections

    /**
     * Check if any trucks are selected
     */
    fun hasSelections(): Boolean {
        return selectedTrucksList.isNotEmpty() || truckSelections.values.any { it.isNotEmpty() }
    }

    /**
     * Get total count of selected trucks
     */
    fun getTotalTruckCount(): Int {
        return selectedTrucksList.sumOf { it.quantity }
    }

    /**
     * Clear all selections
     */
    fun clearAllSelections() {
        selectedTrucksList.clear()
        truckSelections.values.forEach { it.clear() }
    }

    /**
     * Add selected trucks to the list
     * Called when user confirms selection in the bottom sheet
     */
    fun addToSelectedTrucks(truckTypeId: String, selectedSubtypes: Map<String, Int>) {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId) ?: return
        val iconResource = TruckPricingHelper.getTruckIconResource(truckTypeId)
        
        selectedSubtypes.forEach { (specification, quantity) ->
            if (quantity <= 0) return@forEach
            
            val uniqueId = "${truckTypeId}_${specification}_${System.currentTimeMillis()}"
            
            // Check if this truck already exists (update quantity instead of adding new)
            val existingIndex = selectedTrucksList.indexOfFirst { 
                it.truckTypeId == truckTypeId && it.specification == specification 
            }
            
            if (existingIndex != -1) {
                // Update existing truck quantity
                selectedTrucksList[existingIndex].quantity = quantity
            } else {
                // Add new truck
                val selectedTruck = SelectedTruckItem(
                    id = uniqueId,
                    truckTypeId = truckTypeId,
                    truckTypeName = config.displayName,
                    specification = specification,
                    iconResource = iconResource,
                    quantity = quantity
                )
                selectedTrucksList.add(selectedTruck)
            }
        }
    }

    /**
     * Add a truck with specific quantity to the current selection
     */
    fun addTruckToSelectionWithQuantity(
        truckTypeId: String, 
        subtypeName: String, 
        quantity: Int
    ) {
        val config = TruckSubtypesConfig.getConfigById(truckTypeId)
        val displayName = config?.displayName ?: truckTypeId
        
        // Check if same truck type + specification already exists
        val existingIndex = selectedTrucksList.indexOfFirst { 
            it.truckTypeId == truckTypeId && it.specification == subtypeName 
        }
        
        if (existingIndex >= 0) {
            // Update quantity of existing item
            selectedTrucksList[existingIndex].quantity = quantity
        } else {
            // Add new item with specified quantity
            val newItem = SelectedTruckItem(
                id = "${truckTypeId}_${subtypeName}_${System.currentTimeMillis()}",
                truckTypeId = truckTypeId,
                truckTypeName = displayName,
                specification = subtypeName,
                iconResource = TruckPricingHelper.getTruckIconResource(truckTypeId),
                quantity = quantity
            )
            selectedTrucksList.add(newItem)
        }
    }

    /**
     * Remove a truck from selection
     */
    fun removeTruck(truckTypeId: String, specification: String) {
        selectedTrucksList.removeAll { 
            it.truckTypeId == truckTypeId && it.specification == specification 
        }
        truckSelections[truckTypeId]?.remove(specification)
    }

    /**
     * Update quantity for a specific truck
     */
    fun updateQuantity(truckTypeId: String, specification: String, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeTruck(truckTypeId, specification)
            return
        }

        val existingIndex = selectedTrucksList.indexOfFirst { 
            it.truckTypeId == truckTypeId && it.specification == specification 
        }
        
        if (existingIndex >= 0) {
            selectedTrucksList[existingIndex].quantity = newQuantity
        }
        
        truckSelections[truckTypeId]?.set(specification, newQuantity)
    }

    /**
     * Convert current selections to ArrayList for intent extras
     */
    fun toArrayList(): ArrayList<SelectedTruckItem> {
        return ArrayList(selectedTrucksList)
    }

    /**
     * Calculate total price of all selected trucks
     */
    fun calculateTotalPrice(distanceKm: Int): Int {
        return selectedTrucksList.sumOf { truck ->
            val basePrice = TruckPricingHelper.calculateBasePrice(
                truck.truckTypeId, 
                truck.specification, 
                distanceKm
            )
            basePrice * truck.quantity
        }
    }
}
