package com.example.alertawear.wear.data

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object WearDataLayerPaths {
    const val PATH_SOS_REQUEST = "/alerta_wear/sos_request"
    const val PATH_SOS_ACK = "/alerta_wear/sos_ack"
}

data class PhoneNodeConnection(
    val isConnected: Boolean,
    val nodeId: String? = null,
    val nodeName: String? = null
)

data class PhoneAckPayload(
    val requestId: String,
    val ackStatus: String,
    val detail: String
)

interface WearPhoneBridge {
    val incomingAckFlow: StateFlow<PhoneAckPayload?>
    suspend fun checkPhoneConnection(): PhoneNodeConnection
    suspend fun sendSosRequest(requestId: String): Boolean
    fun clearLastAck() {}
}

object WearAckEventBus {
    private val _incomingAck = MutableStateFlow<PhoneAckPayload?>(null)
    val incomingAck: StateFlow<PhoneAckPayload?> = _incomingAck.asStateFlow()

    fun publishAck(payload: PhoneAckPayload) {
        _incomingAck.value = payload
    }

    fun clear() {
        _incomingAck.value = null
    }
}

class RealWearPhoneBridge(
    private val context: Context
) : WearPhoneBridge {

    override val incomingAckFlow: StateFlow<PhoneAckPayload?> = WearAckEventBus.incomingAck

    override fun clearLastAck() {
        WearAckEventBus.clear()
    }

    override suspend fun checkPhoneConnection(): PhoneNodeConnection {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val primary = nodes.firstOrNull()
            if (primary != null) {
                PhoneNodeConnection(
                    isConnected = true,
                    nodeId = primary.id,
                    nodeName = primary.displayName
                )
            } else {
                PhoneNodeConnection(isConnected = false)
            }
        } catch (_: Exception) {
            PhoneNodeConnection(isConnected = false)
        }
    }

    override suspend fun sendSosRequest(requestId: String): Boolean {
        val connection = checkPhoneConnection()
        val targetNodeId = connection.nodeId ?: return false
        return try {
            val payloadJson = JSONObject().apply {
                put("requestId", requestId)
                put("timestamp", System.currentTimeMillis())
            }
            Wearable.getMessageClient(context)
                .sendMessage(
                    targetNodeId,
                    WearDataLayerPaths.PATH_SOS_REQUEST,
                    payloadJson.toString().toByteArray(Charsets.UTF_8)
                )
                .await()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Servicio en el reloj que recibe las confirmaciones (`PATH_SOS_ACK`) enviadas desde el teléfono.
 */
class WearAckListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == WearDataLayerPaths.PATH_SOS_ACK) {
            val raw = String(messageEvent.data, Charsets.UTF_8)
            try {
                val json = JSONObject(raw)
                val requestId = json.optString("requestId", "")
                val ackStatus = json.optString("ackStatus", "RECEIVED_PREPARING")
                val detail = json.optString("detail", "")
                WearAckEventBus.publishAck(
                    PhoneAckPayload(
                        requestId = requestId,
                        ackStatus = ackStatus,
                        detail = detail
                    )
                )
            } catch (_: Exception) {
                // Ignorar payloads malformados
            }
        }
    }
}
