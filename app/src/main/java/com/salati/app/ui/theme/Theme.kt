package com.salati.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salati.app.domain.ThemeMode

// ---------------------------------------------------------------------------
// "Golden hour" palette (inspired by the Deen Muslim Dribbble shot):
// warm off-white background, beige cards, brown brand colour, amber glow.
// ---------------------------------------------------------------------------

private val Brown = Color(0xFF7A4210)
private val BrownDeep = Color(0xFF5C300A)
private val Ink = Color(0xFF2B1D12)
private val Taupe = Color(0xFF8A7B6D)
private val Amber = Color(0xFFF5A83B)
private val AmberDeep = Color(0xFFEE7B30)
private val AmberLight = Color(0xFFFCE3B0)
private val Cream = Color(0xFFFFFBF6)
private val Beige = Color(0xFFF8F0E4)
private val BeigeDark = Color(0xFFF1E4D2)

private val Light = lightColorScheme(
    primary = Brown,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9C4),
    onPrimaryContainer = Ink,
    inversePrimary = Color(0xFFF5B865),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3B8),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFB4610F),
    onTertiary = Color.White,
    tertiaryContainer = AmberLight,
    onTertiaryContainer = Color(0xFF3A2200),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Beige,
    onSurfaceVariant = Taupe,
    surfaceTint = Color.Transparent,          // no grey tint over cards
    inverseSurface = Color(0xFF3A2C20),
    inverseOnSurface = Color(0xFFFBEFE3),
    outline = Color(0xFFCBB9A4),
    outlineVariant = Color(0xFFE8DCCB),
    scrim = Color.Black,
    surfaceBright = Cream,
    surfaceDim = Color(0xFFEBDFCF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFEF9F3),
    surfaceContainer = Color(0xFFFDF8F1),
    surfaceContainerHigh = Color(0xFFFBF3E8),
    surfaceContainerHighest = Beige,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF5B865),
    onPrimary = Color(0xFF452400),
    primaryContainer = Color(0xFF5C300A),
    onPrimaryContainer = Color(0xFFFFE9C4),
    inversePrimary = Brown,
    secondary = Color(0xFFE0C1A0),
    onSecondary = Color(0xFF3F2A14),
    secondaryContainer = Color(0xFF4A3622),
    onSecondaryContainer = Color(0xFFFCE3C8),
    tertiary = Amber,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF6B4100),
    onTertiaryContainer = AmberLight,
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF930006),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF17110B),
    onBackground = Color(0xFFF1E4D6),
    surface = Color(0xFF17110B),
    onSurface = Color(0xFFF1E4D6),
    surfaceVariant = Color(0xFF2A2018),
    onSurfaceVariant = Color(0xFFC4B3A1),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF1E4D6),
    inverseOnSurface = Color(0xFF2B1D12),
    outline = Color(0xFF8E7C6A),
    outlineVariant = Color(0xFF45372A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3B2F25),
    surfaceDim = Color(0xFF17110B),
    surfaceContainerLowest = Color(0xFF110C07),
    surfaceContainerLow = Color(0xFF1F1710),
    surfaceContainer = Color(0xFF241B13),
    surfaceContainerHigh = Color(0xFF2F241A),
    surfaceContainerHighest = Color(0xFF3A2E23),
)

// ---------------------------------------------------------------------------
// Typography: rounded, bold numerals for the clock; generous line height so
// Arabic diacritics are never clipped.
// ---------------------------------------------------------------------------

private val Base = FontFamily.Default

private val SalatiTypography = Typography(
    displayLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp),
    displaySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

// Soft, generous corners like the reference (tiles ~18 dp, containers ~24 dp).
private val SalatiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Status colours: calm tones from the reference's accent set (no harsh red). */
object StatusColors {
    val onTime = Color(0xFF3F8F7F)
    val late = Color(0xFFF5A83B)
    val missed = Color(0xFF9B7BE0)
    val auto = Color(0xFFC9A3EB)
    val unrecorded = Color(0xFFB5A99C)
}

/** Brushes for the "golden hour" hero areas; use via SalatiTheme.brushes. */
@Immutable
data class SalatiBrushes(
    val hero: Brush,
    val card: Brush,
    val glow: Color,
    val mosque: Color,
)

private val LightBrushes = SalatiBrushes(
    hero = Brush.verticalGradient(
        0f to Color(0xFFFFF1DC),
        0.55f to Color(0xFFF8B85A),
        1f to Color(0xFFFCE3B0),
    ),
    card = Brush.linearGradient(listOf(Color(0xFFFFD79A), AmberLight, Beige)),
    glow = Color.White.copy(alpha = 0.55f),
    mosque = Color.White.copy(alpha = 0.6f),
)

private val DarkBrushes = SalatiBrushes(
    hero = Brush.verticalGradient(listOf(Color(0xFF6B3A0C), Color(0xFF3A230F), Color(0xFF17110B))),
    card = Brush.linearGradient(listOf(Color(0xFF5C300A), Color(0xFF3A2A1C), Color(0xFF241B13))),
    glow = Amber.copy(alpha = 0.18f),
    mosque = Amber.copy(alpha = 0.16f),
)

private val LocalBrushes = staticCompositionLocalOf { LightBrushes }

object SalatiTheme {
    val brushes: SalatiBrushes
        @Composable @ReadOnlyComposable get() = LocalBrushes.current
}

@Composable
fun SalatiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalBrushes provides if (dark) DarkBrushes else LightBrushes) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = SalatiTypography,
            shapes = SalatiShapes,
            content = content,
        )
    }
}
