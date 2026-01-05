package com.weelo.logistics.data.models

/**
 * Centralized configuration for all JCB/Construction machinery types and their subtypes
 * This is SEPARATE from trucks and tractors - contains ONLY CONSTRUCTION MACHINERY
 * For earth-moving, excavation, loading, and construction work
 */
object JCBMachinerySubtypesConfig {
    
    val JCB = JCBMachineryConfig(
        id = "jcb",
        displayName = "JCB (Backhoe Loader)",
        subtypes = listOf(
            "JCB 3DX", "JCB 3DX Super", "JCB 3DX Xtra", "JCB 4DX",
            "Small JCB (2.5 Ton)", "Medium JCB (3-4 Ton)", "Large JCB (5+ Ton)",
            "Mini Backhoe Loader"
        )
    )
    
    val EXCAVATOR = JCBMachineryConfig(
        id = "excavator",
        displayName = "Excavator",
        subtypes = listOf(
            "Mini Excavator (1-5 Ton)", "Small Excavator (6-10 Ton)", "Medium Excavator (11-20 Ton)",
            "Large Excavator (21-40 Ton)", "Extra Large Excavator (40+ Ton)",
            "Long Reach Excavator", "Crawler Excavator", "Wheeled Excavator"
        )
    )
    
    val LOADER = JCBMachineryConfig(
        id = "loader",
        displayName = "Wheel Loader",
        subtypes = listOf(
            "Compact Loader", "Small Wheel Loader (1-2 CBM)", "Medium Wheel Loader (2-4 CBM)",
            "Large Wheel Loader (4-6 CBM)", "Extra Large Loader (6+ CBM)",
            "Skid Steer Loader", "Track Loader", "Articulated Loader"
        )
    )
    
    val BULLDOZER = JCBMachineryConfig(
        id = "bulldozer",
        displayName = "Bulldozer",
        subtypes = listOf(
            "Small Bulldozer (50-90 HP)", "Medium Bulldozer (100-200 HP)", "Large Bulldozer (200-400 HP)",
            "Extra Large Bulldozer (400+ HP)", "Crawler Bulldozer", "Wheel Bulldozer",
            "Mini Dozer", "Angle Dozer"
        )
    )
    
    val CRANE = JCBMachineryConfig(
        id = "crane",
        displayName = "Crane",
        subtypes = listOf(
            "Mobile Crane - 10 Ton", "Mobile Crane - 25 Ton", "Mobile Crane - 50 Ton",
            "Mobile Crane - 100 Ton", "Mobile Crane - 200 Ton", "Crawler Crane",
            "Tower Crane", "Pick and Carry Crane"
        )
    )
    
    val PAVER = JCBMachineryConfig(
        id = "paver",
        displayName = "Paver",
        subtypes = listOf(
            "Asphalt Paver - Small", "Asphalt Paver - Medium", "Asphalt Paver - Large",
            "Concrete Paver", "Slip Form Paver", "Wheeled Paver",
            "Tracked Paver", "Block Paver"
        )
    )
    
    val COMPACTOR = JCBMachineryConfig(
        id = "compactor",
        displayName = "Compactor/Roller",
        subtypes = listOf(
            "Vibratory Roller - Small (3-5 Ton)", "Vibratory Roller - Medium (8-10 Ton)", "Vibratory Roller - Large (12+ Ton)",
            "Smooth Wheel Roller", "Pneumatic Roller", "Sheep Foot Roller",
            "Tandem Roller", "Walk Behind Roller"
        )
    )
    
    val GRADER = JCBMachineryConfig(
        id = "grader",
        displayName = "Motor Grader",
        subtypes = listOf(
            "Small Grader (100-150 HP)", "Medium Grader (150-200 HP)", "Large Grader (200-300 HP)",
            "Extra Large Grader (300+ HP)", "Mining Grader", "Rigid Frame Grader",
            "Articulated Grader", "Mini Grader"
        )
    )
    
    val FORKLIFT = JCBMachineryConfig(
        id = "forklift",
        displayName = "Forklift",
        subtypes = listOf(
            "Light Duty Forklift (1-2 Ton)", "Medium Duty Forklift (2-3 Ton)", "Heavy Duty Forklift (3-5 Ton)",
            "Extra Heavy Forklift (5-10 Ton)", "Reach Forklift", "Rough Terrain Forklift",
            "Telescopic Forklift", "Electric Forklift"
        )
    )
    
    val CONCRETE_MIXER = JCBMachineryConfig(
        id = "concrete_mixer",
        displayName = "Concrete Mixer",
        subtypes = listOf(
            "Transit Mixer - 4 CBM", "Transit Mixer - 6 CBM", "Transit Mixer - 8 CBM",
            "Transit Mixer - 10 CBM", "Batching Plant - Mobile", "Batching Plant - Stationary",
            "Concrete Pump", "Boom Placer"
        )
    )
    
    /**
     * Get JCB machinery config by ID
     */
    fun getConfigById(id: String): JCBMachineryConfig? {
        return when (id) {
            "jcb" -> JCB
            "excavator" -> EXCAVATOR
            "loader" -> LOADER
            "bulldozer" -> BULLDOZER
            "crane" -> CRANE
            "paver" -> PAVER
            "compactor" -> COMPACTOR
            "grader" -> GRADER
            "forklift" -> FORKLIFT
            "concrete_mixer" -> CONCRETE_MIXER
            else -> null
        }
    }
    
    /**
     * Get all JCB machinery type IDs that have dialogs
     */
    fun getAllDialogMachineryTypes(): List<String> {
        return listOf("jcb", "excavator", "loader", "bulldozer", "crane", "paver", "compactor", "grader", "forklift", "concrete_mixer")
    }
}
