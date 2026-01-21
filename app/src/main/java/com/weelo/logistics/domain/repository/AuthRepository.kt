package com.weelo.logistics.domain.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.data.remote.api.ProfileResponse
import com.weelo.logistics.data.remote.api.SendOtpData
import com.weelo.logistics.data.remote.api.VerifyOtpData

/**
 * Auth Repository Interface
 * 
 * Domain layer contract for authentication operations.
 * Implementation details are hidden from domain/presentation layers.
 */
interface AuthRepository {
    
    /**
     * Send OTP to phone number
     */
    suspend fun sendOtp(phone: String): Result<SendOtpData>
    
    /**
     * Verify OTP and authenticate user
     */
    suspend fun verifyOtp(phone: String, otp: String): Result<VerifyOtpData>
    
    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshToken(): Result<String>
    
    /**
     * Logout user and clear tokens
     */
    suspend fun logout(): Result<Unit>
    
    /**
     * Check if user is currently logged in
     */
    fun isLoggedIn(): Boolean
    
    /**
     * Get current user ID
     */
    fun getUserId(): String?
    
    /**
     * Get current user phone
     */
    fun getUserPhone(): String?
    
    /**
     * Get user profile from backend
     */
    suspend fun getProfile(): Result<ProfileResponse>
    
    /**
     * Update user profile in backend database
     */
    suspend fun updateProfile(
        name: String,
        email: String?,
        company: String?,
        gstNumber: String?
    ): Result<ProfileResponse>
}
