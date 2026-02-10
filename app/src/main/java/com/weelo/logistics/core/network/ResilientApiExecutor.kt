package com.weelo.logistics.core.network

import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.core.util.NetworkMonitor
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * RESILIENT API EXECUTOR - Production-Grade Network Call Wrapper
 * =============================================================================
 * 
 * Combines Circuit Breaker + Retry Policy for robust network operations.
 * 
 * FEATURES:
 * - Automatic retry with exponential backoff
 * - Circuit breaker to prevent cascading failures
 * - Network connectivity check before requests
 * - Detailed logging for debugging
 * - Proper error transformation
 * 
 * USAGE:
 * ```kotlin
 * val result = resilientExecutor.execute(RetryPolicy.exponentialBackoff()) {
 *     apiService.getBookings()
 * }
 * 
 * result.fold(
 *     onSuccess = { data -> handleData(data) },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 * =============================================================================
 */
@Singleton
class ResilientApiExecutor @Inject constructor(
    private val circuitBreaker: CircuitBreaker,
    private val networkMonitor: NetworkMonitor
) {
    
    companion object {
        private const val TAG = "ResilientApiExecutor"
    }
    
    /**
     * Execute API call with resilience
     * 
     * @param policy Retry policy to use
     * @param operationName Name for logging
     * @param block The API call to execute
     * @return Result<T> with success data or failure exception
     */
    suspend fun <T> execute(
        policy: RetryPolicy = RetryPolicy.exponentialBackoff(),
        operationName: String = "API Call",
        block: suspend () -> T
    ): Result<T> {
        Timber.d("$TAG: Starting $operationName")
        
        // Check network connectivity first
        if (!networkMonitor.isCurrentlyOnline()) {
            Timber.w("$TAG: No network connectivity for $operationName")
            return Result.failure(
                WeeloException.NetworkError("No internet connection. Please check your network.")
            )
        }
        
        // Check circuit breaker
        if (!circuitBreaker.allowRequest()) {
            val stats = circuitBreaker.getStats()
            Timber.w("$TAG: Circuit breaker OPEN for $operationName (reset in ${stats.timeUntilReset}ms)")
            return Result.failure(
                WeeloException.ServiceUnavailable(
                    "Service temporarily unavailable. Please try again in ${stats.timeUntilReset / 1000} seconds."
                )
            )
        }
        
        var lastException: Throwable? = null
        
        // Attempt with retries
        for (attempt in 0..policy.maxRetries) {
            try {
                if (attempt > 0) {
                    val delayMs = policy.getDelayMs(attempt - 1)
                    Timber.d("$TAG: $operationName - Retry attempt $attempt after ${delayMs}ms delay")
                    delay(delayMs)
                    
                    // Re-check circuit breaker before retry
                    if (!circuitBreaker.allowRequest()) {
                        Timber.w("$TAG: Circuit breaker opened during retry")
                        break
                    }
                }
                
                // Execute the API call
                val result = block()
                
                // Success - record with circuit breaker
                circuitBreaker.recordSuccess()
                Timber.d("$TAG: $operationName succeeded${if (attempt > 0) " on attempt ${attempt + 1}" else ""}")
                
                return Result.success(result)
                
            } catch (e: Throwable) {
                lastException = e
                Timber.e(e, "$TAG: $operationName failed on attempt ${attempt + 1}: ${e.message}")
                
                // Record failure with circuit breaker
                circuitBreaker.recordFailure(e)
                
                // Check if we should retry
                if (attempt < policy.maxRetries && policy.shouldRetry(e)) {
                    Timber.d("$TAG: $operationName - Will retry (${policy.maxRetries - attempt} attempts remaining)")
                    continue
                }
                
                // No more retries
                break
            }
        }
        
        // All attempts failed
        val transformedException = transformException(lastException, operationName)
        Timber.e("$TAG: $operationName failed after all attempts: ${transformedException.message}")
        
        return Result.failure(transformedException)
    }
    
    /**
     * Execute API call without retry (for non-idempotent operations like POST)
     */
    suspend fun <T> executeOnce(
        operationName: String = "API Call",
        block: suspend () -> T
    ): Result<T> {
        return execute(
            policy = RetryPolicy.NoRetry,
            operationName = operationName,
            block = block
        )
    }
    
    /**
     * Execute API call with aggressive retry (for critical operations)
     */
    suspend fun <T> executeWithAggressiveRetry(
        operationName: String = "API Call",
        block: suspend () -> T
    ): Result<T> {
        return execute(
            policy = RetryPolicy.aggressive(),
            operationName = operationName,
            block = block
        )
    }
    
    /**
     * Transform raw exception to user-friendly WeeloException
     */
    private fun transformException(exception: Throwable?, operationName: String): WeeloException {
        return when (exception) {
            is WeeloException -> exception
            
            is retrofit2.HttpException -> {
                when (exception.code()) {
                    401 -> WeeloException.Unauthorized("Session expired. Please login again.")
                    403 -> WeeloException.Forbidden("You don't have permission for this action.")
                    404 -> WeeloException.NotFound("The requested resource was not found.")
                    422 -> WeeloException.ValidationError("Invalid data provided.")
                    429 -> WeeloException.RateLimited("Too many requests. Please wait a moment.")
                    in 500..599 -> WeeloException.ServerError("Server error. Please try again later.")
                    else -> WeeloException.ApiError("Request failed: ${exception.message()}", exception.code())
                }
            }
            
            is java.net.SocketTimeoutException -> 
                WeeloException.Timeout("Request timed out. Please check your connection.")
            
            is java.net.UnknownHostException -> 
                WeeloException.NetworkError("Unable to reach server. Please check your internet.")
            
            is java.net.ConnectException -> 
                WeeloException.NetworkError("Connection failed. Please try again.")
            
            is java.io.IOException -> 
                WeeloException.NetworkError("Network error: ${exception.message}")
            
            else -> WeeloException.Unknown("$operationName failed: ${exception?.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Get circuit breaker stats for monitoring
     */
    fun getCircuitBreakerStats(): CircuitBreakerStats {
        return circuitBreaker.getStats()
    }
    
    /**
     * Manually reset circuit breaker (for admin/debug purposes)
     */
    suspend fun resetCircuitBreaker() {
        circuitBreaker.reset()
    }
}
