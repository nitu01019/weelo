package com.weelo.logistics.core.security

import okhttp3.CertificatePinner
import timber.log.Timber

/**
 * =============================================================================
 * CERTIFICATE PINNER - MITM Attack Prevention
 * =============================================================================
 * 
 * Pins the AWS ALB certificate to prevent man-in-the-middle attacks.
 * 
 * HOW TO GET THE PIN HASH:
 * 1. Run: openssl s_client -connect weelo-alb-380596483.ap-south-1.elb.amazonaws.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
 * 2. Copy the hash and add it below
 * 
 * IMPORTANT:
 * - Always include a backup pin (different CA or intermediate)
 * - Update pins before certificate rotation
 * 
 * =============================================================================
 */
object CertificatePinnerConfig {
    
    // Production domain (AWS ALB)
    private const val PRODUCTION_HOST = "weelo-alb-380596483.ap-south-1.elb.amazonaws.com"
    
    // Custom domain (when configured)
    private const val CUSTOM_HOST = "api.weelo.in"
    
    /**
     * Pin hashes for AWS ALB certificate
     * 
     * TODO: Generate actual pins when SSL is configured:
     * 1. openssl s_client -connect $PRODUCTION_HOST:443 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
     * 
     * Current values are placeholders - replace before production!
     */
    private val awsPins = arrayOf(
        // Primary certificate pin (replace with actual)
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        // Backup pin (Amazon Root CA)
        "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
    )
    
    /**
     * Check if certificate pinning is enabled
     * Disabled until SSL is configured on ALB
     */
    val isPinningEnabled: Boolean = false // TODO: Enable when SSL is configured
    
    /**
     * Build CertificatePinner for OkHttp
     * Returns null if pinning is disabled
     */
    fun buildPinner(): CertificatePinner? {
        if (!isPinningEnabled) {
            Timber.w("⚠️ Certificate pinning is DISABLED")
            return null
        }
        
        return try {
            CertificatePinner.Builder()
                .add(PRODUCTION_HOST, *awsPins)
                .add(CUSTOM_HOST, *awsPins)
                .build()
                .also { Timber.i("✅ Certificate pinning enabled") }
        } catch (e: Exception) {
            Timber.e(e, "Failed to build certificate pinner")
            null
        }
    }
}
