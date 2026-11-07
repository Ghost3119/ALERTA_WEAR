package com.example.alertawear

import com.example.alertawear.data.LocationFix
import com.example.alertawear.data.LocationFreshness
import com.example.alertawear.model.LocationOutcomeStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFreshnessTest {

    @Test
    fun `recent fix (30s) is accepted with ACQUIRED and non-null mapsUrl`() {
        val fix = LocationFix(25.0, -100.0, 5f, 30_000L)
        val result = LocationFreshness.toResult(fix)

        assertEquals(LocationOutcomeStatus.ACQUIRED, result.status)
        assertEquals(25.0, result.latitude!!, 0.0001)
        assertEquals(-100.0, result.longitude!!, 0.0001)
        assertNotNull(result.mapsUrl)
        assertTrue(result.mapsUrl!!.contains("maps.google.com"))
    }

    @Test
    fun `boundary check - 120000ms is accepted and 120001ms is rejected`() {
        assertTrue(LocationFreshness.isRecent(120_000L))
        assertFalse(LocationFreshness.isRecent(120_001L))

        val fixValid = LocationFix(25.0, -100.0, 5f, 120_000L)
        assertEquals(LocationOutcomeStatus.ACQUIRED, LocationFreshness.toResult(fixValid).status)

        val fixInvalid = LocationFix(25.0, -100.0, 5f, 120_001L)
        assertEquals(LocationOutcomeStatus.TIMEOUT, LocationFreshness.toResult(fixInvalid).status)
        assertNull(LocationFreshness.toResult(fixInvalid).mapsUrl)
    }

    @Test
    fun `expired fix (5 min) is rejected with no coordinates and null mapsUrl`() {
        val fix = LocationFix(25.0, -100.0, 5f, 300_000L)
        val result = LocationFreshness.toResult(fix)

        assertEquals(LocationOutcomeStatus.TIMEOUT, result.status)
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.mapsUrl)
    }

    @Test
    fun `negative age is rejected`() {
        assertFalse(LocationFreshness.isRecent(-1L))
        val fix = LocationFix(25.0, -100.0, 5f, -1000L)
        val result = LocationFreshness.toResult(fix)

        assertEquals(LocationOutcomeStatus.TIMEOUT, result.status)
        assertNull(result.mapsUrl)
    }

    @Test
    fun `when first source is expired and second is recent, uses second`() = runTest {
        val expiredFix = LocationFix(10.0, 10.0, 10f, 300_000L) // 5 min
        val recentFix = LocationFix(25.0, -100.0, 5f, 30_000L)   // 30 s

        val sources: List<suspend () -> LocationFix?> = listOf(
            { expiredFix },
            { recentFix }
        )

        val best = LocationFreshness.firstRecent(sources)
        assertNotNull(best)
        assertEquals(25.0, best!!.latitude, 0.0001)
    }

    @Test
    fun `when all sources are null or expired, returns null and alert continues without location`() = runTest {
        val sources: List<suspend () -> LocationFix?> = listOf(
            { null },
            { LocationFix(1.0, 1.0, 1.0f, 500_000L) },
            { null }
        )

        val best = LocationFreshness.firstRecent(sources)
        assertNull(best)

        val result = LocationFreshness.toResult(best)
        assertEquals(LocationOutcomeStatus.TIMEOUT, result.status)
        assertNull(result.mapsUrl)

        val smsSender = object : com.example.alertawear.data.PhoneSmsSender {
            override fun hasSendSmsPermission(): Boolean = true
            override fun canDeviceSendSms(): Boolean = true
            override fun sendAlertSms(
                requestId: String,
                sourceNodeId: String?,
                contact: com.example.alertawear.model.TrustedContact,
                messageBody: String
            ) = com.example.alertawear.data.SmsDispatchSubmission(com.example.alertawear.data.SmsSubmissionOutcome.DISPATCH_INITIATED, "")
        }

        val message = smsSender.buildTestAlertMessage(result)
        assertFalse(message.contains("maps.google.com"))
        assertTrue(message.contains("Ubicación no disponible"))
    }
}
