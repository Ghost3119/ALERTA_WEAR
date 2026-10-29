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
 * Estado del reloj Wear OS.
 * Refleja únicamente la conexión real detectada por Wear OS Data Layer (`NodeClient`) y las
 * confirmaciones reales recibidas desde el teléfono (`MessageClient`), incluyendo un tiempo límite
 * para informar honestamente si no se pudo confirmar el resultado sin afirmar éxito ni fallo del SMS.
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
    private val countdownTickMs: Long = 1000L,
    private val ackTimeoutMs: Long = 12_000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var ackTimeoutJob: Job? = null

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
        ackTimeoutJob?.cancel()
        bridge.clearLastAck()
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
        ackTimeoutJob?.cancel()
        ackTimeoutJob = null
        bridge.clearLastAck()
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
     * Si es un reintento, conserva el mismo `activeRequestId` para evitar registros o SMS duplicados en el teléfono.
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
            ackTimeoutJob?.cancel()
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
            ackTimeoutJob?.cancel()
            _uiState.update {
                it.copy(
                    currentScreen = WearScreen.NO_CONNECTION,
                    isPhoneConnected = false,
                    noConnectionReason = "No se pudo enviar\nla solicitud"
                )
            }
        } else {
            // Mientras llega el ACK del teléfono, iniciar temporizador de falta de confirmación
            _uiState.update {
                if (it.statusDetailMessage == "Enviando solicitud...") {
                    it.copy(statusDetailMessage = "Esperando confirmación...")
                } else {
                    it
                }
            }
            startAckTimeout(requestId)
        }
    }

    /**
     * Evita que el reloj se quede indefinidamente en "Preparando" o "Esperando confirmación" si la
     * respuesta del teléfono se pierde o el proceso en segundo plano es interrumpido.
     * Informa que no se pudo confirmar el resultado sin afirmar que el SMS falló ni que se envió.
     */
    private fun startAckTimeout(requestId: String) {
        ackTimeoutJob?.cancel()
        ackTimeoutJob = viewModelScope.launch {
            delay(ackTimeoutMs)
            val current = _uiState.value
            if (current.activeRequestId == requestId && current.currentScreen == WearScreen.IN_PROGRESS) {
                _uiState.update {
                    it.copy(
                        inProgressTitle = "Sin confirmación",
                        inProgressSubtitle = "Revisa tu teléfono",
                        statusDetailMessage = "No se pudo confirmar el resultado",
                        footerMessage = "Toca para volver"
                    )
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
        ackTimeoutJob?.cancel()
        bridge.clearLastAck()
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.HOME,
                activeRequestId = null
            )
        }
        refreshConnectionStatus()
    }

    private fun onAckReceived(ack: PhoneAckPayload) {
        val currentReqId = _uiState.value.activeRequestId ?: return
        if (ack.requestId != currentReqId) {
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
                        footerMessage = "No cierres la app"
                    )
                }
                startAckTimeout(currentReqId)
            }

            "SENDING_SMS_WITH_LOCATION",
            "PREPARED_WITH_LOCATION" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Enviando SMS",
                        inProgressSubtitle = "Ubicación incluida",
                        statusDetailMessage = "Transmitiendo aviso...",
                        footerMessage = "Espera confirmación"
                    )
                }
                startAckTimeout(currentReqId)
            }

            "SENDING_SMS_NO_LOCATION",
            "PREPARED_NO_LOCATION_PERM",
            "PREPARED_GPS_DISABLED",
            "PREPARED_LOCATION_UNAVAILABLE" -> {
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Enviando SMS",
                        inProgressSubtitle = "Sin ubicación disponible",
                        statusDetailMessage = "Transmitiendo aviso...",
                        footerMessage = "Espera confirmación"
                    )
                }
                startAckTimeout(currentReqId)
            }

            "SMS_SENT_BY_DEVICE" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "SMS enviado",
                        inProgressSubtitle = "Por el dispositivo",
                        statusDetailMessage = "Entrega sin confirmar",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "SMS_DELIVERED" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "SMS entregado",
                        inProgressSubtitle = "Confirmado por red",
                        statusDetailMessage = "Entregado al contacto",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "ALERT_CANCELLED" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Alerta cancelada",
                        inProgressSubtitle = "Detenida en el celular",
                        statusDetailMessage = "No se envió SMS",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "ERROR_NO_CONTACT" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Falta contacto",
                        inProgressSubtitle = "Configúralo en el celular",
                        statusDetailMessage = "No se envió SMS",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "ERROR_NO_SMS_PERM" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Permiso SMS",
                        inProgressSubtitle = "Denegado en el celular",
                        statusDetailMessage = "Autoriza SEND_SMS",
                        footerMessage = "Toca para volver"
                    )
                }
            }

            "ERROR_SMS_FAILED" -> {
                ackTimeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentScreen = WearScreen.IN_PROGRESS,
                        inProgressTitle = "Error de envío",
                        inProgressSubtitle = "Revisa SIM o señal",
                        statusDetailMessage = "Fallo al enviar SMS",
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
