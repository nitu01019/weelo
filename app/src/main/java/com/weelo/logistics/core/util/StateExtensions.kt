package com.weelo.logistics.core.util

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

/**
 * Extension functions for state management
 * 
 * These utilities help with:
 * - Converting between state types
 * - Safe state access
 * - State persistence
 */

// Note: SavedStateHandle already has getStateFlow() method built-in
// No need for extension - use savedStateHandle.getStateFlow(key, initialValue) directly

/**
 * Safe state access that won't crash if key doesn't exist
 */
fun <T> SavedStateHandle.getSafe(key: String, default: T): T {
    return try {
        this.get<T>(key) ?: default
    } catch (e: Exception) {
        default
    }
}

/**
 * Set multiple state values at once
 * 
 * Example:
 * ```kotlin
 * savedStateHandle.setMultiple(
 *     "key1" to "value1",
 *     "key2" to 123,
 *     "key3" to true
 * )
 * ```
 */
fun SavedStateHandle.setMultiple(vararg pairs: Pair<String, Any?>) {
    pairs.forEach { (key, value) ->
        this.set(key, value)
    }
}
