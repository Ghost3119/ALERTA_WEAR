package com.example.alertawear.data

import android.content.Context
import android.content.SharedPreferences
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.LocationOutcomeStatus
import com.example.alertawear.model.LocationResult
import com.example.alertawear.model.TrustedContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Contrato de preparación y envío de una alerta en el teléfono (Fase 3).
 * Obtiene la ubicación real mediante [PhoneLocationProvider], arma el SMS de prueba con o sin
 * enlace a Google Maps e inicia el envío real mediante [PhoneSmsSender].
 */
interface AlertPreparationGateway {
    suspend fun prepareAndSendAlert(
        requestId: String,
        sourceNodeId: String?,
        contact: TrustedContact?,
        origin: AlertOrigin,
        isAppInForeground: Boolean
    ): AlertPreparationResult
}

data class AlertPreparationResult(
    val status: AlertStatus,
    val locationResult: LocationResult,
    val locationSummary: String,
    val smsStatusSummary: String,
    val watchAckStatus: String
)

class RealLocationAlertGateway(
    private val locationProvider: PhoneLocationProvider,
    private val smsSender: PhoneSmsSender
) : AlertPreparationGateway {

    override suspend fun prepareAndSendAlert(
        requestId: String,
        sourceNodeId: String?,
        contact: TrustedContact?,
        origin: AlertOrigin,
        isAppInForeground: Boolean
    ): AlertPreparationResult {
        val locationResult = locationProvider.obtainLocation(isAppInForeground = isAppInForeground)

        if (contact == null || !contact.isValid) {
            return AlertPreparationResult(
                status = AlertStatus.ERROR,
                locationResult = locationResult,
                locationSummary = locationResult.detailText,
                smsStatusSummary = "Error: Configura primero un contacto de confianza válido en el teléfono.",
                watchAckStatus = ACK_ERROR_NO_CONTACT
            )
        }

        val messageBody = smsSender.buildTestAlertMessage(locationResult)
        val dispatchResult = smsSender.sendAlertSms(
            requestId = requestId,
            sourceNodeId = sourceNodeId,
            contact = contact,
            messageBody = messageBody
        )

        return when (dispatchResult.outcome) {
            SmsSubmissionOutcome.DISPATCH_INITIATED -> {
                val ack = if (locationResult.status == LocationOutcomeStatus.ACQUIRED) {
                    ACK_SENDING_SMS_WITH_LOCATION
                } else {
                    ACK_SENDING_SMS_NO_LOCATION
                }
                AlertPreparationResult(
                    status = AlertStatus.SENDING_SMS,
                    locationResult = locationResult,
                    locationSummary = locationResult.detailText,
                    smsStatusSummary = dispatchResult.detailMessage,
                    watchAckStatus = ack
                )
            }

            SmsSubmissionOutcome.ERROR_NO_PERMISSION -> AlertPreparationResult(
                status = AlertStatus.ERROR,
                locationResult = locationResult,
                locationSummary = locationResult.detailText,
                smsStatusSummary = dispatchResult.detailMessage,
                watchAckStatus = ACK_ERROR_NO_SMS_PERM
            )

            SmsSubmissionOutcome.ERROR_NO_SIM_OR_CAPABILITY,
            SmsSubmissionOutcome.ERROR_INVALID_CONTACT,
            SmsSubmissionOutcome.ERROR_EXCEPTION -> AlertPreparationResult(
                status = AlertStatus.ERROR,
                locationResult = locationResult,
                locationSummary = locationResult.detailText,
                smsStatusSummary = dispatchResult.detailMessage,
                watchAckStatus = ACK_ERROR_SMS_FAILED
            )
        }
    }

    companion object {
        const val ACK_RECEIVED_PREPARING = "RECEIVED_PREPARING"
        const val ACK_SENDING_SMS_WITH_LOCATION = "SENDING_SMS_WITH_LOCATION"
        const val ACK_SENDING_SMS_NO_LOCATION = "SENDING_SMS_NO_LOCATION"
        const val ACK_SMS_SENT_BY_DEVICE = "SMS_SENT_BY_DEVICE"
        const val ACK_SMS_DELIVERED = "SMS_DELIVERED"
        const val ACK_ERROR_NO_CONTACT = "ERROR_NO_CONTACT"
        const val ACK_ERROR_NO_SMS_PERM = "ERROR_NO_SMS_PERM"
        const val ACK_ERROR_SMS_FAILED = "ERROR_SMS_FAILED"
    }
}

/**
 * Almacenamiento local para el contacto de confianza y el historial de eventos.
 * Incluye deduplicación por `requestId` y verificación para evitar envíos de SMS duplicados
 * ante reintentos de la misma solicitud.
 */
interface LocalAlertStorage {
    fun loadContact(): TrustedContact?
    fun saveContact(contact: TrustedContact)
    fun loadHistory(): List<AlertHistoryEvent>
    fun saveHistory(events: List<AlertHistoryEvent>)
    fun upsertHistoryEventByRequestId(event: AlertHistoryEvent): List<AlertHistoryEvent>
    fun findEventByRequestId(requestId: String): AlertHistoryEvent?
}

