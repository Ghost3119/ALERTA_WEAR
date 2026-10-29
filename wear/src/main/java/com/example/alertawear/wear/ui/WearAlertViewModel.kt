package com.example.alertawear.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alertawear.wear.data.PhoneAckPayload
import com.example.alertawear.wear.data.WearPhoneBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WearScreen {
    HOME,
    COUNTDOWN,
    IN_PROGRESS,
    NO_CONNECTION
}

/**
 * Estado del reloj Wear OS en la Fase 2.
 * Refleja únicamente la conexión real detectada por Wear OS Data Layer (`NodeClient`) y las
 * confirmaciones reales recibidas desde el teléfono (`MessageClient`).
 */
data class WearUiState(
    val currentScreen: WearScreen = WearScreen.HOME,
    val countdownSeconds: Int = 5,
    val isCheckingConnection: Boolean = true,
    val isPhoneConnected: Boolean = false,
    val connectedPhoneName: String? = null,
    val activeRequestId: String? = null,
    val inProgressTitle: String = "Aviso en proceso",
    val inProgressSubtitle: String = "Revisa el celular",
    val statusDetailMessage: String = "Enviando solicitud...",
    val footerMessage: String = "No cierres la app",
    val noConnectionReason: String = "Abre la aplicación\nen tu teléfono"
) {
    val connectionBadgeText: String
        get() = when {
            isCheckingConnection -> "● Verificando..."
            isPhoneConnected -> "● Conectado"
            else -> "● Sin conexión"
        }
}

class WearAlertViewModel(
    private val bridge: WearPhoneBridge,
    private val countdownTickMs: Long = 1000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        refreshConnectionStatus()

        viewModelScope.launch {
            bridge.incomingAckFlow.collect { ack ->
                if (ack != null) {
                    onAckReceived(ack)
                }
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingConnection = true) }
            val connection = bridge.checkPhoneConnection()
            _uiState.update {
                it.copy(
                    isCheckingConnection = false,
                    isPhoneConnected = connection.isConnected,
                    connectedPhoneName = connection.nodeName
                )
            }
        }
    }

    fun onSosLongPressed() {
        countdownJob?.cancel()
        val newRequestId = "sos_${System.currentTimeMillis()}"
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.COUNTDOWN,
                countdownSeconds = 5,
                activeRequestId = newRequestId
            )
        }
        countdownJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _uiState.update { it.copy(countdownSeconds = sec) }
                delay(countdownTickMs)
            }
            transmitSosRequest(newRequestId)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.HOME,
                countdownSeconds = 5,
                activeRequestId = null
            )
        }
        refreshConnectionStatus()
    }

    /**
     * Envía la solicitud SOS real al teléfono al finalizar la cuenta regresiva o al pulsar Reintentar.
     * Si es un reintento, conserva el mismo `activeRequestId` para evitar registros duplicados en el teléfono.
     */
    private suspend fun transmitSosRequest(requestId: String) {
        val connection = bridge.checkPhoneConnection()
        _uiState.update {
            it.copy(
                isCheckingConnection = false,
                isPhoneConnected = connection.isConnected,
                connectedPhoneName = connection.nodeName
            )
        }

        if (!connection.isConnected) {
            _uiState.update {
                it.copy(
                    currentScreen = WearScreen.NO_CONNECTION,
                    noConnectionReason = "Abre la aplicación\nen tu teléfono"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentScreen = WearScreen.IN_PROGRESS,
                inProgressTitle = "Aviso en proceso",
                inProgressSubtitle = "Revisa el celular",
                statusDetailMessage = "Enviando solicitud...",
                footerMessage = "No cierres la app"
            )
        }

        val sent = bridge.sendSosRequest(requestId)
        if (!sent) {
            _uiState.update {
                it.copy(
                    currentScreen = WearScreen.NO_CONNECTION,
                    isPhoneConnected = false,
                    noConnectionReason = "No se pudo enviar\nla solicitud"
                )
            }
        } else {
            // Mientras llega el ACK del teléfono
            _uiState.update {
                if (it.statusDetailMessage == "Enviando solicitud...") {
                    it.copy(statusDetailMessage = "Esperando confirmación...")
                } else {
                    it
                }
            }
        }
    }

    fun retryFromNoConnection() {
        val reqId = _uiState.value.activeRequestId ?: "sos_${System.currentTimeMillis()}"
        _uiState.update { it.copy(activeRequestId = reqId) }
        viewModelScope.launch {
            transmitSosRequest(reqId)
        }
    }

    fun returnToHome() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.HOME,
                activeRequestId = null
            )
        }
        refreshConnectionStatus()
    }

    private fun onAckReceived(ack: PhoneAckPayload) {
        val currentReqId = _uiState.value.activeRequestId
        if (currentReqId != null && ack.requestId.isNotBlank() && ack.requestId != currentReqId) {
            return
        }

        when (ack.ackStatus) {
            "RECEIVED_PREPARING" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Solicitud recibida",
                        inProgressSubtitle = "Revisa el celular",
                        statusDetailMessage = "Preparando ubicación...",
                        footerMessage = "Sin envío a contacto"
                    )
                }
            }

            "PREPARED_WITH_LOCATION" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Aviso preparado",
                        inProgressSubtitle = "Ubicación lista en celular",
                        statusDetailMessage = "Preparado (Sin envío)",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "PREPARED_NO_LOCATION_PERM",
            "PREPARED_GPS_DISABLED",
            "PREPARED_LOCATION_UNAVAILABLE" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Aviso preparado",
                        inProgressSubtitle = "Revisa el celular",
                        statusDetailMessage = "Sin ubicación disponible",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "ERROR_NO_CONTACT" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Falta contacto",
                        inProgressSubtitle = "Configúralo en el celular",
                        statusDetailMessage = "Solicitud recibida",
                        footerMessage = "Toca para volver"
                    )
                }
            }
        }
    }

    class Factory(private val bridge: WearPhoneBridge) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WearAlertViewModel(bridge = bridge) as T
        }
    }
}
