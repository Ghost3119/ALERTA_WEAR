package com.example.alertawear.wear

import com.example.alertawear.wear.ui.WearAlertViewModel
import com.example.alertawear.wear.ui.WearScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearAlertViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state does not claim real phone connection`() {
        val viewModel = WearAlertViewModel(countdownTickMs = 100L)
        assertFalse(viewModel.uiState.value.isRealPhoneConnected)
        assertEquals(WearScreen.HOME, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `long press starts 5 second countdown and cancelling returns to HOME`() = runTest {
        val viewModel = WearAlertViewModel(countdownTickMs = 100L)

        viewModel.onSosLongPressed()
        advanceTimeBy(50L)
        assertEquals(WearScreen.COUNTDOWN, viewModel.uiState.value.currentScreen)
        assertEquals(5, viewModel.uiState.value.countdownSeconds)

        viewModel.cancelCountdown()
        assertEquals(WearScreen.HOME, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `completing countdown transitions to IN_PROGRESS in demo mode or NO_CONNECTION when disabled`() = runTest {
        val viewModel = WearAlertViewModel(countdownTickMs = 100L)

        // Demo mode enabled by default -> transitions to IN_PROGRESS
        viewModel.onSosLongPressed()
        advanceUntilIdle()
        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)

        // Toggle demo connection off -> transitions to NO_CONNECTION after countdown
        viewModel.navigateToScreen(WearScreen.HOME)
        viewModel.toggleDemoConnectionMode()
        viewModel.onSosLongPressed()
        advanceUntilIdle()
        assertEquals(WearScreen.NO_CONNECTION, viewModel.uiState.value.currentScreen)
    }
}
