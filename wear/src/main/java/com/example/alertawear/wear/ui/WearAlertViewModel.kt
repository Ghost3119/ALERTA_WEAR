package com.example.alertawear.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WearScreen {
    HOME,
    COUNTDOWN,
    IN_PROGRESS,
    NO_CONNECTION
}

/**
 * Estado del módulo Wear OS en la Etapa 1.
 * Por regla explícita del proyecto: NO afirmamos que el reloj está conectado si todavía no se ha
 * comprobado la conexión real con el teléfono. Por defecto `isRealPhoneConnected = false`,
 * permitiendo alternar el modo de demostración local (claramente etiquetado como simulado) para
 * recorrer las pantallas de "Alerta en proceso" y "Sin conexión".
 */
data class WearUiState(
    val currentScreen: WearScreen = WearScreen.HOME,
    val countdownSeconds: Int = 5,
    val isRealPhoneConnected: Boolean = false,
    val simulatePhoneReachableForDemo: Boolean = true,
    val statusDetailMessage: String = "Preparando envío"
) {
    val connectionBadgeText: String
        get() = when {
            isRealPhoneConnected -> "● Conectado"
            simulatePhoneReachableForDemo -> "● Demo local (Sin vínculo real)"
            else -> "● Sin conexión comprobada"
        }
}

class WearAlertViewModel(
    private val countdownTickMs: Long = 1000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun onSosLongPressed() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.COUNTDOWN,
                countdownSeconds = 5
            )
        }
        countdownJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _uiState.update { it.copy(countdownSeconds = sec) }
                delay(countdownTickMs)
            }
            onCountdownFinished()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update {
            it.copy(
                currentScreen = WearScreen.HOME,
                countdownSeconds = 5
            )
        }
    }

    private fun onCountdownFinished() {
        val current = _uiState.value
        if (current.isRealPhoneConnected || current.simulatePhoneReachableForDemo) {
            _uiState.update {
                it.copy(
                    currentScreen = WearScreen.IN_PROGRESS,
                    statusDetailMessage = "Preparando envío"
                )
            }
        } else {
            _uiState.update {
                it.copy(currentScreen = WearScreen.NO_CONNECTION)
            }
        }
    }

    fun toggleDemoConnectionMode() {
        _uiState.update {
            it.copy(simulatePhoneReachableForDemo = !it.simulatePhoneReachableForDemo)
        }
    }

    fun navigateToScreen(screen: WearScreen) {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(currentScreen = screen)
        }
    }

    fun retryFromNoConnection() {
        // En Etapa 1 no hay vínculo Bluetooth real, pero permite reintentar o volver al inicio
        val current = _uiState.value
        if (current.isRealPhoneConnected || current.simulatePhoneReachableForDemo) {
            _uiState.update { it.copy(currentScreen = WearScreen.IN_PROGRESS) }
        } else {
            _uiState.update { it.copy(currentScreen = WearScreen.HOME) }
        }
    }
}
