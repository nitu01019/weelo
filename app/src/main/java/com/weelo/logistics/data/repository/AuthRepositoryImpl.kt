package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.*
import com.weelo.logistics.domain.repository.AuthRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Auth Repository Implementation
 * 
 * Handles authentication operations including:
 * - OTP send/verify
 * - Token management
 * - Session handling
 * 
 * SECURITY:
 * - Tokens stored via TokenManager (encrypted)
 * - OTP never logged
 * - Phone numbers masked in logs
 */
class AuthRepositoryImpl @Inject constructor(
    private val apiService: WeeloApiService,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun sendOtp(phone: String): Result<SendOtpData> {
        return try {
            val response = apiService.sendOtp(
                SendOtpRequest(phone = phone, role = "customer")
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Timber.d("OTP sent to ***${phone.takeLast(4)}")
                    Result.Success(data)
                } else {
                    Result.Error(WeeloException.AuthException("Invalid response from server"))
                }
            } else {
                val error = response.body()?.error
                Timber.w("OTP send failed: ${error?.code}")
                Result.Error(WeeloException.AuthException(error?.message ?: "Failed to send OTP"))
            }
        } catch (e: Exception) {
            Timber.e(e, "OTP send error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }

    override suspend fun verifyOtp(phone: String, otp: String): Result<VerifyOtpData> {
        return try {
            val response = apiService.verifyOtp(
                VerifyOtpRequest(phone = phone, otp = otp, role = "customer")
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    // Save tokens securely
                    tokenManager.saveTokens(
                        accessToken = data.tokens.accessToken,
                        refreshToken = data.tokens.refreshToken,
                        expiresInSeconds = data.tokens.expiresIn
                    )
                    // Save user info
                    tokenManager.saveUserInfo(
                        userId = data.user.id,
                        phone = data.user.phone,
                        role = data.user.role
                    )
                    
                    Timber.d("User authenticated: ${data.user.id}")
                    Result.Success(data)
                } else {
                    Result.Error(WeeloException.AuthException("Invalid response from server"))
                }
            } else {
                val error = response.body()?.error
                Timber.w("OTP verification failed: ${error?.code}")
                Result.Error(WeeloException.AuthException(error?.message ?: "Invalid OTP"))
            }
        } catch (e: Exception) {
            Timber.e(e, "OTP verify error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }

    override suspend fun refreshToken(): Result<String> {
        return try {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                return Result.Error(WeeloException.AuthException("No refresh token available"))
            }

            val response = apiService.refreshToken(
                RefreshTokenRequest(refreshToken = refreshToken)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    // Update access token
                    tokenManager.saveTokens(
                        accessToken = data.accessToken,
                        refreshToken = refreshToken, // Keep existing refresh token
                        expiresInSeconds = data.expiresIn
                    )
                    Timber.d("Token refreshed successfully")
                    Result.Success(data.accessToken)
                } else {
                    Result.Error(WeeloException.AuthException("Failed to refresh token"))
                }
            } else {
                // Refresh failed - user needs to re-login
                tokenManager.clearTokens()
                Result.Error(WeeloException.AuthException("Session expired. Please login again."))
            }
        } catch (e: Exception) {
            Timber.e(e, "Token refresh error")
            Result.Error(WeeloException.NetworkException("Network error"))
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val token = tokenManager.getAccessToken()
            if (token != null) {
                apiService.logout("Bearer $token")
            }
            // Always clear local tokens
            tokenManager.clearTokens()
            Timber.d("User logged out")
            Result.Success(Unit)
        } catch (e: Exception) {
            // Still clear local tokens even if API fails
            tokenManager.clearTokens()
            Timber.w(e, "Logout API failed, local tokens cleared")
            Result.Success(Unit)
        }
    }

    override fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    override fun getUserId(): String? {
        return tokenManager.getUserId()
    }

    override fun getUserPhone(): String? {
        return tokenManager.getUserPhone()
    }

    override suspend fun getProfile(): Result<ProfileResponse> {
        return try {
            val token = tokenManager.getAccessToken()
            if (token.isNullOrBlank()) {
                return Result.Error(WeeloException.AuthException("Not authenticated"))
            }

            val response = apiService.getProfile("Bearer $token")

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.let { profileResponse ->
                    Timber.d("Profile loaded for user: ${profileResponse.data?.profile?.id}")
                    Result.Success(profileResponse)
                } ?: Result.Error(WeeloException.AuthException("Invalid response"))
            } else {
                val error = response.body()?.error
                Timber.w("Get profile failed: ${error?.code}")
                Result.Error(WeeloException.AuthException(error?.message ?: "Failed to load profile"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Get profile error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }

    override suspend fun updateProfile(
        name: String,
        email: String?,
        company: String?,
        gstNumber: String?
    ): Result<ProfileResponse> {
        return try {
            val token = tokenManager.getAccessToken()
            if (token.isNullOrBlank()) {
                return Result.Error(WeeloException.AuthException("Not authenticated"))
            }

            val request = UpdateProfileRequest(
                name = name,
                email = email,
                company = company,
                gstNumber = gstNumber
            )

            val response = apiService.updateProfile("Bearer $token", request)

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.let { profileResponse ->
                    Timber.d("Profile updated for user: ${profileResponse.data?.profile?.id}")
                    Result.Success(profileResponse)
                } ?: Result.Error(WeeloException.AuthException("Invalid response"))
            } else {
                val error = response.body()?.error
                Timber.w("Update profile failed: ${error?.code}")
                Result.Error(WeeloException.AuthException(error?.message ?: "Failed to update profile"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Update profile error")
            Result.Error(WeeloException.NetworkException("Network error. Please try again."))
        }
    }
}
