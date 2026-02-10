package com.weelo.logistics.presentation.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.weelo.logistics.MainActivity
import com.weelo.logistics.R
import com.weelo.logistics.databinding.ActivityLoginBinding
import com.weelo.logistics.databinding.BottomSheetPhoneHintsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * LOGIN ACTIVITY - WEELO CUSTOMER APP
 * ============================================================================
 * 
 * Premium Rapido-style login flow with:
 * - Phone number input with +91 prefix
 * - Phone hints bottom sheet for previously used numbers
 * - OTP verification (6 digit) with auto-advance
 * - Button-level loading (no screen flicker)
 * - Resend timer (30 seconds)
 * - Landscape support
 * 
 * ARCHITECTURE:
 * - MVVM pattern with LoginViewModel
 * - View binding for type-safe UI access
 * - StateFlow for reactive UI updates
 * - Coroutines for async operations
 * 
 * SCALABILITY:
 * - Stateless UI (all state in ViewModel)
 * - Clean separation of concerns
 * - Modular helper methods
 * - Easy to extend with new features
 * 
 * @author Weelo Engineering Team
 * ============================================================================
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    // =========================================================================
    // PROPERTIES
    // =========================================================================

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    
    private lateinit var otpFields: List<EditText>
    private var resendTimer: CountDownTimer? = null
    private var canResend = false
    
    // Bottom sheet for phone hints
    private var phoneHintsBottomSheet: BottomSheetDialog? = null

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_blue)

        // Skip login if already authenticated
        if (viewModel.isLoggedIn()) {
            navigateToHome()
            return
        }

        initializeViews()
        setupPhoneInput()
        setupOtpInput()
        setupClickListeners()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
        resendTimer = null
        phoneHintsBottomSheet?.dismiss()
        phoneHintsBottomSheet = null
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    private fun initializeViews() {
        binding.apply {
            otpFields = listOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)
        }
    }

    private fun setupPhoneInput() {
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val isValid = s?.length == 10
                binding.btnSendOtp.isEnabled = isValid
                updateButtonState(binding.btnSendOtp, isValid)
            }
        })

        // Show phone hints when input is focused
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.etPhone.text.isNullOrEmpty()) {
                showPhoneHintsIfAvailable()
            }
        }

        // Also show hints when clicking the input container
        binding.phoneInputContainer.setOnClickListener {
            binding.etPhone.requestFocus()
            if (binding.etPhone.text.isNullOrEmpty()) {
                showPhoneHintsIfAvailable()
            }
        }
    }

    private fun setupOtpInput() {
        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.size - 1) {
                        otpFields[index + 1].requestFocus()
                    }
                    
                    val otpComplete = getOtpFromFields().length == 6
                    binding.btnVerifyOtp.isEnabled = otpComplete
                    updateButtonState(binding.btnVerifyOtp, otpComplete)
                    
                    // Auto-submit when complete
                    if (otpComplete) {
                        viewModel.verifyOtp(getOtpFromFields())
                    }
                }
            })

            // Handle backspace
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                    event.action == android.view.KeyEvent.ACTION_DOWN &&
                    editText.text.isEmpty() && index > 0) {
                    otpFields[index - 1].apply {
                        requestFocus()
                        text.clear()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            btnSendOtp.setOnClickListener {
                val phone = etPhone.text.toString().trim()
                if (validatePhone(phone)) {
                    viewModel.sendOtp(phone)
                }
            }

            btnVerifyOtp.setOnClickListener {
                val otp = getOtpFromFields()
                if (otp.length == 6) {
                    viewModel.verifyOtp(otp)
                } else {
                    showToast("Please enter complete OTP")
                }
            }

            btnBackToPhone.setOnClickListener {
                showPhoneInputScreen()
            }

            resendContainer.setOnClickListener {
                if (canResend) {
                    viewModel.resendOtp()
                    startResendTimer()
                }
            }

            btnHelpPhone.setOnClickListener { showHelpDialog() }
            btnHelpOtp.setOnClickListener { showHelpDialog() }
        }
    }

    // =========================================================================
    // VIEWMODEL OBSERVER
    // =========================================================================

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                handleUiState(state)
            }
        }
    }

    /**
     * Handle UI state changes without screen flicker
     */
    private fun handleUiState(state: LoginUiState) {
        when (state) {
            is LoginUiState.Idle -> {
                hideButtonLoading()
            }
            is LoginUiState.Loading -> {
                showButtonLoading()
            }
            is LoginUiState.OtpSent -> {
                hideButtonLoading()
                showOtpScreen(state.phone, state.mockOtp)
                startResendTimer()
            }
            is LoginUiState.Success -> {
                hideButtonLoading()
                showToast("Login successful!")
                navigateToHome()
            }
            is LoginUiState.Error -> {
                hideButtonLoading()
                // Only clear OTP if on OTP screen and it's an OTP error
                if (binding.otpSection.visibility == View.VISIBLE) {
                    clearOtpFields()
                }
                showToast(state.message)
            }
        }
    }

    // =========================================================================
    // PHONE HINTS BOTTOM SHEET
    // =========================================================================

    /**
     * Show bottom sheet with previously used phone numbers
     */
    private fun showPhoneHintsIfAvailable() {
        val savedPhones = getSavedPhoneNumbers()
        if (savedPhones.isEmpty()) return

        phoneHintsBottomSheet?.dismiss()
        
        val bottomSheetBinding = BottomSheetPhoneHintsBinding.inflate(layoutInflater)
        phoneHintsBottomSheet = BottomSheetDialog(this, R.style.BottomSheetDialogTheme).apply {
            setContentView(bottomSheetBinding.root)
            
            // Setup RecyclerView
            val adapter = PhoneHintAdapter { selectedPhone ->
                binding.etPhone.setText(selectedPhone)
                binding.etPhone.setSelection(selectedPhone.length)
                dismiss()
            }
            
            bottomSheetBinding.rvPhoneHints.apply {
                layoutManager = LinearLayoutManager(this@LoginActivity)
                this.adapter = adapter
            }
            adapter.submitList(savedPhones)
            
            // None of the above button
            bottomSheetBinding.tvNoneOfAbove.setOnClickListener {
                dismiss()
                binding.etPhone.requestFocus()
            }
            
            show()
        }
    }

    /**
     * Get saved phone numbers from preferences
     * Returns list of previously used phone numbers
     */
    private fun getSavedPhoneNumbers(): List<String> {
        // Get from shared preferences - these are phones that successfully logged in before
        val prefs = getSharedPreferences("weelo_auth", MODE_PRIVATE)
        val savedPhonesSet = prefs.getStringSet("saved_phones", emptySet()) ?: emptySet()
        return savedPhonesSet.toList().take(5) // Max 5 numbers
    }

    /**
     * Save phone number after successful login
     */
    private fun savePhoneNumber(phone: String) {
        val prefs = getSharedPreferences("weelo_auth", MODE_PRIVATE)
        val savedPhones = prefs.getStringSet("saved_phones", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        savedPhones.add(phone)
        // Keep only last 5 numbers
        val limitedPhones = savedPhones.toList().takeLast(5).toSet()
        prefs.edit().putStringSet("saved_phones", limitedPhones).apply()
    }

    // =========================================================================
    // SCREEN TRANSITIONS
    // =========================================================================

    private fun showPhoneInputScreen() {
        resendTimer?.cancel()
        binding.apply {
            phoneInputSection.visibility = View.VISIBLE
            otpSection.visibility = View.GONE
            clearOtpFields()
            updateButtonState(btnVerifyOtp, false)
        }
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_blue)
    }

    private fun showOtpScreen(phone: String, mockOtp: String?) {
        binding.apply {
            phoneInputSection.visibility = View.GONE
            otpSection.visibility = View.VISIBLE
            tvOtpSentTo.text = "Sent to $phone"
            
            clearOtpFields()
            updateButtonState(btnVerifyOtp, false)
            
            tvOtpHint.visibility = if (!mockOtp.isNullOrEmpty()) {
                tvOtpHint.text = "Dev Mode: Use OTP $mockOtp"
                View.VISIBLE
            } else {
                View.GONE
            }
            
            etOtp1.requestFocus()
        }
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
    }

    // =========================================================================
    // BUTTON LOADING (NO SCREEN FLICKER)
    // =========================================================================

    /**
     * Show loading inside button - Rapido-style yellow button with spinner
     * 
     * CRITICAL FIX: backgroundTintList = null disables Material Components tinting
     * Without this, Material Button overrides the custom background with its own theme
     */
    private fun showButtonLoading() {
        binding.apply {
            if (otpSection.visibility == View.VISIBLE) {
                // OTP screen - show loading in verify button
                btnVerifyOtp.apply {
                    text = ""  // Clear text to show only spinner
                    isEnabled = false
                    isClickable = false
                    // CRITICAL: Disable Material tint so our background shows
                    backgroundTintList = null
                    background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.bg_button_loading)
                    visibility = View.VISIBLE
                }
                progressVerifyOtp.visibility = View.VISIBLE
                progressVerifyOtp.bringToFront()
            } else {
                // Phone screen - show loading in send button
                btnSendOtp.apply {
                    text = ""  // Clear text to show only spinner
                    isEnabled = false
                    isClickable = false
                    // CRITICAL: Disable Material tint so our background shows
                    backgroundTintList = null
                    background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.bg_button_loading)
                    visibility = View.VISIBLE
                }
                progressSendOtp.visibility = View.VISIBLE
                progressSendOtp.bringToFront()
            }
        }
    }

    /**
     * Hide button loading and restore button state
     * Restores button to its proper enabled/disabled state
     */
    private fun hideButtonLoading() {
        binding.apply {
            // Restore send OTP button
            progressSendOtp.visibility = View.GONE
            btnSendOtp.apply {
                text = "Next"
                isClickable = true
                visibility = View.VISIBLE
            }
            val phoneValid = etPhone.text?.length == 10
            btnSendOtp.isEnabled = phoneValid
            updateButtonState(btnSendOtp, phoneValid)
            
            // Restore verify OTP button
            progressVerifyOtp.visibility = View.GONE
            btnVerifyOtp.apply {
                text = "Next"
                isClickable = true
                visibility = View.VISIBLE
            }
            val otpComplete = getOtpFromFields().length == 6
            btnVerifyOtp.isEnabled = otpComplete
            updateButtonState(btnVerifyOtp, otpComplete)
        }
    }

    // =========================================================================
    // TIMER
    // =========================================================================

    private fun startResendTimer() {
        canResend = false
        resendTimer?.cancel()
        
        resendTimer = object : CountDownTimer(RESEND_TIMEOUT_MS, TIMER_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendOtp.apply {
                    text = "Resend in ${seconds}s"
                    setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.text_secondary))
                }
            }

            override fun onFinish() {
                canResend = true
                binding.tvResendOtp.apply {
                    text = "Resend OTP"
                    setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.primary_blue))
                }
            }
        }.start()
    }

    // =========================================================================
    // UI HELPERS
    // =========================================================================

    private fun updateButtonState(button: Button, enabled: Boolean) {
        button.apply {
            // CRITICAL: Disable Material tint so our custom background shows
            backgroundTintList = null
            background = ContextCompat.getDrawable(
                this@LoginActivity,
                if (enabled) R.drawable.bg_next_button_enabled else R.drawable.bg_next_button_disabled
            )
            // Yellow button needs black text, gray button needs gray text
            setTextColor(ContextCompat.getColor(
                this@LoginActivity,
                if (enabled) R.color.black else R.color.text_secondary
            ))
            visibility = View.VISIBLE
        }
    }

    private fun getOtpFromFields(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.text.clear() }
    }

    private fun validatePhone(phone: String): Boolean {
        return when {
            phone.isEmpty() -> {
                showToast("Please enter phone number")
                false
            }
            phone.length != 10 -> {
                showToast("Please enter valid 10 digit number")
                false
            }
            !phone.all { it.isDigit() } -> {
                showToast("Phone number should contain only digits")
                false
            }
            else -> true
        }
    }

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    private fun navigateToHome() {
        // Save the phone number for future hints
        binding.etPhone.text?.toString()?.let { phone ->
            if (phone.length == 10) savePhoneNumber(phone)
        }
        
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
        finish()
    }

    private fun showHelpDialog() {
        showToast("Help & Support coming soon!")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // CONSTANTS
    // =========================================================================

    companion object {
        private const val RESEND_TIMEOUT_MS = 30_000L
        private const val TIMER_INTERVAL_MS = 1_000L
    }
}
