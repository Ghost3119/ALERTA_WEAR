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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                PhoneNodeConnection(isConnected = true, nodeId = "phone_1", nodeName = "Medium Phone")
            } else {
                PhoneNodeConnection(isConnected = false)
            }
        }

        override suspend fun sendSosRequest(requestId: String): Boolean {
            if (!isConnected || !sendSuccess) return false
            sentRequestIds.add(requestId)
            return true
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
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPhoneConnected)
        assertEquals(WearScreen.HOME, viewModel.uiState.value.currentScreen)
        assertEquals("● Sin conexión", viewModel.uiState.value.connectionBadgeText)
    }

    @Test
    fun `countdown when disconnected transitions to NO_CONNECTION and retry reuses same requestId`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = false)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L)

        viewModel.onSosLongPressed()
        advanceUntilIdle()
        assertEquals(WearScreen.NO_CONNECTION, viewModel.uiState.value.currentScreen)
        val initialRequestId = viewModel.uiState.value.activeRequestId

        // Ahora el teléfono se conecta y el usuario presiona Reintentar
        bridge.isConnected = true
        viewModel.retryFromNoConnection()
        advanceUntilIdle()

        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)
        assertEquals(1, bridge.sentRequestIds.size)
        assertEquals(initialRequestId, bridge.sentRequestIds.first())
    }

    @Test
    fun `receiving ACK updates watch screen to reflect phone preparation state`() = runTest {
        val bridge = FakeWearPhoneBridge(isConnected = true)
        val viewModel = WearAlertViewModel(bridge = bridge, countdownTickMs = 100L)

        viewModel.onSosLongPressed()
        advanceUntilIdle()
        assertEquals(WearScreen.IN_PROGRESS, viewModel.uiState.value.currentScreen)
        val reqId = viewModel.uiState.value.activeRequestId!!

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "RECEIVED_PREPARING",
            detail = "Buscando ubicación..."
        )
        advanceUntilIdle()
        assertEquals("Solicitud recibida", viewModel.uiState.value.inProgressTitle)

        bridge.ackFlow.value = PhoneAckPayload(
            requestId = reqId,
            ackStatus = "PREPARED_WITH_LOCATION",
            detail = "Ubicación lista"
        )
        advanceUntilIdle()
        assertEquals("Aviso preparado", viewModel.uiState.value.inProgressTitle)
        assertTrue(viewModel.uiState.value.statusDetailMessage.contains("Sin envío"))
    }
}
