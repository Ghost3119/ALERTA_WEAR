package com.example.alertawear.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.alertawear.wear.R
import kotlinx.coroutines.delay

// Constantes del tema oscuro original conservadas por compatibilidad
val WearBgColor = Color(0xFF101522)
val WearSurfaceColor = Color(0xFF1C2534)
val WearCoralColor = Color(0xFFD7474B)
val WearTextPrimary = Color(0xFFF6F7FB)
val WearTextSecondary = Color(0xFFAAB7C9)
val WearStatusGreen = Color(0xFF81D6B0)
val WearStatusAmber = Color(0xFFF7D594)

@Immutable
data class WearColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surfaceMuted: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val primaryText: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val success: Color,
    val successSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val warning: Color,
    val warningSoft: Color
)

val LightWearColorPalette = WearColorPalette(
    isDark = false,
    // Reloj legible: fondo sólido blanco, SOS rojo, sin desenfoque
    background = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF2F4F7),
    border = Color(0xFFE6E8EE),
    primary = Color(0xFFD7474B),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFFCECEE),
    primaryText = Color(0xFFB82F37),
    textPrimary = Color(0xFF20283A),
    textMuted = Color(0xFF667085),
    success = Color(0xFF24765A),
    successSoft = Color(0xFFEAF6EF),
    info = Color(0xFF42628F),
    infoSoft = Color(0xFFEEF2FA),
    warning = Color(0xFF92611C),
    warningSoft = Color(0xFFFBF3E3)
)

val DarkWearColorPalette = WearColorPalette(
    isDark = true,
    background = Color(0xFF101522),
    surfaceMuted = Color(0xFF1C2534),
    border = Color(0xFF2A364B),
    primary = Color(0xFFD7474B),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFF3E2228),
    primaryText = Color(0xFFFF8F92),
    textPrimary = Color(0xFFF6F7FB),
    textMuted = Color(0xFFAAB7C9),
    success = Color(0xFF81D6B0),
    successSoft = Color(0xFF163228),
    info = Color(0xFF9BB8E8),
    infoSoft = Color(0xFF1A283E),
    warning = Color(0xFFF7D594),
    warningSoft = Color(0xFF372C1A)
)

val LocalWearColors = staticCompositionLocalOf { LightWearColorPalette }

@Composable
fun AlertaWearWatchApp(
    uiState: WearUiState,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRetryConnection: () -> Unit,
    onRefreshConnection: () -> Unit,
    onReturnToHome: () -> Unit,
    darkTheme: Boolean = false
) {
    val palette = if (darkTheme) DarkWearColorPalette else LightWearColorPalette

    CompositionLocalProvider(LocalWearColors provides palette) {
        MaterialTheme {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
            ) {
                when (uiState.currentScreen) {
                    WearScreen.HOME -> WearHomeScreen(
                        uiState = uiState,
                        onSosLongPressed = onSosLongPressed,
                        onRefreshConnection = onRefreshConnection
                    )

                    WearScreen.COUNTDOWN -> WearCountdownScreen(
                        seconds = uiState.countdownSeconds,
                        onCancel = onCancelCountdown
                    )

                    WearScreen.IN_PROGRESS -> WearAlertInProgressScreen(
                        uiState = uiState,
                        onBackToHome = onReturnToHome
                    )

                    WearScreen.NO_CONNECTION -> WearNoConnectionScreen(
                        reasonText = uiState.noConnectionReason,
                        onRetry = onRetryConnection,
                        onBackToHome = onReturnToHome
                    )
                }
            }
        }
    }
}

@Composable
private fun WearHeaderCaption(onClick: (() -> Unit)? = null) {
    val colors = LocalWearColors.current
    val baseModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    } else {
        Modifier
    }
    Text(
        text = "ALERTA WEAR",
        color = colors.textMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        textAlign = TextAlign.Center,
        modifier = baseModifier
    )
}

