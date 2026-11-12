package dev.rtrcompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFC62828)
private val RedLight = Color(0xFFFF6659)

@Composable
fun RtrTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = RedLight, secondary = RedLight, tertiary = Color(0xFFFFB300))
    } else {
        lightColorScheme(primary = Red, secondary = Red, tertiary = Color(0xFFB26A00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
