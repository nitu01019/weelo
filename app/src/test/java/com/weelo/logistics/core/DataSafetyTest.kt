package com.weelo.logistics.core

import com.weelo.logistics.domain.model.*
import com.weelo.logistics.presentation.home.HomeUiState
import com.weelo.logistics.presentation.location.LocationInputUiState
import com.weelo.logistics.presentation.trucks.TruckTypesUiState
import com.weelo.logistics.presentation.pricing.PricingUiState
import org.junit.Test
import org.junit.Assert.*

/**
 * Data Safety Tests
 * 
 * These tests verify that UI can render safely with:
 * - Null data
 * - Empty data
 * - Partial data
 * - Zero values
 * 
 * All tests should PASS without crashes or exceptions
 */
class DataSafetyTest {

    // ========================================
    // LocationModel Safety Tests
    // ========================================

    @Test
    fun `LocationModel empty() creates safe default`() {
        val location = LocationModel.empty()
        
        // Should not crash
        assertNotNull(location)
        assertEquals("", location.address)
        assertEquals(0.0, location.latitude, 0.0)
        assertEquals(0.0, location.longitude, 0.0)
        assertFalse(location.isValid())
    }

    @Test
    fun `LocationModel with partial data renders safely`() {
        val location = LocationModel(address = "Test Address")
        
        // Should have safe defaults for missing fields
        assertEquals("Test Address", location.address)
        assertEquals("", location.city)
        assertEquals("", location.state)
        assertEquals(0.0, location.latitude, 0.0)
        assertTrue(location.isValid())
    }

    @Test
    fun `LocationModel toShortString handles empty city`() {
        val location = LocationModel(address = "123 Main St", city = "")
        
        // Should not crash with empty city
        val result = location.toShortString()
        assertEquals("123 Main St", result)
    }

    @Test
    fun `LocationModel toFullString handles partial data`() {
        val location = LocationModel(
            address = "123 Main St",
            city = "Mumbai",
            state = "",  // Missing state
            pincode = ""  // Missing pincode
        )
        
        // Should only include non-empty parts
        val result = location.toFullString()
        assertEquals("123 Main St, Mumbai", result)
    }

    // ========================================
    // VehicleModel Safety Tests
    // ========================================

    @Test
    fun `VehicleModel with minimal data is safe`() {
        val vehicle = VehicleModel(
            id = "truck1",
            name = "Tata Ace",
            category = VehicleCategory.MINI,
            capacityTons = "1.5T"
        )
        
        // Should have safe defaults
        assertNotNull(vehicle)
        assertEquals("", vehicle.description)
        assertEquals(emptyList<String>(), vehicle.features)
        assertEquals(0, vehicle.availableCount)
    }

    @Test
    fun `VehicleModel getDisplayName handles empty name`() {
        val vehicle = VehicleModel(
            id = "truck1",
            name = "",
            category = VehicleCategory.MINI,
            capacityTons = "1.5T"
        )
        
        // Should return fallback
        assertEquals("Unknown Vehicle", vehicle.getDisplayName())
    }

    @Test
    fun `VehicleModel hasFeatures returns false for empty list`() {
        val vehicle = VehicleModel(
            id = "truck1",
            name = "Truck",
            category = VehicleCategory.MINI,
            capacityTons = "1.5T"
        )
        
        assertFalse(vehicle.hasFeatures())
        assertEquals(0, vehicle.getFeaturesCount())
    }

    // ========================================
    // PricingModel Safety Tests
    // ========================================

    @Test
    fun `PricingModel with zero values is safe`() {
        val pricing = PricingModel(0, 0, 0, 0, 0, 0)
        
        // Should not crash
        assertNotNull(pricing)
        assertEquals(0, pricing.totalAmount)
        assertEquals(0, pricing.baseFare)
    }

    @Test
    fun `PricingModel calculate handles zero distance`() {
        val vehicle = VehicleModel(
            id = "truck1",
            name = "Truck",
            category = VehicleCategory.MINI,
            capacityTons = "1.5T",
            basePrice = 1000,
            pricePerKm = 50
        )
        
        // Should not crash with zero distance
        val pricing = PricingModel.calculate(vehicle, 0)
        assertNotNull(pricing)
        assertTrue(pricing.totalAmount >= 0)
    }

    // ========================================
    // BookingModel Safety Tests
    // ========================================

    @Test
    fun `BookingModel without driver is safe`() {
        val booking = BookingModel(
            fromLocation = LocationModel(address = "From"),
            toLocation = LocationModel(address = "To"),
            vehicle = VehicleModel("1", "Truck", VehicleCategory.MINI, "1T"),
            distanceKm = 10,
            pricing = PricingModel(0, 0, 0, 0, 0, 0)
        )
        
        // Should handle null driver fields
        assertFalse(booking.hasDriver())
        assertEquals("Driver not assigned", booking.getDriverDisplayName())
    }

    // ========================================
    // HomeUiState Safety Tests
    // ========================================

