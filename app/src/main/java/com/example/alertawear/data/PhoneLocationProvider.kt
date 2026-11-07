package com.example.alertawear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.alertawear.model.LocationOutcomeStatus
import com.example.alertawear.model.LocationResult
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

interface PhoneLocationProvider {
    fun hasForegroundLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun obtainLocation(isAppInForeground: Boolean): LocationResult
}

class FusedPhoneLocationProvider(
    private val context: Context,
    private val timeoutMs: Long = 5000L
) : PhoneLocationProvider {

    override fun hasForegroundLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isLocationEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun Location.toLocationFix(): LocationFix {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - this.elapsedRealtimeNanos) / 1_000_000L
        return LocationFix(
            latitude = this.latitude,
            longitude = this.longitude,
            accuracyMeters = this.accuracy,
            ageMs = ageMs
        )
    }

    @Suppress("MissingPermission")
    override suspend fun obtainLocation(isAppInForeground: Boolean): LocationResult {
        if (!hasForegroundLocationPermission()) {
            return LocationResult(
                status = LocationOutcomeStatus.PERMISSION_DENIED,
                detailText = "Permiso de ubicación denegado en el teléfono."
            )
        }

        if (!isAppInForeground && !hasBackgroundLocationPermission()) {
            return LocationResult(
                status = LocationOutcomeStatus.BACKGROUND_RESTRICTED,
                detailText = "Solicitud recibida con app cerrada: Android requiere abrir la app o permiso 'Permitir todo el tiempo' para leer ubicación."
            )
        }

        if (!isLocationEnabled()) {
            return LocationResult(
                status = LocationOutcomeStatus.DISABLED,
                detailText = "Ubicación (GPS) desactivada en los ajustes del teléfono."
            )
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        return try {
            val highAccRequest = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(120_000L)
                .build()

            val source1: suspend () -> LocationFix? = {
                val loc = withTimeoutOrNull(timeoutMs) {
                    fusedClient.getCurrentLocation(highAccRequest, cancellationTokenSource.token).await()
                }
                loc?.toLocationFix()
            }

            val source2: suspend () -> LocationFix? = {
                val loc = fusedClient.lastLocation.await()
                loc?.toLocationFix()
            }

            val source3: suspend () -> LocationFix? = {
                val fallbackToken = CancellationTokenSource()
                val balancedReq = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(3000L)
                    .build()
                val loc = withTimeoutOrNull(3200L) {
                    fusedClient.getCurrentLocation(balancedReq, fallbackToken.token).await()
                }
                loc?.toLocationFix()
            }

            val bestFix = LocationFreshness.firstRecent(source1, source2, source3)
            LocationFreshness.toResult(bestFix)
        } catch (e: SecurityException) {
            LocationResult(
                status = LocationOutcomeStatus.PERMISSION_DENIED,
                detailText = "Permiso de ubicación insuficiente."
            )
        } catch (_: CancellationException) {
            LocationResult(
                status = LocationOutcomeStatus.TIMEOUT,
                detailText = "Tiempo de espera agotado al buscar ubicación."
            )
        } catch (e: Exception) {
            LocationResult(
                status = LocationOutcomeStatus.UNAVAILABLE,
                detailText = "Ubicación no disponible (${e.localizedMessage ?: "error de proveedor"})."
            )
        } finally {
            cancellationTokenSource.cancel()
        }
    }
}
