package com.weelo.logistics.presentation.location

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.weelo.logistics.R
import com.weelo.logistics.domain.model.LocationModel
import timber.log.Timber
import java.util.Locale

/**
 * =============================================================================
 * LOCATION VIEW FACTORY - Programmatic View Creation for Location Items
 * =============================================================================
 * 
 * SCALABILITY:
 * - Lightweight view creation (no XML inflation overhead)
 * - Reusable across different screens
 * - Memory efficient - views created on demand
 * - Distance calculation only when user location available
 * 
 * EASY UNDERSTANDING:
 * - Single responsibility: Creates location item views
 * - Clear method names and parameters
 * - Comprehensive documentation
 * 
 * MODULARITY:
 * - Separate factory class, not embedded in Activity
 * - Can be injected via Hilt if needed
 * - Easy to unit test
 * - Configurable styling via ViewConfig
 * 
 * CODING STANDARDS:
 * - Follows Android best practices
 * - Uses Timber for logging
 * - Consistent naming conventions
 * - Proper null safety
 * 
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
class LocationViewFactory(private val context: Context) {

    // ==========================================================================
    // CONFIGURATION
    // ==========================================================================
    
    /**
     * View configuration for styling
     * MODULARITY: Centralized styling configuration
     */
    data class ViewConfig(
        val showDistance: Boolean = false,
        val showFavoriteIcon: Boolean = true,
        val iconResId: Int = R.drawable.ic_clock_recent,
        val iconTint: Int = Color.parseColor("#888888"),
        val nameTextColor: Int = Color.parseColor("#1A1A1A"),
        val addressTextColor: Int = Color.parseColor("#666666"),
        val distanceTextColor: Int = Color.parseColor("#888888"),
        val dividerColor: Int = Color.parseColor("#EEEEEE")
    )
    
    companion object {
        /**
         * Config for recent/cached locations
         * RAPIDO STYLE: Shows distance below icon, darker text, remove button
         */
        val RECENT_LOCATION_CONFIG = ViewConfig(
            showDistance = true,  // RAPIDO STYLE: Distance below icon
            showFavoriteIcon = true,
            iconResId = R.drawable.ic_clock_recent,
            iconTint = Color.parseColor("#666666"),  // Darker icon
            nameTextColor = Color.parseColor("#1A1A1A"),  // Dark text for readability
            addressTextColor = Color.parseColor("#555555"),  // Slightly darker address
            distanceTextColor = Color.parseColor("#888888")
        )
        
        /**
         * Config for search results from Google Places
         */
        val SEARCH_RESULT_CONFIG = ViewConfig(
            showDistance = true,
            showFavoriteIcon = false,
            iconResId = R.drawable.ic_location,
            iconTint = Color.parseColor("#666666"),
            nameTextColor = Color.parseColor("#1A1A1A"),
            addressTextColor = Color.parseColor("#555555"),
            distanceTextColor = Color.parseColor("#888888")
        )
    }
    
    // Density for dp to px conversion
    private val density = context.resources.displayMetrics.density
    
    // User location for distance calculation
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null
    
    // ==========================================================================
    // PUBLIC METHODS
    // ==========================================================================
    
    /**
     * Update user location for distance calculations
     * SCALABILITY: Called once, used for all items
     */
    fun updateUserLocation(lat: Double, lng: Double) {
        userLatitude = lat
        userLongitude = lng
    }
    
    /**
     * Create a location item view with click handling
     * 
     * SCALABILITY: Programmatic creation avoids XML inflation overhead
     * EASY UNDERSTANDING: Single method for view creation
     * 
     * @param location The location data to display
     * @param config View configuration for styling
     * @param onClick Callback when the view is clicked
     * @param onFavoriteClick Callback when favorite heart is clicked
     * @param onRemoveClick Callback when remove (X) is clicked
     * @return The created view
     */
    fun createLocationView(
        location: LocationModel,
        config: ViewConfig = RECENT_LOCATION_CONFIG,
        onClick: (LocationModel) -> Unit,
        onFavoriteClick: ((LocationModel) -> Unit)? = null,
        onRemoveClick: ((LocationModel) -> Unit)? = null
    ): View {
        return LinearLayout(context).apply {
            // Container setup
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            
            // Add child views
            addView(createIconSection(location, config))
            addView(createTextSection(location, config))
            
            // Add favorite icon with click handler
            if (config.showFavoriteIcon) {
                addView(createFavoriteIcon(location, onFavoriteClick))
            }
            
            // Add remove button if callback provided
            if (onRemoveClick != null) {
                addView(createRemoveButton(location, onRemoveClick))
            }
            
            // CRITICAL: Click listener on container
            setOnClickListener {
                Timber.d("Location clicked: ${location.address}")
                onClick(location)
            }
        }
    }
    
    /**
     * Create a divider view
     * MODULARITY: Separate method for divider creation
     */
    fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                marginStart = dp(54) // Align with text
            }
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }
    }
    
    // ==========================================================================
    // PRIVATE HELPER METHODS
    // ==========================================================================
    
    /**
     * Create the left icon section (icon + distance below - RAPIDO STYLE)
     * 
     * RAPIDO STYLE: Distance is ALWAYS below the icon, not to the right
     * SCALABILITY: Distance calculated locally, no API calls
     * EASY UNDERSTANDING: Vertical layout - icon on top, distance below
     */
    private fun createIconSection(location: LocationModel, config: ViewConfig): View {
        return LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = dp(44)  // Slightly wider for distance text
            
            // Icon (Clock for recent, Pin for search)
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setImageResource(config.iconResId)
                setColorFilter(config.iconTint)
            })
            
            // Distance BELOW icon (RAPIDO STYLE - always show if available)
            if (config.showDistance) {
                val distanceText = calculateDistance(location)
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(3)
                    }
                    text = if (distanceText.isNotEmpty()) distanceText else ""
                    textSize = 10f
                    setTextColor(config.distanceTextColor)
                    gravity = Gravity.CENTER
                    // Hide if no distance available (no user location)
                    visibility = if (distanceText.isNotEmpty()) View.VISIBLE else View.GONE
                })
            }
        }
    }
    
    /**
     * Create the middle text section (name + address)
     */
    private fun createTextSection(location: LocationModel, config: ViewConfig): View {
        // Split address into name and subtitle
        val parts = location.address.split(",", limit = 2)
        val name = parts.getOrNull(0)?.trim() ?: location.address
        val address = parts.getOrNull(1)?.trim() ?: ""
        
        return LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(10)
            }
            orientation = LinearLayout.VERTICAL
            
            // Name (bold)
            addView(TextView(context).apply {
                text = name
                setTextColor(config.nameTextColor)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            
            // Address (gray)
            if (address.isNotEmpty()) {
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(3)
                    }
                    text = address
                    setTextColor(config.addressTextColor)
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
    }
    
    /**
     * Create the right favorite icon (heart) with click handler
     * 
     * RAPIDO STYLE:
     * - Filled red heart (❤️) when favorited
     * - Outline heart (🤍) when not favorited
     * - Click fills heart with red and moves location to top priority
     * 
     * MODULARITY: Separate view creation with callback
     * EASY UNDERSTANDING: Clear visual feedback for favorite state
     */
    private fun createFavoriteIcon(
        location: LocationModel, 
        onFavoriteClick: ((LocationModel) -> Unit)?
    ): View {
        return TextView(context).apply {
            // Red filled heart for favorites, outline for non-favorites
            text = if (location.isFavorite) {
                context.getString(R.string.favorite_icon_filled)
            } else {
                context.getString(R.string.favorite_icon_empty)
            }
            textSize = 20f
            setPadding(dp(10), dp(10), dp(6), dp(10))
            
            // Make clickable if callback provided
            if (onFavoriteClick != null) {
                isClickable = true
                isFocusable = true
                // Add ripple effect for touch feedback
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    Timber.d("Favorite clicked: ${location.address}, current=${location.isFavorite}")
                    onFavoriteClick(location)
                }
            }
        }
    }
    
    /**
     * Create remove (X) button with circular background for cached locations
     * 
     * RAPIDO STYLE: X button inside a circle for clear visual affordance
     * MODULARITY: Separate view creation with callback
     * EASY UNDERSTANDING: Clear delete action with visual feedback
     */
    private fun createRemoveButton(
        location: LocationModel,
        onRemoveClick: (LocationModel) -> Unit
    ): View {
        return TextView(context).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            
            // Set fixed size for circular button
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginStart = dp(4)
            }
            
            // Circular background (RAPIDO STYLE)
            background = createCircleDrawable()
            
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Timber.d("Remove clicked: ${location.address}")
                onRemoveClick(location)
            }
        }
    }
    
    /**
     * Create circular drawable for remove button background
     * 
     * RAPIDO STYLE: Light gray circle with subtle border
     * MODULARITY: Reusable drawable creation
     */
    private fun createCircleDrawable(): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F5F5F5"))  // Light gray fill
            setStroke(dp(1), Color.parseColor("#E0E0E0"))  // Subtle border
        }
    }
    
    /**
     * Calculate distance from user location
     * SCALABILITY: Native Android calculation, no API calls
     */
    private fun calculateDistance(location: LocationModel): String {
        val lat = userLatitude ?: return ""
        val lng = userLongitude ?: return ""
        
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, location.latitude, location.longitude, results)
        val distanceMeters = results[0]
        
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000)
        }
    }
    
    /**
     * Convert dp to pixels
     * EASY UNDERSTANDING: Simple utility function
     */
    private fun dp(value: Int): Int = (value * density).toInt()
}
