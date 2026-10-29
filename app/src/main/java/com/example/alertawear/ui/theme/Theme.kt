package com.example.alertawear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Constantes del tema oscuro original conservadas por compatibilidad
val AlertaBackground = Color(0xFF101522)
val AlertaSurface = Color(0xFF1C2534)
val AlertaActiveSurface = Color(0xFF44282D)
val AlertaCoral = Color(0xFFD7474B)
val AlertaTextPrimary = Color(0xFFF6F7FB)
val AlertaTextSecondary = Color(0xFFAAB7C9)
val AlertaStatusGreen = Color(0xFF81D6B0)
val AlertaStatusAmber = Color(0xFFF7D594)
val AlertaStatusError = Color(0xFFFF8A80)

@Immutable
data class AlertaColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
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
    val warningSoft: Color,
    val navGlassSurface: Color,
    val navGlassBorder: Color,
    val navShadowColor: Color
)

val LightAlertaColorPalette = AlertaColorPalette(
    isDark = false,
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
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
    warningSoft = Color(0xFFFBF3E3),
    // Superficie blanca casi opaca para la navbar flotante tipo cristal sin desenfocar iconos ni texto
    navGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.93f),
    navGlassBorder = Color(0xFFE6E8EE),
    navShadowColor = Color(0x241F2940)
)

val DarkAlertaColorPalette = AlertaColorPalette(
    isDark = true,
    background = Color(0xFF101522),
    surface = Color(0xFF1C2534),
    surfaceMuted = Color(0xFF232E40),
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
    warningSoft = Color(0xFF372C1A),
    navGlassSurface = Color(0xFF1C2534).copy(alpha = 0.94f),
    navGlassBorder = Color(0xFF2E3B52),
    navShadowColor = Color(0x66000000)
)

val LocalAlertaColors = staticCompositionLocalOf { LightAlertaColorPalette }

object AlertaTheme {
    val colors: AlertaColorPalette
        @Composable
        get() = LocalAlertaColors.current
}

private val AlertaLightMaterialScheme = lightColorScheme(
    primary = LightAlertaColorPalette.primary,
    onPrimary = LightAlertaColorPalette.onPrimary,
    secondary = LightAlertaColorPalette.info,
    onSecondary = Color.White,
    background = LightAlertaColorPalette.background,
    onBackground = LightAlertaColorPalette.textPrimary,
    surface = LightAlertaColorPalette.surface,
    onSurface = LightAlertaColorPalette.textPrimary,
    surfaceVariant = LightAlertaColorPalette.primarySoft,
    onSurfaceVariant = LightAlertaColorPalette.textMuted,
    outline = LightAlertaColorPalette.border
)

private val AlertaDarkMaterialScheme = darkColorScheme(
    primary = DarkAlertaColorPalette.primary,
    onPrimary = DarkAlertaColorPalette.onPrimary,
    secondary = DarkAlertaColorPalette.warning,
    onSecondary = DarkAlertaColorPalette.background,
    background = DarkAlertaColorPalette.background,
    onBackground = DarkAlertaColorPalette.textPrimary,
    surface = DarkAlertaColorPalette.surface,
    onSurface = DarkAlertaColorPalette.textPrimary,
    surfaceVariant = DarkAlertaColorPalette.primarySoft,
    onSurfaceVariant = DarkAlertaColorPalette.textMuted,
    outline = DarkAlertaColorPalette.border
)

@Composable
fun AlertaWearTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkAlertaColorPalette else LightAlertaColorPalette
    val materialScheme = if (darkTheme) AlertaDarkMaterialScheme else AlertaLightMaterialScheme

    CompositionLocalProvider(LocalAlertaColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content
        )
    }
}
