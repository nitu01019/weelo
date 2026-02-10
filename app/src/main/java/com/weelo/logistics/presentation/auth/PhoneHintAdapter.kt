package com.weelo.logistics.presentation.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weelo.logistics.databinding.ItemPhoneHintBinding

/**
 * ============================================================================
 * PHONE HINT ADAPTER
 * ============================================================================
 * 
 * Displays previously used phone numbers in a bottom sheet for quick selection.
 * Uses ListAdapter with DiffUtil for efficient updates.
 * 
 * USAGE:
 * - Shows phone numbers from device or saved preferences
 * - User can tap to auto-fill the number
 * - Formatted display: "XXXXX XXXXX" format
 * 
 * @author Weelo Engineering Team
 * ============================================================================
 */
class PhoneHintAdapter(
    private val onPhoneSelected: (String) -> Unit
) : ListAdapter<String, PhoneHintAdapter.PhoneHintViewHolder>(PhoneDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhoneHintViewHolder {
        val binding = ItemPhoneHintBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhoneHintViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhoneHintViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhoneHintViewHolder(
        private val binding: ItemPhoneHintBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(phone: String) {
            // Format phone as "XXXXX XXXXX"
            val formattedPhone = formatPhoneNumber(phone)
            binding.tvPhoneNumber.text = formattedPhone
            
            binding.root.setOnClickListener {
                onPhoneSelected(phone)
            }
        }

        /**
         * Format 10-digit phone number as "XXXXX XXXXX"
         */
        private fun formatPhoneNumber(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return if (digits.length == 10) {
                "${digits.substring(0, 5)} ${digits.substring(5)}"
            } else {
                phone
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class PhoneDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
