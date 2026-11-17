package dev.bikecompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.bikecompanion.R

/**
 * Colour roles. Both themes fill the same roles, so components ask for a role
 * and never a hex value. See docs/design/DESIGN_SYSTEM.md#colour.
 */
@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val amber: Color,
    val red: Color,
    val blue: Color,
    val violet: Color,
    val green: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0F1012),
    panel = Color(0xFF18191C),
    panel2 = Color(0xFF222327),
    line = Color(0xFF2C2D32),
    text = Color(0xFFF3F2EC),
    muted = Color(0xFF94938C),
    accent = Color(0xFFCDF545),
    onAccent = Color(0xFF141500),
    accentText = Color(0xFFCDF545),
    amber = Color(0xFFFFB547),
    red = Color(0xFFFF5D4D),
    blue = Color(0xFF7CC4FF),
    violet = Color(0xFFB8A4FF),
    green = Color(0xFF5ED891),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF4F2ED),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFECE9E2),
    line = Color(0xFFDEDAD0),
    text = Color(0xFF151513),
    muted = Color(0xFF68665F),
    accent = Color(0xFFCDF545),
    onAccent = Color(0xFF141500),
    accentText = Color(0xFF4A6400),
    amber = Color(0xFFA35F00),
    red = Color(0xFFCC3524),
    blue = Color(0xFF1E67B0),
    violet = Color(0xFF6447D1),
    green = Color(0xFF1E8A4E),
)

/**
 * The active palette as plain properties, readable anywhere (composables,
 * draw lambdas, map overlays). Backed by snapshot state, so reading it inside
 * composition or drawing re-runs when the theme changes.
 */
object Tone {
    var palette by mutableStateOf(DarkPalette)
        internal set

    val isDark get() = palette.dark
    val Bg get() = palette.bg
    val Panel get() = palette.panel
    val Panel2 get() = palette.panel2
    val Line get() = palette.line
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val AccentText get() = palette.accentText
    val Amber get() = palette.amber
    val Red get() = palette.red
    val Blue get() = palette.blue
    val Violet get() = palette.violet
    val Green get() = palette.green

    /** Data colour for "normal": the signature light on dark, its deep variant on paper. */
    val Teal get() = palette.accentText

    /** Container tint for danger cards. */
    val RedDeep get() = palette.red.copy(alpha = 0.14f)
}

// --------------------------------------------------------------------- type

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Numerals and titles. */
val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Everything you read. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/** Digits that keep their width while they change. */
const val TABULAR = "tnum"

/** Number styles beyond Material's scale. */
object TypeX {
    /** The speed readout. */
    val hero = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 96.sp,
        letterSpacing = (-0.04).em, fontFeatureSettings = TABULAR,
    )

    /** A large value inside a card. */
    val number = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp,
        letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR,
    )

    val numberSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp,
        letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR,
    )
}

private val type = Typography(
    displayLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.005.em),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.005.em),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
)

// -------------------------------------------------------------------- theme

private fun scheme(p: Palette) = if (p.dark) {
    darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.accent.copy(alpha = 0.18f), onPrimaryContainer = p.text,
        secondary = p.amber, onSecondary = Color.Black,
        tertiary = p.blue,
        background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text,
        surfaceVariant = p.panel2, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.panel, surfaceContainer = p.panel,
        surfaceContainerHigh = p.panel2, surfaceContainerHighest = p.panel2,
        outline = p.line, outlineVariant = p.line,
        error = p.red, onError = Color.White, errorContainer = p.red.copy(alpha = 0.16f), onErrorContainer = p.text,
        inverseSurface = p.text, inverseOnSurface = p.bg,
    )
} else {
    lightColorScheme(
        primary = p.accentText, onPrimary = Color.White,
        primaryContainer = p.accent, onPrimaryContainer = p.onAccent,
        secondary = p.amber, onSecondary = Color.White,
        tertiary = p.blue,
        background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text,
        surfaceVariant = p.panel2, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.panel, surfaceContainerLow = p.panel, surfaceContainer = p.panel,
        surfaceContainerHigh = p.panel2, surfaceContainerHighest = p.panel2,
        outline = p.line, outlineVariant = p.line,
        error = p.red, onError = Color.White, errorContainer = p.red.copy(alpha = 0.12f), onErrorContainer = p.text,
        inverseSurface = p.text, inverseOnSurface = p.bg,
    )
}

/** Follows the phone, or the rider's choice in Settings > Appearance. */
@Composable
fun BikeTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    // Set before the children read it, so the first frame is already right.
    if (Tone.palette !== p) Tone.palette = p
    MaterialTheme(colorScheme = scheme(p), typography = type, content = content)
}
