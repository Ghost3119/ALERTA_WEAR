package com.example.alertawear.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alertawear.R
import com.example.alertawear.data.SharedPrefsAlertStorage
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.model.AlertStatus
import com.example.alertawear.model.TrustedContact
import com.example.alertawear.ui.MobileScreen
import com.example.alertawear.ui.MobileUiState
import com.example.alertawear.ui.theme.AlertaActiveSurface
import com.example.alertawear.ui.theme.AlertaBackground
import com.example.alertawear.ui.theme.AlertaCoral
import com.example.alertawear.ui.theme.AlertaStatusAmber
import com.example.alertawear.ui.theme.AlertaStatusError
import com.example.alertawear.ui.theme.AlertaStatusGreen
import com.example.alertawear.ui.theme.AlertaSurface
import com.example.alertawear.ui.theme.AlertaTextPrimary
import com.example.alertawear.ui.theme.AlertaTextSecondary
import com.example.alertawear.ui.theme.AlertaWearTheme
import kotlinx.coroutines.delay

@Composable
fun AlertaWearMobileApp(
    uiState: MobileUiState,
    onNavigate: (MobileScreen) -> Unit,
    onDraftNameChange: (String) -> Unit,
    onDraftPhoneChange: (String) -> Unit,
    onSaveContact: () -> Unit,
    onFillSampleContact: () -> Unit,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onSimulateWatchAlert: () -> Unit,
    onSimulateErrorAlert: () -> Unit,
    onCancelActiveAlert: () -> Unit
) {
    Scaffold(
        containerColor = AlertaBackground,
        bottomBar = {
            MobileBottomNavBar(
                currentScreen = uiState.currentScreen,
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlertaBackground)
                .padding(innerPadding)
        ) {
            when (uiState.currentScreen) {
                MobileScreen.HOME -> MobileHomeScreen(
                    uiState = uiState,
                    onSosLongPressed = onSosLongPressed,
                    onCancelCountdown = onCancelCountdown,
                    onOpenContact = { onNavigate(MobileScreen.CONTACT) },
                    onSimulateWatchAlert = onSimulateWatchAlert
                )

                MobileScreen.CONTACT -> MobileContactScreen(
                    uiState = uiState,
                    onDraftNameChange = onDraftNameChange,
                    onDraftPhoneChange = onDraftPhoneChange,
                    onSaveContact = onSaveContact,
                    onFillSampleContact = onFillSampleContact
                )

                MobileScreen.ALERT_IN_PROGRESS -> MobileAlertInProgressScreen(
                    uiState = uiState,
                    onCancelActiveAlert = onCancelActiveAlert,
                    onSimulateErrorAlert = onSimulateErrorAlert,
                    onOpenContact = { onNavigate(MobileScreen.CONTACT) }
                )

                MobileScreen.HISTORY -> MobileHistoryScreen(
                    uiState = uiState,
                    onSimulateWatchAlert = onSimulateWatchAlert
                )
            }
        }
    }
}

