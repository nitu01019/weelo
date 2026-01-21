package com.weelo.logistics.presentation.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
 * SplashActivity - Like Rapido Style
 * 
 * - Dark brown background (dull)
 * - Bright orange-yellow circle (highlighted with glow)
 * - "Weelo" bold text in center
 * - 2 seconds display time
 * - Instant load - no delays
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Show splash for 2 seconds then navigate
        lifecycleScope.launch {
            delay(2000)
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        val intent = if (isUserLoggedIn()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun isUserLoggedIn(): Boolean {
        return tokenManager.getAccessToken() != null
    }
}
