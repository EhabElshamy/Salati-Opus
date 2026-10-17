package com.salati.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.salati.app.domain.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE7),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4F635F),
    secondaryContainer = Color(0xFFD2E8E3),
    tertiary = Color(0xFF8A6A2F),
    tertiaryContainer = Color(0xFFF6E2BD),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDDE5E3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3CC),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFCDEBE7),
    secondary = Color(0xFFB6CCC7),
    secondaryContainer = Color(0xFF374B47),
    tertiary = Color(0xFFE2C38A),
    tertiaryContainer = Color(0xFF5B4413),
    background = Color(0xFF101514),
    surface = Color(0xFF101514),
    surfaceVariant = Color(0xFF3F4947),
)

/** Status colours: calm, non-alarming tones (no red "failure" colour). */
object StatusColors {
    val onTime = Color(0xFF2E8B57)
    val late = Color(0xFFC9A227)
    val missed = Color(0xFF8E7CC3)
    val auto = Color(0xFFB0A8D0)
    val unrecorded = Color(0xFF9E9E9E)
}

@Composable
fun SalatiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
