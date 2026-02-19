package com.weelo.logistics.data.remote

import com.weelo.logistics.BuildConfig
import timber.log.Timber

/**
 * =============================================================================
 * API CONFIGURATION - WEELO CUSTOMER APP
 * =============================================================================
 * 
 * Connects to: WEELO UNIFIED BACKEND (Desktop/weelo-backend)
 * 
 * This app shares the same backend as the Captain App.
 * All API endpoints are versioned under /api/v1/
 * 
 * ENVIRONMENT SETUP:
 * - Emulator: Uses 10.0.2.2 (Android's localhost alias)
 * - Physical Device: Use your Mac's local IP (run: ipconfig getifaddr en0)
 * - Production: Uses api.weelologistics.com (AWS/Cloud)
 * 
 * SECURITY:
 * - No secrets stored here
 * - URLs are non-sensitive configuration
 * - HTTPS required for production
 * 
 * AWS MIGRATION:
 * - Update PRODUCTION_HOST when moving to AWS
 * - Update certificate pinning hashes for new domain
 * =============================================================================
 */
object ApiConfig {
    
    /**
     * =========================================================================
     * ENVIRONMENT SELECTOR
     * =========================================================================
     * Change this single value to switch between environments:
     * - EMULATOR: Testing on Android emulator (localhost via 10.0.2.2)
     * - DEVICE: Testing on physical phone over WiFi (same network as laptop)
     * - STAGING: Pre-production testing
     * - PRODUCTION: Live production (AWS/Cloud)
     */
    enum class Environment {
        EMULATOR,    // Android emulator → laptop localhost
        DEVICE,      // Physical phone → laptop over WiFi
        STAGING,     // Staging server (pre-production)
        PRODUCTION   // Live production server (AWS)
    }
    
    /**
     * CURRENT ACTIVE ENVIRONMENT
     * ===========================
     * Change this to switch environments easily!
     * 
     * For development: EMULATOR or DEVICE
     * For testing: STAGING
     * For release: PRODUCTION (auto-selected for release builds)
     * 
     * NOTE: Now using PRODUCTION for both debug and release
     * to test with AWS backend. Change back to DEVICE for local testing.
     */
    private val currentEnvironment: Environment
        get() = Environment.PRODUCTION  // Use AWS backend
    
    /**
     * YOUR LAPTOP'S WIFI IP ADDRESS
     * ==============================
     * Update this when testing on physical device!
     * 
     * To find your IP:
     * - Mac: Run `ipconfig getifaddr en0` in Terminal
     * - Windows: Run `ipconfig` and find IPv4 Address
     * - Linux: Run `hostname -I`
     * 
     * Make sure phone and laptop are on SAME WiFi network!
     */
    private const val DEVICE_IP = "192.168.1.10"  // <-- UPDATE THIS WITH YOUR IP!
    
    // Port where backend runs
    private const val PORT = "3000"
    
    // API version path
    private const val API_PATH = "/api/v1/"
    
    // =========================================================================
    // URL DEFINITIONS (Don't change unless server locations change)
    // =========================================================================
    
    // Development URLs (HTTP - local only)
    private const val EMULATOR_HOST = "10.0.2.2"  // Android's localhost alias
    private val EMULATOR_BASE = "http://$EMULATOR_HOST:$PORT$API_PATH"
    private val DEVICE_BASE = "http://$DEVICE_IP:$PORT$API_PATH"
    
    // Production URLs (AWS/Cloud)
    // Update these when migrating to custom domain
    private const val STAGING_HOST = "staging-api.weelologistics.com"
    private const val PRODUCTION_HOST = "weelo-alb-380596483.ap-south-1.elb.amazonaws.com"
    private const val STAGING_BASE = "https://$STAGING_HOST$API_PATH"
    // TODO: CRITICAL — Change to HTTPS when SSL certificate is configured on ALB
    // Currently HTTP because ALB does not have SSL configured yet.
    // Must switch to HTTPS before public production launch.
    // TODO: CRITICAL — Change to HTTPS when SSL certificate is configured on ALB
    // Currently HTTP because ALB does not have HTTPS listener yet.
    // Must switch to HTTPS before public production launch.
    private const val PRODUCTION_BASE = "http://$PRODUCTION_HOST$API_PATH"
    
    // WebSocket URLs (for Socket.IO real-time communication)
    private val WS_EMULATOR = "http://$EMULATOR_HOST:$PORT"
    private val WS_DEVICE = "http://$DEVICE_IP:$PORT"
    private const val WS_STAGING = "wss://$STAGING_HOST"
    // TODO: CRITICAL — Change to WSS when SSL certificate is configured on ALB
    // TODO: CRITICAL — Change to WSS when SSL certificate is configured on ALB
    private const val WS_PRODUCTION = "ws://$PRODUCTION_HOST"
    
