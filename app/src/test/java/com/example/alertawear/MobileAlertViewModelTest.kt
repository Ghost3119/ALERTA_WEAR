package com.example.alertawear

import android.app.Activity
import android.telephony.SmsManager
import com.example.alertawear.data.AlertEventBus
import com.example.alertawear.data.AndroidPhoneSmsSender
import com.example.alertawear.data.InMemoryAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.PhoneSmsSender
import com.example.alertawear.data.PhoneSosRequestProcessor
import com.example.alertawear.data.RealLocationAlertGateway
import com.example.alertawear.data.SmsDeliveryResultProcessor
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
import org.junit.Assert.assertNotEquals
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

    /**
     * Doble de prueba para [PhoneSmsSender] que usa la implementación real de
     * [PhoneSmsSender.buildTestAlertMessage] pero intercepta el envío sin usar `SmsManager` real.
     */
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
        AlertEventBus.resetForTesting()
    }

    @After
    fun tearDown() {
        AlertEventBus.resetForTesting()
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

        monitor.state = WatchConnectionState(
            isConnected = true,
            connectedNodeName = "Wear OS XL Round",
            connectedNodeId = "node_watch_1",
            isChecking = false
        )
        viewModel.refreshDeviceAndLocationStatus()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.watchConnection.isConnected)
        assertEquals("Wear OS XL Round", viewModel.uiState.value.watchConnection.connectedNodeName)
    }

    @Test
    fun `saveContact persists valid contact and rejects blank fields`() = runTest {
        val storage = InMemoryAlertStorage()
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

        // Intentar guardar con campos vacíos no debe persistir nada
        viewModel.onDraftNameChanged("   ")
        viewModel.onDraftPhoneChanged("")
        viewModel.saveContact()
        assertNull(storage.loadContact())
        assertEquals("Ingresa nombre y teléfono para guardar.", viewModel.uiState.value.contactSavedFeedback)

        // Guardar un contacto válido debe persistir y limpiar espacios
        viewModel.onDraftNameChanged("  María López  ")
        viewModel.onDraftPhoneChanged("  +52 800 000 0000  ")
        viewModel.saveContact()

        val saved = storage.loadContact()
        assertNotNull(saved)
        assertEquals("María López", saved?.name)
        assertEquals("+52 800 000 0000", saved?.phone)
        assertTrue(saved!!.isValid)
        assertEquals("Contacto guardado localmente.", viewModel.uiState.value.contactSavedFeedback)
    }

    @Test
    fun `countdown starts at 5 seconds and can be cancelled before triggering SMS`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("María", "+52 800 123 4567")
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
        assertEquals("No se envió ningún SMS.", viewModel.uiState.value.historyEvents.first().smsStatusNote)
        assertEquals(0, smsSender.dispatchedMessages.size)
    }

    @Test
    fun `incoming SOS from watch prepares message with location link and sends ACKs`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Contacto Prueba", "+52 800 111 2222")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val sentAcks = mutableListOf<Pair<String, String>>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, reqId, ackStatus, _ ->
                sentAcks.add(reqId to ackStatus)
            }
        )

        val event = processor.processIncomingSos(
            sourceNodeId = "watch_node_1",
            requestId = "sos_watch_01",
            isAppInForeground = true
        )

        assertEquals(AlertStatus.SENDING_SMS, event.status)
        assertEquals(1, smsSender.dispatchedMessages.size)
        val sentMsg = smsSender.dispatchedMessages.first()
        assertEquals(
            "Alerta de prueba de Alerta Wear. Necesito ayuda.\nMi ubicación: https://maps.google.com/?q=25.42321,-101.00530",
            sentMsg.messageBody
        )
        assertEquals(
            listOf(
                "sos_watch_01" to RealLocationAlertGateway.ACK_RECEIVED_PREPARING,
                "sos_watch_01" to RealLocationAlertGateway.ACK_SENDING_SMS_WITH_LOCATION
            ),
            sentAcks
        )
    }

    @Test
    fun `missing contact sets ERROR state, sends ACK_ERROR_NO_CONTACT, and does not send SMS`() = runTest {
        val storage = InMemoryAlertStorage(initialContact = null)
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

        val event = processor.processIncomingSos("watch_1", "sos_no_contact", isAppInForeground = true)
        assertEquals(AlertStatus.ERROR, event.status)
        assertEquals(0, smsSender.dispatchedMessages.size)
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_ERROR_NO_CONTACT))
    }

    @Test
    fun `missing SEND_SMS permission sets ERROR state, sends ACK_ERROR_NO_SMS_PERM, and does not send SMS`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender(hasPermission = false)
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val sentAcks = mutableListOf<String>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, _, ackStatus, _ ->
                sentAcks.add(ackStatus)
            }
        )

        val event = processor.processIncomingSos("watch_1", "sos_no_sms_perm", isAppInForeground = true)
        assertEquals(AlertStatus.ERROR, event.status)
        assertEquals(0, smsSender.dispatchedMessages.size)
        assertTrue(event.smsStatusNote.contains("Permiso SEND_SMS denegado"))
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_ERROR_NO_SMS_PERM))
    }

    @Test
    fun `missing SIM or telephony capability sets ERROR state and sends ACK_ERROR_SMS_FAILED`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender(hasPermission = true, canSend = false)
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val sentAcks = mutableListOf<String>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, _, ackStatus, _ ->
                sentAcks.add(ackStatus)
            }
        )

        val event = processor.processIncomingSos("watch_1", "sos_no_sim", isAppInForeground = true)
        assertEquals(AlertStatus.ERROR, event.status)
        assertEquals(0, smsSender.dispatchedMessages.size)
        assertTrue(sentAcks.contains(RealLocationAlertGateway.ACK_ERROR_SMS_FAILED))
    }

    @Test
    fun `location permission denied or unavailable prepares SMS clearly indicating no location`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
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
        assertEquals(
            "Alerta de prueba de Alerta Wear. Necesito ayuda.\nUbicación no disponible (Permiso de ubicación denegado).",
            body
        )
    }

    @Test
    fun `processor deduplicates repeated requestId on retry and never sends duplicate SMS`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
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

        val history = storage.loadHistory()
        assertEquals(1, history.size)
        assertEquals("sos_100", history.first().requestId)
        assertEquals(AlertStatus.SENDING_SMS, history.first().status)
        assertEquals(1, smsSender.dispatchedMessages.size)
    }

    @Test
    fun `SMS sent broadcast marks SENT_BY_DEVICE and never marks DELIVERED without delivery confirmation`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val watchAcks = mutableListOf<String>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, _, ackStatus, _ ->
                watchAcks.add(ackStatus)
            }
        )

        // 1. Inicia el proceso: queda en SENDING_SMS (no marcado aún como enviado ni entregado)
        val initial = processor.processIncomingSos("node_1", "sos_delivery_test", isAppInForeground = true)
        assertEquals(AlertStatus.SENDING_SMS, initial.status)
        assertNotEquals(AlertStatus.SENT_BY_DEVICE, initial.status)
        assertNotEquals(AlertStatus.DELIVERED, initial.status)

        // 2. Llega confirmación de envío del módem (ACTION_SMS_SENT con RESULT_OK):
        //    Pasa a SENT_BY_DEVICE ("entrega sin confirmar"), NO a DELIVERED.
        val afterSent = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = Activity.RESULT_OK,
            requestId = "sos_delivery_test",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )
        assertNotNull(afterSent)
        assertEquals(AlertStatus.SENT_BY_DEVICE, afterSent?.status)
        assertNotEquals(AlertStatus.DELIVERED, afterSent?.status)
        assertTrue(afterSent!!.smsStatusNote.contains("entrega sin confirmar"))
        assertTrue(watchAcks.contains(RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE))
        assertFalse(watchAcks.contains(RealLocationAlertGateway.ACK_SMS_DELIVERED))

        // 3. Si llega un broadcast de entrega fallido o incompleto, sigue en SENT_BY_DEVICE sin confirmar
        val afterUnconfirmedDelivery = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_DELIVERED,
            resultCode = Activity.RESULT_CANCELED,
            requestId = "sos_delivery_test",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )
        assertEquals(AlertStatus.SENT_BY_DEVICE, afterUnconfirmedDelivery?.status)
        assertFalse(watchAcks.contains(RealLocationAlertGateway.ACK_SMS_DELIVERED))

        // 4. Solo cuando llega ACTION_SMS_DELIVERED con RESULT_OK se marca como DELIVERED
        val afterConfirmedDelivery = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_DELIVERED,
            resultCode = Activity.RESULT_OK,
            requestId = "sos_delivery_test",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )
        assertEquals(AlertStatus.DELIVERED, afterConfirmedDelivery?.status)
        assertTrue(watchAcks.contains(RealLocationAlertGateway.ACK_SMS_DELIVERED))
    }

    @Test
    fun `SMS failure broadcast transitions event to ERROR with network reason and notifies watch`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val watchAcks = mutableListOf<String>()

        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = gateway,
            ackSender = { _, _, ackStatus, _ ->
                watchAcks.add(ackStatus)
            }
        )

        processor.processIncomingSos("node_1", "sos_fail_radio", isAppInForeground = true)

        val afterFailure = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE,
            requestId = "sos_fail_radio",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        assertNotNull(afterFailure)
        assertEquals(AlertStatus.ERROR, afterFailure?.status)
        assertTrue(afterFailure!!.smsStatusNote.contains("Sin servicio móvil / sin señal"))
        assertTrue(watchAcks.contains(RealLocationAlertGateway.ACK_ERROR_SMS_FAILED))
    }

    @Test
    fun `cancelActiveAlert after SMS dispatch started navigates to history without overwriting status to CANCELLED`() = runTest {
        val storage = InMemoryAlertStorage(
            initialContact = TrustedContact("Carlos", "+52 800 999 8888")
        )
        val locProvider = FakeLocationProvider()
        val smsSender = FakePhoneSmsSender()
        val gateway = RealLocationAlertGateway(locProvider, smsSender)
        val monitor = FakeWatchMonitor()
        val viewModel = MobileAlertViewModel(storage, gateway, monitor, locProvider, smsSender)

        viewModel.triggerAlertPreparation(requestId = "req_already_sending", origin = AlertOrigin.WATCH_DATA_LAYER)
        advanceUntilIdle()
        assertEquals(AlertStatus.SENDING_SMS, viewModel.uiState.value.activeAlertStatus)

        // Al pulsar el botón de acción cuando ya está en SENDING_SMS, debe ir a Historial sin marcar CANCELLED
        viewModel.cancelActiveAlert()
        assertEquals(MobileScreen.HISTORY, viewModel.uiState.value.currentScreen)
        assertEquals(AlertStatus.SENDING_SMS, storage.findEventByRequestId("req_already_sending")?.status)
    }
}
