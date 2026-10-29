package com.example.alertawear

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.alertawear.data.SharedPrefsAlertStorage
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba instrumentada de contexto Android y persistencia local aislada en [SharedPrefsAlertStorage].
 * Usa un archivo de preferencias exclusivo de prueba (`test_alerta_wear_isolated_prefs`) para no
 * modificar los datos reales configurados por el usuario en el dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class AlertStorageInstrumentedTest {

    private val testPrefsName = "test_alerta_wear_isolated_prefs"
    private lateinit var appContext: Context
    private lateinit var storage: SharedPrefsAlertStorage

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
        storage = SharedPrefsAlertStorage(appContext, prefsName = testPrefsName)
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun packageNameAndIsolatedStoragePersistContactAndHistory() {
        assertEquals("com.example.alertawear", appContext.packageName)

        val contact = TrustedContact(name = "Contacto Test", phone = "8000000000")
        storage.saveContact(contact)
        val loadedContact = storage.loadContact()
        assertNotNull(loadedContact)
        assertEquals("Contacto Test", loadedContact?.name)
        assertEquals("8000000000", loadedContact?.phone)

        val firstEvent = AlertHistoryEvent(
            id = "evt_1",
            requestId = "req_instrumented_1",
            sourceNodeId = "node_watch",
            title = "Solicitud SOS desde reloj",
            timestampLabel = "Hoy · 12:00:00",
            status = AlertStatus.SENDING_SMS,
            origin = AlertOrigin.WATCH_DATA_LAYER,
            locationNote = "Ubicación preparada",
            smsStatusNote = "Enviando SMS..."
        )
        storage.upsertHistoryEventByRequestId(firstEvent)

        // Actualizar el mismo requestId no debe duplicar la entrada en SharedPreferences
        val updatedEvent = firstEvent.copy(
            status = AlertStatus.SENT_BY_DEVICE,
            smsStatusNote = "SMS enviado por el dispositivo (entrega sin confirmar)."
        )
        val history = storage.upsertHistoryEventByRequestId(updatedEvent)

        assertEquals(1, history.size)
        assertEquals(AlertStatus.SENT_BY_DEVICE, history.first().status)
    }
}
