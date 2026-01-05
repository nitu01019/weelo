package com.weelo.logistics.data.models

/**
 * Centralized configuration for all truck types and their subtypes
 */
object TruckSubtypesConfig {
    
    val OPEN = TruckConfig(
        id = "open",
        displayName = "Open",
        subtypes = listOf(
            "17 Feet", "19 Feet", "20 Feet", "22 Feet", "24 Feet",
            "10 Wheeler", "12 Wheeler", "14 Wheeler", "16 Wheeler", "18 Wheeler"
        )
    )
    
    val CONTAINER = TruckConfig(
        id = "container",
        displayName = "Container",
        subtypes = listOf(
            "19 Feet", "20 Feet", "22 Feet", "24 Feet",
            "32 Feet Single Axle", "32 Feet Multi Axle", "32 Feet Triple Axle"
        )
    )
    
    val LCV = TruckConfig(
        id = "lcv",
        displayName = "LCV",
        subtypes = listOf("LCV Open", "LCV Container"),
        subtypeLengths = mapOf(
            "LCV Open" to listOf("14 Feet", "17 Feet", "19 Feet", "20 Feet", "22 Feet", "24 Feet"),
            "LCV Container" to listOf("14 Feet", "17 Feet", "19 Feet", "20 Feet", "22 Feet", "24 Feet", "32 Feet SXL")
        )
    )
    
    val MINI = TruckConfig(
        id = "mini",
        displayName = "Mini/Pickup",
        subtypes = listOf("Pickup Truck - Dost", "Mini Truck - Tata Ace")
    )
    
    val TRAILER = TruckConfig(
        id = "trailer",
        displayName = "Trailer",
        subtypes = listOf(
            "8-11 Ton", "12-15 Ton", "16-19 Ton", "20-22 Ton", "23-25 Ton",
            "26-28 Ton", "29-31 Ton", "32-35 Ton", "36-41 Ton", "42+ Ton"
        )
    )
    
    val TIPPER = TruckConfig(
        id = "tipper",
        displayName = "Tipper",
        subtypes = listOf(
            "9-11 Ton", "15-17 Ton", "18-19 Ton", "20-24 Ton",
            "25 Ton", "26-28 Ton", "29 Ton", "30 Ton"
        )
    )
    
    val TANKER = TruckConfig(
        id = "tanker",
        displayName = "Tanker",
        subtypes = listOf(
            "8-11 Ton", "12-15 Ton", "16-20 Ton", "21-25 Ton",
            "26-29 Ton", "30-31 Ton", "32-35 Ton", "36 Ton"
        )
    )
    
    val DUMPER = TruckConfig(
        id = "dumper",
        displayName = "Dumper",
        subtypes = listOf(
            "9-11 Ton", "12-15 Ton", "16-19 Ton", "20-22 Ton",
            "23-25 Ton", "26-28 Ton", "29-30 Ton", "31+ Ton"
        )
    )
    
    val BULKER = TruckConfig(
        id = "bulker",
        displayName = "Bulker",
        subtypes = listOf(
            "20-22 Ton", "23-25 Ton", "26-28 Ton", "29-31 Ton", "32+ Ton"
        )
    )
    
    /**
     * Get truck config by ID
     */
    fun getConfigById(id: String): TruckConfig? {
        return when (id) {
            "open" -> OPEN
            "container" -> CONTAINER
            "lcv" -> LCV
            "mini" -> MINI
            "trailer" -> TRAILER
            "tipper" -> TIPPER
            "tanker" -> TANKER
            "dumper" -> DUMPER
            "bulker" -> BULKER
            else -> null
        }
    }
    
    /**
     * Get all truck type IDs that have dialogs
     */
    fun getAllDialogTruckTypes(): List<String> {
        return listOf("open", "container", "lcv", "mini", "trailer", "tipper", "tanker", "dumper", "bulker")
    }
}
