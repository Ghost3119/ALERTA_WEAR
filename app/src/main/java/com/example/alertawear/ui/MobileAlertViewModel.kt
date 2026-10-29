package com.example.alertawear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alertawear.data.AlertEventBus
import com.example.alertawear.data.AlertPreparationGateway
import com.example.alertawear.data.LocalAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.SharedPrefsAlertStorage
import com.example.alertawear.data.WatchConnectionMonitor
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.model.WatchConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MobileScreen(val label: String) {
    HOME("Inicio"),
    CONTACT("Contacto"),
    ALERT_IN_PROGRESS("Alerta"),
    HISTORY("Historial")
}

data class MobileUiState(
    val currentScreen: MobileScreen = MobileScreen.HOME,
    val watchConnection: WatchConnectionState = WatchConnectionState(),
    val hasLocationPermission: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    val trustedContact: TrustedContact? = null,
    val draftContactName: String = "",
    val draftContactPhone: String = "",
    val contactSavedFeedback: String? = null,
    val countdownSeconds: Int? = null,
    val activeRequestId: String? = null,
    val activeAlertStatus: AlertStatus = AlertStatus.PENDING,
    val activeAlertOrigin: AlertOrigin = AlertOrigin.WATCH_DATA_LAYER,
    val activeLocationStatusText: String = "Esperando solicitud SOS...",
    val activeDetailText: String = "Envío pendiente de confirmación",
    val historyEvents: List<AlertHistoryEvent> = emptyList()
)

