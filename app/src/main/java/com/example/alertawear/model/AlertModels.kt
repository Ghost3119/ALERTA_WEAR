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
 * Estados de preparación de una alerta en la Etapa 1 (sin afirmar nunca "enviado" o "entregado"
 * cuando solamente se ha preparado o simulado localmente).
 */
enum class AlertStatus(val displayLabel: String, val description: String) {
    PENDING(
        displayLabel = "Envío pendiente de confirmación",
        description = "Preparando datos y buscando ubicación..."
    ),
    PREPARED(
        displayLabel = "Aviso preparado",
        description = "Información lista. Envío real aún no ejecutado."
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
 * Estado de la verificación de conexión con el reloj Wear OS.
 * Por requerimiento de Etapa 1, NO se muestra el reloj como conectado si no se ha comprobado
 * una conexión real.
 */
enum class WatchConnectionStatus(
    val title: String,
    val subtitle: String,
    val isConnected: Boolean
) {
    UNVERIFIED(
        title = "Conexión con reloj sin verificar",
        subtitle = "Etapa 1: Demostración local sin vínculo activo",
        isConnected = false
    ),
    DISCONNECTED(
        title = "Reloj sin conexión",
        subtitle = "Verifica el vínculo Bluetooth y abre la app en el reloj",
        isConnected = false
    ),
    SIMULATED_CONNECTED(
        title = "Reloj conectado (Simulado)",
        subtitle = "Listo para recibir alertas (vista previa de diseño)",
        isConnected = true
    )
}

/**
 * Origen del evento de alerta.
 */
enum class AlertOrigin(val label: String) {
    WATCH_SIMULATED("Desde tu reloj (Simulado)"),
    PHONE_LOCAL("Prueba local en teléfono"),
    SAMPLE_DATA("Registro de ejemplo (Simulado)")
}

/**
 * Evento registrado en el historial local del teléfono.
 */
data class AlertHistoryEvent(
    val id: String,
    val title: String,
    val timestampLabel: String,
    val status: AlertStatus,
    val origin: AlertOrigin,
    val locationNote: String = "Ubicación simulada (pendiente GPS real en Etapa 2)",
    val isSimulated: Boolean = true
)
