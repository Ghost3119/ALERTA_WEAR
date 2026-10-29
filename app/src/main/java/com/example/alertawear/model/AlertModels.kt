package com.example.alertawear.model

/**
 * Representa el contacto de confianza configurado en el teléfono.
 */
data class TrustedContact(
    val name: String,
    val phone: String,
    val isSimulatedSample: Boolean = false
) {
    val isValid: Boolean
        get() = name.isNotBlank() && phone.isNotBlank()
}

/**
 * Estados reales del ciclo de vida de una alerta (Fase 3 con envío por SMS):
 * - RECEIVED: Solicitud recibida desde el reloj o iniciada en el teléfono.
 * - PREPARING_LOCATION: Buscando ubicación en el teléfono.
 * - SENDING_SMS: SmsManager ha iniciado la transmisión del SMS; esperando callback de `sentIntent`.
 * - SENT_BY_DEVICE: Confirmado por `sentIntent` (`Activity.RESULT_OK`) que el dispositivo envió el SMS a la red celular (entrega aún sin confirmar).
 * - DELIVERED: Confirmado únicamente cuando llega `deliveryIntent` del operador.
 * - CANCELLED: Cancelada antes de iniciar el envío.
 * - ERROR: Fallo real (falta contacto, sin permiso SEND_SMS, sin SIM/señal o fallo de radio en `sentIntent`).
 */
enum class AlertStatus(val displayLabel: String, val description: String) {
    RECEIVED(
        displayLabel = "Solicitud recibida",
        description = "Iniciando procesamiento de la alerta..."
    ),
    PREPARING_LOCATION(
        displayLabel = "Preparando aviso",
        description = "Obteniendo ubicación en el teléfono..."
    ),
    SENDING_SMS(
        displayLabel = "Enviando SMS...",
        description = "Transmitiendo SMS de prueba al contacto de confianza..."
    ),
    SENT_BY_DEVICE(
        displayLabel = "Enviado por el dispositivo (Entrega sin confirmar)",
        description = "El teléfono envió el SMS a la red móvil. Aún no hay confirmación de entrega."
    ),
    DELIVERED(
        displayLabel = "Entregado al destinatario",
        description = "El operador confirmó la entrega del SMS."
    ),
    CANCELLED(
        displayLabel = "Alerta cancelada",
        description = "Cancelada por el usuario antes de enviar el SMS."
    ),
    ERROR(
        displayLabel = "Error de envío",
        description = "No se pudo enviar el SMS al contacto de confianza."
    );

    companion object {
        fun fromStoredName(name: String): AlertStatus {
            return when (name) {
                "PENDING" -> PREPARING_LOCATION
                "PREPARED" -> SENT_BY_DEVICE
                else -> entries.firstOrNull { it.name == name } ?: ERROR
            }
        }
    }
}

/**
 * Resultado del intento de obtener la ubicación en el teléfono.
 */
enum class LocationOutcomeStatus(val label: String) {
    ACQUIRED("Ubicación obtenida"),
    PERMISSION_DENIED("Permiso de ubicación denegado"),
    BACKGROUND_RESTRICTED("Sin permiso de ubicación en segundo plano"),
    DISABLED("Ubicación / GPS desactivado en el teléfono"),
    TIMEOUT("Tiempo de espera agotado al buscar ubicación"),
    UNAVAILABLE("Ubicación no disponible")
}

data class LocationResult(
    val status: LocationOutcomeStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val detailText: String
) {
    val mapsUrl: String?
        get() = if (status == LocationOutcomeStatus.ACQUIRED && latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            null
        }
}

/**
 * Estado real de la conexión con el reloj Wear OS mediante Wearable Data Layer (NodeClient).
 */
data class WatchConnectionState(
    val isConnected: Boolean = false,
    val connectedNodeName: String? = null,
    val connectedNodeId: String? = null,
    val isChecking: Boolean = true
) {
    val title: String
        get() = when {
            isChecking -> "Verificando conexión con reloj..."
            isConnected && !connectedNodeName.isNullOrBlank() -> "Reloj conectado ($connectedNodeName)"
            isConnected -> "Reloj conectado"
            else -> "Reloj desconectado"
        }

    val subtitle: String
        get() = when {
            isChecking -> "Consultando nodos de Wear OS Data Layer..."
            isConnected -> "Listo para recibir solicitudes SOS reales"
            else -> "Empareja el reloj en Device Manager → Pair Wearable"
        }
}

/**
 * Origen del evento de alerta.
 */
enum class AlertOrigin(val label: String) {
    WATCH_DATA_LAYER("Desde tu reloj (Wear Data Layer)"),
    PHONE_LOCAL("Iniciada en el teléfono")
}

/**
 * Evento registrado en el historial local del teléfono.
 * Incluye `requestId` y `sourceNodeId` para evitar envíos duplicados y notificar cambios asíncronos
 * (enviado / entregado / error) de vuelta al reloj.
 */
data class AlertHistoryEvent(
    val id: String,
    val requestId: String,
    val sourceNodeId: String? = null,
    val title: String,
    val timestampLabel: String,
    val status: AlertStatus,
    val origin: AlertOrigin,
    val locationNote: String,
    val smsStatusNote: String = "",
    val isSimulated: Boolean = false
)
