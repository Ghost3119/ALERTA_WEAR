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
 * Contrato de preparación de una alerta en el teléfono (Fase 2).
 * Obtiene la ubicación real mediante [PhoneLocationProvider] y verifica el contacto de confianza.
 * Diseñado para que en la Fase 3 pueda conectarse el método de envío externo sin afirmar
 * en esta fase que se notificó a un contacto.
 */
interface AlertPreparationGateway {
    suspend fun prepareAlert(
        contact: TrustedContact?,
        origin: AlertOrigin,
        isAppInForeground: Boolean
    ): AlertPreparationResult
}

data class AlertPreparationResult(
    val status: AlertStatus,
    val locationResult: LocationResult,
    val locationSummary: String,
    val detailMessage: String,
    val watchAckStatus: String
)

class RealLocationAlertGateway(
    private val locationProvider: PhoneLocationProvider
) : AlertPreparationGateway {

    override suspend fun prepareAlert(
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
                detailMessage = "Solicitud recibida, pero falta configurar un contacto de confianza en el teléfono.",
                watchAckStatus = ACK_ERROR_NO_CONTACT
            )
        }

        val ackCode = when (locationResult.status) {
            LocationOutcomeStatus.ACQUIRED -> ACK_PREPARED_WITH_LOCATION
            LocationOutcomeStatus.PERMISSION_DENIED,
            LocationOutcomeStatus.BACKGROUND_RESTRICTED -> ACK_PREPARED_NO_LOCATION_PERM
            LocationOutcomeStatus.DISABLED -> ACK_PREPARED_GPS_DISABLED
            LocationOutcomeStatus.TIMEOUT,
            LocationOutcomeStatus.UNAVAILABLE -> ACK_PREPARED_LOCATION_UNAVAILABLE
        }

        val detail = when (locationResult.status) {
            LocationOutcomeStatus.ACQUIRED ->
                "Solicitud recibida y aviso preparado con ubicación para ${contact.name} (Sin envío externo en Fase 2)."
            else ->
                "Solicitud recibida y aviso preparado sin ubicación (${locationResult.status.label}) para ${contact.name} (Sin envío externo)."
        }

        return AlertPreparationResult(
            status = AlertStatus.PREPARED,
            locationResult = locationResult,
            locationSummary = locationResult.detailText,
            detailMessage = detail,
            watchAckStatus = ackCode
        )
    }

    companion object {
        const val ACK_RECEIVED_PREPARING = "RECEIVED_PREPARING"
        const val ACK_PREPARED_WITH_LOCATION = "PREPARED_WITH_LOCATION"
        const val ACK_PREPARED_NO_LOCATION_PERM = "PREPARED_NO_LOCATION_PERM"
        const val ACK_PREPARED_GPS_DISABLED = "PREPARED_GPS_DISABLED"
        const val ACK_PREPARED_LOCATION_UNAVAILABLE = "PREPARED_LOCATION_UNAVAILABLE"
        const val ACK_ERROR_NO_CONTACT = "ERROR_NO_CONTACT"
    }
}

/**
 * Almacenamiento local para el contacto de confianza y el historial de eventos.
 * Incluye deduplicación por `requestId` para evitar registrar varias veces una misma solicitud
 * si se repite por un reintento.
 */
interface LocalAlertStorage {
    fun loadContact(): TrustedContact?
    fun saveContact(contact: TrustedContact)
    fun loadHistory(): List<AlertHistoryEvent>
    fun saveHistory(events: List<AlertHistoryEvent>)
    fun upsertHistoryEventByRequestId(event: AlertHistoryEvent): List<AlertHistoryEvent>
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
                    add(
                        AlertHistoryEvent(
                            id = id,
                            requestId = reqId,
                            title = obj.optString("title", "Solicitud SOS"),
                            timestampLabel = obj.optString("timestampLabel", "Reciente"),
                            status = runCatching {
                                AlertStatus.valueOf(obj.optString("status", AlertStatus.PREPARED.name))
                            }.getOrDefault(AlertStatus.PREPARED),
                            origin = runCatching {
                                AlertOrigin.valueOf(obj.optString("origin", AlertOrigin.WATCH_DATA_LAYER.name))
                            }.getOrDefault(AlertOrigin.WATCH_DATA_LAYER),
                            locationNote = obj.optString(
                                "locationNote",
                                "Ubicación no disponible"
                            ),
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
                put("title", event.title)
                put("timestampLabel", event.timestampLabel)
                put("status", event.status.name)
                put("origin", event.origin.name)
                put("locationNote", event.locationNote)
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
            current[existingIndex] = event.copy(id = existing.id)
        } else {
            current.add(0, event)
        }
        saveHistory(current)
        AlertEventBus.notifyHistoryUpdated(current, event)
        return current
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
 * Bus en memoria para sincronizar eventos recibidos por [WearableListenerService] en segundo plano
 * con el [MobileAlertViewModel] si la actividad está abierta o se abre después.
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
            current[existingIndex] = event.copy(id = existing.id)
        } else {
            current.add(0, event)
        }
        savedHistory = current
        return current
    }
}