@Composable
private fun AppHeaderBrand() {
    Text(
        text = "ALERTA WEAR",
        color = AlertaCoral,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun MobileHomeScreen(
    uiState: MobileUiState,
    onSosLongPressed: () -> Unit,
    onCancelCountdown: () -> Unit,
    onOpenContact: () -> Unit,
    onSimulateWatchAlert: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        AppHeaderBrand()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Tu seguridad,\nsiempre cerca.",
            color = AlertaTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta de estado del reloj: NO afirma que el reloj está conectado si no se ha comprobado
        Surface(
            color = AlertaSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                val statusColor = if (uiState.watchConnectionStatus.isConnected) {
                    AlertaStatusGreen
                } else {
                    AlertaStatusAmber
                }
                Text(
                    text = "●  ${uiState.watchConnectionStatus.title}",
                    color = statusColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.watchConnectionStatus.subtitle,
                    color = AlertaTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Botón SOS o Cuenta regresiva local
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.countdownSeconds != null) {
                Surface(
                    color = AlertaActiveSurface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Enviando alerta de prueba en",
                            color = AlertaTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.countdownSeconds}",
                            color = AlertaTextPrimary,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onCancelCountdown,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertaSurface,
                                contentColor = AlertaTextPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Cancelar cuenta regresiva",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                HoldableSosButton(
                    outerSizeDp = 170,
                    innerSizeDp = 140,
                    textSizeSp = 30,
                    onHoldCompleted = onSosLongPressed
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mantén presionado para alertar",
                    color = AlertaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Tarjeta Contacto de confianza
        Surface(
            color = AlertaSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenContact)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Contacto de confianza",
                    color = AlertaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val contact = uiState.trustedContact
                if (contact != null && contact.isValid) {
                    Text(
                        text = "${contact.name} · ${contact.phone}",
                        color = AlertaStatusGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (contact.isSimulatedSample) {
                        Text(
                            text = "Dato de prueba local configurado (toca para editar)",
                            color = AlertaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = "Configura un contacto en la app (toca aquí)",
                        color = AlertaTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Panel explícito de simulación Etapa 1
        Surface(
            color = AlertaSurface.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AlertaTextSecondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "MODO DEMOSTRACIÓN LOCAL (ETAPA 1)",
                    color = AlertaStatusAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Prueba la pantalla de Alerta en proceso como si llegara la solicitud desde el reloj.",
                    color = AlertaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSimulateWatchAlert,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Simular solicitud desde reloj →",
                        color = AlertaCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
                        // Respaldo para accesibilidad y gestos largos estándar
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
            modifier = Modifier.size(outerSizeDp.dp)
        )
        if (isPressed || animatedProgress > 0f) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                color = AlertaCoral,
                strokeWidth = 5.dp,
                modifier = Modifier.size((outerSizeDp - 4).dp)
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_sos_inner_ellipse),
            contentDescription = null,
            modifier = Modifier.size(innerSizeDp.dp)
        )
        Text(
            text = "SOS",
            color = AlertaBackground,
            fontSize = textSizeSp.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MobileContactScreen(
    uiState: MobileUiState,
    onDraftNameChange: (String) -> Unit,
    onDraftPhoneChange: (String) -> Unit,
    onSaveContact: () -> Unit,
    onFillSampleContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        AppHeaderBrand()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Contacto de\nconfianza",
            color = AlertaTextPrimary,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Persona que recibirá tu aviso.",
            color = AlertaTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "NOMBRE",
            color = AlertaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.draftContactName,
            onValueChange = onDraftNameChange,
            placeholder = {
                Text(
                    text = "Ej. María",
                    color = AlertaTextSecondary,
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AlertaSurface,
                unfocusedContainerColor = AlertaSurface,
                focusedBorderColor = AlertaCoral,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = AlertaTextPrimary,
                unfocusedTextColor = AlertaTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "TELÉFONO",
            color = AlertaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.draftContactPhone,
            onValueChange = onDraftPhoneChange,
            placeholder = {
                Text(
                    text = "Ej. +52 844 000 0000",
                    color = AlertaTextSecondary,
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AlertaSurface,
                unfocusedContainerColor = AlertaSurface,
                focusedBorderColor = AlertaCoral,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = AlertaTextPrimary,
                unfocusedTextColor = AlertaTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSaveContact,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertaCoral,
                contentColor = AlertaBackground
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Guardar contacto",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.contactSavedFeedback != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.contactSavedFeedback,
                color = AlertaStatusGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Prueba el envío con su consentimiento.",
            color = AlertaTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onFillSampleContact) {
            Text(
                text = "Cargar contacto de prueba (María · +52 844 000 0000)",
                color = AlertaStatusAmber,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun MobileAlertInProgressScreen(
    uiState: MobileUiState,
    onCancelActiveAlert: () -> Unit,
    onSimulateErrorAlert: () -> Unit,
    onOpenContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        AppHeaderBrand()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Alerta iniciada",
            color = AlertaTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (uiState.activeAlertOrigin == AlertOrigin.WATCH_SIMULATED) {
                "Desde tu reloj (Simulado en Etapa 1)"
            } else {
                uiState.activeAlertOrigin.label
            },
            color = AlertaTextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta superior de preparación (#44282D)
        Surface(
            color = AlertaActiveSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                val headerText = when (uiState.activeAlertStatus) {
                    AlertStatus.PENDING -> "Preparando aviso"
                    AlertStatus.PREPARED -> "Aviso preparado (Sin envío real)"
                    AlertStatus.CANCELLED -> "Alerta cancelada"
                    AlertStatus.ERROR -> "Error en la preparación"
                }
                Text(
                    text = headerText,
                    color = AlertaTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.activeLocationStatusText,
                    color = if (uiState.activeAlertStatus == AlertStatus.ERROR) {
                        AlertaStatusError
                    } else {
                        AlertaStatusAmber
                    },
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta de Contacto de confianza y estado del envío
        Surface(
            color = AlertaSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Contacto de confianza",
                    color = AlertaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val contact = uiState.trustedContact
                if (contact != null && contact.isValid) {
                    Text(
                        text = "${contact.name} (${contact.phone})",
                        color = AlertaTextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Text(
                        text = "Sin contacto configurado",
                        color = AlertaStatusError,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onOpenContact)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = uiState.activeAlertStatus.displayLabel,
                    color = AlertaTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "El estado se actualiza cuando el envío\nse confirme o falle.",
            color = AlertaTextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onCancelActiveAlert,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertaSurface,
                contentColor = AlertaTextPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Cancelar alerta",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onSimulateErrorAlert,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Simular estado de error en preparación",
                color = AlertaStatusAmber,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun MobileHistoryScreen(
    uiState: MobileUiState,
    onSimulateWatchAlert: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        AppHeaderBrand()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Historial",
            color = AlertaTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Actividad reciente",
            color = AlertaTextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        uiState.historyEvents.forEach { event ->
            Surface(
                color = AlertaSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = event.title,
                        color = AlertaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${event.timestampLabel} · ${event.origin.label}",
                        color = AlertaTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val badgeColor = when (event.status) {
                        AlertStatus.PENDING -> AlertaStatusAmber
                        AlertStatus.PREPARED -> AlertaStatusAmber
                        AlertStatus.CANCELLED -> AlertaTextSecondary
                        AlertStatus.ERROR -> AlertaStatusError
                    }
                    Text(
                        text = "● ${event.status.displayLabel}",
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tarjeta Ubicación del diseño Figma
        Surface(
            color = AlertaSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Ubicación",
                    color = AlertaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Se incluye si está disponible",
                    color = AlertaTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "La entrega depende del método de envío\ny de la conexión disponible.",
            color = AlertaTextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSimulateWatchAlert) {
            Text(
                text = "Generar nueva prueba de alerta",
                color = AlertaCoral,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MobileBottomNavBar(
    currentScreen: MobileScreen,
    onNavigate: (MobileScreen) -> Unit
) {
    Surface(
        color = AlertaBackground,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            MobileScreen.entries.forEach { screen ->
                val isSelected = currentScreen == screen
                Text(
                    text = screen.label,
                    color = if (isSelected) AlertaCoral else AlertaTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigate(screen) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740)
@Composable
fun MobileHomeScreenPreview() {
    AlertaWearTheme {
        AlertaWearMobileApp(
            uiState = MobileUiState(
                trustedContact = TrustedContact("María", "+52 844 000 0000", isSimulatedSample = true),
                historyEvents = SharedPrefsAlertStorage.defaultSampleHistory()
            ),
            onNavigate = {},
            onDraftNameChange = {},
            onDraftPhoneChange = {},
            onSaveContact = {},
            onFillSampleContact = {},
            onSosLongPressed = {},
            onCancelCountdown = {},
            onSimulateWatchAlert = {},
            onSimulateErrorAlert = {},
            onCancelActiveAlert = {}
        )
    }
}
