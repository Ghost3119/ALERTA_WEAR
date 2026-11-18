package com.example.alertawear.data

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.LocationResult
import com.example.alertawear.model.TrustedContact
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale

interface PhoneSmsSender {
    fun hasSendSmsPermission(): Boolean
    fun canDeviceSendSms(): Boolean

    /**
     * Construye el texto del SMS identificándolo claramente como prueba e incluyendo el enlace a
     * Google Maps cuando hay coordenadas válidas, o explicando por qué la ubicación no está disponible.
     */
    fun buildTestAlertMessage(locationResult: LocationResult): String {
        val mapsUrl = locationResult.mapsUrl
        return if (mapsUrl != null) {
            val lat = String.format(Locale.US, "%.5f", locationResult.latitude)
            val lon = String.format(Locale.US, "%.5f", locationResult.longitude)
            "Alerta de prueba de Alerta Wear. Necesito ayuda.\nMi ubicación: https://maps.google.com/?q=$lat,$lon"
        } else {
            "Alerta de prueba de Alerta Wear. Necesito ayuda.\nUbicación no disponible (${locationResult.status.label})."
        }
    }

    fun sendAlertSms(
        requestId: String,
        sourceNodeId: String?,
        contact: TrustedContact,
        messageBody: String
    ): SmsDispatchSubmission
}

enum class SmsSubmissionOutcome {
    DISPATCH_INITIATED,
    ERROR_NO_PERMISSION,
    ERROR_NO_SIM_OR_CAPABILITY,
    ERROR_INVALID_CONTACT,
    ERROR_EXCEPTION
}

data class SmsDispatchSubmission(
    val outcome: SmsSubmissionOutcome,
    val detailMessage: String
)

class AndroidPhoneSmsSender(
    private val context: Context
) : PhoneSmsSender {

    override fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun canDeviceSendSms(): Boolean {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (!hasTelephony) return false
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        return telephonyManager.simState == TelephonyManager.SIM_STATE_READY
    }

    override fun sendAlertSms(
        requestId: String,
        sourceNodeId: String?,
        contact: TrustedContact,
        messageBody: String
    ): SmsDispatchSubmission {
        if (!contact.isValid) {
            return SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.ERROR_INVALID_CONTACT,
                detailMessage = "Error: Contacto de confianza no válido."
            )
        }

        if (!hasSendSmsPermission()) {
            return SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.ERROR_NO_PERMISSION,
                detailMessage = "Error: Permiso SEND_SMS denegado en el teléfono."
            )
        }

        if (!canDeviceSendSms()) {
            return SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.ERROR_NO_SIM_OR_CAPABILITY,
                detailMessage = "Error: El teléfono no tiene tarjeta SIM lista o capacidad de telefonía."
            )
        }

        return try {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                return SmsDispatchSubmission(
                    outcome = SmsSubmissionOutcome.ERROR_NO_SIM_OR_CAPABILITY,
                    detailMessage = "Error: SmsManager no disponible en este dispositivo."
                )
            }

            val dividedParts = smsManager.divideMessage(messageBody)
            val parts = if (dividedParts.isNullOrEmpty()) arrayListOf(messageBody) else dividedParts
            val totalParts = parts.size

            SmsDeliveryResultProcessor.registerExpectedParts(requestId, totalParts)

            val sentIntents = ArrayList<PendingIntent>(totalParts)
            val deliveryIntents = ArrayList<PendingIntent>(totalParts)

            for (index in parts.indices) {
                val sentIntent = Intent(context, SmsStatusBroadcastReceiver::class.java).apply {
                    action = ACTION_SMS_SENT
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra(EXTRA_SOURCE_NODE_ID, sourceNodeId)
                    putExtra(EXTRA_CONTACT_NAME, contact.name)
                    putExtra(EXTRA_PART_INDEX, index)
                    putExtra(EXTRA_TOTAL_PARTS, totalParts)
                }
                val sentPi = PendingIntent.getBroadcast(
                    context,
                    (requestId.hashCode() * 31) + index,
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                sentIntents.add(sentPi)

                val deliveredIntent = Intent(context, SmsStatusBroadcastReceiver::class.java).apply {
                    action = ACTION_SMS_DELIVERED
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra(EXTRA_SOURCE_NODE_ID, sourceNodeId)
                    putExtra(EXTRA_CONTACT_NAME, contact.name)
                    putExtra(EXTRA_PART_INDEX, index)
                    putExtra(EXTRA_TOTAL_PARTS, totalParts)
                }
                val deliveredPi = PendingIntent.getBroadcast(
                    context,
                    (requestId.hashCode() * 31) + 100 + index,
                    deliveredIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                deliveryIntents.add(deliveredPi)
            }

            if (totalParts > 1) {
                smsManager.sendMultipartTextMessage(
                    contact.phone.trim(),
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    contact.phone.trim(),
                    null,
                    messageBody,
                    sentIntents.first(),
                    deliveryIntents.first()
                )
            }

            SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.DISPATCH_INITIATED,
                detailMessage = "Enviando SMS de prueba a ${contact.name} (${contact.phone})..."
            )
        } catch (e: Exception) {
            SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.ERROR_EXCEPTION,
                detailMessage = "Error al iniciar el envío de SMS (${e.localizedMessage ?: e.javaClass.simpleName})."
            )
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.example.alertawear.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.alertawear.ACTION_SMS_DELIVERED"
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_SOURCE_NODE_ID = "extra_source_node_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"
    }
}

