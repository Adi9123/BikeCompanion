package dev.rtrcompanion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Instrument-cluster palette: near-black panels, Apache red for the accent,
 * amber and teal for gauges. Always dark — it reads better on a bike mount in
 * daylight and at night than a white screen.
 */
object Rtr {
    val Bg = Color(0xFF0B0D11)
    val Panel = Color(0xFF14171D)
    val Panel2 = Color(0xFF1C2028)
    val Line = Color(0xFF2A2F39)
    val Red = Color(0xFFE5242E)
    val RedDeep = Color(0xFF3A1216)
    val Amber = Color(0xFFFFB020)
    val Teal = Color(0xFF2EC4B6)
    val Green = Color(0xFF3DD68C)
    val Blue = Color(0xFF4D9DFF)
    val Violet = Color(0xFFA78BFA)
    val Text = Color(0xFFEEF0F3)
    val Muted = Color(0xFF8C93A0)
}

private val scheme = darkColorScheme(
    primary = Rtr.Red,
    onPrimary = Color.White,
    primaryContainer = Rtr.RedDeep,
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Rtr.Amber,
    onSecondary = Color.Black,
    tertiary = Rtr.Teal,
    background = Rtr.Bg,
    onBackground = Rtr.Text,
    surface = Rtr.Bg,
    onSurface = Rtr.Text,
    surfaceVariant = Rtr.Panel2,
    onSurfaceVariant = Rtr.Muted,
    surfaceContainerLowest = Rtr.Bg,
    surfaceContainerLow = Rtr.Panel,
    surfaceContainer = Rtr.Panel,
    surfaceContainerHigh = Rtr.Panel2,
    surfaceContainerHighest = Rtr.Panel,
    outline = Rtr.Line,
    outlineVariant = Rtr.Line,
    error = Color(0xFFFF5A5F),
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val type = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun RtrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
