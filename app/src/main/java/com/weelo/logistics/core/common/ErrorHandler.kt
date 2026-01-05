package com.weelo.logistics.core.common

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handler for converting exceptions to user-friendly messages
 */
object ErrorHandler {
    
    fun handleException(exception: Exception): String {
        return when (exception) {
            is UnknownHostException -> "No internet connection. Please check your network."
            is SocketTimeoutException -> "Connection timeout. Please try again."
            is HttpException -> handleHttpException(exception)
            is IOException -> "Network error occurred. Please try again."
            else -> exception.message ?: "An unexpected error occurred"
        }
    }
    
    private fun handleHttpException(exception: HttpException): String {
        return when (exception.code()) {
            400 -> "Bad request. Please check your input."
            401 -> "Unauthorized. Please log in again."
            403 -> "Access forbidden."
            404 -> "Resource not found."
            408 -> "Request timeout. Please try again."
            500 -> "Server error. Please try again later."
            502 -> "Bad gateway. Server is unavailable."
            503 -> "Service unavailable. Please try again later."
            else -> "Error ${exception.code()}: ${exception.message()}"
        }
    }
}