/**
 * Procesa el resultado de los callbacks de `SmsManager` (`ACTION_SMS_SENT` y `ACTION_SMS_DELIVERED`)
 * soportando mensajes divididos en múltiples partes (multipart SMS):
 * - Solo confirma `SENT_BY_DEVICE` o `DELIVERED` cuando se completan todas las partes esperadas.
 * - Conserva cualquier error ocurrido en alguna de las partes sin sobreescribirlo con partes exitosas.
 * - Controla respuestas duplicadas o fuera de orden por índice de parte.
 */
object SmsDeliveryResultProcessor {

    private data class MultipartTrackingState(
        var totalParts: Int,
        val sentOkParts: MutableSet<Int> = mutableSetOf(),
        val sentFailedParts: MutableMap<Int, Int> = mutableMapOf(),
        val deliveredOkParts: MutableSet<Int> = mutableSetOf(),
        val deliveredFailedParts: MutableSet<Int> = mutableSetOf(),
        var sentCompleteNotified: Boolean = false,
        var deliveredCompleteNotified: Boolean = false,
        var errorNotified: Boolean = false
    )

    private val lock = Any()
    private val multipartStates = mutableMapOf<String, MultipartTrackingState>()

    fun registerExpectedParts(requestId: String, totalParts: Int) {
        val safeTotal = totalParts.coerceAtLeast(1)
        synchronized(lock) {
            val state = multipartStates.getOrPut(requestId) { MultipartTrackingState(safeTotal) }
            if (safeTotal > state.totalParts) {
                state.totalParts = safeTotal
            }
        }
    }

    fun resetForTesting() {
        synchronized(lock) {
            multipartStates.clear()
        }
    }

    fun handleBroadcastResult(
        storage: LocalAlertStorage,
        action: String,
        resultCode: Int,
        requestId: String,
        sourceNodeId: String?,
        contactName: String,
        partIndex: Int = 0,
        totalParts: Int = 1,
        onNotifyWatch: (nodeId: String?, requestId: String, ackStatus: String, detail: String) -> Unit
    ): AlertHistoryEvent? {
        synchronized(lock) {
            val existing = storage.findEventByRequestId(requestId) ?: return null
            val safeTotal = totalParts.coerceAtLeast(1)
            val state = multipartStates.getOrPut(requestId) { MultipartTrackingState(safeTotal) }
            if (safeTotal > state.totalParts) {
                state.totalParts = safeTotal
            }

            if (partIndex !in 0 until state.totalParts) {
                return existing
            }

            return when (action) {
                AndroidPhoneSmsSender.ACTION_SMS_SENT -> {
                    // Ignorar respuestas duplicadas para la misma parte
                    if (partIndex in state.sentOkParts || partIndex in state.sentFailedParts) {
                        return existing
                    }

                    if (resultCode == Activity.RESULT_OK) {
                        state.sentOkParts.add(partIndex)

                        // Si alguna otra parte ya falló antes, conservar el estado de ERROR
                        if (state.sentFailedParts.isNotEmpty() || existing.status == AlertStatus.ERROR) {
                            return existing
                        }

                        // Si aún faltan partes por enviar, mantener en progreso sin confirmar envío completo
                        if (state.sentOkParts.size < state.totalParts) {
                            return existing
                        }

                        // Todas las partes enviadas: si ya estaba en DELIVERED (llegó fuera de orden), no degradar
                        if (existing.status == AlertStatus.DELIVERED || state.deliveredCompleteNotified) {
                            state.sentCompleteNotified = true
                            return existing
                        }

                        if (state.sentCompleteNotified) {
                            return existing
                        }
                        state.sentCompleteNotified = true

                        val updated = existing.copy(
                            status = AlertStatus.SENT_BY_DEVICE,
                            smsStatusNote = "SMS enviado por el dispositivo a $contactName (entrega sin confirmar por el operador)."
                        )
                        storage.upsertHistoryEventByRequestId(updated)
                        onNotifyWatch(
                            sourceNodeId ?: existing.sourceNodeId,
                            requestId,
                            RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE,
                            updated.smsStatusNote
                        )
                        updated
                    } else {
                        state.sentFailedParts[partIndex] = resultCode
                        val errorReason = mapSmsErrorCode(resultCode)
                        val note = if (state.totalParts > 1) {
                            "Error al enviar SMS ($errorReason) en parte ${partIndex + 1} de ${state.totalParts}."
                        } else {
                            "Error al enviar SMS ($errorReason)."
                        }
                        val updated = existing.copy(
                            status = AlertStatus.ERROR,
                            smsStatusNote = note
                        )
                        storage.upsertHistoryEventByRequestId(updated)
                        if (!state.errorNotified) {
                            state.errorNotified = true
                            onNotifyWatch(
                                sourceNodeId ?: existing.sourceNodeId,
                                requestId,
                                RealLocationAlertGateway.ACK_ERROR_SMS_FAILED,
                                updated.smsStatusNote
                            )
                        }
                        updated
                    }
                }

                AndroidPhoneSmsSender.ACTION_SMS_DELIVERED -> {
                    // Si alguna parte falló en el envío, conservar el error sin marcar como entregado
                    if (state.sentFailedParts.isNotEmpty() || existing.status == AlertStatus.ERROR) {
                        return existing
                    }

                    if (resultCode == Activity.RESULT_OK) {
                        // Ignorar confirmaciones de entrega duplicadas de la misma parte
                        if (partIndex in state.deliveredOkParts) {
                            return existing
                        }
                        state.deliveredOkParts.add(partIndex)

                        // Solo confirmar entrega cuando todas las partes fueron entregadas sin fallos
                        if (state.deliveredFailedParts.isNotEmpty() || state.deliveredOkParts.size < state.totalParts) {
                            return existing
                        }

                        if (state.deliveredCompleteNotified) {
                            return existing
                        }
                        state.deliveredCompleteNotified = true

                        val updated = existing.copy(
                            status = AlertStatus.DELIVERED,
                            smsStatusNote = "SMS entregado al destinatario ($contactName)."
                        )
                        storage.upsertHistoryEventByRequestId(updated)
                        onNotifyWatch(
                            sourceNodeId ?: existing.sourceNodeId,
                            requestId,
                            RealLocationAlertGateway.ACK_SMS_DELIVERED,
                            updated.smsStatusNote
                        )
                        updated
                    } else {
                        // Sin confirmación positiva de entrega en esta parte: registrar fallo de entrega y mantener estado previo
                        state.deliveredFailedParts.add(partIndex)
                        existing
                    }
                }

                else -> existing
            }
        }
    }

