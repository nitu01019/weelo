package com.weelo.logistics.core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber

/**
 * ThemeManager - Dark/Light theme management
 * 
 * SCALABILITY: App-wide theme control
 * PERSISTENCE: Saves user preference
 * EASY TO USE: Single method to toggle/apply
 * 
 * Usage:
 * ```
 * // Initialize in Application
 * ThemeManager.initialize(context)
 * ThemeManager.applyTheme()
 * 
 * // Toggle theme
 * ThemeManager.toggleTheme()
 * 
 * // Set specific theme
 * ThemeManager.setTheme(ThemeMode.DARK)
 * ```
 */
object ThemeManager {

    private const val PREFS_NAME = "weelo_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    /**
     * Initialize theme manager
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        Timber.d("ThemeManager initialized")
    }

    /**
     * Apply saved theme preference
     */
    fun applyTheme() {
        checkInitialized()
        val mode = getSavedThemeMode()
        applyThemeMode(mode)
    }

    /**
     * Set theme mode
     */
    fun setTheme(mode: ThemeMode) {
        checkInitialized()
        saveThemeMode(mode)
        applyThemeMode(mode)
    }

    /**
     * Toggle between light and dark
     */
    fun toggleTheme() {
        checkInitialized()
        val current = getSavedThemeMode()
        val new = when (current) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setTheme(new)
    }

    /**
     * Get current theme mode
     */
    fun getCurrentTheme(): ThemeMode {
        checkInitialized()
        return getSavedThemeMode()
    }

    /**
     * Check if dark mode is active
     */
    fun isDarkMode(): Boolean {
        checkInitialized()
        return getSavedThemeMode() == ThemeMode.DARK
    }

    private fun getSavedThemeMode(): ThemeMode {
        // ALWAYS USE LIGHT MODE - No dark theme needed
        return ThemeMode.LIGHT
    }

    private fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        Timber.d("Theme saved: $mode")
    }

    private fun applyThemeMode(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        Timber.d("Theme applied: $mode")
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("ThemeManager not initialized. Call initialize() first.")
        }
    }
}

/**
 * Theme mode options
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}
