package com.weelo.logistics.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * CIRCUIT BREAKER - Production-Grade Network Resilience
 * =============================================================================
 * 
 * Implements the Circuit Breaker pattern to prevent cascading failures
 * when backend services are unavailable.
 * 
 * STATES:
 * - CLOSED: Normal operation, requests flow through
 * - OPEN: Circuit tripped, requests fail fast
 * - HALF_OPEN: Testing if service recovered
 * 
 * CONFIGURATION:
 * - failureThreshold: Number of failures before opening circuit (default: 5)
 * - resetTimeoutMs: Time to wait before testing recovery (default: 30s)
 * - successThreshold: Successes needed to close circuit (default: 2)
 * 
 * =============================================================================
 */
@Singleton
class CircuitBreaker @Inject constructor() {
    
    companion object {
        private const val TAG = "CircuitBreaker"
        
        // Default configuration
        const val DEFAULT_FAILURE_THRESHOLD = 5
        const val DEFAULT_RESET_TIMEOUT_MS = 30_000L // 30 seconds
        const val DEFAULT_SUCCESS_THRESHOLD = 2
    }
    
    /**
     * Circuit breaker states
     */
    enum class State {
        CLOSED,     // Normal operation
        OPEN,       // Failing fast
        HALF_OPEN   // Testing recovery
    }
    
    // Configuration
    private var failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD
    private var resetTimeoutMs: Long = DEFAULT_RESET_TIMEOUT_MS
    private var successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD
    
    // State tracking
    private val _state = MutableStateFlow(State.CLOSED)
    val state: StateFlow<State> = _state.asStateFlow()
    
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    
    private val mutex = Mutex()
    
    /**
     * Configure circuit breaker parameters
     */
    fun configure(
        failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
        resetTimeoutMs: Long = DEFAULT_RESET_TIMEOUT_MS,
        successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD
    ) {
        this.failureThreshold = failureThreshold
        this.resetTimeoutMs = resetTimeoutMs
        this.successThreshold = successThreshold
        Timber.d("$TAG: Configured - failures=$failureThreshold, timeout=${resetTimeoutMs}ms, successes=$successThreshold")
    }
    
    /**
     * Check if request should be allowed
     * Returns true if request can proceed, false if circuit is open
     */
    suspend fun allowRequest(): Boolean = mutex.withLock {
        return@withLock when (_state.value) {
            State.CLOSED -> true
            
            State.OPEN -> {
                // Check if reset timeout has passed
                val timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get()
                if (timeSinceFailure >= resetTimeoutMs) {
                    Timber.d("$TAG: Reset timeout passed, transitioning to HALF_OPEN")
                    _state.value = State.HALF_OPEN
                    successCount.set(0)
                    true
                } else {
                    Timber.d("$TAG: Circuit OPEN, rejecting request (${resetTimeoutMs - timeSinceFailure}ms remaining)")
                    false
                }
            }
            
            State.HALF_OPEN -> {
                Timber.d("$TAG: HALF_OPEN state, allowing test request")
                true
            }
        }
    }
    
    /**
     * Record a successful request
     */
    suspend fun recordSuccess() = mutex.withLock {
        when (_state.value) {
            State.CLOSED -> {
                // Reset failure count on success
                failureCount.set(0)
            }
            
            State.HALF_OPEN -> {
                val successes = successCount.incrementAndGet()
                Timber.d("$TAG: HALF_OPEN success $successes/$successThreshold")
                
                if (successes >= successThreshold) {
                    Timber.d("$TAG: Success threshold reached, closing circuit")
                    _state.value = State.CLOSED
                    failureCount.set(0)
                    successCount.set(0)
                }
            }
            
            State.OPEN -> {
                // Shouldn't happen, but handle gracefully
                Timber.w("$TAG: Success recorded while OPEN - unexpected state")
            }
        }
    }
    
    /**
     * Record a failed request
     */
    suspend fun recordFailure(exception: Throwable? = null) = mutex.withLock {
        lastFailureTime.set(System.currentTimeMillis())
        
        when (_state.value) {
            State.CLOSED -> {
                val failures = failureCount.incrementAndGet()
                Timber.d("$TAG: CLOSED failure $failures/$failureThreshold")
                
                if (failures >= failureThreshold) {
                    Timber.w("$TAG: Failure threshold reached, opening circuit")
                    _state.value = State.OPEN
                }
            }
            
            State.HALF_OPEN -> {
                Timber.w("$TAG: HALF_OPEN failure, reopening circuit")
                _state.value = State.OPEN
                successCount.set(0)
            }
            
            State.OPEN -> {
                // Already open, just update timestamp
                Timber.d("$TAG: Additional failure while OPEN")
            }
        }
        
        exception?.let {
            Timber.e(it, "$TAG: Failure recorded: ${it.message}")
        }
    }
    
    /**
     * Force reset the circuit breaker (for manual recovery)
     */
    suspend fun reset() = mutex.withLock {
        Timber.d("$TAG: Manual reset triggered")
        _state.value = State.CLOSED
        failureCount.set(0)
        successCount.set(0)
        lastFailureTime.set(0)
    }
    
    /**
     * Get current statistics
     */
    fun getStats(): CircuitBreakerStats {
        return CircuitBreakerStats(
            state = _state.value,
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = lastFailureTime.get(),
            timeUntilReset = if (_state.value == State.OPEN) {
                maxOf(0, resetTimeoutMs - (System.currentTimeMillis() - lastFailureTime.get()))
            } else 0
        )
    }
}

/**
 * Statistics for circuit breaker monitoring
 */
data class CircuitBreakerStats(
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Long,
    val timeUntilReset: Long
)
