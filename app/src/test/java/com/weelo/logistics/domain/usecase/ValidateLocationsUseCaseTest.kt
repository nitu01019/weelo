package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ValidateLocationsUseCase
 */
class ValidateLocationsUseCaseTest {

    private lateinit var validateLocationsUseCase: ValidateLocationsUseCase

    @Before
    fun setup() {
        validateLocationsUseCase = ValidateLocationsUseCase()
    }

    @Test
    fun `validate locations with valid inputs returns success`() {
        // Given
        val fromLocation = LocationModel(address = "Jammu")
        val toLocation = LocationModel(address = "Delhi")

        // When
        val result = validateLocationsUseCase(fromLocation, toLocation)

        // Then
        assertTrue(result is Result.Success)
        assertEquals(true, (result as Result.Success).data)
    }

    @Test
    fun `validate locations with empty from address returns error`() {
        // Given
        val fromLocation = LocationModel(address = "")
        val toLocation = LocationModel(address = "Delhi")

        // When
        val result = validateLocationsUseCase(fromLocation, toLocation)

        // Then
        assertTrue(result is Result.Error)
    }

    @Test
    fun `validate locations with empty to address returns error`() {
        // Given
        val fromLocation = LocationModel(address = "Jammu")
        val toLocation = LocationModel(address = "")

        // When
        val result = validateLocationsUseCase(fromLocation, toLocation)

        // Then
        assertTrue(result is Result.Error)
    }

    @Test
    fun `validate locations with same addresses returns error`() {
        // Given
        val fromLocation = LocationModel(address = "Jammu")
        val toLocation = LocationModel(address = "Jammu")

        // When
        val result = validateLocationsUseCase(fromLocation, toLocation)

        // Then
        assertTrue(result is Result.Error)
    }

    @Test
    fun `validate locations with too short address returns error`() {
        // Given
        val fromLocation = LocationModel(address = "J")
        val toLocation = LocationModel(address = "Delhi")

        // When
        val result = validateLocationsUseCase(fromLocation, toLocation)

        // Then
        assertTrue(result is Result.Error)
    }
}
