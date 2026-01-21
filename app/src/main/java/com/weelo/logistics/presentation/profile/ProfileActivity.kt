package com.weelo.logistics.presentation.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.weelo.logistics.R
import com.weelo.logistics.core.util.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ProfileActivity - Customer Profile Screen
 * 
 * Displays and allows editing of customer profile data.
 * All data is fetched from and saved to the backend database.
 * 
 * Features:
 * - Display profile photo placeholder
 * - Show/edit name, email, company, GST number
 * - Phone number (read-only - from login)
 * - Save changes to backend
 */
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    // UI Elements
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout
    private lateinit var profileImage: ImageView
    private lateinit var phoneText: TextView
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var companyInput: TextInputEditText
    private lateinit var gstInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        setupListeners()
        observeViewModel()

        // Load profile data
        viewModel.loadProfile()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        contentLayout = findViewById(R.id.contentLayout)
        profileImage = findViewById(R.id.profileImage)
        phoneText = findViewById(R.id.phoneText)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        companyInput = findViewById(R.id.companyInput)
        gstInput = findViewById(R.id.gstInput)
        saveButton = findViewById(R.id.saveButton)
        logoutButton = findViewById(R.id.logoutButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveProfile()
        }

        logoutButton.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun saveProfile() {
        val name = nameInput.text?.toString()?.trim() ?: ""
        val email = emailInput.text?.toString()?.trim()
        val company = companyInput.text?.toString()?.trim()
        val gst = gstInput.text?.toString()?.trim()

        if (name.isEmpty()) {
            nameInput.error = "Name is required"
            return
        }

        viewModel.updateProfile(
            name = name,
            email = email,
            company = company,
            gstNumber = gst
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ProfileUiState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        contentLayout.visibility = View.VISIBLE // Show content while loading
                        saveButton.isEnabled = false
                    }
                    is ProfileUiState.Success -> {
                        progressBar.visibility = View.GONE
                        contentLayout.visibility = View.VISIBLE
                        saveButton.isEnabled = true
                        
                        // Populate fields
                        phoneText.text = formatPhoneNumber(state.profile.phone)
                        nameInput.setText(state.profile.name)
                        emailInput.setText(state.profile.email ?: "")
                        companyInput.setText(state.profile.company ?: "")
                        gstInput.setText(state.profile.gstNumber ?: "")
                    }
                    is ProfileUiState.Error -> {
                        progressBar.visibility = View.GONE
                        contentLayout.visibility = View.VISIBLE
                        saveButton.isEnabled = true
                        showToast(state.message)
                    }
                    is ProfileUiState.Saved -> {
                        progressBar.visibility = View.GONE
                        saveButton.isEnabled = true
                        showToast("Profile saved successfully!")
                    }
                    is ProfileUiState.LoggedOut -> {
                        showToast("Logged out")
                        // Navigate to login screen
                        val intent = Intent(this@ProfileActivity, com.weelo.logistics.presentation.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.length == 10) {
            "+91 ${phone.substring(0, 5)} ${phone.substring(5)}"
        } else if (phone.startsWith("+91")) {
            phone
        } else {
            "+91 $phone"
        }
    }
}
