package com.example.alertawear.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alertawear.R
import com.example.alertawear.model.AlertHistoryEvent
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.model.WatchConnectionState
import com.example.alertawear.ui.MobileScreen
import com.example.alertawear.ui.MobileUiState
import com.example.alertawear.ui.theme.AlertaTheme
import com.example.alertawear.ui.theme.AlertaWearTheme
import kotlinx.coroutines.delay

@Composable
fun AlertaWearMobileApp(
    uiState: MobileUiState,
    onNavigate: (MobileScreen) -> Unit,
    onRefreshStatus: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDraftNameChange: (String) -> Unit,
    onDraftPhoneChange: (String) -> Unit,
    onSaveContact: () -> Unit,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onCancelActiveAlert: () -> Unit
) {
    val colors = AlertaTheme.colors
    val hasActiveInFlightAlert = uiState.activeRequestId != null &&
        uiState.activeAlertStatus in listOf(
            AlertStatus.RECEIVED,
            AlertStatus.PREPARING_LOCATION,
            AlertStatus.SENDING_SMS
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        when (uiState.currentScreen) {
            MobileScreen.HOME -> MobileHomeScreen(
                uiState = uiState,
                hasActiveInFlightAlert = hasActiveInFlightAlert,
                onRefreshStatus = onRefreshStatus,
                onRequestPermissions = onRequestPermissions,
                onSosLongPressed = onSosLongPressed,
                onCancelCountdown = onCancelCountdown,
                onOpenContact = { onNavigate(MobileScreen.CONTACT) },
                onOpenActiveAlert = { onNavigate(MobileScreen.ALERT_IN_PROGRESS) }
            )

            MobileScreen.CONTACT -> MobileContactScreen(
                uiState = uiState,
                hasActiveInFlightAlert = hasActiveInFlightAlert,
                onDraftNameChange = onDraftNameChange,
                onDraftPhoneChange = onDraftPhoneChange,
                onSaveContact = onSaveContact,
                onRequestPermissions = onRequestPermissions,
                onOpenActiveAlert = { onNavigate(MobileScreen.ALERT_IN_PROGRESS) }
            )

            MobileScreen.ALERT_IN_PROGRESS -> MobileAlertInProgressScreen(
                uiState = uiState,
                onCancelActiveAlert = onCancelActiveAlert,
                onRequestPermissions = onRequestPermissions,
                onOpenContact = { onNavigate(MobileScreen.CONTACT) }
            )

            MobileScreen.HISTORY -> MobileHistoryScreen(
                uiState = uiState,
                hasActiveInFlightAlert = hasActiveInFlightAlert,
                onRequestPermissions = onRequestPermissions,
                onOpenActiveAlert = { onNavigate(MobileScreen.ALERT_IN_PROGRESS) }
            )
        }

        MobileFloatingGlassNavBar(
            currentScreen = uiState.currentScreen,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun AppHeaderBrand(
    hasActiveInFlightAlert: Boolean = false,
    onOpenActiveAlert: (() -> Unit)? = null
) {
    val colors = AlertaTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.primary)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stat_shield_alert),
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Alerta Wear",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Siempre a tu lado",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Surface(
                color = colors.primarySoft,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "PRUEBA",
                    color = colors.primaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        if (hasActiveInFlightAlert && onOpenActiveAlert != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = colors.primarySoft,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenActiveAlert)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bell),
                            contentDescription = null,
                            tint = colors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alerta en curso",
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Ver estado →",
                        color = colors.primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MobileHomeScreen(
    uiState: MobileUiState,
    hasActiveInFlightAlert: Boolean = false,
    onRefreshStatus: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenActiveAlert: (() -> Unit)? = null
) {
    val colors = AlertaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 116.dp)
    ) {
        AppHeaderBrand(
            hasActiveInFlightAlert = hasActiveInFlightAlert,
            onOpenActiveAlert = onOpenActiveAlert
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Siempre cerca.",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                uiState.watchConnection.isConnected -> "Tu reloj y tu teléfono, conectados."
                uiState.watchConnection.isChecking -> "Verificando el vínculo con tu reloj."
                else -> "Conecta tu reloj para activar alertas remotas."
            },
            color = colors.textMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Tarjeta de conexión real con el reloj vía Wear OS Data Layer (NodeClient)
        val isWatchConnected = uiState.watchConnection.isConnected
        val isWatchChecking = uiState.watchConnection.isChecking
        val watchCardBg = when {
            isWatchConnected -> colors.successSoft
            else -> colors.warningSoft
        }
        val watchCardAccent = when {
            isWatchConnected -> colors.success
            else -> colors.warning
        }
        val watchTitle = when {
            isWatchChecking -> "Verificando reloj..."
            isWatchConnected -> "Reloj conectado"
            else -> "Reloj desconectado"
        }
        val watchSubtitle = when {
            isWatchChecking -> "Consultando Wear OS Data Layer..."
            isWatchConnected && !uiState.watchConnection.connectedNodeName.isNullOrBlank() ->
                "Listo para recibir tu alerta · ${uiState.watchConnection.connectedNodeName}"
            isWatchConnected -> "Listo para recibir tu alerta"
            else -> "Acerca o vincula tu reloj (toca para actualizar)"
        }

        Surface(
            color = watchCardBg,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, watchCardAccent.copy(alpha = 0.14f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onRefreshStatus)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isWatchConnected || isWatchChecking) {
                            R.drawable.ic_watch
                        } else {
                            R.drawable.ic_offline
                        }
                    ),
                    contentDescription = null,
                    tint = watchCardAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = watchTitle,
                        color = watchCardAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = watchSubtitle,
                        color = watchCardAccent.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Botón SOS rojo o Cuenta regresiva cancelable
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.countdownSeconds != null) {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Text(
                            text = "Enviando alerta de prueba en",
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(colors.primarySoft)
                        ) {
                            Text(
                                text = "${uiState.countdownSeconds}",
                                color = colors.primaryText,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onCancelCountdown,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surfaceMuted,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "Cancelar",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            } else {
                HoldableSosButton(
                    outerSizeDp = 166,
                    innerSizeDp = 134,
                    textSizeSp = 28,
                    onHoldCompleted = onSosLongPressed
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mantén presionado para activar",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tendrás 5 segundos para cancelar.",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Tarjeta resumen de Contacto de confianza
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenContact)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primarySoft)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_user),
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                val contact = uiState.trustedContact
                Column(modifier = Modifier.weight(1f)) {
                    if (contact != null && contact.isValid) {
                        Text(
                            text = contact.name,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tu contacto de confianza",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Configurar contacto",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Elige a quién avisar desde tu reloj",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Aviso de permisos solo cuando falta Ubicación o SMS para mantener limpia la pantalla principal
        if (!uiState.hasLocationPermission || !uiState.hasSendSmsPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = colors.warningSoft,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permisos requeridos pendientes",
                            color = colors.warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val locStatus = if (uiState.hasLocationPermission) "Ubicación: OK" else "Ubicación: Pendiente"
                        val smsStatus = if (uiState.hasSendSmsPermission) "SMS: OK" else "SMS: Pendiente"
                        Text(
                            text = "$locStatus · $smsStatus",
                            color = colors.warning,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = onRequestPermissions) {
                        Text(
                            text = "Permitir",
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Los avisos de esta versión se envían como SMS de prueba.",
            color = colors.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HoldableSosButton(
    outerSizeDp: Int,
    innerSizeDp: Int,
    textSizeSp: Int,
    onHoldCompleted: () -> Unit,
    holdDurationMs: Long = 1200L
) {
    val colors = AlertaTheme.colors
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "sosHoldProgress")

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val steps = 24
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
            .size(outerSizeDp.dp)
            .semantics {
                contentDescription = "Botón SOS. Mantén presionado para iniciar alerta"
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
        // Halo suave exterior (#FCECEE en tema claro)
        Box(
            modifier = Modifier
                .size(outerSizeDp.dp)
                .clip(CircleShape)
                .background(colors.primarySoft)
        )

        if (isPressed || animatedProgress > 0f) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                color = colors.primary,
                strokeWidth = 5.dp,
                modifier = Modifier.size((outerSizeDp - 4).dp)
            )
        }

        // Círculo SOS rojo principal (#D7474B)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(innerSizeDp.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = colors.primary.copy(alpha = 0.35f),
                    spotColor = colors.primary.copy(alpha = 0.35f)
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
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SOS",
                    color = colors.onPrimary,
                    fontSize = textSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun MobileContactScreen(
    uiState: MobileUiState,
    hasActiveInFlightAlert: Boolean = false,
    onDraftNameChange: (String) -> Unit,
    onDraftPhoneChange: (String) -> Unit,
    onSaveContact: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenActiveAlert: (() -> Unit)? = null
) {
    val colors = AlertaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 116.dp)
    ) {
        AppHeaderBrand(
            hasActiveInFlightAlert = hasActiveInFlightAlert,
            onOpenActiveAlert = onOpenActiveAlert
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Tu contacto",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Elige a quién avisar desde tu reloj.",
            color = colors.textMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Tarjeta superior informativa del contacto
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.primarySoft)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_user),
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val saved = uiState.trustedContact
                if (saved != null && saved.isValid) {
                    Text(
                        text = "${saved.name} · ${saved.phone}",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "Recibirá tus avisos de prueba.",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Nombre",
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.draftContactName,
            onValueChange = onDraftNameChange,
            placeholder = {
                Text(
                    text = "Ej. María",
                    color = colors.textMuted,
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Número de teléfono",
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.draftContactPhone,
            onValueChange = onDraftPhoneChange,
            placeholder = {
                Text(
                    text = "Ej. +52 844 000 0000",
                    color = colors.textMuted,
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onSaveContact()
                if (!uiState.hasSendSmsPermission) {
                    onRequestPermissions()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Guardar contacto",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.contactSavedFeedback != null) {
            val isErrorFeedback = uiState.contactSavedFeedback.startsWith("Ingresa")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = uiState.contactSavedFeedback,
                color = if (isErrorFeedback) colors.primaryText else colors.success,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta informativa de privacidad local (#EEF2FA / #42628F)
        Surface(
            color = colors.infoSoft,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Tus datos permanecen en este teléfono.",
                    color = colors.info,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Usa un contacto que sepa que recibirá mensajes de prueba.",
                    color = colors.info,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun MobileAlertInProgressScreen(
    uiState: MobileUiState,
    onCancelActiveAlert: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenContact: () -> Unit
) {
    val colors = AlertaTheme.colors
    val originShort = when (uiState.activeAlertOrigin) {
        AlertOrigin.WATCH_DATA_LAYER -> "Desde tu reloj"
        AlertOrigin.PHONE_LOCAL -> "Desde este teléfono"
    }
    val matchingEvent = uiState.historyEvents.firstOrNull { it.requestId == uiState.activeRequestId }
    val timeLabel = matchingEvent?.timestampLabel ?: "Hace unos segundos"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 116.dp)
    ) {
        AppHeaderBrand()

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Alerta iniciada",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$originShort · $timeLabel",
            color = colors.textMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Tarjeta principal de estado según el resultado real del envío SMS
        val statusVisual = rememberAlertStatusVisual(uiState.activeAlertStatus)
        Surface(
            color = statusVisual.cardBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, statusVisual.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusVisual.iconCircleBg)
                ) {
                    Icon(
                        painter = painterResource(id = statusVisual.iconRes),
                        contentDescription = null,
                        tint = statusVisual.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = statusVisual.title,
                    color = statusVisual.accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.activeSmsStatusText,
                    color = statusVisual.accentColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mensaje de prueba · Alerta Wear",
                    color = statusVisual.accentColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                if (!uiState.hasLocationPermission || !uiState.hasSendSmsPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRequestPermissions) {
                        Text(
                            text = "Conceder permisos faltantes (Ubicación / SMS)",
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta Contacto de confianza
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "CONTACTO DE CONFIANZA",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                val contact = uiState.trustedContact
                if (contact != null && contact.isValid) {
                    Text(
                        text = contact.name,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.phone,
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Sin contacto configurado",
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Toca aquí para configurar tu contacto",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenContact)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de Ubicación
        val hasCoordinatesInNote = uiState.activeLocationStatusText.startsWith("Ubicación preparada")
        val locationTitle = when {
            hasCoordinatesInNote -> "Ubicación incluida"
            uiState.activeAlertStatus in listOf(AlertStatus.RECEIVED, AlertStatus.PREPARING_LOCATION) ->
                "Obteniendo ubicación"
            uiState.activeAlertStatus == AlertStatus.CANCELLED -> "Ubicación cancelada"
            else -> "Sin ubicación adjunta"
        }
        val locationAccent = if (hasCoordinatesInNote) colors.info else colors.textMuted

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pin),
                        contentDescription = null,
                        tint = locationAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = locationTitle,
                        color = locationAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasCoordinatesInNote) {
                        "Enlace a Maps adjunto al SMS. (${uiState.activeLocationStatusText})"
                    } else {
                        uiState.activeLocationStatusText
                    },
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val canStillCancel = uiState.activeAlertStatus in listOf(
            AlertStatus.RECEIVED,
            AlertStatus.PREPARING_LOCATION
        )

        OutlinedButton(
            onClick = onCancelActiveAlert,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.textPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (canStillCancel) "Cancelar alerta" else "Ver en el historial",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "\"Entregado\" aparece solo al recibir la confirmación de la red.",
            color = colors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

private data class AlertStatusCardVisual(
    val title: String,
    val cardBg: Color,
    val borderColor: Color,
    val iconCircleBg: Color,
    val accentColor: Color,
    @param:DrawableRes val iconRes: Int
)

@Composable
private fun rememberAlertStatusVisual(status: AlertStatus): AlertStatusCardVisual {
    val colors = AlertaTheme.colors
    val circleOverlay = if (colors.isDark) colors.surface else Color.White
    return when (status) {
        AlertStatus.DELIVERED -> AlertStatusCardVisual(
            title = "SMS entregado",
            cardBg = colors.successSoft,
            borderColor = colors.success.copy(alpha = 0.18f),
            iconCircleBg = circleOverlay,
            accentColor = colors.success,
            iconRes = R.drawable.ic_check
        )

        AlertStatus.SENT_BY_DEVICE -> AlertStatusCardVisual(
            title = "SMS enviado",
            cardBg = colors.infoSoft,
            borderColor = colors.info.copy(alpha = 0.18f),
            iconCircleBg = circleOverlay,
            accentColor = colors.info,
            iconRes = R.drawable.ic_bell
        )

        AlertStatus.SENDING_SMS -> AlertStatusCardVisual(
            title = "Enviando SMS",
            cardBg = colors.warningSoft,
            borderColor = colors.warning.copy(alpha = 0.2f),
            iconCircleBg = circleOverlay,
            accentColor = colors.warning,
            iconRes = R.drawable.ic_bell
        )

        AlertStatus.RECEIVED,
        AlertStatus.PREPARING_LOCATION -> AlertStatusCardVisual(
            title = "Preparando aviso",
            cardBg = colors.warningSoft,
            borderColor = colors.warning.copy(alpha = 0.2f),
            iconCircleBg = circleOverlay,
            accentColor = colors.warning,
            iconRes = R.drawable.ic_bell
        )

        AlertStatus.CANCELLED -> AlertStatusCardVisual(
            title = "Alerta cancelada",
            cardBg = colors.surface,
            borderColor = colors.border,
            iconCircleBg = colors.surfaceMuted,
            accentColor = colors.textMuted,
            iconRes = R.drawable.ic_nav_history
        )

        AlertStatus.ERROR -> AlertStatusCardVisual(
            title = "Error en el envío",
            cardBg = colors.primarySoft,
            borderColor = colors.primary.copy(alpha = 0.2f),
            iconCircleBg = circleOverlay,
            accentColor = colors.primaryText,
            iconRes = R.drawable.ic_offline
        )
    }
}

@Composable
fun MobileHistoryScreen(
    uiState: MobileUiState,
    hasActiveInFlightAlert: Boolean = false,
    onRequestPermissions: () -> Unit,
    onOpenActiveAlert: (() -> Unit)? = null
) {
    val colors = AlertaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 116.dp)
    ) {
        AppHeaderBrand(
            hasActiveInFlightAlert = hasActiveInFlightAlert,
            onOpenActiveAlert = onOpenActiveAlert
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Tu historial",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "El estado de cada aviso, en un solo lugar.",
            color = colors.textMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "RECIENTE",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.historyEvents.isEmpty()) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Sin avisos registrados todavía",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cuando actives una alerta desde el reloj o el teléfono, verás aquí su estado real de envío y ubicación.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            uiState.historyEvents.forEach { event ->
                HistoryEventCard(event = event)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (!uiState.hasLocationPermission || !uiState.hasSendSmsPermission) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onRequestPermissions) {
                Text(
                    text = "Conceder permisos de Ubicación / SMS",
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "La confirmación de entrega depende del operador móvil.",
            color = colors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun HistoryEventCard(event: AlertHistoryEvent) {
    val colors = AlertaTheme.colors
    val (badgeBg, accentColor, iconRes, statusHeadline) = when (event.status) {
        AlertStatus.DELIVERED -> HistoryVisual(
            badgeBg = colors.successSoft,
            accent = colors.success,
            iconRes = R.drawable.ic_bell,
            headline = "SMS entregado"
        )

        AlertStatus.SENT_BY_DEVICE -> HistoryVisual(
            badgeBg = colors.infoSoft,
            accent = colors.info,
            iconRes = R.drawable.ic_bell,
            headline = "Enviado · entrega sin confirmar"
        )

        AlertStatus.RECEIVED,
        AlertStatus.PREPARING_LOCATION,
        AlertStatus.SENDING_SMS -> HistoryVisual(
            badgeBg = colors.warningSoft,
            accent = colors.warning,
            iconRes = R.drawable.ic_bell,
            headline = event.status.displayLabel
        )

        AlertStatus.CANCELLED -> HistoryVisual(
            badgeBg = colors.surfaceMuted,
            accent = colors.textMuted,
            iconRes = R.drawable.ic_nav_history,
            headline = "Cancelada antes del envío"
        )

        AlertStatus.ERROR -> HistoryVisual(
            badgeBg = colors.primarySoft,
            accent = colors.primaryText,
            iconRes = R.drawable.ic_offline,
            headline = "Error en el envío"
        )
    }

    val cleanTitle = when {
        event.status == AlertStatus.CANCELLED -> "Alerta cancelada"
        event.origin == AlertOrigin.WATCH_DATA_LAYER -> "Alerta desde el reloj"
        else -> "Alerta desde el teléfono"
    }

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeBg)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanTitle,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.timestampLabel,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusHeadline,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            val detailLine = event.smsStatusNote.takeIf {
                it.isNotBlank() && it != statusHeadline
            } ?: event.locationNote
            if (detailLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detailLine,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private data class HistoryVisual(
    val badgeBg: Color,
    val accent: Color,
    @param:DrawableRes val iconRes: Int,
    val headline: String
)

private data class FloatingNavItem(
    val screen: MobileScreen,
    val label: String,
    @param:DrawableRes val iconRes: Int
)

private val FloatingNavDestinations = listOf(
    FloatingNavItem(MobileScreen.HOME, "Inicio", R.drawable.ic_nav_home),
    FloatingNavItem(MobileScreen.CONTACT, "Contacto", R.drawable.ic_nav_user),
    FloatingNavItem(MobileScreen.HISTORY, "Historial", R.drawable.ic_nav_history)
)

/**
 * Barra de navegación flotante tipo cristal con las 3 secciones principales:
 * Inicio, Contacto e Historial.
 *
 * Usa una superficie blanca casi opaca con borde y sombra suaves para lograr el efecto
 * translúcido sobre el fondo sin desenfocar en ningún caso sus propios iconos ni etiquetas.
 */
@Composable
fun MobileFloatingGlassNavBar(
    currentScreen: MobileScreen,
    onNavigate: (MobileScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AlertaTheme.colors
    val pillShape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .shadow(
                elevation = 14.dp,
                shape = pillShape,
                ambientColor = colors.navShadowColor,
                spotColor = colors.navShadowColor
            )
            .clip(pillShape)
            .background(colors.navGlassSurface)
            .border(width = 1.dp, color = colors.navGlassBorder, shape = pillShape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            FloatingNavDestinations.forEach { item ->
                // Cuando se muestra el flujo Alerta iniciada, Inicio aparece activo tal como en el diseño Figma
                val isSelected = currentScreen == item.screen ||
                    (currentScreen == MobileScreen.ALERT_IN_PROGRESS && item.screen == MobileScreen.HOME)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigate(item.screen) }
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(48.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isSelected) colors.primarySoft else Color.Transparent)
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = item.label,
                            tint = if (isSelected) colors.primaryText else colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        color = if (isSelected) colors.primaryText else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview(name = "Inicio - Tema Claro", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
fun MobileHomeLightPreview() {
    AlertaWearTheme(darkTheme = false) {
        AlertaWearMobileApp(
            uiState = MobileUiState(
                watchConnection = WatchConnectionState(
                    isConnected = true,
                    connectedNodeName = "Wear OS XL Round",
                    isChecking = false
                ),
                hasLocationPermission = true,
                isLocationServicesEnabled = true,
                hasSendSmsPermission = true,
                canDeviceSendSms = true,
                trustedContact = TrustedContact("María", "+52 844 000 1042")
            ),
            onNavigate = {},
            onRefreshStatus = {},
            onRequestPermissions = {},
            onDraftNameChange = {},
            onDraftPhoneChange = {},
            onSaveContact = {},
            onSosLongPressed = {},
            onCancelCountdown = {},
            onCancelActiveAlert = {}
        )
    }
}

@Preview(name = "Inicio - Tema Oscuro", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
fun MobileHomeDarkPreview() {
    AlertaWearTheme(darkTheme = true) {
        AlertaWearMobileApp(
            uiState = MobileUiState(
                watchConnection = WatchConnectionState(
                    isConnected = true,
                    connectedNodeName = "Wear OS XL Round",
                    isChecking = false
                ),
                hasLocationPermission = true,
                isLocationServicesEnabled = true,
                hasSendSmsPermission = true,
                canDeviceSendSms = true,
                trustedContact = TrustedContact("María", "+52 844 000 1042")
            ),
            onNavigate = {},
            onRefreshStatus = {},
            onRequestPermissions = {},
            onDraftNameChange = {},
            onDraftPhoneChange = {},
            onSaveContact = {},
            onSosLongPressed = {},
            onCancelCountdown = {},
            onCancelActiveAlert = {}
        )
    }
}
