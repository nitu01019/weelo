package com.weelo.logistics.presentation.location

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.LocationModel
import com.weelo.logistics.domain.usecase.AddRecentLocationUseCase
import com.weelo.logistics.domain.usecase.GetRecentLocationsUseCase
import com.weelo.logistics.domain.usecase.ValidateLocationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for LocationInputViewModel
 */
@ExperimentalCoroutinesApi
class LocationInputViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LocationInputViewModel
    private lateinit var getRecentLocationsUseCase: GetRecentLocationsUseCase
    private lateinit var addRecentLocationUseCase: AddRecentLocationUseCase
    private lateinit var validateLocationsUseCase: ValidateLocationsUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        getRecentLocationsUseCase = mockk()
        addRecentLocationUseCase = mockk()
        validateLocationsUseCase = mockk()

        // Mock recent locations flow
        every { getRecentLocationsUseCase() } returns flowOf(
            Result.Success(emptyList())
        )

        viewModel = LocationInputViewModel(
            getRecentLocationsUseCase,
            addRecentLocationUseCase,
            validateLocationsUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onContinueClicked with valid locations triggers navigation`() {
        // Given
        val fromAddress = "Jammu"
        val toAddress = "Delhi"
        
        coEvery { validateLocationsUseCase(any(), any()) } returns Result.Success(true)
        coEvery { addRecentLocationUseCase(any()) } returns Result.Success(Unit)

        // When
        viewModel.onContinueClicked(fromAddress, toAddress)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { validateLocationsUseCase(any(), any()) }
        coVerify(exactly = 2) { addRecentLocationUseCase(any()) }
        
        val navigationEvent = viewModel.navigationEvent.value
        assertTrue(navigationEvent is LocationNavigationEvent.NavigateToMap)
    }

    @Test
    fun `onContinueClicked with invalid locations shows error`() {
        // Given
        val fromAddress = ""
        val toAddress = "Delhi"
        
        coEvery { validateLocationsUseCase(any(), any()) } returns Result.Error(
            Exception("Invalid location")
        )

        // When
        viewModel.onContinueClicked(fromAddress, toAddress)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertNotNull(uiState?.errorMessage)
    }

    @Test
    fun `loadRecentLocations populates ui state`() {
        // Given
        val locations = listOf(
            LocationModel(address = "Jammu"),
            LocationModel(address = "Delhi")
        )
        every { getRecentLocationsUseCase() } returns flowOf(Result.Success(locations))

        // When
        viewModel.loadRecentLocations()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(2, uiState?.recentLocations?.size)
    }
}