    @Test
    fun `HomeUiState empty state is safe`() {
        val state = HomeUiState()
        
        // Should have safe defaults
        assertTrue(state.vehicleCategories.isEmpty())
        assertTrue(state.categoryCounts.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `HomeUiState isEmpty detects empty state correctly`() {
        val state = HomeUiState()
        assertTrue(state.isEmpty())
        
        val loadingState = HomeUiState(isLoading = true)
        assertFalse(loadingState.isEmpty())
        
        val errorState = HomeUiState(errorMessage = "Error")
        assertFalse(errorState.isEmpty())
    }

    @Test
    fun `HomeUiState getCountForCategory returns zero for missing category`() {
        val state = HomeUiState(
            categoryCounts = mapOf(VehicleCategory.OPEN to 5)
        )
        
        // Should return 0 for category not in map
        assertEquals(5, state.getCountForCategory(VehicleCategory.OPEN))
        assertEquals(0, state.getCountForCategory(VehicleCategory.CONTAINER))
    }

    // ========================================
    // LocationInputUiState Safety Tests
    // ========================================

    @Test
    fun `LocationInputUiState empty state is safe`() {
        val state = LocationInputUiState()
        
        assertTrue(state.recentLocations.isEmpty())
        assertNull(state.selectedLocation)
        assertFalse(state.isLoading)
    }

    @Test
    fun `LocationInputUiState isEmpty works correctly`() {
        val state = LocationInputUiState()
        assertTrue(state.isEmpty())
        assertFalse(state.hasRecentLocations())
        assertFalse(state.hasSelection())
    }

    // ========================================
    // TruckTypesUiState Safety Tests
    // ========================================

    @Test
    fun `TruckTypesUiState empty state is safe`() {
        val state = TruckTypesUiState()
        
        assertTrue(state.vehicles.isEmpty())
        assertNull(state.selectedVehicle)
        assertFalse(state.isLoading)
    }

    @Test
    fun `TruckTypesUiState canProceed requires selection`() {
        val emptyState = TruckTypesUiState()
        assertFalse(emptyState.canProceed())
        
        val selectedState = TruckTypesUiState(
            selectedVehicle = VehicleModel("1", "Truck", VehicleCategory.MINI, "1T")
        )
        assertTrue(selectedState.canProceed())
    }

    // ========================================
    // PricingUiState Safety Tests
    // ========================================

    @Test
    fun `PricingUiState empty state is safe`() {
        val state = PricingUiState()
        
        // Should have safe defaults
        assertNotNull(state.fromLocation)
        assertNotNull(state.toLocation)
        assertNull(state.vehicle)
        assertEquals(0, state.distanceKm)
        assertNotNull(state.pricing)
    }

    @Test
    fun `PricingUiState hasCompleteData validates correctly`() {
        val incompleteState = PricingUiState()
        assertFalse(incompleteState.hasCompleteData())
        
        val completeState = PricingUiState(
            fromLocation = LocationModel(address = "From Location"),
            toLocation = LocationModel(address = "To Location"),
            vehicle = VehicleModel("1", "Truck", VehicleCategory.MINI, "1T"),
            distanceKm = 10
        )
        assertTrue(completeState.hasCompleteData())
    }

    @Test
    fun `PricingUiState getFormattedDistance handles zero`() {
        val state = PricingUiState(distanceKm = 0)
        assertEquals("Calculating...", state.getFormattedDistance())
        
        val stateWithDistance = PricingUiState(distanceKm = 25)
        assertEquals("25 km", stateWithDistance.getFormattedDistance())
    }

    @Test
    fun `PricingUiState getVehicleName handles null vehicle`() {
        val state = PricingUiState()
        assertEquals("No vehicle selected", state.getVehicleName())
        
        val stateWithVehicle = PricingUiState(
            vehicle = VehicleModel("1", "Tata Ace", VehicleCategory.MINI, "1T")
        )
        assertEquals("Tata Ace", stateWithVehicle.getVehicleName())
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `All models handle extreme values safely`() {
        // Test with very large numbers
        val pricing = PricingModel.calculate(
            vehicle = VehicleModel(
                id = "1",
                name = "Truck",
                category = VehicleCategory.MINI,
                capacityTons = "1T",
                basePrice = Int.MAX_VALUE / 2,
                pricePerKm = 1000
            ),
            distanceKm = 1000
        )
        
        // Should not crash
        assertNotNull(pricing)
        assertTrue(pricing.totalAmount > 0)
    }

    @Test
    fun `All UiStates can be copied safely`() {
        val homeState = HomeUiState().copy(isLoading = true)
        assertTrue(homeState.isLoading)
        
        val locationState = LocationInputUiState().copy(isLoading = true)
        assertTrue(locationState.isLoading)
        
        val truckState = TruckTypesUiState().copy(isLoading = true)
        assertTrue(truckState.isLoading)
        
        val pricingState = PricingUiState().copy(isLoading = true)
        assertTrue(pricingState.isLoading)
    }

    @Test
    fun `Collections are never null`() {
        val homeState = HomeUiState()
        assertNotNull(homeState.vehicleCategories)
        assertNotNull(homeState.categoryCounts)
        
        val locationState = LocationInputUiState()
        assertNotNull(locationState.recentLocations)
        
        val truckState = TruckTypesUiState()
        assertNotNull(truckState.vehicles)
        
        val vehicle = VehicleModel("1", "Truck", VehicleCategory.MINI, "1T")
        assertNotNull(vehicle.features)
    }
}
