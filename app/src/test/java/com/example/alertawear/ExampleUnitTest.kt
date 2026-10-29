package com.example.alertawear

import com.example.alertawear.data.InMemoryAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.PhoneSmsSender
import com.example.alertawear.data.PhoneSosRequestProcessor
import com.example.alertawear.data.RealLocationAlertGateway
import com.example.alertawear.data.SmsDispatchSubmission
import com.example.alertawear.data.SmsSubmissionOutcome
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
import java.util.Locale

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

    private class FakePhoneSmsSender(
        var hasPermission: Boolean = true,
        var canSend: Boolean = true
    ) : PhoneSmsSender {
        data class DispatchedSms(
            val requestId: String,
            val sourceNodeId: String?,
            val contact: TrustedContact,
            val messageBody: String
        )

        val dispatchedMessages = mutableListOf<DispatchedSms>()

        override fun hasSendSmsPermission(): Boolean = hasPermission
        override fun canDeviceSendSms(): Boolean = canSend

        override fun buildTestAlertMessage(locationResult: LocationResult): String {
            return if (locationResult.mapsUrl != null) {
                val lat = String.format(Locale.US, "%.5f", locationResult.latitude)
                val lon = String.format(Locale.US, "%.5f", locationResult.longitude)
                "Alerta de prueba de Alerta Wear. Necesito ayuda.\nMi ubicación: https://maps.google.com/?q=$lat,$lon"
            } else {
                "Alerta de prueba de Alerta Wear. Necesito ayuda.\nUbicación no disponible (${locationResult.status.label})."
            }
        }

        override fun sendAlertSms(
            requestId: String,
            sourceNodeId: String?,
            contact: TrustedContact,
            messageBody: String
        ): SmsDispatchSubmission {
            if (!contact.isValid) {
                return SmsDispatchSubmission(
                    outcome = SmsSubmissionOutcome.ERROR_INVALID_CONTACT,
                    detailMessage = "Error: Contacto de confianza no válido."
                )
            }
            if (!hasPermission) {
                return SmsDispatchSubmission(
                    outcome = SmsSubmissionOutcome.ERROR_NO_PERMISSION,
                    detailMessage = "Error: Permiso SEND_SMS denegado en el teléfono."
                )
            }
            if (!canSend) {
                return SmsDispatchSubmission(
                    outcome = SmsSubmissionOutcome.ERROR_NO_SIM_OR_CAPABILITY,
                    detailMessage = "Error: El teléfono no tiene tarjeta SIM lista o capacidad de telefonía."
                )
            }
            dispatchedMessages.add(DispatchedSms(requestId, sourceNodeId, contact, messageBody))
            return SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.DISPATCH_INITIATED,
                detailMessage = "Enviando SMS de prueba a ${contact.name} (${contact.phone})..."
            )
        }
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
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor(
            WatchConnectionState(isConnected = false, isChecking = false)
        )
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.watchConnection.isConnected)
    }

    @Test
    fun `saveContact persists valid contact and updates state`() = runTest {
        val storage = InMemoryAlertStorage()
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

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
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(
            storage = storage,
            gateway = gateway,
            watchConnectionMonitor = monitor,
            locationProvider = locProvider,
            smsSender = smsSender,
            countdownTickMs = 100L
        )

        viewModel.startSosCountdown(AlertOrigin.PHONE_LOCAL)
        advanceTimeBy(150L)
        assertNotNull(viewModel.uiState.value.countdownSeconds)

        viewModel.cancelCountdown()
        assertNull(viewModel.uiState.value.countdownSeconds)
        assertEquals(AlertStatus.CANCELLED, viewModel.uiState.value.historyEvents.first().status)
        assertEquals(0, smsSender.dispatchedMessages.size)
    }

    @Test
    fun `processor deduplicates repeated requestId on retry and never sends duplicate SMS`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 844 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
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

        // El historial debe tener 1 registro en SENDING_SMS (no marcado aún como enviado sin broadcast)
        // y el SMS solo debe haberse despachado 1 vez
        val history = storage.loadHistory()
        assertEquals(1, history.size)
        assertEquals("sos_100", history.first().requestId)
        assertEquals(AlertStatus.SENDING_SMS, history.first().status)
        assertEquals(1, smsSender.dispatchedMessages.size)
        assertTrue(
            smsSender.dispatchedMessages.first().messageBody.contains(
                "https://maps.google.com/?q=25.42321,-101.00530"
            )
        )
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_RECEIVED_PREPARING))
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_SENDING_SMS_WITH_LOCATION))
    }

    @Test
    fun `location permission denied still sends SMS indicating location unavailable`() = runTest {
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
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

        viewModel.triggerAlertPreparation(requestId = "req_perm_denied", origin = AlertOrigin.WATCH_DATA_LAYER)
        advanceUntilIdle()

        assertEquals(MobileScreen.ALERT_IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(AlertStatus.SENDING_SMS, viewModel.uiState.value.activeAlertStatus)
        assertEquals(1, smsSender.dispatchedMessages.size)
        val body = smsSender.dispatchedMessages.first().messageBody
        assertTrue(body.contains("Alerta de prueba de Alerta Wear"))
        assertTrue(body.contains("Ubicación no disponible"))
    }

    @Test
    fun `missing SEND_SMS permission sets ERROR state and does not dispatch SMS`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 844 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender(hasPermission = false)
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

        viewModel.triggerAlertPreparation(requestId = "req_no_sms_perm", origin = AlertOrigin.WATCH_DATA_LAYER)
        advanceUntilIdle()

        assertEquals(AlertStatus.ERROR, viewModel.uiState.value.activeAlertStatus)
        assertEquals(0, smsSender.dispatchedMessages.size)
        assertTrue(viewModel.uiState.value.activeSmsStatusText.contains("Permiso SEND_SMS denegado"))
    }
}
