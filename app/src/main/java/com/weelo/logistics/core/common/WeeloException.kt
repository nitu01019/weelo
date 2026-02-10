package com.weelo.logistics.core.common

/**
 * Sealed class for all Weelo app exceptions
 * Provides type-safe error handling across the app
 */
sealed class WeeloException(message: String) : Exception(message) {
    
    // Network related exceptions
    data class NetworkError(val errorMessage: String) : WeeloException(errorMessage)
    data class Timeout(val errorMessage: String) : WeeloException(errorMessage)
    
    // HTTP status code exceptions
    data class Unauthorized(val errorMessage: String) : WeeloException(errorMessage)
    data class Forbidden(val errorMessage: String) : WeeloException(errorMessage)
    data class NotFound(val errorMessage: String) : WeeloException(errorMessage)
    data class ValidationError(val errorMessage: String) : WeeloException(errorMessage)
    data class RateLimited(val errorMessage: String) : WeeloException(errorMessage)
    data class ServerError(val errorMessage: String) : WeeloException(errorMessage)
    data class ServiceUnavailable(val errorMessage: String) : WeeloException(errorMessage)
    data class ApiError(val errorMessage: String, val code: Int) : WeeloException(errorMessage)
    
    // Generic exceptions
    data class Unknown(val errorMessage: String) : WeeloException(errorMessage)
    data class UnknownException(val errorMessage: String) : WeeloException(errorMessage)
    
    // Location/Geocoding exceptions
    data class LocationException(val errorMessage: String) : WeeloException(errorMessage)
    data class NetworkException(val errorMessage: String) : WeeloException(errorMessage)
    
    // Business logic exceptions
    data class ValidationException(val errorMessage: String) : WeeloException(errorMessage)
    data class BookingException(val errorMessage: String) : WeeloException(errorMessage)
    data class AuthException(val errorMessage: String) : WeeloException(errorMessage)
    data class OtpException(val errorMessage: String) : WeeloException(errorMessage)
    
    // Additional properties for specific exception types
    val requiresReAuth: Boolean
        get() = this is Unauthorized || this is Forbidden
    
    val isRecoverable: Boolean
        get() = when (this) {
            is NetworkError, is Timeout, is RateLimited, is ServerError, is ServiceUnavailable -> true
            else -> false
        }
    
    // Get user-friendly message
    fun getUserMessage(): String {
        return when (this) {
            is NetworkError -> errorMessage
            is Timeout -> errorMessage
            is Unauthorized -> errorMessage
            is Forbidden -> errorMessage
            is NotFound -> errorMessage
            is ValidationError -> errorMessage
            is RateLimited -> errorMessage
            is ServerError -> errorMessage
            is ServiceUnavailable -> errorMessage
            is ApiError -> errorMessage
            is Unknown -> errorMessage
            is UnknownException -> errorMessage
            is LocationException -> errorMessage
            is NetworkException -> errorMessage
            is ValidationException -> errorMessage
            is BookingException -> errorMessage
            is AuthException -> errorMessage
            is OtpException -> errorMessage
        }
    }
}
