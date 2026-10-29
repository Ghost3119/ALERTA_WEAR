package com.example.alertawear.wear.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.alertawear.wear.R
import kotlinx.coroutines.delay

val WearBgColor = Color(0xFF101522)
val WearSurfaceColor = Color(0xFF1C2534)
val WearActiveSurfaceColor = Color(0xFF44282D)
val WearCoralColor = Color(0xFFFF6C68)
val WearTextPrimary = Color(0xFFF6F7FB)
val WearTextSecondary = Color(0xFFAAB7C9)
val WearStatusGreen = Color(0xFF81D6B0)
val WearStatusAmber = Color(0xFFF7D594)

@Composable
fun AlertaWearWatchApp(
    uiState: WearUiState,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRetryConnection: () -> Unit,
    onToggleDemoConnectionMode: () -> Unit,
    onNavigateToScreen: (WearScreen) -> Unit
) {
    MaterialTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(WearBgColor)
        ) {
            when (uiState.currentScreen) {
                WearScreen.HOME -> WearHomeScreen(
                    uiState = uiState,
                    onSosLongPressed = onSosLongPressed,
                    onToggleDemoConnectionMode = onToggleDemoConnectionMode
                )

                WearScreen.COUNTDOWN -> WearCountdownScreen(
                    seconds = uiState.countdownSeconds,
                    onCancel = onCancelCountdown
                )

                WearScreen.IN_PROGRESS -> WearAlertInProgressScreen(
                    statusDetail = uiState.statusDetailMessage,
                    onBackToHome = { onNavigateToScreen(WearScreen.HOME) },
                    onShowNoConnectionDemo = { onNavigateToScreen(WearScreen.NO_CONNECTION) }
                )

                WearScreen.NO_CONNECTION -> WearNoConnectionScreen(
                    onRetry = onRetryConnection
                )
            }
        }
    }
}

@Composable
fun WearHomeScreen(
    uiState: WearUiState,
    onSosLongPressed: () -> Unit,
    onToggleDemoConnectionMode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "ALERTA WEAR",
            color = WearCoralColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Mantén presionado",
            color = WearTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))

        WearHoldableSosButton(
            onHoldCompleted = onSosLongPressed
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Indicador inferior: toca para alternar en Etapa 1 entre Demo local y Sin conexión
        val badgeColor = when {
            uiState.isRealPhoneConnected -> WearStatusGreen
            uiState.simulatePhoneReachableForDemo -> WearStatusAmber
            else -> WearCoralColor
        }
        Text(
            text = uiState.connectionBadgeText,
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleDemoConnectionMode)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WearHoldableSosButton(
    onHoldCompleted: () -> Unit,
    holdDurationMs: Long = 1100L
) {
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "wearSosProgress")

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val steps = 22
            val stepDelay = holdDurationMs / steps
            for (i in 1..steps) {
                delay(stepDelay)
                progress = i.toFloat() / steps
            }
            isPressed = false
            progress = 0f
            onHoldCompleted()
        } else {
            progress = 0f
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Botón SOS. Mantén presionado para iniciar cuenta regresiva"
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onLongPress = {
                        if (isPressed) {
                            isPressed = false
                            progress = 0f
                            onHoldCompleted()
                        }
                    }
                )
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_sos_outer_ellipse),
            contentDescription = null,
            modifier = Modifier.size(104.dp)
        )
        if (isPressed || animatedProgress > 0f) {
            CircularProgressIndicator(
                progress = animatedProgress,
                indicatorColor = WearCoralColor,
                trackColor = Color.Transparent,
                strokeWidth = 4.dp,
                modifier = Modifier.size(102.dp)
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_sos_inner_ellipse),
            contentDescription = null,
            modifier = Modifier.size(84.dp)
        )
        Text(
            text = "SOS",
            color = WearBgColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WearCountdownScreen(
    seconds: Int,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "ALERTA WEAR",
            color = WearCoralColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enviando alerta en",
            color = WearTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$seconds",
            color = WearTextPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = WearSurfaceColor,
                contentColor = WearTextPrimary
            ),
            shape = RoundedCornerShape(19.dp),
            modifier = Modifier
                .width(126.dp)
                .height(36.dp)
        ) {
            Text(
                text = "Cancelar",
                color = WearTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WearAlertInProgressScreen(
    statusDetail: String,
    onBackToHome: () -> Unit,
    onShowNoConnectionDemo: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = "ALERTA WEAR",
            color = WearCoralColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Aviso en proceso",
            color = WearTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Revisa el celular",
            color = WearTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$statusDetail (Simulado)",
            color = WearStatusAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onShowNoConnectionDemo)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No cierres la app",
            color = WearTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onBackToHome)
        )
    }
}

@Composable
fun WearNoConnectionScreen(
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = "ALERTA WEAR",
            color = WearCoralColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Sin conexión",
            color = WearTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Abre la aplicación\nen tu teléfono",
            color = WearTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = WearSurfaceColor,
                contentColor = WearTextPrimary
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .width(108.dp)
                .height(34.dp)
        ) {
            Text(
                text = "Reintentar",
                color = WearTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearHomePreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(currentScreen = WearScreen.HOME),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onToggleDemoConnectionMode = {},
        onNavigateToScreen = {}
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearCountdownPreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(currentScreen = WearScreen.COUNTDOWN, countdownSeconds = 5),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onToggleDemoConnectionMode = {},
        onNavigateToScreen = {}
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearInProgressPreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(currentScreen = WearScreen.IN_PROGRESS),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onToggleDemoConnectionMode = {},
        onNavigateToScreen = {}
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearNoConnectionPreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(currentScreen = WearScreen.NO_CONNECTION),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onToggleDemoConnectionMode = {},
        onNavigateToScreen = {}
    )
}
