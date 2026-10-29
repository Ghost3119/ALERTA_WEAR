package com.example.alertawear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.alertawear.MainActivity
import com.example.alertawear.R
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.WatchConnectionState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
 * 1. Verifica si el `requestId` ya fue enviado, cancelado o está en proceso para evitar
 *    envíos duplicados ante reintentos.
 * 2. Envía confirmación inmediata ("RECEIVED_PREPARING") al reloj.
 * 3. Obtiene la ubicación y dispara el envío real por `SmsManager` a través de [AlertPreparationGateway],
 *    verificando antes de invocar `SmsManager` que el usuario no haya cancelado la alerta en el teléfono.
 * 4. Respeta actualizaciones rápidas de [SmsStatusBroadcastReceiver] (`SENT_BY_DEVICE`, `DELIVERED`, `ERROR`)
 *    para no sobrescribirlas y devuelve el estado resultante al reloj.
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
    ): AlertHistoryEvent = PROCESS_MUTEX.withLock {
        // 1. Evitar envío duplicado si este requestId ya está en curso, completado o cancelado
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
            val existingAck = when (existing.status) {
                AlertStatus.DELIVERED -> RealLocationAlertGateway.ACK_SMS_DELIVERED
                AlertStatus.SENT_BY_DEVICE -> RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE
                AlertStatus.CANCELLED -> RealLocationAlertGateway.ACK_CANCELLED
                AlertStatus.RECEIVED, AlertStatus.PREPARING_LOCATION -> RealLocationAlertGateway.ACK_RECEIVED_PREPARING
                else -> if (existing.locationNote.startsWith("Ubicación preparada:")) {
                    RealLocationAlertGateway.ACK_SENDING_SMS_WITH_LOCATION
                } else {
                    RealLocationAlertGateway.ACK_SENDING_SMS_NO_LOCATION
                }
            }
            ackSender(
                sourceNodeId,
                requestId,
                existingAck,
                existing.smsStatusNote.ifBlank { existing.locationNote }
            )
            return existing
        }

        // 2. Confirmar recepción inmediata de la solicitud al reloj
        ackSender(
            sourceNodeId,
            requestId,
            RealLocationAlertGateway.ACK_RECEIVED_PREPARING,
            "Solicitud recibida en el teléfono. Preparando ubicación y SMS..."
        )

        val initialEvent = AlertHistoryEvent(
            id = existing?.id ?: "evt_$requestId",
            requestId = requestId,
            sourceNodeId = sourceNodeId,
            title = "Solicitud SOS desde reloj",
            timestampLabel = SharedPrefsAlertStorage.formatCurrentTimestamp(),
            status = AlertStatus.PREPARING_LOCATION,
            origin = AlertOrigin.WATCH_DATA_LAYER,
            locationNote = "Buscando ubicación en el teléfono...",
            smsStatusNote = "Preparando mensaje SMS de prueba...",
            isSimulated = false
        )
        storage.upsertHistoryEventByRequestId(initialEvent)

        // 3. Intentar obtener ubicación y disparar envío de SMS (comprobando si se canceló durante la lectura de ubicación)
        val contact = storage.loadContact()
        val result = gateway.prepareAndSendAlert(
            requestId = requestId,
            sourceNodeId = sourceNodeId,
            contact = contact,
            origin = AlertOrigin.WATCH_DATA_LAYER,
            isAppInForeground = isAppInForeground,
            isCancelledCheck = {
                storage.findEventByRequestId(requestId)?.status == AlertStatus.CANCELLED
            }
        )

        // Consultar de nuevo por si SmsStatusBroadcastReceiver o una cancelación actualizaron el estado de forma síncrona/rápida
        val latestStored = storage.findEventByRequestId(requestId)
        val hasTerminalOrUpdatedStoredState = latestStored != null && latestStored.status in listOf(
            AlertStatus.SENT_BY_DEVICE,
            AlertStatus.DELIVERED,
            AlertStatus.ERROR,
            AlertStatus.CANCELLED
        )

        val effectiveStatus = if (hasTerminalOrUpdatedStoredState) {
            latestStored!!.status
        } else {
            result.status
        }

        val effectiveSmsNote = if (hasTerminalOrUpdatedStoredState && latestStored!!.smsStatusNote.isNotBlank()) {
            latestStored.smsStatusNote
        } else {
            result.smsStatusSummary
        }

        val effectiveLocationNote = if (effectiveStatus == AlertStatus.CANCELLED) {
            "Cancelada durante la preparación"
        } else {
            result.locationSummary
        }

        val finalEvent = initialEvent.copy(
            title = when {
                effectiveStatus == AlertStatus.CANCELLED -> "Alerta cancelada"
                contact == null || !contact.isValid -> "Solicitud SOS desde reloj (Sin contacto)"
                else -> "Solicitud SOS desde reloj"
            },
            status = effectiveStatus,
            locationNote = effectiveLocationNote,
            smsStatusNote = effectiveSmsNote
        )
        storage.upsertHistoryEventByRequestId(finalEvent)

        val ackToSend = when (effectiveStatus) {
            AlertStatus.DELIVERED -> RealLocationAlertGateway.ACK_SMS_DELIVERED
            AlertStatus.SENT_BY_DEVICE -> RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE
            AlertStatus.CANCELLED -> RealLocationAlertGateway.ACK_CANCELLED
            AlertStatus.ERROR -> if (result.status == AlertStatus.ERROR) {
                result.watchAckStatus
            } else {
                RealLocationAlertGateway.ACK_ERROR_SMS_FAILED
            }
            else -> result.watchAckStatus
        }

        // 4. Devolver estado actual al reloj
        ackSender(
            sourceNodeId,
            requestId,
            ackToSend,
            effectiveSmsNote
        )

        return finalEvent
    }

    companion object {
        private val PROCESS_MUTEX = Mutex()
    }
}

/**
 * Servicio de Google Play Services Wearable que recibe la solicitud SOS del reloj.
 *
 * `WearableListenerService.onMessageReceived` se ejecuta en un hilo de trabajo gestionado por el
 * servicio. Mantener la llamada bloqueada con un límite de tiempo (`withTimeoutOrNull(14_000L)`)
 * conserva el vínculo activo con Google Play Services mientras se resuelve la ubicación y se
 * entrega el mensaje a `SmsManager`, evitando que el servicio se desvincule en el primer milisegundo.
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
            val smsSender = AndroidPhoneSmsSender(appCtx)
            val gateway = RealLocationAlertGateway(locationProvider, smsSender)

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

            runBlocking {
                withTimeoutOrNull(14_000L) {
                    processor.processIncomingSos(
                        sourceNodeId = sourceNodeId,
                        requestId = requestId,
                        isAppInForeground = AlertEventBus.isUiInForeground.value
                    )
                }
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
            .setSmallIcon(R.drawable.ic_stat_shield_alert)
            .setContentTitle("Solicitud SOS desde tu reloj")
            .setContentText("Procesando alerta SMS de prueba. Toca para abrir Alerta Wear.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            manager.notify(1001, notification)
        }
    }
}
