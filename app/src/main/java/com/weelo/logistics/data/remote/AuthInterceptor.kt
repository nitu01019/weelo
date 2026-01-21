package com.weelo.logistics.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Auth Interceptor - Automatically adds authentication token to requests
 * 
 * SECURITY:
 * - Token is retrieved fresh from secure storage on each request
 * - Never caches token in memory longer than necessary
 * - Skips auth for public endpoints
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        /**
         * Endpoints that don't require authentication
         */
        private val PUBLIC_ENDPOINTS = listOf(
            "auth/send-otp",
            "auth/verify-otp",
            "vehicles/types",
            "vehicles/pricing",
            "health"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Skip auth for public endpoints
        if (PUBLIC_ENDPOINTS.any { path.contains(it) }) {
            return chain.proceed(originalRequest)
        }

        // Skip if Authorization header already present (manual override)
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        // Get token from secure storage
        val token = tokenManager.getAccessToken()

        // If no token, proceed without auth (will likely get 401)
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        // Add Bearer token
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
