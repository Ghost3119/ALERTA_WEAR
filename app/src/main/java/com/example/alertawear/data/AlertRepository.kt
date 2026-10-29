package com.example.alertawear.data

import android.content.Context
import android.content.SharedPreferences
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contrato para preparar el envío de una alerta.
 * Diseñado para que en etapas posteriores se pueda conectar el método de envío real
 * sin alterar la lógica de estados ni afirmar "enviado" prematuramente.
 */
interface AlertPreparationGateway {
    suspend fun prepareAlert(
        contact: TrustedContact?,
        origin: AlertOrigin
    ): AlertPreparationResult
}

data class AlertPreparationResult(
    val status: AlertStatus,
    val locationSummary: String,
    val detailMessage: String
)

/**
 * Implementación local de demostración para la Etapa 1.
 * Prepara la información de la alerta y distingue claramente entre PREPARADO y ERROR
 * (por ejemplo, si no hay contacto configurado), sin simular un envío real de SMS/WhatsApp.
 */
class LocalDemoAlertGateway : AlertPreparationGateway {
    override suspend fun prepareAlert(
        contact: TrustedContact?,
        origin: AlertOrigin
    ): AlertPreparationResult {
        if (contact == null || !contact.isValid) {
            return AlertPreparationResult(
                status = AlertStatus.ERROR,
                locationSummary = "Ubicación omitida",
                detailMessage = "Error: Configura primero un contacto de confianza válido."
            )
        }
        return AlertPreparationResult(
            status = AlertStatus.PREPARED,
            locationSummary = "Ubicación de prueba preparada (demostración local)",
            detailMessage = "Aviso preparado para ${contact.name}. Envío pendiente de confirmación."
        )
    }
}

/**
 * Almacenamiento local para el contacto de confianza y el historial de eventos.
 */
interface LocalAlertStorage {
    fun loadContact(): TrustedContact?
    fun saveContact(contact: TrustedContact)
    fun loadHistory(): List<AlertHistoryEvent>
    fun saveHistory(events: List<AlertHistoryEvent>)
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
            return defaultSampleHistory()
        }
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        AlertHistoryEvent(
                            id = obj.optString("id", "evt_$i"),
                            title = obj.optString("title", "Prueba de alerta"),
                            timestampLabel = obj.optString("timestampLabel", "Reciente"),
                            status = runCatching {
                                AlertStatus.valueOf(obj.optString("status", AlertStatus.PREPARED.name))
                            }.getOrDefault(AlertStatus.PREPARED),
                            origin = runCatching {
                                AlertOrigin.valueOf(obj.optString("origin", AlertOrigin.SAMPLE_DATA.name))
                            }.getOrDefault(AlertOrigin.SAMPLE_DATA),
                            locationNote = obj.optString(
                                "locationNote",
                                "Se incluye si está disponible"
                            ),
                            isSimulated = obj.optBoolean("isSimulated", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            defaultSampleHistory()
        }
    }

    override fun saveHistory(events: List<AlertHistoryEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject().apply {
                put("id", event.id)
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

    companion object {
        private const val PREFS_NAME = "alerta_wear_local_prefs"
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_CONTACT_SIMULATED = "contact_simulated"
        private const val KEY_HISTORY_JSON = "history_json"

        fun defaultSampleHistory(): List<AlertHistoryEvent> = listOf(
            AlertHistoryEvent(
                id = "sample_1",
                title = "Prueba de alerta (Dato simulado)",
                timestampLabel = "Hoy · 18:30 (Ejemplo)",
                status = AlertStatus.PREPARED,
                origin = AlertOrigin.SAMPLE_DATA,
                locationNote = "Se incluye si está disponible (Simulado)",
                isSimulated = true
            )
        )
    }
}

/**
 * Almacenamiento en memoria para pruebas unitarias y previsualizaciones en Compose.
 */
class InMemoryAlertStorage(
    initialContact: TrustedContact? = null,
    initialHistory: List<AlertHistoryEvent> = SharedPrefsAlertStorage.defaultSampleHistory()
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
}