@Composable
fun WearHomeScreen(
    uiState: WearUiState,
    onSosLongPressed: () -> Unit,
    onRefreshConnection: () -> Unit
) {
    val colors = LocalWearColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        WearHeaderCaption()
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "¿Necesitas ayuda?",
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))

        WearHoldableSosButton(
            onHoldCompleted = onSosLongPressed
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Mantén presionado",
            color = colors.textMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        val badgeColor = when {
            uiState.isCheckingConnection -> colors.warning
            uiState.isPhoneConnected -> colors.success
            else -> colors.primaryText
        }
        Text(
            text = uiState.connectionBadgeText,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRefreshConnection)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WearHoldableSosButton(
    onHoldCompleted: () -> Unit,
    holdDurationMs: Long = 1100L
) {
    val colors = LocalWearColors.current
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
            .size(94.dp)
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
        // Halo suave exterior (#FCECEE)
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(colors.primarySoft)
        )

        if (isPressed || animatedProgress > 0f) {
            CircularProgressIndicator(
                progress = animatedProgress,
                indicatorColor = colors.primary,
                trackColor = Color.Transparent,
                strokeWidth = 3.5.dp,
                modifier = Modifier.size(92.dp)
            )
        }

        // Botón central SOS rojo (#D7474B)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = colors.primary.copy(alpha = 0.3f),
                    spotColor = colors.primary.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(colors.primary)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sos_shield),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "SOS",
                    color = colors.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WearCountdownScreen(
    seconds: Int,
    onCancel: () -> Unit
) {
    val colors = LocalWearColors.current
    val pillShape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        WearHeaderCaption()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Alerta en",
            color = colors.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.primarySoft)
        ) {
            Text(
                text = "$seconds",
                color = colors.primaryText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colors.surfaceMuted,
                contentColor = colors.textPrimary
            ),
            shape = pillShape,
            modifier = Modifier
                .width(114.dp)
                .height(32.dp)
                .border(width = 1.dp, color = colors.border, shape = pillShape)
        ) {
            Text(
                text = "Cancelar",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WearAlertInProgressScreen(
    uiState: WearUiState,
    onBackToHome: () -> Unit
) {
    val colors = LocalWearColors.current
    val isTerminal = uiState.footerMessage == "Toca para volver"
    val visual = rememberWearProgressVisual(uiState.inProgressTitle)
    val pillShape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        WearHeaderCaption(onClick = if (isTerminal) onBackToHome else null)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(visual.circleBg)
        ) {
            Icon(
                painter = painterResource(id = visual.iconRes),
                contentDescription = null,
                tint = visual.iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = uiState.inProgressTitle,
            color = visual.titleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = uiState.inProgressSubtitle,
            color = colors.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        if (isTerminal) {
            if (uiState.inProgressTitle != "SMS entregado") {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.statusDetailMessage,
                    color = visual.detailColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Button(
                onClick = onBackToHome,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.surfaceMuted,
                    contentColor = colors.textPrimary
                ),
                shape = pillShape,
                modifier = Modifier
                    .width(116.dp)
                    .height(30.dp)
                    .border(width = 1.dp, color = colors.border, shape = pillShape)
            ) {
                Text(
                    text = "Volver al inicio",
                    color = colors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = uiState.statusDetailMessage,
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class WearProgressVisual(
    val circleBg: Color,
    val iconTint: Color,
    val titleColor: Color,
    val detailColor: Color,
    @param:DrawableRes val iconRes: Int
)

@Composable
private fun rememberWearProgressVisual(title: String): WearProgressVisual {
    val colors = LocalWearColors.current
    return when (title) {
        "SMS entregado" -> WearProgressVisual(
            circleBg = colors.successSoft,
            iconTint = colors.success,
            titleColor = colors.success,
            detailColor = colors.success,
            iconRes = R.drawable.ic_check
        )

        "SMS enviado" -> WearProgressVisual(
            circleBg = colors.infoSoft,
            iconTint = colors.info,
            titleColor = colors.info,
            detailColor = colors.info,
            iconRes = R.drawable.ic_check
        )

        "Sin confirmación" -> WearProgressVisual(
            circleBg = colors.warningSoft,
            iconTint = colors.warning,
            titleColor = colors.textPrimary,
            detailColor = colors.warning,
            iconRes = R.drawable.ic_bell
        )

        "Falta contacto",
        "Permiso SMS",
        "Error de envío" -> WearProgressVisual(
            circleBg = colors.primarySoft,
            iconTint = colors.primaryText,
            titleColor = colors.primaryText,
            detailColor = colors.primaryText,
            iconRes = R.drawable.ic_offline
        )

        "Alerta cancelada" -> WearProgressVisual(
            circleBg = colors.surfaceMuted,
            iconTint = colors.textMuted,
            titleColor = colors.textPrimary,
            detailColor = colors.textMuted,
            iconRes = R.drawable.ic_bell
        )

        else -> WearProgressVisual(
            circleBg = colors.primarySoft,
            iconTint = colors.primary,
            titleColor = colors.textPrimary,
            detailColor = colors.textMuted,
            iconRes = R.drawable.ic_bell
        )
    }
}

@Composable
fun WearNoConnectionScreen(
    reasonText: String,
    onRetry: () -> Unit,
    onBackToHome: () -> Unit
) {
    val colors = LocalWearColors.current
    val pillShape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        WearHeaderCaption(onClick = onBackToHome)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.warningSoft)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_offline),
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sin conexión",
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = reasonText,
            color = colors.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colors.surfaceMuted,
                contentColor = colors.textPrimary
            ),
            shape = pillShape,
            modifier = Modifier
                .width(108.dp)
                .height(32.dp)
                .border(width = 1.dp, color = colors.border, shape = pillShape)
        ) {
            Text(
                text = "Reintentar",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Reloj Inicio - Claro", widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearHomeLightPreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(
            currentScreen = WearScreen.HOME,
            isCheckingConnection = false,
            isPhoneConnected = true
        ),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onRefreshConnection = {},
        onReturnToHome = {},
        darkTheme = false
    )
}

@Preview(name = "Reloj Inicio - Oscuro", widthDp = 240, heightDp = 240, showBackground = true)
@Composable
fun WearHomeDarkPreview() {
    AlertaWearWatchApp(
        uiState = WearUiState(
            currentScreen = WearScreen.HOME,
            isCheckingConnection = false,
            isPhoneConnected = true
        ),
        onSosLongPressed = {},
        onCancelCountdown = {},
        onRetryConnection = {},
        onRefreshConnection = {},
        onReturnToHome = {},
        darkTheme = true
    )
}
