package com.example.alertawear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alertawear.data.AlertEventBus
import com.example.alertawear.data.AlertPreparationGateway
import com.example.alertawear.data.LocalAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.PhoneSmsSender
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
    val hasSendSmsPermission: Boolean = false,
    val canDeviceSendSms: Boolean = false,
    val trustedContact: TrustedContact? = null,
    val draftContactName: String = "",
    val draftContactPhone: String = "",
    val contactSavedFeedback: String? = null,
    val countdownSeconds: Int? = null,
    val activeRequestId: String? = null,
    val activeAlertStatus: AlertStatus = AlertStatus.PREPARING_LOCATION,
    val activeAlertOrigin: AlertOrigin = AlertOrigin.WATCH_DATA_LAYER,
    val activeLocationStatusText: String = "Esperando solicitud SOS...",
    val activeSmsStatusText: String = "Envío pendiente de confirmación",
    val historyEvents: List<AlertHistoryEvent> = emptyList()
)

class MobileAlertViewModel(
    private val storage: LocalAlertStorage,
    private val gateway: AlertPreparationGateway,
    private val watchConnectionMonitor: WatchConnectionMonitor,
    private val locationProvider: PhoneLocationProvider,
    private val smsSender: PhoneSmsSender,
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
                hasSendSmsPermission = smsSender.hasSendSmsPermission(),
                canDeviceSendSms = smsSender.canDeviceSendSms(),
                historyEvents = savedHistory
            )
        }

        refreshDeviceAndLocationStatus()

        // Observar actualizaciones en tiempo real de PhoneWearableListenerService y SmsStatusBroadcastReceiver
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
                            activeSmsStatusText = incomingEvent.smsStatusNote.ifBlank { incomingEvent.status.displayLabel },
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
            val hasLocPerm = locationProvider.hasForegroundLocationPermission()
            val gpsEnabled = locationProvider.isLocationEnabled()
            val hasSmsPerm = smsSender.hasSendSmsPermission()
            val simReady = smsSender.canDeviceSendSms()
            val latestHistory = storage.loadHistory()
            _uiState.update {
                it.copy(
                    watchConnection = connection,
                    hasLocationPermission = hasLocPerm,
                    isLocationServicesEnabled = gpsEnabled,
                    hasSendSmsPermission = hasSmsPerm,
                    canDeviceSendSms = simReady,
                    historyEvents = latestHistory
                )
            }
        }
    }

    fun onPermissionsResult() {
        _uiState.update {
            it.copy(
                hasLocationPermission = locationProvider.hasForegroundLocationPermission(),
                isLocationServicesEnabled = locationProvider.isLocationEnabled(),
                hasSendSmsPermission = smsSender.hasSendSmsPermission(),
                canDeviceSendSms = smsSender.canDeviceSendSms()
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
     * Inicia la cuenta regresiva cancelable de 5 segundos antes de iniciar cualquier envío de SMS.
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
            locationNote = "Cancelada antes de solicitar ubicación",
            smsStatusNote = "No se envió ningún SMS."
        )
    }

    /**
     * Ejecuta la preparación de ubicación y el envío real de SMS de prueba, deduplicando por `requestId`.
     */
    fun triggerAlertPreparation(
        requestId: String = "req_${System.currentTimeMillis()}",
        origin: AlertOrigin = AlertOrigin.PHONE_LOCAL
    ) {
        countdownJob?.cancel()
        preparationJob?.cancel()

        // Evitar duplicados si este requestId ya está en preparación, enviando, enviado o cancelado
        val existing = storage.findEventByRequestId(requestId)
        if (existing != null && existing.status in listOf(
                AlertStatus.RECEIVED,
                AlertStatus.PREPARING_LOCATION,
                AlertStatus.SENDING_SMS,
                AlertStatus.SENT_BY_DEVICE,
                AlertStatus.DELIVERED,
                AlertStatus.CANCELLED
            )
        ) {
            _uiState.update {
                it.copy(
                    currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                    activeRequestId = requestId,
                    activeAlertStatus = existing.status,
                    activeLocationStatusText = existing.locationNote,
                    activeSmsStatusText = existing.smsStatusNote
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentScreen = MobileScreen.ALERT_IN_PROGRESS,
                countdownSeconds = null,
                activeRequestId = requestId,
                activeAlertOrigin = origin,
                activeAlertStatus = AlertStatus.PREPARING_LOCATION,
                activeLocationStatusText = "Buscando ubicación...",
                activeSmsStatusText = "Preparando envío de SMS de prueba..."
            )
        }

        upsertEvent(
            requestId = requestId,
            title = if (origin == AlertOrigin.WATCH_DATA_LAYER) {
                "Solicitud SOS desde reloj"
            } else {
                "Solicitud SOS desde teléfono"
            },
            status = AlertStatus.PREPARING_LOCATION,
            origin = origin,
            locationNote = "Buscando ubicación...",
            smsStatusNote = "Preparando envío de SMS de prueba..."
        )

        preparationJob = viewModelScope.launch {
            val contact = _uiState.value.trustedContact
            val result = gateway.prepareAndSendAlert(
                requestId = requestId,
                sourceNodeId = null,
                contact = contact,
                origin = origin,
                isAppInForeground = true,
                isCancelledCheck = {
                    storage.findEventByRequestId(requestId)?.status == AlertStatus.CANCELLED
                }
            )

            // Respetar si SmsStatusBroadcastReceiver o una cancelación ya actualizaron el estado
            val latest = storage.findEventByRequestId(requestId)
            val hasUpdatedTerminalState = latest != null && latest.status in listOf(
                AlertStatus.SENT_BY_DEVICE,
                AlertStatus.DELIVERED,
                AlertStatus.ERROR,
                AlertStatus.CANCELLED
            )
            val effectiveStatus = if (hasUpdatedTerminalState) {
                latest!!.status
            } else {
                result.status
            }
            val effectiveSmsNote = if (hasUpdatedTerminalState && latest!!.smsStatusNote.isNotBlank()) {
                latest.smsStatusNote
            } else {
                result.smsStatusSummary
            }
            val effectiveLocationNote = if (effectiveStatus == AlertStatus.CANCELLED) {
                "Cancelada durante la preparación"
            } else {
                result.locationSummary
            }

            _uiState.update {
                it.copy(
                    activeAlertStatus = effectiveStatus,
                    activeLocationStatusText = effectiveLocationNote,
                    activeSmsStatusText = effectiveSmsNote
                )
            }
            upsertEvent(
                requestId = requestId,
                title = when {
                    effectiveStatus == AlertStatus.CANCELLED -> "Alerta cancelada"
                    origin == AlertOrigin.WATCH_DATA_LAYER -> "Solicitud SOS desde reloj"
                    else -> "Solicitud SOS desde teléfono"
                },
                status = effectiveStatus,
                origin = origin,
                locationNote = effectiveLocationNote,
                smsStatusNote = effectiveSmsNote
            )
        }
    }

    fun cancelActiveAlert() {
        val current = _uiState.value
        // Solo se puede cancelar mientras la alerta está recibida o buscando ubicación (antes de invocar SmsManager)
        val canStillCancel = current.activeAlertStatus in listOf(
            AlertStatus.RECEIVED,
            AlertStatus.PREPARING_LOCATION
        )
        if (!canStillCancel) {
            _uiState.update { it.copy(currentScreen = MobileScreen.HISTORY) }
            return
        }
        countdownJob?.cancel()
        preparationJob?.cancel()
        val reqId = current.activeRequestId ?: "cancel_${System.currentTimeMillis()}"
        _uiState.update {
            it.copy(
                activeAlertStatus = AlertStatus.CANCELLED,
                activeLocationStatusText = "Preparación detenida por el usuario",
                activeSmsStatusText = "Alerta cancelada antes de enviar SMS.",
                currentScreen = MobileScreen.HISTORY
            )
        }
        upsertEvent(
            requestId = reqId,
            title = "Alerta cancelada",
            status = AlertStatus.CANCELLED,
            origin = current.activeAlertOrigin,
            locationNote = "Cancelada durante la preparación",
            smsStatusNote = "Alerta cancelada antes de enviar SMS."
        )
    }

    private fun upsertEvent(
        requestId: String,
        title: String,
        status: AlertStatus,
        origin: AlertOrigin,
        locationNote: String,
        smsStatusNote: String
    ) {
        val event = AlertHistoryEvent(
            id = "evt_$requestId",
            requestId = requestId,
            title = title,
            timestampLabel = SharedPrefsAlertStorage.formatCurrentTimestamp(),
            status = status,
            origin = origin,
            locationNote = locationNote,
            smsStatusNote = smsStatusNote,
            isSimulated = false
        )
        val updated = storage.upsertHistoryEventByRequestId(event)
        _uiState.update { it.copy(historyEvents = updated) }
    }

    class Factory(
        private val storage: LocalAlertStorage,
        private val gateway: AlertPreparationGateway,
        private val watchConnectionMonitor: WatchConnectionMonitor,
        private val locationProvider: PhoneLocationProvider,
        private val smsSender: PhoneSmsSender
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MobileAlertViewModel(
                storage = storage,
                gateway = gateway,
                watchConnectionMonitor = watchConnectionMonitor,
                locationProvider = locationProvider,
                smsSender = smsSender
            ) as T
        }
    }
}
