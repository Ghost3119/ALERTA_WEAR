package com.example.alertawear.data

import com.example.alertawear.model.LocationOutcomeStatus
import com.example.alertawear.model.LocationResult
import java.util.Locale

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val ageMs: Long
)

object LocationFreshness {
    const val MAX_AGE_MS = 120_000L

    fun isRecent(ageMs: Long): Boolean {
        return ageMs in 0L..MAX_AGE_MS
    }

    suspend fun firstRecent(sources: List<suspend () -> LocationFix?>): LocationFix? {
        for (source in sources) {
            val fix = runCatching { source() }.getOrNull()
            if (fix != null && isRecent(fix.ageMs)) {
                return fix
            }
        }
        return null
    }

    suspend fun firstRecent(vararg sources: suspend () -> LocationFix?): LocationFix? {
        return firstRecent(sources.toList())
    }

    fun toResult(fix: LocationFix?): LocationResult {
        return if (fix != null && isRecent(fix.ageMs)) {
            val latFormatted = String.format(Locale.US, "%.5f", fix.latitude)
            val lonFormatted = String.format(Locale.US, "%.5f", fix.longitude)
            LocationResult(
                status = LocationOutcomeStatus.ACQUIRED,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                detailText = "Ubicación preparada: $latFormatted, $lonFormatted"
            )
        } else {
            LocationResult(
                status = LocationOutcomeStatus.TIMEOUT,
                detailText = "no se obtuvo una ubicación reciente"
            )
        }
    }
}
