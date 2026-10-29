package com.example.alertawear

import com.example.alertawear.data.InMemoryAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.PhoneSosRequestProcessor
import com.example.alertawear.data.RealLocationAlertGateway
import com.example.alertawear.data.WatchConnectionMonitor
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.LocationOutcomeStatus
import com.example.alertawear.model.LocationResult
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.model.WatchConnectionState
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

    private class FakeLocationProvider(
        var hasPermission: Boolean = true,
        var isEnabled: Boolean = true,
        var result: LocationResult = LocationResult(
            status = LocationOutcomeStatus.ACQUIRED,
            latitude = 25.42321,
            longitude = -101.0053,
            detailText = "Ubicación preparada: 25.42321, -101.00530"
        )
    ) : PhoneLocationProvider {
        override fun hasForegroundLocationPermission(): Boolean = hasPermission
        override fun isLocationEnabled(): Boolean = isEnabled
        override suspend fun obtainLocation(isAppInForeground: Boolean): LocationResult = result
    }

    private class FakeWatchMonitor(
        var state: WatchConnectionState = WatchConnectionState(
            isConnected = false,
            isChecking = false
        )
    ) : WatchConnectionMonitor {
        override suspend fun checkConnectedWatch(): WatchConnectionState = state
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial watch connection reflects real monitor state`() = runTest {
        val storage = InMemoryAlertStorage()
        val locProvider = FakeLocationProvider()
        val gateway = RealLocationAlertGateway(locProvider)
        val monitor = FakeWatchMonitor(
            WatchConnectionState(isConnected = false, isChecking = false)
        )
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.watchConnection.isConnected)
    }

    @Test
    fun `saveContact persists valid contact and updates state`() = runTest {
        val storage = InMemoryAlertStorage()
        val locProvider = FakeLocationProvider()
        val gateway = RealLocationAlertGateway(locProvider)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider)

        viewModel.onDraftNameChanged("María")
        viewModel.onDraftPhoneChanged("+52 844 000 0000")
        viewModel.saveContact()

        val saved = storage.loadContact()
        assertNotNull(saved)
        assertEquals("María", saved?.name)
        assertEquals("+52 844 000 0000", saved?.phone)
    }

    @Test
    fun `countdown can be cancelled before triggering alert preparation`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("María", "+52 844 123 4567")
        )
        val locProvider = FakeLocationProvider()
        val gateway = RealLocationAlertGateway(locProvider)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(
            storage = storage,
            gateway = gateway,
            watchConnectionMonitor = monitor,
            locationProvider = locProvider,
            countdownTickMs = 100L
        )

        viewModel.startSosCountdown(AlertOrigin.PHONE_LOCAL)
        advanceTimeBy(150L)
        assertNotNull(viewModel.uiState.value.countdownSeconds)

        viewModel.cancelCountdown()
        assertNull(viewModel.uiState.value.countdownSeconds)
        assertEquals(AlertStatus.CANCELLED, viewModel.uiState.value.historyEvents.first().status)
    }

    @Test
    fun `processor deduplicates repeated requestId on retry and sends ACKs`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 844 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val gateway = RealLocationAlertGateway(locProvider)
        val sentAcks = mutableListOf<String>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, _, ackStatus, _ ->
                sentAcks.add(ackStatus)
            }
        )

        // Primera solicitud con requestId "sos_100"
        processor.processIncomingSos("node_1", "sos_100", isAppInForeground = true)
        // Reintento con el mismo requestId "sos_100"
        processor.processIncomingSos("node_1", "sos_100", isAppInForeground = true)

        // El historial debe tener exactamente 1 registro para "sos_100", no duplicados
        val history = storage.loadHistory()
        assertEquals(1, history.size)
        assertEquals("sos_100", history.first().requestId)
        assertEquals(AlertStatus.PREPARED, history.first().status)
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_RECEIVED_PREPARING))
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_PREPARED_WITH_LOCATION))
    }

    @Test
    fun `permission denied still prepares alert with clear no-location note`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 844 999 8888")
        )
        val locProvider = FakeLocationProvider(
            hasPermission = false,
            result = LocationResult(
                status = LocationOutcomeStatus.PERMISSION_DENIED,
                detailText = "Permiso de ubicación denegado en el teléfono."
            )
        )
        val gateway = RealLocationAlertGateway(locProvider)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider)

        viewModel.triggerAlertPreparation(requestId = "req_perm_denied", origin = AlertOrigin.WATCH_DATA_LAYER)
        advanceUntilIdle()

        assertEquals(MobileScreen.ALERT_IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(AlertStatus.PREPARED, viewModel.uiState.value.activeAlertStatus)
        assertTrue(viewModel.uiState.value.activeLocationStatusText.contains("Permiso de ubicación denegado"))
    }
}
