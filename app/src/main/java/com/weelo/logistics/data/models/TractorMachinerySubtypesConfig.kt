package com.weelo.logistics.data.models

/**
 * Centralized configuration for all tractor machinery types and their subtypes
 * This is SEPARATE from trucks - contains ONLY AGRICULTURAL MACHINERY (for farming)
 * Construction machinery (JCB, Loader, Excavator) should be in separate JCB section
 */
object TractorMachinerySubtypesConfig {
    
    val TRACTOR = TractorMachineryConfig(
        id = "tractor",
        displayName = "Tractor",
        subtypes = listOf(
            "20-30 HP", "31-40 HP", "41-50 HP", "51-60 HP",
            "61-75 HP", "76-90 HP", "91-110 HP", "110+ HP"
        )
    )
    
    val TROLLEY = TractorMachineryConfig(
        id = "trolley",
        displayName = "Trolley",
        subtypes = listOf(
            "2 Wheel Trolley - Small (1-2 Ton)", "2 Wheel Trolley - Medium (3-4 Ton)", "2 Wheel Trolley - Large (5-6 Ton)",
            "4 Wheel Trolley - Small (2-3 Ton)", "4 Wheel Trolley - Medium (4-6 Ton)", "4 Wheel Trolley - Large (7-10 Ton)",
            "Hydraulic Trolley", "Tipper Trolley"
        )
    )
    
    val THRESHER = TractorMachineryConfig(
        id = "thresher",
        displayName = "Thresher",
        subtypes = listOf(
            "Wheat Thresher", "Rice Thresher", "Corn/Maize Thresher",
            "Multi-Crop Thresher", "Portable Thresher", "Stationary Thresher",
            "Power Thresher", "Pedal Thresher"
        )
    )
    
    val HARVESTER = TractorMachineryConfig(
        id = "harvester",
        displayName = "Harvester",
        subtypes = listOf(
            "Combine Harvester - Small", "Combine Harvester - Medium", "Combine Harvester - Large",
            "Cotton Harvester", "Sugarcane Harvester", "Potato Harvester",
            "Forage Harvester", "Reaper (Crop Cutting)"
        )
    )
    
    val CULTIVATOR = TractorMachineryConfig(
        id = "cultivator",
        displayName = "Cultivator",
        subtypes = listOf(
            "Disc Cultivator", "Spring Cultivator", "Rotary Cultivator",
            "Power Tiller", "Heavy Duty Cultivator", "Light Cultivator",
            "Field Cultivator", "Row Cultivator"
        )
    )
    
    val SEEDER = TractorMachineryConfig(
        id = "seeder",
        displayName = "Seeder/Planter",
        subtypes = listOf(
            "Seed Drill", "Zero Till Drill", "Multi-Crop Planter",
            "Pneumatic Planter", "Precision Planter", "Broadcast Seeder",
            "Row Planter", "Seed Cum Fertilizer Drill"
        )
    )
    
    val SPRAYER = TractorMachineryConfig(
        id = "sprayer",
        displayName = "Sprayer",
        subtypes = listOf(
            "Boom Sprayer", "Orchard Sprayer", "High Pressure Sprayer",
            "Knapsack Sprayer - Motorized", "Tractor Mounted Sprayer", "Self-Propelled Sprayer",
            "Air Blast Sprayer", "Mist Sprayer"
        )
    )
    
    val ROTAVATOR = TractorMachineryConfig(
        id = "rotavator",
        displayName = "Rotavator",
        subtypes = listOf(
            "Light Duty Rotavator", "Medium Duty Rotavator", "Heavy Duty Rotavator",
            "Standard Rotavator - 4 Feet", "Standard Rotavator - 5 Feet", "Standard Rotavator - 6 Feet",
            "Standard Rotavator - 7 Feet", "Reverse Rotavator"
        )
    )
    
    val PLOUGH = TractorMachineryConfig(
        id = "plough",
        displayName = "Plough",
        subtypes = listOf(
            "Disc Plough - 2 Disc", "Disc Plough - 3 Disc", "Disc Plough - 4 Disc",
            "Mould Board Plough", "Reversible Plough", "Chisel Plough",
            "Sub-Soiler Plough", "Harrow"
        )
    )
    
    val BALER = TractorMachineryConfig(
        id = "baler",
        displayName = "Baler",
        subtypes = listOf(
            "Round Baler - Small", "Round Baler - Medium", "Round Baler - Large",
            "Square Baler - Small", "Square Baler - Large", "Mini Baler",
            "Silage Baler", "Hay Baler"
        )
    )
    
    /**
     * Get tractor machinery config by ID
     */
    fun getConfigById(id: String): TractorMachineryConfig? {
        return when (id) {
            "tractor" -> TRACTOR
            "trolley" -> TROLLEY
            "thresher" -> THRESHER
            "harvester" -> HARVESTER
            "cultivator" -> CULTIVATOR
            "seeder" -> SEEDER
            "sprayer" -> SPRAYER
            "rotavator" -> ROTAVATOR
            "plough" -> PLOUGH
            "baler" -> BALER
            else -> null
        }
    }
    
    /**
     * Get all tractor machinery type IDs that have dialogs
     */
    fun getAllDialogMachineryTypes(): List<String> {
        return listOf("tractor", "trolley", "thresher", "harvester", "cultivator", "seeder", "sprayer", "rotavator", "plough", "baler")
    }
}
