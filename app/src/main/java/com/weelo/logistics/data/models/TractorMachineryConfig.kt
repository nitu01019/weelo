package com.weelo.logistics.data.models

/**
 * Configuration for tractor machinery type and its subtypes
 * SEPARATE from truck configuration - for agricultural/construction machinery
 */
data class TractorMachineryConfig(
    val id: String,
    val displayName: String,
    val subtypes: List<String>,
    val gridColumns: Int = 4
)