class SharedPrefsAlertStorage(context: Context) : LocalAlertStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadContact(): TrustedContact? {
        val name = prefs.getString(KEY_CONTACT_NAME, null) ?: return null
        val phone = prefs.getString(KEY_CONTACT_PHONE, null) ?: return null
        val isSimulated = prefs.getBoolean(KEY_CONTACT_SIMULATED, false)
        if (name.isBlank() || phone.isBlank()) return null
        return TrustedContact(
            name = name,
            phone = phone,
            isSimulatedSample = isSimulated
        )
    }

    override fun saveContact(contact: TrustedContact) {
        prefs.edit()
            .putString(KEY_CONTACT_NAME, contact.name.trim())
            .putString(KEY_CONTACT_PHONE, contact.phone.trim())
            .putBoolean(KEY_CONTACT_SIMULATED, contact.isSimulatedSample)
            .apply()
    }

    override fun loadHistory(): List<AlertHistoryEvent> {
        val rawJson = prefs.getString(KEY_HISTORY_JSON, null)
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "evt_$i")
                    val reqId = obj.optString("requestId", id)
                    val rawSourceNode = obj.optString("sourceNodeId", "")
                    add(
                        AlertHistoryEvent(
                            id = id,
                            requestId = reqId,
                            sourceNodeId = rawSourceNode.ifBlank { null },
                            title = obj.optString("title", "Solicitud SOS"),
                            timestampLabel = obj.optString("timestampLabel", "Reciente"),
                            status = AlertStatus.fromStoredName(
                                obj.optString("status", AlertStatus.SENT_BY_DEVICE.name)
                            ),
                            origin = runCatching {
                                AlertOrigin.valueOf(obj.optString("origin", AlertOrigin.WATCH_DATA_LAYER.name))
                            }.getOrDefault(AlertOrigin.WATCH_DATA_LAYER),
                            locationNote = obj.optString(
                                "locationNote",
                                "Ubicación no disponible"
                            ),
                            smsStatusNote = obj.optString("smsStatusNote", ""),
                            isSimulated = obj.optBoolean("isSimulated", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveHistory(events: List<AlertHistoryEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject().apply {
                put("id", event.id)
                put("requestId", event.requestId)
                put("sourceNodeId", event.sourceNodeId.orEmpty())
                put("title", event.title)
                put("timestampLabel", event.timestampLabel)
                put("status", event.status.name)
                put("origin", event.origin.name)
                put("locationNote", event.locationNote)
                put("smsStatusNote", event.smsStatusNote)
                put("isSimulated", event.isSimulated)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    @Synchronized
    override fun upsertHistoryEventByRequestId(event: AlertHistoryEvent): List<AlertHistoryEvent> {
        val current = loadHistory().toMutableList()
        val existingIndex = current.indexOfFirst { it.requestId == event.requestId }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = event.copy(
                id = existing.id,
                sourceNodeId = event.sourceNodeId ?: existing.sourceNodeId
            )
        } else {
            current.add(0, event)
        }
        saveHistory(current)
        AlertEventBus.notifyHistoryUpdated(current, event)
        return current
    }

    @Synchronized
    override fun findEventByRequestId(requestId: String): AlertHistoryEvent? {
        return loadHistory().firstOrNull { it.requestId == requestId }
    }

    companion object {
        private const val PREFS_NAME = "alerta_wear_local_prefs"
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_CONTACT_SIMULATED = "contact_simulated"
        private const val KEY_HISTORY_JSON = "history_json"

        fun formatCurrentTimestamp(): String {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return "Hoy · ${timeFormat.format(Date())}"
        }
    }
}

/**
 * Bus en memoria para sincronizar eventos recibidos por [PhoneWearableListenerService] o
 * [SmsStatusBroadcastReceiver] con el [MobileAlertViewModel].
 */
object AlertEventBus {
    private val _latestIncomingEvent = MutableStateFlow<AlertHistoryEvent?>(null)
    val latestIncomingEvent: StateFlow<AlertHistoryEvent?> = _latestIncomingEvent.asStateFlow()

    private val _isUiInForeground = MutableStateFlow(false)
    val isUiInForeground: StateFlow<Boolean> = _isUiInForeground.asStateFlow()

    fun setUiInForeground(inForeground: Boolean) {
        _isUiInForeground.value = inForeground
    }

    fun notifyHistoryUpdated(allEvents: List<AlertHistoryEvent>, latest: AlertHistoryEvent) {
        _latestIncomingEvent.value = latest
    }
}

/**
 * Almacenamiento en memoria para pruebas unitarias y previsualizaciones en Compose.
 */
class InMemoryAlertStorage(
    initialContact: TrustedContact? = null,
    initialHistory: List<AlertHistoryEvent> = emptyList()
) : LocalAlertStorage {
    private var savedContact: TrustedContact? = initialContact
    private var savedHistory: List<AlertHistoryEvent> = initialHistory

    override fun loadContact(): TrustedContact? = savedContact

    override fun saveContact(contact: TrustedContact) {
        savedContact = contact
    }

    override fun loadHistory(): List<AlertHistoryEvent> = savedHistory

    override fun saveHistory(events: List<AlertHistoryEvent>) {
        savedHistory = events
    }

    override fun upsertHistoryEventByRequestId(event: AlertHistoryEvent): List<AlertHistoryEvent> {
        val current = savedHistory.toMutableList()
        val existingIndex = current.indexOfFirst { it.requestId == event.requestId }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = event.copy(
                id = existing.id,
                sourceNodeId = event.sourceNodeId ?: existing.sourceNodeId
            )
        } else {
            current.add(0, event)
        }
        savedHistory = current
        return current
    }

    override fun findEventByRequestId(requestId: String): AlertHistoryEvent? {
        return savedHistory.firstOrNull { it.requestId == requestId }
    }
}