    // =========================================================================
    // ACTIVE URLS (Auto-selected based on currentEnvironment)
    // =========================================================================
    
    /**
     * Active Base URL - Used for all REST API calls
     */
    val BASE_URL: String
        get() = when (currentEnvironment) {
            Environment.EMULATOR -> EMULATOR_BASE
            Environment.DEVICE -> DEVICE_BASE
            Environment.STAGING -> STAGING_BASE
            Environment.PRODUCTION -> PRODUCTION_BASE
        }
    
    /**
     * Active WebSocket URL - Used for real-time communication
     */
    val SOCKET_URL: String
        get() = when (currentEnvironment) {
            Environment.EMULATOR -> WS_EMULATOR
            Environment.DEVICE -> WS_DEVICE
            Environment.STAGING -> WS_STAGING
            Environment.PRODUCTION -> WS_PRODUCTION
        }
    
    /**
     * Check if currently in development mode
     */
    val isDevelopment: Boolean
        get() = currentEnvironment == Environment.EMULATOR || 
                currentEnvironment == Environment.DEVICE
    
    /**
     * Get human-readable environment name (for logging/debugging)
     */
    val environmentName: String
        get() = currentEnvironment.name
    
    /**
     * Log current configuration (call at app startup for debugging)
     */
    fun logConfiguration() {
        Timber.i("""
            |╔══════════════════════════════════════════════════════════════╗
            |║  WEELO CUSTOMER APP - API CONFIGURATION                      ║
            |╠══════════════════════════════════════════════════════════════╣
            |║  Environment: $environmentName
            |║  Base URL: $BASE_URL
            |║  WebSocket URL: $SOCKET_URL
            |╚══════════════════════════════════════════════════════════════╝
        """.trimMargin())
    }
    
    // Legacy object for backward compatibility
    object BaseUrls {
        val PRODUCTION get() = PRODUCTION_BASE
        val STAGING get() = STAGING_BASE
        val DEVELOPMENT get() = EMULATOR_BASE
        val LOCAL_DEVICE get() = DEVICE_BASE
    }
    
    object SocketUrls {
        val PRODUCTION get() = WS_PRODUCTION
        val STAGING get() = WS_STAGING
        val DEVELOPMENT get() = WS_EMULATOR
    }
    
    /**
     * API Endpoints - Grouped by Module
     * All endpoints are relative to BASE_URL
     */
    object Endpoints {
        // =====================================================================
        // AUTH MODULE - /api/v1/auth
        // =====================================================================
        const val AUTH_SEND_OTP = "auth/send-otp"
        const val AUTH_VERIFY_OTP = "auth/verify-otp"
        const val AUTH_REFRESH_TOKEN = "auth/refresh"
        const val AUTH_LOGOUT = "auth/logout"
        
        // =====================================================================
        // PROFILE MODULE - /api/v1/profile
        // =====================================================================
        const val PROFILE_GET = "profile"
        const val PROFILE_CREATE = "profile"
        const val PROFILE_UPDATE = "profile"
        
        // =====================================================================
        // BOOKING MODULE - /api/v1/bookings (Customer creates bookings)
        // =====================================================================
        const val BOOKINGS = "bookings"
        const val BOOKING_CREATE = "bookings"
        const val BOOKING_DETAILS = "bookings/{bookingId}"
        const val BOOKING_CANCEL = "bookings/{bookingId}/cancel"
        const val BOOKING_STATUS = "bookings/{bookingId}/status"
        const val MY_BOOKINGS = "bookings/my-bookings"
        
        // =====================================================================
        // TRACKING MODULE - /api/v1/tracking (Customer tracks driver)
        // =====================================================================
        const val TRACKING_GET_LOCATION = "tracking/{bookingId}/location"
        const val TRACKING_HISTORY = "tracking/{bookingId}/history"
        
        // =====================================================================
        // PRICING MODULE - /api/v1/pricing (Fare estimation)
        // =====================================================================
        const val PRICING_ESTIMATE = "pricing/estimate"
        
        // =====================================================================
        // VEHICLE MODULE - /api/v1/vehicles (View available vehicles)
        // =====================================================================
        const val VEHICLES = "vehicles"
        const val VEHICLE_TYPES = "vehicles/types"
    }
    
    /**
     * Timeout configurations (in seconds)
     */
    object Timeouts {
        const val CONNECT = 30L
        const val READ = 30L
        const val WRITE = 30L
    }
    
    /**
     * API version
     */
    const val API_VERSION = "v1"
}
