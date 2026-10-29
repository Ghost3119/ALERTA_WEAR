package com.example.alertawear.wear

import com.example.alertawear.wear.data.PhoneAckPayload
import com.example.alertawear.wear.data.PhoneNodeConnection
import com.example.alertawear.wear.data.WearPhoneBridge
import com.example.alertawear.wear.ui.WearAlertViewModel
import com.example.alertawear.wear.ui.WearScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearAlertViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeWearPhoneBridge(
        var isConnected: Boolean = false,
        var sendSuccess: Boolean = true
    ) : WearPhoneBridge {
        val ackFlow = MutableStateFlow<PhoneAckPayload?>(null)
        val sentRequestIds = mutableListOf<String>()

        override val incomingAckFlow: StateFlow<PhoneAckPayload?> = ackFlow

        override suspend fun checkPhoneConnection(): PhoneNodeConnection {
            return if (isConnected) {
                PhoneNodeConnection(isConnected = true, nodeId = "phone_1", nodeName = "Phone Test")
            } else {
                PhoneNodeConnection(isConnected = false)
            }
        }

        override suspend fun sendSosRequest(requestId: String): Boolean {
            if (!isConnected || !sendSuccess) return false
            sentRequestIds.add(requestId)
            return true
        }

        override fun clearLastAck() {
            ackFlow.value = null
        }
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
    fun `initial state shows disconnected when no phone node is paired`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = false)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPhoneConnected)
        assertEquals(WearScreen.HOME, viewModel.uiState.value.currentScreen)
        assertEquals("● Sin conexión", viewModel.uiState.value.connectionBadgeText)
    }

    @Test
    fun `countdown starts and can be cancelled before sending request to phone`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(150L)
        assertEquals(WearScreen.COUNTDOWN, viewModel.uiState.value.currentScreen)

        viewModel.cancelCountdown()
        advanceUntilIdle()

        assertEquals(WearScreen.HOME, viewModel.uiState.value.currentScreen)
        assertNull(viewModel.uiState.value.activeRequestId)
        assertEquals(0, bridge.sentRequestIds.size)
    }

    @Test
    fun `countdown when disconnected transitions to NO_CONNECTION and retry reuses same requestId`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = false)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(WearScreen.NO_CONNECTION, viewModel.uiState.value.currentScreen)
        val initialRequestId = viewModel.uiState.value.activeRequestId

        // Ahora el teléfono se conecta y el usuario presiona Reintentar
        bridge.isConnected = true
        viewModel.retryFromNoConnection()
        runCurrent()

        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(1, bridge.sentRequestIds.size)
        assertEquals(initialRequestId, bridge.sentRequestIds.first())
    }

    @Test
    fun `send failure transitions to NO_CONNECTION and allows retry with same requestId`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true, sendSuccess = false)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(WearScreen.NO_CONNECTION, viewModel.uiState.value.currentScreen)
        assertTrue(viewModel.uiState.value.noConnectionReason.contains("No se pudo enviar"))
        val reqId = viewModel.uiState.value.activeRequestId

        bridge.sendSuccess = true
        viewModel.retryFromNoConnection()
        runCurrent()

        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(listOf(reqId), bridge.sentRequestIds)
    }

    @Test
    fun `receiving ACK updates watch screen through preparation, sending, sent unconfirmed, and delivered states`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)
        val reqId = viewModel.uiState.value.activeRequestId!!

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "RECEIVED_PREPARING",
            detail = "Buscando ubicación..."
        )
        runCurrent()
        assertEquals("Solicitud recibida", viewModel.uiState.value.inProgressTitle)

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "SENDING_SMS_WITH_LOCATION",
            detail = "Enviando SMS con ubicación"
        )
        runCurrent()
        assertEquals("Enviando SMS", viewModel.uiState.value.inProgressTitle)
        assertTrue(viewModel.uiState.value.inProgressSubtitle.contains("Ubicación incluida"))

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "SENDING_SMS_NO_LOCATION",
            detail = "Enviando SMS sin ubicación"
        )
        runCurrent()
        assertEquals("Enviando SMS", viewModel.uiState.value.inProgressTitle)
        assertTrue(viewModel.uiState.value.inProgressSubtitle.contains("Sin ubicación disponible"))

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "SMS_SENT_BY_DEVICE",
            detail = "SMS enviado por el teléfono (entrega sin confirmar)"
        )
        runCurrent()
        assertEquals("SMS enviado", viewModel.uiState.value.inProgressTitle)
        assertTrue(viewModel.uiState.value.inProgressSubtitle.contains("Por el dispositivo"))
        assertTrue(viewModel.uiState.value.statusDetailMessage.contains("sin confirmar"))

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "SMS_DELIVERED",
            detail = "Entrega confirmada"
        )
        runCurrent()
        assertEquals("SMS entregado", viewModel.uiState.value.inProgressTitle)
    }

    @Test
    fun `receiving error ACKs from phone displays clear error states on watch`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 5000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(600L)
        runCurrent()
        val reqId = viewModel.uiState.value.activeRequestId!!

        bridge.ackFlow.value = PhoneAckPayload(reqId, "ERROR_NO_CONTACT", "Sin contacto")
        runCurrent()
        assertEquals("Falta contacto", viewModel.uiState.value.inProgressTitle)

        bridge.ackFlow.value = PhoneAckPayload(reqId, "ERROR_NO_SMS_PERM", "Sin permiso SMS")
        runCurrent()
        assertEquals("Permiso SMS", viewModel.uiState.value.inProgressTitle)

        bridge.ackFlow.value = PhoneAckPayload(reqId, "ERROR_SMS_FAILED", "Fallo red")
        runCurrent()
        assertEquals("Error de envío", viewModel.uiState.value.inProgressTitle)
    }

    @Test
    fun `missing ACK after timeout transitions watch out of preparing to unconfirmed without claiming success or failure`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L, ackTimeoutMs = 2000L)

        viewModel.onSosLongPressed()
        advanceTimeBy(600L)
        runCurrent()
        val reqId = viewModel.uiState.value.activeRequestId!!

        // Llega RECEIVED_PREPARING ("Preparando ubicación...") pero se pierde el resto de respuestas
        bridge.ackFlow.value = PhoneAckPayload(reqId, "RECEIVED_PREPARING", "Preparando...")
        runCurrent()
        assertEquals("Solicitud recibida", viewModel.uiState.value.inProgressTitle)
        assertEquals("Preparando ubicación...", viewModel.uiState.value.statusDetailMessage)

        // Transcurre el tiempo límite sin confirmación final
        advanceTimeBy(2100L)
        runCurrent()

        assertEquals("Sin confirmación", viewModel.uiState.value.inProgressTitle)
        assertEquals("Revisa tu teléfono", viewModel.uiState.value.inProgressSubtitle)
        assertEquals("No se pudo confirmar el resultado", viewModel.uiState.value.statusDetailMessage)
    }
}
