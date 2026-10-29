package com.example.alertawear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alertawear.data.AlertPreparationGateway
import com.example.alertawear.data.LocalAlertStorage
import com.example.alertawear.data.LocalDemoAlertGateway
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.model.WatchConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MobileScreen(val label: String) {
    HOME("Inicio"),
    CONTACT("Contacto"),
    ALERT_IN_PROGRESS("Alerta"),
    HISTORY("Historial")
}

data class MobileUiState(
    val currentScreen: MobileScreen = MobileScreen.HOME,
    val watchConnectionStatus: WatchConnectionStatus = WatchConnectionStatus.UNVERIFIED,
    val trustedContact: TrustedContact? = null,
    val draftContactName: String = "",
    val draftContactPhone: String = "",
    val contactSavedFeedback: String? = null,
    val countdownSeconds: Int? = null,
    val activeAlertStatus: AlertStatus = AlertStatus.PENDING,
    val activeAlertOrigin: AlertOrigin = AlertOrigin.WATCH_SIMULATED,
    val activeLocationStatusText: String = "Buscando ubicación...",
    val activeDetailText: String = "Envío pendiente de confirmación",
    val historyEvents: List<AlertHistoryEvent> = emptyList()
)

class MobileAlertViewModel(
    private val storage: LocalAlertStorage,
    private val gateway: AlertPreparationGateway = LocalDemoAlertGateway(),
    private val countdownTickMs: Long = 1000L,
    private val locationPrepDelayMs: Long = 1800L
) : ViewModel() {

    private val _uiState = MutableStateFlow(MobileUiState())
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var preparationJob: Job? = null

    init {
        val savedContact = storage.loadContact()
        val savedHistory = storage.loadHistory()
        _uiState.update {
            it.copy(
                trustedContact = savedContact,
                draftContactName = savedContact?.name.orEmpty(),
                draftContactPhone = savedContact?.phone.orEmpty(),
                historyEvents = savedHistory
            )
        }
    }

    fun navigateTo(screen: MobileScreen) {
        _uiState.update {
            it.copy(
                currentScreen = screen,
                contactSavedFeedback = null
            )
        }
    }

    fun onDraftNameChanged(name: String) {
        _uiState.update {
            it.copy(draftContactName = name, contactSavedFeedback = null)
        }
    }

    fun onDraftPhoneChanged(phone: String) {
        _uiState.update {
            it.copy(draftContactPhone = phone, contactSavedFeedback = null)
        }
    }

    fun fillSampleContact() {
        _uiState.update {
            it.copy(
                draftContactName = "María (Contacto de prueba)",
                draftContactPhone = "+52 844 000 0000",
                contactSavedFeedback = "Datos de ejemplo cargados. Presiona Guardar contacto."
            )
        }
    }

    fun saveContact() {
        val current = _uiState.value
        val trimmedName = current.draftContactName.trim()
        val trimmedPhone = current.draftContactPhone.trim()
        if (trimmedName.isBlank() || trimmedPhone.isBlank()) {
            _uiState.update {
                it.copy(contactSavedFeedback = "Ingresa nombre y teléfono para guardar.")
            }
            return
        }
        val isSample = trimmedName.contains("prueba", ignoreCase = true) ||
            trimmedPhone == "+52 844 000 0000"
        val newContact = TrustedContact(
            name = trimmedName,
            phone = trimmedPhone,
            isSimulatedSample = isSample
        )
        storage.saveContact(newContact)
        _uiState.update {
            it.copy(
                trustedContact = newContact,
                draftContactName = newContact.name,
                draftContactPhone = newContact.phone,
                contactSavedFeedback = "Contacto guardado localmente."
            )
        }
    }

    /**
     * Inicia la cuenta regresiva cancelable de 5 segundos al mantener presionado SOS.
     */
    fun startSosCountdown(origin: AlertOrigin = AlertOrigin.PHONE_LOCAL) {
        countdownJob?.cancel()
        preparationJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _uiState.update {
                    it.copy(
                        countdownSeconds = sec,
                        activeAlertOrigin = origin
                    )
                }
                delay(countdownTickMs)
            }
            _uiState.update { it.copy(countdownSeconds = null) }
            triggerAlertPreparation(origin)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        val origin = _uiState.value.activeAlertOrigin
        _uiState.update { it.copy(countdownSeconds = null) }
        recordHistoryEvent(
            title = "Alerta cancelada en cuenta regresiva",
            status = AlertStatus.CANCELLED,
            origin = origin,
            locationNote = "Cancelada antes de preparar ubicación"
        )
    }

    /**
     * Inicia el flujo de "Alerta en proceso" (directamente o tras terminar cuenta regresiva).
     */
    fun triggerAlertPreparation(origin: AlertOrigin = AlertOrigin.WATCH_SIMULATED) {
        countdownJob?.cancel()
        preparationJob?.cancel()

        _uiState.update {
            it.copy(
                currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                countdownSeconds = null,
                activeAlertOrigin = origin,
                activeAlertStatus = AlertStatus.PENDING,
                activeLocationStatusText = "Buscando ubicación... (Simulado)",
                activeDetailText = "Envío pendiente de confirmación"
            )
        }

        preparationJob = viewModelScope.launch {
            delay(locationPrepDelayMs)
            val contact = _uiState.value.trustedContact
            val result = gateway.prepareAlert(contact, origin)
            _uiState.update {
                it.copy(
                    activeAlertStatus = result.status,
                    activeLocationStatusText = result.locationSummary,
                    activeDetailText = result.detailMessage
                )
            }
            recordHistoryEvent(
                title = if (origin == AlertOrigin.WATCH_SIMULATED) {
                    "Solicitud desde reloj (Simulado)"
                } else {
                    "Prueba de alerta local"
                },
                status = result.status,
                origin = origin,
                locationNote = result.locationSummary
            )
        }
    }

    fun cancelActiveAlert() {
        countdownJob?.cancel()
        preparationJob?.cancel()
        val origin = _uiState.value.activeAlertOrigin
        _uiState.update {
            it.copy(
                activeAlertStatus = AlertStatus.CANCELLED,
                activeLocationStatusText = "Preparación detenida por el usuario",
                activeDetailText = "Alerta cancelada. No se realizó ningún envío.",
                currentScreen = MobileScreen.HISTORY
            )
        }
        recordHistoryEvent(
            title = "Alerta cancelada",
            status = AlertStatus.CANCELLED,
            origin = origin,
            locationNote = "Cancelada durante la preparación"
        )
    }

    fun simulateErrorState() {
        countdownJob?.cancel()
        preparationJob?.cancel()
        val origin = _uiState.value.activeAlertOrigin
        _uiState.update {
            it.copy(
                currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                activeAlertStatus = AlertStatus.ERROR,
                activeLocationStatusText = "No se pudo obtener ubicación (Simulado)",
                activeDetailText = "Error al preparar el aviso. Sin envío."
            )
        }
        recordHistoryEvent(
            title = "Fallo de preparación (Simulado)",
            status = AlertStatus.ERROR,
            origin = origin,
            locationNote = "Ubicación no disponible en simulación de error"
        )
    }

    private fun recordHistoryEvent(
        title: String,
        status: AlertStatus,
        origin: AlertOrigin,
        locationNote: String
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timestampLabel = "Hoy · ${timeFormat.format(Date())} (Local)"
        val newEvent = AlertHistoryEvent(
            id = "evt_${System.currentTimeMillis()}",
            title = title,
            timestampLabel = timestampLabel,
            status = status,
            origin = origin,
            locationNote = locationNote,
            isSimulated = true
        )
        _uiState.update { current ->
            val updated = listOf(newEvent) + current.historyEvents
            storage.saveHistory(updated)
            current.copy(historyEvents = updated)
        }
    }

    class Factory(private val storage: LocalAlertStorage) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MobileAlertViewModel(storage = storage) as T
        }
    }
}
