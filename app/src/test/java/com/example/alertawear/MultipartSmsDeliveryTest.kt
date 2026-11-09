package com.example.alertawear

import android.app.Activity
import android.telephony.SmsManager
import com.example.alertawear.data.AndroidPhoneSmsSender
import com.example.alertawear.data.InMemoryAlertStorage
import com.example.alertawear.data.PhoneLocationProvider
import com.example.alertawear.data.PhoneSmsSender
import com.example.alertawear.data.PhoneSosRequestProcessor
import com.example.alertawear.data.RealLocationAlertGateway
import com.example.alertawear.data.SmsDeliveryResultProcessor
import com.example.alertawear.data.SmsDispatchSubmission
import com.example.alertawear.data.SmsSubmissionOutcome
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.LocationOutcomeStatus
import com.example.alertawear.model.LocationResult
import com.example.alertawear.model.TrustedContact
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultipartSmsDeliveryTest {

    private class FakeLocationProvider : PhoneLocationProvider {
        override fun hasForegroundLocationPermission(): Boolean = true
        override fun isLocationEnabled(): Boolean = true
        override suspend fun obtainLocation(isAppInForeground: Boolean): LocationResult {
            return LocationResult(
                status = LocationOutcomeStatus.ACQUIRED,
                latitude = 25.42321,
                longitude = -101.0053,
                detailText = "Ubicación preparada"
            )
        }
    }

    private class FakePhoneSmsSender : PhoneSmsSender {
        override fun hasSendSmsPermission(): Boolean = true
        override fun canDeviceSendSms(): Boolean = true
        override fun sendAlertSms(
            requestId: String,
            sourceNodeId: String?,
            contact: TrustedContact,
            messageBody: String
        ): SmsDispatchSubmission {
            return SmsDispatchSubmission(
                outcome = SmsSubmissionOutcome.DISPATCH_INITIATED,
                detailMessage = "Enviando SMS de prueba..."
            )
        }
    }

    @Before
    fun setUp() {
        SmsDeliveryResultProcessor.resetForTesting()
    }

    @After
    fun tearDown() {
        SmsDeliveryResultProcessor.resetForTesting()
    }

    @Test
    fun `confirmacion parcial mantiene SENDING_SMS hasta recibir todas las partes`() = runTest {
        val storage = InMemoryAlertStorage(TrustedContact("Carlos", "+52 800 999 8888"))
        val watchAcks = mutableListOf<String>()
        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = RealLocationAlertGateway(FakeLocationProvider(), FakePhoneSmsSender()),
            ackSender = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        processor.processIncomingSos("node_1", "sos_parcial", isAppInForeground = true)
        watchAcks.clear()

        val afterPart0 = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = Activity.RESULT_OK,
            requestId = "sos_parcial",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            partIndex = 0,
            totalParts = 3,
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )
        val afterPart1 = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = Activity.RESULT_OK,
            requestId = "sos_parcial",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            partIndex = 1,
            totalParts = 3,
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        assertEquals(AlertStatus.SENDING_SMS, afterPart0?.status)
        assertEquals(AlertStatus.SENDING_SMS, afterPart1?.status)
        assertFalse(watchAcks.contains(RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE))
    }

    @Test
    fun `confirmacion completa en desorden marca SENT_BY_DEVICE y DELIVERED al completar todas las partes`() = runTest {
        val storage = InMemoryAlertStorage(TrustedContact("Carlos", "+52 800 999 8888"))
        val watchAcks = mutableListOf<String>()
        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = RealLocationAlertGateway(FakeLocationProvider(), FakePhoneSmsSender()),
            ackSender = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        processor.processIncomingSos("node_1", "sos_completo", isAppInForeground = true)
        watchAcks.clear()

        // Llegan fuera de orden: 2, 0 y 1
        for (part in listOf(2, 0, 1)) {
            SmsDeliveryResultProcessor.handleBroadcastResult(
                storage = storage,
                action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
                resultCode = Activity.RESULT_OK,
                requestId = "sos_completo",
                sourceNodeId = "node_1",
                contactName = "Carlos",
                partIndex = part,
                totalParts = 3,
                onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
            )
        }
        assertEquals(AlertStatus.SENT_BY_DEVICE, storage.findEventByRequestId("sos_completo")?.status)
        assertEquals(listOf(RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE), watchAcks)

        // Entrega fuera de orden: 1, 2 y 0
        for (part in listOf(1, 2, 0)) {
            SmsDeliveryResultProcessor.handleBroadcastResult(
                storage = storage,
                action = AndroidPhoneSmsSender.ACTION_SMS_DELIVERED,
                resultCode = Activity.RESULT_OK,
                requestId = "sos_completo",
                sourceNodeId = "node_1",
                contactName = "Carlos",
                partIndex = part,
                totalParts = 3,
                onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
            )
        }
        assertEquals(AlertStatus.DELIVERED, storage.findEventByRequestId("sos_completo")?.status)
        assertEquals(
            listOf(
                RealLocationAlertGateway.ACK_SMS_SENT_BY_DEVICE,
                RealLocationAlertGateway.ACK_SMS_DELIVERED
            ),
            watchAcks
        )
    }

    @Test
    fun `respuestas duplicadas de una misma parte se ignoran y no completan antes de tiempo`() = runTest {
        val storage = InMemoryAlertStorage(TrustedContact("Carlos", "+52 800 999 8888"))
        val watchAcks = mutableListOf<String>()
        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = RealLocationAlertGateway(FakeLocationProvider(), FakePhoneSmsSender()),
            ackSender = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        processor.processIncomingSos("node_1", "sos_duplicado", isAppInForeground = true)
        watchAcks.clear()

        repeat(2) {
            SmsDeliveryResultProcessor.handleBroadcastResult(
                storage = storage,
                action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
                resultCode = Activity.RESULT_OK,
                requestId = "sos_duplicado",
                sourceNodeId = "node_1",
                contactName = "Carlos",
                partIndex = 0,
                totalParts = 2,
                onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
            )
        }
        assertEquals(AlertStatus.SENDING_SMS, storage.findEventByRequestId("sos_duplicado")?.status)
        assertTrue(watchAcks.isEmpty())
    }

    @Test
    fun `confirmacion fallida en una parte conserva ERROR aunque las demas partes tengan exito`() = runTest {
        val storage = InMemoryAlertStorage(TrustedContact("Carlos", "+52 800 999 8888"))
        val watchAcks = mutableListOf<String>()
        val processor = PhoneSosRequestProcessor(
            storage = storage,
            gateway = RealLocationAlertGateway(FakeLocationProvider(), FakePhoneSmsSender()),
            ackSender = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        processor.processIncomingSos("node_1", "sos_fallido", isAppInForeground = true)
        watchAcks.clear()

        SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE,
            requestId = "sos_fallido",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            partIndex = 0,
            totalParts = 2,
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )
        val afterPart1Ok = SmsDeliveryResultProcessor.handleBroadcastResult(
            storage = storage,
            action = AndroidPhoneSmsSender.ACTION_SMS_SENT,
            resultCode = Activity.RESULT_OK,
            requestId = "sos_fallido",
            sourceNodeId = "node_1",
            contactName = "Carlos",
            partIndex = 1,
            totalParts = 2,
            onNotifyWatch = { _, _, ackStatus, _ -> watchAcks.add(ackStatus) }
        )

        assertEquals(AlertStatus.ERROR, afterPart1Ok?.status)
        assertEquals(listOf(RealLocationAlertGateway.ACK_ERROR_SMS_FAILED), watchAcks)
    }
}