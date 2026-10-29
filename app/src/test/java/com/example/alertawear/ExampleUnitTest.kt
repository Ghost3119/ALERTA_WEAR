package com.example.alertawear

import com.example.alertawear.data.InMemoryAlertStorage
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.model.WatchConnectionStatus
import com.example.alertawear.ui.MobileAlertViewModel
import com.example.alertawear.ui.MobileScreen
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileAlertViewModelTest {

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
    fun `initial watch connection status is unverified and not connected`() {
        val storage = InMemoryAlertStorage()
        val viewModel = MobileAlertViewModel(storage = storage)

        val state = viewModel.uiState.value
        assertEquals(WatchConnectionStatus.UNVERIFIED, state.watchConnectionStatus)
        assertFalse(state.watchConnectionStatus.isConnected)
    }

    @Test
    fun `saveContact persists valid contact and updates state`() {
        val storage = InMemoryAlertStorage()
        val viewModel = MobileAlertViewModel(storage = storage)

        viewModel.onDraftNameChanged("María")
        viewModel.onDraftPhoneChanged("+52 844 000 0000")
        viewModel.saveContact()

        val saved = storage.loadContact()
        assertNotNull(saved)
        assertEquals("María", saved?.name)
        assertEquals("+52 844 000 0000", saved?.phone)
        assertTrue(saved?.isSimulatedSample == true)
    }

    @Test
    fun `countdown can be cancelled before triggering alert preparation`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("María", "+52 844 123 4567", isSimulatedSample = false)
        )
        val viewModel = MobileAlertViewModel(
            storage = storage,
            countdownTickMs = 100L,
            locationPrepDelayMs = 100L
        )

        viewModel.startSosCountdown(AlertOrigin.PHONE_LOCAL)
        advanceTimeBy(150L)
        assertNotNull(viewModel.uiState.value.countdownSeconds)

        viewModel.cancelCountdown()
        assertNull(viewModel.uiState.value.countdownSeconds)
        assertEquals(AlertStatus.CANCELLED, viewModel.uiState.value.historyEvents.first().status)
    }

    @Test
    fun `triggerAlertPreparation transitions from PENDING to PREPARED when contact exists`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 844 999 8888", isSimulatedSample = false)
        )
        val viewModel = MobileAlertViewModel(
            storage = storage,
            countdownTickMs = 100L,
            locationPrepDelayMs = 200L
        )

        viewModel.triggerAlertPreparation(AlertOrigin.WATCH_SIMULATED)
        assertEquals(MobileScreen.ALERT_IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(AlertStatus.PENDING, viewModel.uiState.value.activeAlertStatus)

        advanceUntilIdle()
        assertEquals(AlertStatus.PREPARED, viewModel.uiState.value.activeAlertStatus)
        assertEquals(AlertStatus.PREPARED, viewModel.uiState.value.historyEvents.first().status)
    }

    @Test
    fun `triggerAlertPreparation results in ERROR when no contact is configured`() = runTest {
        val storage = InMemoryAlertStorage(initialContact = null)
        val viewModel = MobileAlertViewModel(
            storage = storage,
            countdownTickMs = 100L,
            locationPrepDelayMs = 200L
        )

        viewModel.triggerAlertPreparation(AlertOrigin.WATCH_SIMULATED)
        advanceUntilIdle()

        assertEquals(AlertStatus.ERROR, viewModel.uiState.value.activeAlertStatus)
        assertEquals(AlertStatus.ERROR, viewModel.uiState.value.historyEvents.first().status)
    }
}