class MobileAlertViewModel(
    private val storage: LocalAlertStorage,
    private val gateway: AlertPreparationGateway,
    private val watchConnectionMonitor: WatchConnectionMonitor,
    private val locationProvider: PhoneLocationProvider,
    private val countdownTickMs: Long = 1000L
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
                hasLocationPermission = locationProvider.hasForegroundLocationPermission(),
                isLocationServicesEnabled = locationProvider.isLocationEnabled(),
                historyEvents = savedHistory
            )
        }

        refreshDeviceAndLocationStatus()

        // Observar solicitudes reales que llegan desde el reloj vía PhoneWearableListenerService
        viewModelScope.launch {
            AlertEventBus.latestIncomingEvent.collect { incomingEvent ->
                if (incomingEvent != null) {
                    val updatedList = storage.loadHistory()
                    _uiState.update {
                        it.copy(
                            currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                            activeRequestId = incomingEvent.requestId,
                            activeAlertOrigin = incomingEvent.origin,
                            activeAlertStatus = incomingEvent.status,
                            activeLocationStatusText = incomingEvent.locationNote,
                            activeDetailText = incomingEvent.status.displayLabel,
                            historyEvents = updatedList
                        )
                    }
                }
            }
        }
    }

    fun refreshDeviceAndLocationStatus() {
        viewModelScope.launch {
            val connection = watchConnectionMonitor.checkConnectedWatch()
            val hasPerm = locationProvider.hasForegroundLocationPermission()
            val gpsEnabled = locationProvider.isLocationEnabled()
            val latestHistory = storage.loadHistory()
            _uiState.update {
                it.copy(
                    watchConnection = connection,
                    hasLocationPermission = hasPerm,
                    isLocationServicesEnabled = gpsEnabled,
                    historyEvents = latestHistory
                )
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val gpsEnabled = locationProvider.isLocationEnabled()
        _uiState.update {
            it.copy(
                hasLocationPermission = granted || locationProvider.hasForegroundLocationPermission(),
                isLocationServicesEnabled = gpsEnabled
            )
        }
    }

    fun navigateTo(screen: MobileScreen) {
        refreshDeviceAndLocationStatus()
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
        val newContact = TrustedContact(
            name = trimmedName,
            phone = trimmedPhone,
            isSimulatedSample = false
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
     * Inicia la cuenta regresiva cancelable de 5 segundos al mantener presionado SOS en el teléfono.
     */
    fun startSosCountdown(origin: AlertOrigin = AlertOrigin.PHONE_LOCAL) {
        countdownJob?.cancel()
        preparationJob?.cancel()
        val reqId = "phone_${System.currentTimeMillis()}"
        countdownJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _uiState.update {
                    it.copy(
                        countdownSeconds = sec,
                        activeRequestId = reqId,
                        activeAlertOrigin = origin
                    )
                }
                delay(countdownTickMs)
            }
            _uiState.update { it.copy(countdownSeconds = null) }
            triggerAlertPreparation(requestId = reqId, origin = origin)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        val current = _uiState.value
        val reqId = current.activeRequestId ?: "cancel_${System.currentTimeMillis()}"
        _uiState.update { it.copy(countdownSeconds = null) }
        upsertEvent(
            requestId = reqId,
            title = "Alerta cancelada en cuenta regresiva",
            status = AlertStatus.CANCELLED,
            origin = current.activeAlertOrigin,
            locationNote = "Cancelada antes de solicitar ubicación"
        )
    }

    /**
     * Ejecuta la preparación real de la alerta en el teléfono (intentando obtener ubicación real
     * y deduplicando por `requestId`).
     */
    fun triggerAlertPreparation(
        requestId: String = "req_${System.currentTimeMillis()}",
        origin: AlertOrigin = AlertOrigin.PHONE_LOCAL
    ) {
        countdownJob?.cancel()
        preparationJob?.cancel()

        _uiState.update {
            it.copy(
                currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                countdownSeconds = null,
                activeRequestId = requestId,
                activeAlertOrigin = origin,
                activeAlertStatus = AlertStatus.PENDING,
                activeLocationStatusText = "Buscando ubicación...",
                activeDetailText = "Solicitud recibida · Preparando aviso"
            )
        }

        upsertEvent(
            requestId = requestId,
            title = if (origin == AlertOrigin.WATCH_DATA_LAYER) {
                "Solicitud SOS desde reloj"
            } else {
                "Solicitud SOS desde teléfono"
            },
            status = AlertStatus.PENDING,
            origin = origin,
            locationNote = "Buscando ubicación..."
        )

        preparationJob = viewModelScope.launch {
            val contact = _uiState.value.trustedContact
            val result = gateway.prepareAlert(
                contact = contact,
                origin = origin,
                isAppInForeground = true
            )
            _uiState.update {
                it.copy(
                    activeAlertStatus = result.status,
                    activeLocationStatusText = result.locationSummary,
                    activeDetailText = result.detailMessage
                )
            }
            upsertEvent(
                requestId = requestId,
                title = if (origin == AlertOrigin.WATCH_DATA_LAYER) {
                    "Solicitud SOS desde reloj"
                } else {
                    "Solicitud SOS desde teléfono"
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
        val current = _uiState.value
        val reqId = current.activeRequestId ?: "cancel_${System.currentTimeMillis()}"
        _uiState.update {
            it.copy(
                activeAlertStatus = AlertStatus.CANCELLED,
                activeLocationStatusText = "Preparación detenida por el usuario",
                activeDetailText = "Alerta cancelada. No se realizó ningún envío.",
                currentScreen = MobileScreen.HISTORY
            )
        }
        upsertEvent(
            requestId = reqId,
            title = "Alerta cancelada",
            status = AlertStatus.CANCELLED,
            origin = current.activeAlertOrigin,
            locationNote = "Cancelada durante la preparación"
        )
    }

    private fun upsertEvent(
        requestId: String,
        title: String,
        status: AlertStatus,
        origin: AlertOrigin,
        locationNote: String
    ) {
        val event = AlertHistoryEvent(
            id = "evt_$requestId",
            requestId = requestId,
            title = title,
            timestampLabel = SharedPrefsAlertStorage.formatCurrentTimestamp(),
            status = status,
            origin = origin,
            locationNote = locationNote,
            isSimulated = false
        )
        val updated = storage.upsertHistoryEventByRequestId(event)
        _uiState.update { it.copy(historyEvents = updated) }
    }

    class Factory(
        private val storage: LocalAlertStorage,
        private val gateway: AlertPreparationGateway,
        private val watchConnectionMonitor: WatchConnectionMonitor,
        private val locationProvider: PhoneLocationProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MobileAlertViewModel(
                storage = storage,
                gateway = gateway,
                watchConnectionMonitor = watchConnectionMonitor,
                locationProvider = locationProvider
            ) as T
        }
    }
}
