package com.weelo.logistics.core.util

import android.widget.TextView
import timber.log.Timber

/**
 * Safe data access extensions
 * 
 * These extensions ensure UI never crashes due to null/empty data.
 * All functions provide safe fallbacks and never return null to UI.
 * 
 * DATA SAFETY PRINCIPLES:
 * 1. Never assume data exists
 * 2. Always provide fallbacks
 * 3. Handle null, empty, and partial data
 * 4. Log issues but don't crash
 * 5. UI should render gracefully with zero data
 */

// ========================================
// String Safety
// ========================================

/**
 * Get string or default value
 * Handles null, empty, and blank strings
 */
fun String?.orDefault(default: String = ""): String {
    return if (this.isNullOrBlank()) default else this
}

/**
 * Get string or placeholder
 * Shows clear indication that data is missing
 */
fun String?.orPlaceholder(placeholder: String = "N/A"): String {
    return if (this.isNullOrBlank()) placeholder else this
}

/**
 * Capitalize first letter safely
 */
fun String?.capitalizeOrEmpty(): String {
    return this?.replaceFirstChar { it.uppercase() } ?: ""
}

/**
 * Truncate string with ellipsis
 */
fun String?.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (this == null || this.length <= maxLength) return this.orEmpty()
    return "${this.substring(0, maxLength - ellipsis.length)}$ellipsis"
}

// ========================================
// Number Safety
// ========================================

/**
 * Get number or zero
 * Safe for all numeric operations
 */
fun Int?.orZero(): Int = this ?: 0
fun Long?.orZero(): Long = this ?: 0L
fun Float?.orZero(): Float = this ?: 0f
fun Double?.orZero(): Double = this ?: 0.0

/**
 * Get number or default
 */
fun Int?.orDefault(default: Int): Int = this ?: default
fun Double?.orDefault(default: Double): Double = this ?: default

/**
 * Format currency safely
 */
fun Int?.toSafeCurrency(): String {
    val value = this.orZero()
    return "₹${String.format("%,d", value)}"
}

fun Double?.toSafeCurrency(): String {
    val value = this.orZero()
    return "₹${String.format("%,.2f", value)}"
}

/**
 * Format distance safely
 */
fun Int?.toSafeDistance(): String {
    val value = this.orZero()
    return "$value km"
}

fun Double?.toSafeDistance(): String {
    val value = this.orZero()
    return String.format("%.1f km", value)
}

// ========================================
// Collection Safety
// ========================================

/**
 * Get list or empty list
 * Never returns null to UI
 */
fun <T> List<T>?.orEmptyList(): List<T> = this ?: emptyList()

/**
 * Safe first element
 */
fun <T> List<T>?.firstOrNull(): T? = this?.firstOrNull()

/**
 * Safe size
 */
fun <T> List<T>?.safeSize(): Int = this?.size ?: 0

/**
 * Check if list has items
 */
fun <T> List<T>?.hasItems(): Boolean = !this.isNullOrEmpty()

// ========================================
// Boolean Safety
// ========================================

/**
 * Get boolean or default
 */
fun Boolean?.orFalse(): Boolean = this ?: false
fun Boolean?.orTrue(): Boolean = this ?: true

// ========================================
// Nested Field Safety
// ========================================

/**
 * Safe nested field access
 * 
 * Example:
 * val name = user?.profile?.name.orDefault("Unknown")
 * 
 * Instead of:
 * val name = user?.profile?.name ?: "Unknown" // Can still throw NPE in chains
 */
inline fun <T, R> T?.letOrDefault(default: R, block: (T) -> R): R {
    return if (this != null) {
        try {
            block(this)
        } catch (e: Exception) {
            Timber.e(e, "Error accessing nested field")
            default
        }
    } else {
        default
    }
}

/**
 * Safe nested nullable access
 */
inline fun <T, R> T?.letOrNull(block: (T) -> R?): R? {
    return if (this != null) {
        try {
            block(this)
        } catch (e: Exception) {
            Timber.e(e, "Error accessing nested nullable field")
            null
        }
    } else {
        null
    }
}

// ========================================
// View Binding Safety
// ========================================

/**
 * Set text safely
 * Handles null and empty strings gracefully
 */
fun TextView.setTextSafe(text: String?, fallback: String = "") {
    this.text = text.orDefault(fallback)
}

/**
 * Set text or hide view
 */
fun TextView.setTextOrHide(text: String?) {
    if (text.isNullOrBlank()) {
        this.visibility = android.view.View.GONE
    } else {
        this.visibility = android.view.View.VISIBLE
        this.text = text
    }
}

/**
 * Set text or show placeholder
 */
fun TextView.setTextOrPlaceholder(text: String?, placeholder: String = "Not available") {
    this.text = text.orPlaceholder(placeholder)
}

// ========================================
// Data Validation
// ========================================

/**
 * Check if string is valid (not null, not empty, not blank)
 */
fun String?.isValidData(): Boolean {
    return !this.isNullOrBlank()
}

/**
 * Check if number is valid (not null, not zero, not negative)
 */
fun Int?.isValidData(): Boolean {
    return this != null && this > 0
}

fun Double?.isValidData(): Boolean {
    return this != null && this > 0.0
}

// ========================================
// Safe Parsing
// ========================================

/**
 * Parse int safely
 */
fun String?.toIntOrZero(): Int {
    return try {
        this?.toIntOrNull() ?: 0
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse int: $this")
        0
    }
}

/**
 * Parse double safely
 */
fun String?.toDoubleOrZero(): Double {
    return try {
        this?.toDoubleOrNull() ?: 0.0
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse double: $this")
        0.0
    }
}

/**
 * Parse boolean safely
 */
fun String?.toBooleanOrFalse(): Boolean {
    return try {
        this?.toBooleanStrictOrNull() ?: false
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse boolean: $this")
        false
    }
}

// ========================================
// Object Safety
// ========================================

/**
 * Safe toString
 * Never throws NPE
 */
fun Any?.toStringSafe(): String {
    return try {
        this?.toString() ?: ""
    } catch (e: Exception) {
        Timber.e(e, "Failed to convert to string")
        ""
    }
}

/**
 * Get value or log and return default
 */
fun <T> T?.orDefaultWithLog(default: T, fieldName: String = "Field"): T {
    if (this == null) {
        Timber.w("$fieldName is null, using default: $default")
    }
    return this ?: default
}

// ========================================
// UI State Safety
// ========================================

/**
 * Check if UI state is empty
 * Used to show empty states in UI
 */
fun <T> List<T>?.isEmptyState(): Boolean = this.isNullOrEmpty()

/**
 * Get count for UI display
 */
fun <T> List<T>?.displayCount(): String {
    val count = this?.size ?: 0
    return if (count == 0) "No items" else "$count item${if (count > 1) "s" else ""}"
}
