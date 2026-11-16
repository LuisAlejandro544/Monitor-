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
 * Paleta cromática técnica inspirada en administradores de tareas y monitores de sistemas.
 * Proporciona alto contraste y colores semánticos claros para carga de RAM y estados congelados.
 */
object MemScopeColors {
    val CyanTelemetry = Color(0xFF22D3EE)
    val CyanDeep = Color(0xFF0891B2)
    val EmeraldHealthy = Color(0xFF34D399)
    val AmberWarning = Color(0xFFFBBF24)
    val CrimsonPeak = Color(0xFFF87171)
    val IndigoFrozen = Color(0xFF818CF8)
    val SlateSurfaceDark = Color(0xFF0E1520)
    val SlateCardDark = Color(0xFF16202F)
    val SlateElevatedDark = Color(0xFF1E2B3E)
    val SlateBorderDark = Color(0xFF2B3B52)
}

private val DarkColorScheme = darkColorScheme(
    primary = MemScopeColors.CyanTelemetry,
    onPrimary = Color(0xFF052028),
    primaryContainer = Color(0xFF0F3947),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = MemScopeColors.IndigoFrozen,
    onSecondary = Color(0xFF13163B),
    secondaryContainer = Color(0xFF252B5C),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = MemScopeColors.EmeraldHealthy,
    onTertiary = Color(0xFF04291E),
    background = MemScopeColors.SlateSurfaceDark,
    onBackground = Color(0xFFE6EDF6),
    surface = MemScopeColors.SlateCardDark,
    onSurface = Color(0xFFE6EDF6),
    surfaceVariant = MemScopeColors.SlateElevatedDark,
    onSurfaceVariant = Color(0xFF9FB0C7),
    outline = MemScopeColors.SlateBorderDark,
    error = MemScopeColors.CrimsonPeak,
    onError = Color(0xFF3D0A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = MemScopeColors.CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF083344),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
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
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
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
        lineHeight = 12.sp,
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
