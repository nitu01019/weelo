package com.weelo.logistics.data.repository

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.core.common.WeeloException
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.data.remote.api.*
import com.weelo.logistics.domain.repository.AuthRepository
import org.json.JSONObject
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
                val friendlyMsg = error?.message?.takeIf { it.isNotBlank() && !it.startsWith("{") }
                    ?: parseFriendlyError(response, "Failed to send OTP. Please try again.")
                Result.Error(WeeloException.AuthException(friendlyMsg))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "OTP send error")
            Result.Error(WeeloException.NetworkException(friendlyNetworkError(e)))
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
                val friendlyMsg = error?.message?.takeIf { it.isNotBlank() && !it.startsWith("{") }
                    ?: parseFriendlyError(response, "Invalid OTP. Please check and try again.")
                Result.Error(WeeloException.AuthException(friendlyMsg))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "OTP verify error")
            Result.Error(WeeloException.NetworkException(friendlyNetworkError(e)))
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
                val friendlyMsg = error?.message?.takeIf { it.isNotBlank() && !it.startsWith("{") }
                    ?: parseFriendlyError(response, "Failed to load profile. Please try again.")
                Result.Error(WeeloException.AuthException(friendlyMsg))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Get profile error")
            Result.Error(WeeloException.NetworkException(friendlyNetworkError(e)))
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
                val friendlyMsg = error?.message?.takeIf { it.isNotBlank() && !it.startsWith("{") }
                    ?: parseFriendlyError(response, "Failed to update profile. Please try again.")
                Result.Error(WeeloException.AuthException(friendlyMsg))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Update profile error")
            Result.Error(WeeloException.NetworkException(friendlyNetworkError(e)))
        }
    }

    // =========================================================================
    // FRIENDLY ERROR MESSAGE HELPERS
    // =========================================================================
    //
    // Ensures users NEVER see raw JSON or error codes on screen.
    // All API errors are converted to plain English messages.
    // =========================================================================

    /**
     * Parses error body JSON and maps error codes to friendly messages.
     */
    private fun parseFriendlyError(response: retrofit2.Response<*>, defaultMsg: String): String {
        try {
            val errorBodyStr = response.errorBody()?.string()
            if (!errorBodyStr.isNullOrBlank()) {
                val json = JSONObject(errorBodyStr)
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optString("code", "") ?: ""
                val message = errorObj?.optString("message", "") ?: ""
                return mapErrorCode(code, message, defaultMsg)
            }
        } catch (_: Exception) {}
        return defaultMsg
    }

    /**
     * Maps backend error codes to plain English.
     */
    private fun mapErrorCode(code: String, serverMsg: String, defaultMsg: String): String {
        return when (code) {
            "OTP_RATE_LIMIT_EXCEEDED" -> "Too many OTP attempts. Please try again in 2 minutes."
            "RATE_LIMIT_EXCEEDED", "TOO_MANY_REQUESTS" -> "Too many requests. Please wait a moment and try again."
            "OTP_EXPIRED" -> "OTP has expired. Please request a new one."
            "INVALID_OTP" -> "Incorrect OTP. Please check and try again."
            "OTP_ALREADY_VERIFIED" -> "This OTP has already been used. Please request a new one."
            "OTP_MAX_ATTEMPTS" -> "Too many incorrect attempts. Please request a new OTP."
            "OTP_SEND_FAILED" -> "Could not send OTP. Please check your number and try again."
            "USER_NOT_FOUND" -> "No account found with this phone number."
            "ACCOUNT_DISABLED" -> "Your account has been disabled. Please contact support."
            "ACCOUNT_SUSPENDED" -> "Your account is suspended. Please contact support."
            "UNAUTHORIZED" -> "Session expired. Please log in again."
            "FORBIDDEN" -> "You don't have permission for this action."
            "SERVICE_UNAVAILABLE" -> "Service is temporarily unavailable. Please try again shortly."
            "INTERNAL_ERROR", "SERVER_ERROR" -> "Something went wrong. Please try again."
            "INVALID_PHONE" -> "Please enter a valid 10-digit phone number."
            "VALIDATION_ERROR" -> serverMsg.ifBlank { "Please check your input and try again." }
            else -> if (serverMsg.isNotBlank() && !serverMsg.startsWith("{")) serverMsg else defaultMsg
        }
    }

    /**
     * Converts network exceptions to plain English.
     */
    private fun friendlyNetworkError(e: Exception): String {
        return when {
            e.message?.contains("timeout", true) == true -> "Connection timed out. Please check your internet and try again."
            e.message?.contains("Unable to resolve", true) == true -> "No internet connection. Please check your network."
            e.message?.contains("Connection refused", true) == true -> "Server is not reachable. Please try again later."
            else -> "Something went wrong. Please check your internet connection and try again."
        }
    }
}
