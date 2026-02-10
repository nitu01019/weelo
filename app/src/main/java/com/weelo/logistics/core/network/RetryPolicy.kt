package com.weelo.logistics.core.network

import timber.log.Timber
import kotlin.math.min
import kotlin.math.pow

/**
 * =============================================================================
 * RETRY POLICY - Configurable Retry Strategies
 * =============================================================================
 * 
 * Provides different retry strategies for network operations:
 * - Exponential backoff (recommended for most cases)
 * - Linear backoff
 * - Fixed delay
 * - No retry
 * 
 * USAGE:
 * ```kotlin
 * val policy = RetryPolicy.exponentialBackoff(maxRetries = 3)
 * val result = resilientExecutor.execute(policy) { api.getData() }
 * ```
 * =============================================================================
 */
sealed class RetryPolicy {
    
    abstract val maxRetries: Int
    abstract fun getDelayMs(attempt: Int): Long
    abstract fun shouldRetry(exception: Throwable): Boolean
    
    /**
     * No retry - fail immediately
     */
    object NoRetry : RetryPolicy() {
        override val maxRetries: Int = 0
        override fun getDelayMs(attempt: Int): Long = 0
        override fun shouldRetry(exception: Throwable): Boolean = false
    }
    
    /**
     * Fixed delay retry
     */
    data class FixedDelay(
        override val maxRetries: Int = 3,
        private val delayMs: Long = 1000,
        private val retryableExceptions: Set<Class<out Throwable>> = DEFAULT_RETRYABLE
    ) : RetryPolicy() {
        
        override fun getDelayMs(attempt: Int): Long = delayMs
        
        override fun shouldRetry(exception: Throwable): Boolean {
            return isRetryable(exception, retryableExceptions)
        }
    }
    
    /**
     * Linear backoff retry
     * Delay increases linearly: delay * attempt
     */
    data class LinearBackoff(
        override val maxRetries: Int = 3,
        private val initialDelayMs: Long = 1000,
        private val maxDelayMs: Long = 10000,
        private val retryableExceptions: Set<Class<out Throwable>> = DEFAULT_RETRYABLE
    ) : RetryPolicy() {
        
        override fun getDelayMs(attempt: Int): Long {
            val delay = initialDelayMs * attempt
            return min(delay, maxDelayMs)
        }
        
        override fun shouldRetry(exception: Throwable): Boolean {
            return isRetryable(exception, retryableExceptions)
        }
    }
    
    /**
     * Exponential backoff retry (RECOMMENDED)
     * Delay increases exponentially: initialDelay * 2^attempt
     * With optional jitter to prevent thundering herd
     */
    data class ExponentialBackoff(
        override val maxRetries: Int = 3,
        private val initialDelayMs: Long = 1000,
        private val maxDelayMs: Long = 30000,
        private val multiplier: Double = 2.0,
        private val jitterFactor: Double = 0.1, // 10% jitter
        private val retryableExceptions: Set<Class<out Throwable>> = DEFAULT_RETRYABLE
    ) : RetryPolicy() {
        
        override fun getDelayMs(attempt: Int): Long {
            // Calculate exponential delay
            val exponentialDelay = initialDelayMs * multiplier.pow(attempt.toDouble())
            val cappedDelay = min(exponentialDelay.toLong(), maxDelayMs)
            
            // Add jitter to prevent thundering herd
            val jitter = (cappedDelay * jitterFactor * (Math.random() * 2 - 1)).toLong()
            val finalDelay = maxOf(0, cappedDelay + jitter)
            
            Timber.d("RetryPolicy: Attempt $attempt, delay=${finalDelay}ms (base=$cappedDelay, jitter=$jitter)")
            return finalDelay
        }
        
        override fun shouldRetry(exception: Throwable): Boolean {
            return isRetryable(exception, retryableExceptions)
        }
    }
    
    companion object {
        private const val TAG = "RetryPolicy"
        
        /**
         * Default retryable exceptions
         */
        val DEFAULT_RETRYABLE: Set<Class<out Throwable>> = setOf(
            java.net.SocketTimeoutException::class.java,
            java.net.UnknownHostException::class.java,
            java.net.ConnectException::class.java,
            java.io.IOException::class.java,
            retrofit2.HttpException::class.java
        )
        
        /**
         * HTTP status codes that are retryable
         */
        val RETRYABLE_HTTP_CODES = setOf(
            408, // Request Timeout
            429, // Too Many Requests
            500, // Internal Server Error
            502, // Bad Gateway
            503, // Service Unavailable
            504  // Gateway Timeout
        )
        
        /**
         * Check if exception is retryable
         */
        fun isRetryable(
            exception: Throwable,
            retryableExceptions: Set<Class<out Throwable>>
        ): Boolean {
            // Check if it's a retryable HTTP error
            if (exception is retrofit2.HttpException) {
                val isRetryable = exception.code() in RETRYABLE_HTTP_CODES
                Timber.d("$TAG: HTTP ${exception.code()} - retryable=$isRetryable")
                return isRetryable
            }
            
            // Check against retryable exception types
            val isRetryable = retryableExceptions.any { it.isInstance(exception) }
            Timber.d("$TAG: ${exception::class.simpleName} - retryable=$isRetryable")
            return isRetryable
        }
        
        /**
         * Create default exponential backoff policy
         */
        fun exponentialBackoff(
            maxRetries: Int = 3,
            initialDelayMs: Long = 1000,
            maxDelayMs: Long = 30000
        ): ExponentialBackoff {
            return ExponentialBackoff(
                maxRetries = maxRetries,
                initialDelayMs = initialDelayMs,
                maxDelayMs = maxDelayMs
            )
        }
        
        /**
         * Create aggressive retry policy for critical operations
         */
        fun aggressive(): ExponentialBackoff {
            return ExponentialBackoff(
                maxRetries = 5,
                initialDelayMs = 500,
                maxDelayMs = 15000,
                multiplier = 1.5
            )
        }
        
        /**
         * Create conservative retry policy for non-critical operations
         */
        fun conservative(): ExponentialBackoff {
            return ExponentialBackoff(
                maxRetries = 2,
                initialDelayMs = 2000,
                maxDelayMs = 10000
            )
        }
    }
}
