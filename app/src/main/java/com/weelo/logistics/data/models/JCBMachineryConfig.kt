package com.weelo.logistics.data.models

/**
 * Configuration for JCB/Construction machinery type and its subtypes
 * SEPARATE from tractor (agricultural) and truck (transport) sections
 * Contains construction and earth-moving equipment
 */
data class JCBMachineryConfig(
    val id: String,
    val displayName: String,
    val subtypes: List<String>,
    val gridColumns: Int = 4
)
