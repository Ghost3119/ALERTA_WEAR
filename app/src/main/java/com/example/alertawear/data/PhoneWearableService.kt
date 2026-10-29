package com.example.alertawear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.alertawear.MainActivity
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.WatchConnectionState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object WearDataLayerContract {
    const val PATH_SOS_REQUEST = "/alerta_wear/sos_request"
    const val PATH_SOS_ACK = "/alerta_wear/sos_ack"
    const val PATH_SOS_CANCEL = "/alerta_wear/sos_cancel"
}

interface WatchConnectionMonitor {
    suspend fun checkConnectedWatch(): WatchConnectionState
}

class RealWatchConnectionMonitor(
    private val context: Context
) : WatchConnectionMonitor {
    override suspend fun checkConnectedWatch(): WatchConnectionState {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val primaryNode = nodes.firstOrNull()
            if (primaryNode != null) {
                WatchConnectionState(
                    isConnected = true,
                    connectedNodeName = primaryNode.displayName,
                    connectedNodeId = primaryNode.id,
                    isChecking = false
                )
            } else {
                WatchConnectionState(
                    isConnected = false,
                    connectedNodeName = null,
                    connectedNodeId = null,
                    isChecking = false
                )
            }
        } catch (_: Exception) {
            WatchConnectionState(
                isConnected = false,
                connectedNodeName = null,
                connectedNodeId = null,
                isChecking = false
            )
        }
    }
}

/**
 * Coordinador en el teléfono que procesa una solicitud SOS entrante desde el reloj:
 * 1. Envía confirmación inmediata ("RECEIVED_PREPARING") al reloj.
 * 2. Registra/actualiza el evento en el historial local deduplicado por `requestId`.
 * 3. Intenta obtener la ubicación con [AlertPreparationGateway].
 * 4. Actualiza el evento en el historial local y envía el resultado final al reloj.
 */
class PhoneSosRequestProcessor(
    private val storage: LocalAlertStorage,
    private val gateway: AlertPreparationGateway,
    private val ackSender: suspend (nodeId: String, requestId: String, ackStatus: String, detail: String) -> Unit
) {
    suspend fun processIncomingSos(
        sourceNodeId: String,
        requestId: String,
        isAppInForeground: Boolean
    ): AlertHistoryEvent {
        // 1. Confirmar recepción inmediata de la solicitud al reloj
        ackSender(
            sourceNodeId,
            requestId,
            RealLocationAlertGateway.ACK_RECEIVED_PREPARING,
            "Solicitud recibida en el teléfono. Buscando ubicación..."
        )

        val initialEvent = AlertHistoryEvent(
            id = "evt_$requestId",
            requestId = requestId,
            title = "Solicitud SOS recibida desde reloj",
            timestampLabel = SharedPrefsAlertStorage.formatCurrentTimestamp(),
            status = AlertStatus.PENDING,
            origin = AlertOrigin.WATCH_DATA_LAYER,
            locationNote = "Buscando ubicación en el teléfono...",
            isSimulated = false
        )
        storage.upsertHistoryEventByRequestId(initialEvent)

        // 2. Intentar obtener ubicación y preparar aviso
        val contact = storage.loadContact()
        val result = gateway.prepareAlert(
            contact = contact,
            origin = AlertOrigin.WATCH_DATA_LAYER,
            isAppInForeground = isAppInForeground
        )

        val finalEvent = initialEvent.copy(
            title = if (result.status == AlertStatus.PREPARED) {
                "Solicitud SOS desde reloj"
            } else {
                "Solicitud SOS desde reloj (Sin contacto)"
            },
            status = result.status,
            locationNote = result.locationSummary
        )
        storage.upsertHistoryEventByRequestId(finalEvent)

        // 3. Devolver resultado real de la preparación al reloj
        ackSender(
            sourceNodeId,
            requestId,
            result.watchAckStatus,
            result.locationSummary
        )

        return finalEvent
    }
}

/**
 * Servicio de Google Play Services Wearable que despierta la aplicación del teléfono cuando
 * llega un mensaje por Data Layer (`MessageClient`), incluso si la UI de la app está cerrada.
 */
class PhoneWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == WearDataLayerContract.PATH_SOS_REQUEST) {
            val appCtx = applicationContext
            val payloadStr = String(messageEvent.data, Charsets.UTF_8)
            val requestId = parseRequestId(payloadStr)
            val sourceNodeId = messageEvent.sourceNodeId

            val storage = SharedPrefsAlertStorage(appCtx)
            val locationProvider = FusedPhoneLocationProvider(appCtx)
            val gateway = RealLocationAlertGateway(locationProvider)

            val processor = PhoneSosRequestProcessor(
                storage = storage,
                gateway = gateway,
                ackSender = { nodeId, reqId, ackStatus, detail ->
                    sendAckToWatch(appCtx, nodeId, reqId, ackStatus, detail)
                }
            )

            val isForeground = AlertEventBus.isUiInForeground.value
            if (!isForeground) {
                showIncomingSosNotification(requestId)
            }

            // Usar un scope que no se cancele cuando WearableListenerService destruya la instancia tras retornar onMessageReceived
            APP_BACKGROUND_SCOPE.launch {
                processor.processIncomingSos(
                    sourceNodeId = sourceNodeId,
                    requestId = requestId,
                    isAppInForeground = AlertEventBus.isUiInForeground.value
                )
            }
        }
    }

    private fun parseRequestId(rawPayload: String): String {
        return try {
            val json = JSONObject(rawPayload)
            json.optString("requestId").takeIf { it.isNotBlank() } ?: rawPayload.ifBlank { "req_default" }
        } catch (_: Exception) {
            rawPayload.ifBlank { "req_${System.currentTimeMillis()}" }
        }
    }

    private suspend fun sendAckToWatch(
        appContext: Context,
        nodeId: String,
        requestId: String,
        ackStatus: String,
        detail: String
    ) {
        try {
            val json = JSONObject().apply {
                put("requestId", requestId)
                put("ackStatus", ackStatus)
                put("detail", detail)
            }
            Wearable.getMessageClient(appContext)
                .sendMessage(
                    nodeId,
                    WearDataLayerContract.PATH_SOS_ACK,
                    json.toString().toByteArray(Charsets.UTF_8)
                )
                .await()
        } catch (_: Exception) {
            // Si el reloj se desconectó antes del ACK, el evento ya quedó registrado localmente.
        }
    }

    private fun showIncomingSosNotification(requestId: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "alerta_wear_sos_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Solicitudes SOS del reloj",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica cuando el reloj envía una solicitud SOS"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_request_id", requestId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Solicitud SOS desde tu reloj")
            .setContentText("Toca para abrir Alerta Wear y preparar ubicación.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            manager.notify(1001, notification)
        }
    }

    companion object {
        private val APP_BACKGROUND_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
