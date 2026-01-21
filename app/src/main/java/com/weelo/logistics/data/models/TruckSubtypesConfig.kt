package com.weelo.logistics.data.models

/**
 * Subtype capacity information for tonnage-based pricing
 */
data class SubtypeCapacity(
    val name: String,
    val minTonnage: Double,
    val maxTonnage: Double,
    val capacityKg: Int,
    val lengthFeet: Int? = null
)

/**
 * Enhanced TruckConfig with capacity data
 */
data class TruckConfigEnhanced(
    val id: String,
    val displayName: String,
    val subtypes: List<String>,
    val subtypeCapacities: Map<String, SubtypeCapacity> = emptyMap(),
    val subtypeLengths: Map<String, List<String>> = emptyMap()
)

/**
 * Centralized configuration for all truck types and their subtypes
 * Enhanced with tonnage/capacity data for pricing calculations
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
    
    // Enhanced OPEN with capacity data
    val OPEN_CAPACITIES = mapOf(
        "17 Feet" to SubtypeCapacity("17 Feet", 5.0, 7.0, 7000, 17),
        "19 Feet" to SubtypeCapacity("19 Feet", 6.0, 9.0, 9000, 19),
        "20 Feet" to SubtypeCapacity("20 Feet", 7.0, 10.0, 10000, 20),
        "22 Feet" to SubtypeCapacity("22 Feet", 9.0, 12.0, 12000, 22),
        "24 Feet" to SubtypeCapacity("24 Feet", 10.0, 15.0, 15000, 24),
        "10 Wheeler" to SubtypeCapacity("10 Wheeler", 15.0, 21.0, 21000),
        "12 Wheeler" to SubtypeCapacity("12 Wheeler", 21.0, 25.0, 25000),
        "14 Wheeler" to SubtypeCapacity("14 Wheeler", 25.0, 30.0, 30000),
        "16 Wheeler" to SubtypeCapacity("16 Wheeler", 30.0, 35.0, 35000),
        "18 Wheeler" to SubtypeCapacity("18 Wheeler", 35.0, 40.0, 40000)
    )
    
    val CONTAINER = TruckConfig(
        id = "container",
        displayName = "Container",
        subtypes = listOf(
            "19 Feet", "20 Feet", "22 Feet", "24 Feet",
            "32 Feet Single Axle", "32 Feet Multi Axle", "32 Feet Triple Axle"
        )
    )
    
    val CONTAINER_CAPACITIES = mapOf(
        "19 Feet" to SubtypeCapacity("19 Feet", 6.0, 9.0, 9000, 19),
        "20 Feet" to SubtypeCapacity("20 Feet", 7.0, 10.0, 10000, 20),
        "22 Feet" to SubtypeCapacity("22 Feet", 9.0, 14.0, 14000, 22),
        "24 Feet" to SubtypeCapacity("24 Feet", 10.0, 16.0, 16000, 24),
        "32 Feet Single Axle" to SubtypeCapacity("32 Feet Single Axle", 14.0, 18.0, 18000, 32),
        "32 Feet Multi Axle" to SubtypeCapacity("32 Feet Multi Axle", 18.0, 24.0, 24000, 32),
        "32 Feet Triple Axle" to SubtypeCapacity("32 Feet Triple Axle", 24.0, 28.0, 28000, 32)
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
    
    val LCV_CAPACITIES = mapOf(
        "LCV Open - 14 Feet" to SubtypeCapacity("LCV Open - 14 Feet", 2.0, 4.0, 4000, 14),
        "LCV Open - 17 Feet" to SubtypeCapacity("LCV Open - 17 Feet", 3.0, 5.0, 5000, 17),
        "LCV Open - 19 Feet" to SubtypeCapacity("LCV Open - 19 Feet", 4.0, 6.0, 6000, 19),
        "LCV Container - 14 Feet" to SubtypeCapacity("LCV Container - 14 Feet", 2.0, 4.0, 4000, 14),
        "LCV Container - 17 Feet" to SubtypeCapacity("LCV Container - 17 Feet", 3.0, 5.0, 5000, 17),
        "LCV Container - 19 Feet" to SubtypeCapacity("LCV Container - 19 Feet", 4.0, 6.0, 6000, 19),
        "LCV Container - 32 Feet SXL" to SubtypeCapacity("LCV Container - 32 Feet SXL", 7.0, 9.0, 9000, 32)
    )
    
    val MINI = TruckConfig(
        id = "mini",
        displayName = "Mini/Pickup",
        subtypes = listOf("Pickup Truck - Dost", "Mini Truck - Tata Ace")
    )
    
    val MINI_CAPACITIES = mapOf(
        "Pickup Truck - Dost" to SubtypeCapacity("Pickup Truck - Dost", 0.5, 1.5, 1500),
        "Mini Truck - Tata Ace" to SubtypeCapacity("Mini Truck - Tata Ace", 0.5, 1.0, 1000)
    )
    
    val TRAILER = TruckConfig(
        id = "trailer",
        displayName = "Trailer",
        subtypes = listOf(
            "8-11 Ton", "12-15 Ton", "16-19 Ton", "20-22 Ton", "23-25 Ton",
            "26-28 Ton", "29-31 Ton", "32-35 Ton", "36-41 Ton", "42+ Ton"
        )
    )
    
    val TRAILER_CAPACITIES = mapOf(
        "8-11 Ton" to SubtypeCapacity("8-11 Ton", 8.0, 11.0, 11000),
        "12-15 Ton" to SubtypeCapacity("12-15 Ton", 12.0, 15.0, 15000),
        "16-19 Ton" to SubtypeCapacity("16-19 Ton", 16.0, 19.0, 19000),
        "20-22 Ton" to SubtypeCapacity("20-22 Ton", 20.0, 22.0, 22000),
        "23-25 Ton" to SubtypeCapacity("23-25 Ton", 23.0, 25.0, 25000),
        "26-28 Ton" to SubtypeCapacity("26-28 Ton", 26.0, 28.0, 28000),
        "29-31 Ton" to SubtypeCapacity("29-31 Ton", 29.0, 31.0, 31000),
        "32-35 Ton" to SubtypeCapacity("32-35 Ton", 32.0, 35.0, 35000),
        "36-41 Ton" to SubtypeCapacity("36-41 Ton", 36.0, 41.0, 41000),
        "42+ Ton" to SubtypeCapacity("42+ Ton", 42.0, 50.0, 50000)
    )
    
    val TIPPER = TruckConfig(
        id = "tipper",
        displayName = "Tipper",
        subtypes = listOf(
            "9-11 Ton", "15-17 Ton", "18-19 Ton", "20-24 Ton",
            "25 Ton", "26-28 Ton", "29 Ton", "30 Ton"
        )
    )
    
    val TIPPER_CAPACITIES = mapOf(
        "9-11 Ton" to SubtypeCapacity("9-11 Ton", 9.0, 11.0, 11000),
        "15-17 Ton" to SubtypeCapacity("15-17 Ton", 15.0, 17.0, 17000),
        "18-19 Ton" to SubtypeCapacity("18-19 Ton", 18.0, 19.0, 19000),
        "20-24 Ton" to SubtypeCapacity("20-24 Ton", 20.0, 24.0, 24000),
        "25 Ton" to SubtypeCapacity("25 Ton", 25.0, 25.0, 25000),
        "26-28 Ton" to SubtypeCapacity("26-28 Ton", 26.0, 28.0, 28000),
        "29 Ton" to SubtypeCapacity("29 Ton", 29.0, 29.0, 29000),
        "30 Ton" to SubtypeCapacity("30 Ton", 30.0, 30.0, 30000)
    )
    
    val TANKER = TruckConfig(
        id = "tanker",
        displayName = "Tanker",
        subtypes = listOf(
            "8-11 Ton", "12-15 Ton", "16-20 Ton", "21-25 Ton",
            "26-29 Ton", "30-31 Ton", "32-35 Ton", "36 Ton"
        )
    )
    
    val TANKER_CAPACITIES = mapOf(
        "8-11 Ton" to SubtypeCapacity("8-11 Ton", 8.0, 11.0, 11000),
        "12-15 Ton" to SubtypeCapacity("12-15 Ton", 12.0, 15.0, 15000),
        "16-20 Ton" to SubtypeCapacity("16-20 Ton", 16.0, 20.0, 20000),
        "21-25 Ton" to SubtypeCapacity("21-25 Ton", 21.0, 25.0, 25000),
        "26-29 Ton" to SubtypeCapacity("26-29 Ton", 26.0, 29.0, 29000),
        "30-31 Ton" to SubtypeCapacity("30-31 Ton", 30.0, 31.0, 31000),
        "32-35 Ton" to SubtypeCapacity("32-35 Ton", 32.0, 35.0, 35000),
        "36 Ton" to SubtypeCapacity("36 Ton", 36.0, 36.0, 36000)
    )
    
    val DUMPER = TruckConfig(
        id = "dumper",
        displayName = "Dumper",
        subtypes = listOf(
            "9-11 Ton", "12-15 Ton", "16-19 Ton", "20-22 Ton",
            "23-25 Ton", "26-28 Ton", "29-30 Ton", "31+ Ton"
        )
    )
    
    val DUMPER_CAPACITIES = mapOf(
        "9-11 Ton" to SubtypeCapacity("9-11 Ton", 9.0, 11.0, 11000),
        "12-15 Ton" to SubtypeCapacity("12-15 Ton", 12.0, 15.0, 15000),
        "16-19 Ton" to SubtypeCapacity("16-19 Ton", 16.0, 19.0, 19000),
        "20-22 Ton" to SubtypeCapacity("20-22 Ton", 20.0, 22.0, 22000),
        "23-25 Ton" to SubtypeCapacity("23-25 Ton", 23.0, 25.0, 25000),
        "26-28 Ton" to SubtypeCapacity("26-28 Ton", 26.0, 28.0, 28000),
        "29-30 Ton" to SubtypeCapacity("29-30 Ton", 29.0, 30.0, 30000),
        "31+ Ton" to SubtypeCapacity("31+ Ton", 31.0, 40.0, 40000)
    )
    
    val BULKER = TruckConfig(
        id = "bulker",
        displayName = "Bulker",
        subtypes = listOf(
            "20-22 Ton", "23-25 Ton", "26-28 Ton", "29-31 Ton", "32+ Ton"
        )
    )
    
    val BULKER_CAPACITIES = mapOf(
        "20-22 Ton" to SubtypeCapacity("20-22 Ton", 20.0, 22.0, 22000),
        "23-25 Ton" to SubtypeCapacity("23-25 Ton", 23.0, 25.0, 25000),
        "26-28 Ton" to SubtypeCapacity("26-28 Ton", 26.0, 28.0, 28000),
        "29-31 Ton" to SubtypeCapacity("29-31 Ton", 29.0, 31.0, 31000),
        "32+ Ton" to SubtypeCapacity("32+ Ton", 32.0, 40.0, 40000)
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
     * Get capacity info for a specific vehicle type and subtype
     */
    fun getCapacityInfo(vehicleType: String, subtype: String): SubtypeCapacity? {
        return when (vehicleType.lowercase()) {
            "open" -> OPEN_CAPACITIES[subtype]
            "container" -> CONTAINER_CAPACITIES[subtype]
            "lcv" -> LCV_CAPACITIES[subtype]
            "mini" -> MINI_CAPACITIES[subtype]
            "trailer" -> TRAILER_CAPACITIES[subtype]
            "tipper" -> TIPPER_CAPACITIES[subtype]
            "tanker" -> TANKER_CAPACITIES[subtype]
            "dumper" -> DUMPER_CAPACITIES[subtype]
            "bulker" -> BULKER_CAPACITIES[subtype]
            else -> null
        }
    }
    
    /**
     * Get all capacities for a vehicle type
     */
    fun getAllCapacitiesForType(vehicleType: String): Map<String, SubtypeCapacity> {
        return when (vehicleType.lowercase()) {
            "open" -> OPEN_CAPACITIES
            "container" -> CONTAINER_CAPACITIES
            "lcv" -> LCV_CAPACITIES
            "mini" -> MINI_CAPACITIES
            "trailer" -> TRAILER_CAPACITIES
            "tipper" -> TIPPER_CAPACITIES
            "tanker" -> TANKER_CAPACITIES
            "dumper" -> DUMPER_CAPACITIES
            "bulker" -> BULKER_CAPACITIES
            else -> emptyMap()
        }
    }
    
    /**
     * Find suitable subtypes for a given cargo weight (in kg)
     */
    fun findSuitableSubtypes(vehicleType: String, cargoWeightKg: Int): List<SubtypeCapacity> {
        val capacities = getAllCapacitiesForType(vehicleType)
        return capacities.values
            .filter { it.capacityKg >= cargoWeightKg }
            .sortedBy { it.capacityKg }
    }
    
    /**
     * Get all truck type IDs that have dialogs
     */
    fun getAllDialogTruckTypes(): List<String> {
        return listOf("open", "container", "lcv", "mini", "trailer", "tipper", "tanker", "dumper", "bulker")
    }
}
