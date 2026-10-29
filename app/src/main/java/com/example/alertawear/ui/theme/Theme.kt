package com.example.alertawear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AlertaBackground = Color(0xFF101522)
val AlertaSurface = Color(0xFF1C2534)
val AlertaActiveSurface = Color(0xFF44282D)
val AlertaCoral = Color(0xFFFF6C68)
val AlertaTextPrimary = Color(0xFFF6F7FB)
val AlertaTextSecondary = Color(0xFFAAB7C9)
val AlertaStatusGreen = Color(0xFF81D6B0)
val AlertaStatusAmber = Color(0xFFF7D594)
val AlertaStatusError = Color(0xFFFF8A80)

private val AlertaDarkColorScheme = darkColorScheme(
    primary = AlertaCoral,
    onPrimary = AlertaBackground,
    secondary = AlertaStatusAmber,
    onSecondary = AlertaBackground,
    background = AlertaBackground,
    onBackground = AlertaTextPrimary,
    surface = AlertaSurface,
    onSurface = AlertaTextPrimary,
    surfaceVariant = AlertaActiveSurface,
    onSurfaceVariant = AlertaTextSecondary
)

@Composable
fun AlertaWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlertaDarkColorScheme,
        content = content
    )
}
