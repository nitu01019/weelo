package com.weelo.logistics.presentation.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.weelo.logistics.MainActivity
import com.weelo.logistics.R
import com.weelo.logistics.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Login Activity - Customer Authentication
 * 
 * Handles phone number input and OTP verification flow.
 * Uses the backend API for authentication.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    
    private lateinit var otpFields: List<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already logged in
        if (viewModel.isLoggedIn()) {
            navigateToHome()
            return
        }

        setupUI()
        setupOtpFields()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            // Send OTP button
            btnSendOtp.setOnClickListener {
                val phone = etPhone.text.toString().trim()
                if (validatePhone(phone)) {
                    viewModel.sendOtp(phone)
                }
            }

            // Verify OTP button
            btnVerifyOtp.setOnClickListener {
                val otp = getOtpFromFields()
                if (otp.length == 6) {
                    viewModel.verifyOtp(otp)
                } else {
                    Toast.makeText(this@LoginActivity, "Please enter complete OTP", Toast.LENGTH_SHORT).show()
                }
            }

            // Back to phone input
            btnBackToPhone.setOnClickListener {
                showPhoneInput()
            }

            // Resend OTP
            tvResendOtp.setOnClickListener {
                viewModel.resendOtp()
            }

            // Phone input validation
            etPhone.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    btnSendOtp.isEnabled = s?.length == 10
                    btnSendOtp.alpha = if (s?.length == 10) 1.0f else 0.5f
                }
            })
        }
    }

    private fun setupOtpFields() {
        binding.apply {
            otpFields = listOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)

            otpFields.forEachIndexed { index, editText ->
                editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (s?.length == 1 && index < otpFields.size - 1) {
                            otpFields[index + 1].requestFocus()
                        }
                        
                        // Auto verify when all fields filled - OPTIMIZED: immediate submit
                        val otp = getOtpFromFields()
                        if (otp.length == 6) {
                            // Submit immediately without button click delay
                            viewModel.verifyOtp(otp)
                        }
                    }
                })

                // Handle backspace
                editText.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                        event.action == android.view.KeyEvent.ACTION_DOWN &&
                        editText.text.isEmpty() && index > 0) {
                        otpFields[index - 1].requestFocus()
                        otpFields[index - 1].text.clear()
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is LoginUiState.Idle -> hideLoading()
                    is LoginUiState.Loading -> showLoading()
                    is LoginUiState.OtpSent -> {
                        hideLoading()
                        showOtpInput(state.phone, state.mockOtp)
                    }
                    is LoginUiState.Success -> {
                        hideLoading()
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        navigateToHome()
                    }
                    is LoginUiState.Error -> {
                        hideLoading()
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun validatePhone(phone: String): Boolean {
        return when {
            phone.isEmpty() -> {
                Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
                false
            }
            phone.length != 10 -> {
                Toast.makeText(this, "Please enter valid 10 digit number", Toast.LENGTH_SHORT).show()
                false
            }
            !phone.all { it.isDigit() } -> {
                Toast.makeText(this, "Phone number should contain only digits", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun getOtpFromFields(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }

    private fun showPhoneInput() {
        binding.apply {
            phoneInputSection.visibility = View.VISIBLE
            otpSection.visibility = View.GONE
            clearOtpFields()
        }
    }

    private fun showOtpInput(phone: String, mockOtp: String?) {
        binding.apply {
            phoneInputSection.visibility = View.GONE
            otpSection.visibility = View.VISIBLE
            tvOtpSentTo.text = "OTP sent to +91 $phone"
            
            // Show mock OTP hint in dev mode
            if (!mockOtp.isNullOrEmpty()) {
                tvOtpHint.visibility = View.VISIBLE
                tvOtpHint.text = "💡 Dev Mode: Use OTP $mockOtp"
            } else {
                tvOtpHint.visibility = View.GONE
            }
            
            etOtp1.requestFocus()
        }
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.text.clear() }
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
