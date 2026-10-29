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
 * Estados de preparación de una alerta en la Fase 2.
 * Distingue claramente entre:
 * - Solicitud recibida y en preparación (PENDING)
 * - Aviso preparado en el teléfono con o sin ubicación (PREPARED)
 * - Alerta cancelada (CANCELLED)
 * - Error de preparación, como falta de contacto (ERROR)
 * Nunca afirma que se envió un mensaje externo (SMS/WhatsApp) a un contacto.
 */
enum class AlertStatus(val displayLabel: String, val description: String) {
    PENDING(
        displayLabel = "Solicitud recibida · Preparando aviso",
        description = "Obteniendo ubicación en el teléfono..."
    ),
    PREPARED(
        displayLabel = "Aviso preparado (Sin envío externo)",
        description = "Información de alerta preparada en el teléfono."
    ),
    CANCELLED(
        displayLabel = "Alerta cancelada",
        description = "Cancelada por el usuario antes de completar."
    ),
    ERROR(
        displayLabel = "Error al preparar",
        description = "No se pudo completar la preparación del aviso."
    )
}

/**
 * Resultado del intento de obtener la ubicación en el teléfono.
 */
enum class LocationOutcomeStatus(val label: String) {
    ACQUIRED("Ubicación obtenida"),
    PERMISSION_DENIED("Permiso de ubicación denegado"),
    BACKGROUND_RESTRICTED("Sin permiso en segundo plano (abre la app)"),
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
)

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
            else -> "Empareja el emulador en Device Manager → Pair Wearable"
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
 * Incluye `requestId` para evitar duplicados cuando un reintento envía la misma solicitud.
 */
data class AlertHistoryEvent(
    val id: String,
    val requestId: String,
    val title: String,
    val timestampLabel: String,
    val status: AlertStatus,
    val origin: AlertOrigin,
    val locationNote: String,
    val isSimulated: Boolean = false
)
