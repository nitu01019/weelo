package com.weelo.logistics.data.models

/**
 * Configuration for truck type and its subtypes
 * @param id Unique identifier for the truck type
 * @param displayName Human-readable name
 * @param subtypes List of subtype options (e.g., "LCV Open", "LCV Container")
 * @param gridColumns Number of columns for the subtype grid
 * @param lengthSubtypes Default length options (used when subtypeLengths is empty)
 * @param subtypeLengths Map of subtype name to its specific length options
 */
data class TruckConfig(
    val id: String,
    val displayName: String,
    val subtypes: List<String>,
    val gridColumns: Int = 4,
    val lengthSubtypes: List<String> = emptyList(),
    val subtypeLengths: Map<String, List<String>> = emptyMap()
)

