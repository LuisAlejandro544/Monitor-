package com.memscope.taskmonitor.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Paleta cromática técnica inspirada en consolas profesionales de telemetría y
 * administradores de tareas de escritorio (Obsidian Telemetry).
 * Alto contraste, bordes definidos y acentos técnicos para carga de RAM, picos y congelación.
 */
object MemScopeColors {
    val CyanTelemetry = Color(0xFF00E5FF)
    val CyanDeep = Color(0xFF0284C7)
    val EmeraldHealthy = Color(0xFF10D98B)
    val AmberWarning = Color(0xFFFFB020)
    val CrimsonPeak = Color(0xFFFF4757)
    val IndigoFrozen = Color(0xFF3B82F6)
    val SlateSurfaceDark = Color(0xFF070B11)
    val SlateCardDark = Color(0xFF0E141E)
    val SlateElevatedDark = Color(0xFF151E2C)
    val SlateBorderDark = Color(0xFF223044)
    val GraphCanvasDark = Color(0xFF05080D)
}

private val DarkColorScheme = darkColorScheme(
    primary = MemScopeColors.CyanTelemetry,
    onPrimary = Color(0xFF021A20),
    primaryContainer = Color(0xFF093542),
    onPrimaryContainer = Color(0xFFA7F5FF),
    secondary = MemScopeColors.IndigoFrozen,
    onSecondary = Color(0xFF071633),
    secondaryContainer = Color(0xFF162C54),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = MemScopeColors.EmeraldHealthy,
    onTertiary = Color(0xFF022619),
    background = MemScopeColors.SlateSurfaceDark,
    onBackground = Color(0xFFEDF2F7),
    surface = MemScopeColors.SlateCardDark,
    onSurface = Color(0xFFEDF2F7),
    surfaceVariant = MemScopeColors.SlateElevatedDark,
    onSurfaceVariant = Color(0xFF94A6BE),
    outline = MemScopeColors.SlateBorderDark,
    error = MemScopeColors.CrimsonPeak,
    onError = Color(0xFF3A060C)
)

private val LightColorScheme = lightColorScheme(
    primary = MemScopeColors.CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F0FF),
    onPrimaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE6ECF4),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val MemScopeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun MemScopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MemScopeTypography,
        content = content
    )
}