    fun mapSmsErrorCode(code: Int): String {
        return when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Fallo genérico de red o saldo"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "Sin servicio móvil / sin señal"
            SmsManager.RESULT_ERROR_NULL_PDU -> "PDU nulo"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio móvil apagada / Modo avión"
            else -> "Código $code"
        }
    }
}

/**
 * Recibe el resultado real de `SmsManager` (`ACTION_SMS_SENT` y `ACTION_SMS_DELIVERED`)
 * para no marcar nunca un SMS como enviado o entregado antes de que el sistema lo confirme.
 */
class SmsStatusBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val requestId = intent.getStringExtra(AndroidPhoneSmsSender.EXTRA_REQUEST_ID) ?: return
        val sourceNodeId = intent.getStringExtra(AndroidPhoneSmsSender.EXTRA_SOURCE_NODE_ID)
        val contactName = intent.getStringExtra(AndroidPhoneSmsSender.EXTRA_CONTACT_NAME).orEmpty()
        val partIndex = intent.getIntExtra(AndroidPhoneSmsSender.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(AndroidPhoneSmsSender.EXTRA_TOTAL_PARTS, 1)

        val storage = SharedPrefsAlertStorage(context.applicationContext)
        SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = action,
            resultCode = resultCode,
            requestId = requestId,
            sourceNodeId = sourceNodeId,
            contactName = contactName,
            partIndex = partIndex,
            totalParts = totalParts,
            onNotifyWatch = { nodeId, reqId, ackStatus, detail ->
                notifyWatchAsync(
                    context = context.applicationContext,
                    nodeId = nodeId,
                    requestId = reqId,
                    ackStatus = ackStatus,
                    detail = detail
                )
            }
        )
    }

    private fun notifyWatchAsync(
        context: Context,
        nodeId: String?,
        requestId: String,
        ackStatus: String,
        detail: String
    ) {
        if (nodeId.isNullOrBlank()) return
        val pendingResult = goAsync()
        RECEIVER_SCOPE.launch {
            try {
                val json = JSONObject().apply {
                    put("requestId", requestId)
                    put("ackStatus", ackStatus)
                    put("detail", detail)
                }
                Wearable.getMessageClient(context)
                    .sendMessage(
                        nodeId,
                        WearDataLayerContract.PATH_SOS_ACK,
                        json.toString().toByteArray(Charsets.UTF_8)
                    )
                    .await()
            } catch (_: Exception) {
                // Ignorar si el reloj se desconectó
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val RECEIVER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}