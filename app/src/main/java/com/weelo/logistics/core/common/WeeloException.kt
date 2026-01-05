package com.weelo.logistics.core.common

/**
 * Custom exception hierarchy for Weelo app
 * 
 * Provides type-safe error handling throughout the application.
 * Each exception type has specific meaning and can be handled differently.
 */
sealed class WeeloException(message: String) : Exception(message) {
    
    /**
     * Network-related errors (no connection, timeout, etc.)
     */
    class NetworkException(message: String = "Network error occurred") : WeeloException(message)
    
    /**
     * Authentication/Authorization errors
     * - Invalid credentials
     * - Session expired
     * - Token refresh failed
     */
    class AuthException(message: String = "Authentication failed") : WeeloException(message)
    
    /**
     * Location-related errors
     * - Invalid location
     * - Location not found
     * - GPS errors
     */
    class LocationException(message: String = "Location error") : WeeloException(message)
    
    /**
     * Vehicle/Booking related errors
     * - Vehicle not available
     * - Booking failed
     * - Invalid vehicle selection
     */
    class BookingException(message: String = "Booking error") : WeeloException(message)
    
    /**
     * Validation errors
     * - Invalid input
     * - Missing required fields
     * - Format errors
     */
    class ValidationException(message: String = "Validation error") : WeeloException(message)
    
    /**
     * Server errors (5xx responses)
     */
    class ServerException(message: String = "Server error") : WeeloException(message)
    
    /**
     * Unknown or unexpected errors
     */
    class UnknownException(message: String = "An unexpected error occurred") : WeeloException(message)
}
