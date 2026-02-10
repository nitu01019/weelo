package com.weelo.logistics.core.security

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * RATE LIMIT INTERCEPTOR - Client-side API Throttling
 * =============================================================================
 * 
 * Prevents rapid-fire API calls that could:
 * - Overload the backend
 * - Indicate brute force attacks
 * - Waste user's battery/data
 * 
 * CONFIGURATION:
 * - 100 requests per minute per endpoint
 * - 429 Too Many Requests returned if exceeded
 * 
 * =============================================================================
 */
@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {
    
    companion object {
        // Rate limit: 100 requests per minute
        private const val MAX_REQUESTS_PER_MINUTE = 100
        private val TIME_WINDOW_MS = TimeUnit.MINUTES.toMillis(1)
    }
    
    // Track request counts per endpoint
    private val requestCounts = ConcurrentHashMap<String, MutableList<Long>>()
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val endpoint = request.url.encodedPath
        
        // Check rate limit
        if (isRateLimited(endpoint)) {
            Timber.w("⚠️ Rate limit exceeded for: $endpoint")
            throw RateLimitExceededException("Too many requests. Please wait a moment.")
        }
        
        // Record this request
        recordRequest(endpoint)
        
        return chain.proceed(request)
    }
    
    /**
     * Check if endpoint has exceeded rate limit
     */
    private fun isRateLimited(endpoint: String): Boolean {
        val now = System.currentTimeMillis()
        val requests = requestCounts[endpoint] ?: return false
        
        // Clean old entries
        synchronized(requests) {
            requests.removeAll { now - it > TIME_WINDOW_MS }
        }
        
        return requests.size >= MAX_REQUESTS_PER_MINUTE
    }
    
    /**
     * Record a request timestamp for endpoint
     */
    private fun recordRequest(endpoint: String) {
        val now = System.currentTimeMillis()
        requestCounts.getOrPut(endpoint) { mutableListOf() }.add(now)
    }
    
    /**
     * Clear all request history (for testing)
     */
    fun clearHistory() {
        requestCounts.clear()
    }
}

/**
 * Exception thrown when rate limit is exceeded
 */
class RateLimitExceededException(message: String) : IOException(message)
