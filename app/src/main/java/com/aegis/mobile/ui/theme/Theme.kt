package com.aegis.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cyber / Tactical Terminal Color Palette
val TerminalBg = Color(0xFF0D0E11)
val TerminalCardBg = Color(0xFF15171E)
val TerminalElevated = Color(0xFF1C1F2A)
val PhosphorCyan = Color(0xFF00E5FF)
val PhosphorGreen = Color(0xFF00E676)
val PhosphorAmber = Color(0xFFFFD600)
val PhosphorAlertRed = Color(0xFFFF3D71)
val BorderTactical = Color(0xFF202636)
val TextPrimary = Color(0xFFE0E6ED)
val TextMuted = Color(0xFF8892B0)

private val TacticalDarkColorScheme = darkColorScheme(
    primary = PhosphorCyan,
    onPrimary = Color(0xFF0D0E11),
    primaryContainer = Color(0xFF0A2B38),
    onPrimaryContainer = PhosphorCyan,
    background = TerminalBg,
    onBackground = TextPrimary,
    surface = TerminalCardBg,
    onSurface = TextPrimary,
    surfaceVariant = TerminalElevated,
    onSurfaceVariant = TextMuted,
    outline = BorderTactical,
    error = PhosphorAlertRed,
    onError = Color.White
)

val TacticalTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = PhosphorCyan
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = TextMuted
    )
)

@Composable
fun CrisisNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = TacticalTypography,
        content = content
    )
}
