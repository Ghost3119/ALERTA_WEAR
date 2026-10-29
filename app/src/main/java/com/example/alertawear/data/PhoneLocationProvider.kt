package com.example.alertawear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
import java.util.Locale

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
            // 1. Intentar primero obtener una ubicación fresca de alta precisión
            val highAccRequest = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(120_000L)
                .build()

            val freshLocation = runCatching {
                withTimeoutOrNull(timeoutMs) {
                    fusedClient.getCurrentLocation(highAccRequest, cancellationTokenSource.token).await()
                }
            }.getOrNull()

            // 2. Respaldo en interiores (Wi-Fi / Celda) o última ubicación conocida del dispositivo
            val fallbackToken = CancellationTokenSource()
            val location = freshLocation
                ?: runCatching { fusedClient.lastLocation.await() }.getOrNull()
                ?: runCatching {
                    val balancedReq = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setDurationMillis(3000L)
                        .build()
                    withTimeoutOrNull(3200L) {
                        fusedClient.getCurrentLocation(balancedReq, fallbackToken.token).await()
                    }
                }.getOrNull()

            if (location != null) {
                val latFormatted = String.format(Locale.US, "%.5f", location.latitude)
                val lonFormatted = String.format(Locale.US, "%.5f", location.longitude)
                LocationResult(
                    status = LocationOutcomeStatus.ACQUIRED,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    detailText = "Ubicación preparada: $latFormatted, $lonFormatted"
                )
            } else {
                LocationResult(
                    status = LocationOutcomeStatus.TIMEOUT,
                    detailText = "Tiempo de espera agotado al buscar señal de ubicación."
                )
            }
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
