package com.weelo.logistics.presentation.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.weelo.logistics.MainActivity
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.TokenManager
import com.weelo.logistics.presentation.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ============================================================================
 * SPLASH ACTIVITY - WEELO CUSTOMER APP
 * ============================================================================
 * 
 * TWO-STEP SPLASH FLOW WITH SMOOTH NAVIGATION:
 * 
 * STEP 1 - INSTANT (Window Background):
 *   - Golden WEELO logo with tire design
 *   - Shows IMMEDIATELY when user taps app icon
 *   - Defined in Theme.Weelo.Splash windowBackground
 *   - High quality image (720x1080, ~120KB)
 * 
 * STEP 2 - ACTIVITY LAYOUT:
 *   - Vehicles illustration (Tractor, Truck, JCB, Tempo)
 *   - Large centered image (800x800, ~130KB)
 *   - Displays for 1.5 seconds
 * 
 * STEP 3 - SMOOTH NAVIGATION:
 *   - Right-to-left slide animation
 *   - Navigates to Login (if not logged in) or Home (if logged in)
 *   - 300ms smooth cubic interpolation
 * 
 * ============================================================================
 * ARCHITECTURE NOTES (For Backend Team):
 * ============================================================================
 * 
 * - MVVM Pattern: Activity only handles UI, no business logic
 * - Dependency Injection: TokenManager injected via Hilt
 * - Coroutines: Async delay without blocking main thread
 * - Lifecycle-aware: Uses lifecycleScope (auto-cancels on destroy)
 * 
 * SCALABILITY:
 * - Stateless design: No shared mutable state
 * - Fast startup: Optimized images, minimal code path
 * - Memory efficient: Images loaded once, cleared on finish()
 * 
 * CONFIGURATION:
 * - Change SPLASH_DURATION_MS to adjust timing
 * - Change animations in res/anim/ folder
 * - Change images in res/drawable/ folder
 * 
 * @author Weelo Engineering Team
 * @version 2.0
 * ============================================================================
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    // =========================================================================
    // DEPENDENCIES (Injected by Hilt)
    // =========================================================================
    
    @Inject
    lateinit var tokenManager: TokenManager

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // NOTE: MODE_NIGHT_NO is set in WeeloApplication.onCreate()
        // which runs BEFORE any Activity window is drawn.
        // This ensures splash 1 (window background) is never dimmed.
        
        // Configure status bar and navigation bar colors
        setupSystemBars()
        
        // Display splash 2 (vehicles)
        setContentView(R.layout.activity_splash)

        // Start navigation timer
        startNavigationTimer()
    }

    // =========================================================================
    // SETUP
    // =========================================================================

    /**
     * Configure system bars to match splash 2 background color
     */
    private fun setupSystemBars() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.splash_vehicles_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.splash_vehicles_bg)
    }

    /**
     * Start timer for splash 2 display duration
     */
    private fun startNavigationTimer() {
        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            navigateWithAnimation()
        }
    }

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    /**
     * Navigate to next screen with smooth right-to-left slide animation
     */
    private fun navigateWithAnimation() {
        // Determine destination based on auth state
        val destination = getDestinationScreen()
        
        // Start destination activity
        startActivity(Intent(this, destination))
        
        // Apply smooth slide animation (right to left)
        // New screen slides in from right, current slides out to left
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        
        // Finish splash activity (remove from back stack)
        finish()
    }

    /**
     * Get destination screen based on user authentication state
     * 
     * @return LoginActivity if not logged in, MainActivity if logged in
     */
    private fun getDestinationScreen(): Class<*> {
        return if (isUserLoggedIn()) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
    }

    /**
     * Check if user has valid authentication token
     * 
     * @return true if user has valid access token
     */
    private fun isUserLoggedIn(): Boolean {
        return tokenManager.getAccessToken() != null
    }

    // =========================================================================
    // CONFIGURATION CONSTANTS
    // =========================================================================

    companion object {
        /**
         * Duration to display splash 2 (vehicles) in milliseconds
         * 
         * Total splash time = Android window draw time (~200-500ms) + this duration
         * 
         * Recommended values:
         * - 1000ms: Fast, minimal branding
         * - 1500ms: Balanced (current)
         * - 2000ms: More branding time
         */
        private const val SPLASH_DURATION_MS = 1500L
    }
}
