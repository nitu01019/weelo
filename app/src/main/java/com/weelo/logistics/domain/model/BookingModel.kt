package com.weelo.logistics.domain.model

/**
 * Domain model for Booking
 * 
 * DATA SAFETY:
 * - id defaults to empty string (valid for new bookings)
 * - Driver fields are nullable (driver not assigned yet is valid)
 * - All required fields must be provided by caller
 * - Status defaults to PENDING (safe initial state)
 * - createdAt auto-generates timestamp
 */
data class BookingModel(
    val id: String = "",
    val fromLocation: LocationModel,
    val toLocation: LocationModel,
    val vehicle: VehicleModel,
    val distanceKm: Int,
    val pricing: PricingModel,
    val status: BookingStatus = BookingStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null
) {
    /**
     * Check if driver is assigned
     */
    fun hasDriver(): Boolean = driverId != null
    
    /**
     * Get driver display name safely
     */
    fun getDriverDisplayName(): String = driverName ?: "Driver not assigned"
    
    /**
     * Check if booking is active
     */
    fun isActive(): Boolean = status != BookingStatus.CANCELLED && status != BookingStatus.COMPLETED
}

/**
 * Pricing breakdown model
 */
/**
 * Pricing model for booking calculations
 * 
 * DATA SAFETY:
 * - All fields are non-null integers
 * - Zero values are valid (represents free or not calculated)
 * - UI should check if totalAmount > 0 before displaying
 * - All pricing calculations return safe Int values
 */
data class PricingModel(
    val baseFare: Int,
    val distanceCharge: Int,
    val gstAmount: Int,
    val totalAmount: Int,
    val distanceKm: Int,
    val pricePerKm: Int
) {
    companion object {
        fun calculate(
            vehicle: VehicleModel,
            distanceKm: Int,
            gstRate: Double = 0.18
        ): PricingModel {
            val basePrice = (vehicle.basePrice * vehicle.priceMultiplier).toInt()
            val perKmRate = (vehicle.pricePerKm * vehicle.priceMultiplier).toInt()
            val distanceCharge = distanceKm * perKmRate
            val totalPrice = basePrice + distanceCharge
            val gst = (totalPrice * gstRate).toInt()
            val finalPrice = totalPrice + gst
            
            return PricingModel(
                baseFare = basePrice,
                distanceCharge = distanceCharge,
                gstAmount = gst,
                totalAmount = finalPrice,
                distanceKm = distanceKm,
                pricePerKm = perKmRate
            )
        }
    }
}

/**
 * Booking status enum
 */
enum class BookingStatus(val displayName: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    DRIVER_ASSIGNED("Driver Assigned"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}
