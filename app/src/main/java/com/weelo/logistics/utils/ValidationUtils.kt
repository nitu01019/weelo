package com.weelo.logistics.utils

/**
 * Input validation utilities for production
 */
object ValidationUtils {
    
    /**
     * Validate location input
     */
    fun isValidLocation(location: String): Boolean {
        return location.trim().isNotEmpty() && location.trim().length >= 2
    }
    
    /**
     * Validate phone number (Indian format)
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        val phoneRegex = "^[6-9]\\d{9}$".toRegex()
        return phone.matches(phoneRegex)
    }
    
    /**
     * Validate email
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
    
    /**
     * Sanitize input to prevent injection
     */
    fun sanitizeInput(input: String): String {
        return input.trim()
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("&", "&amp;")
    }
    
    /**
     * Check if distance is within reasonable range
     */
    fun isValidDistance(distance: Int): Boolean {
        return distance in 1..10000 // 1 km to 10,000 km
    }
}
