package pl.vistula.checker.ui.compose

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Design tokens of the prototype (one main colour = text, one accent used only for "next class").
 * Everything that is not part of Material's colour scheme lives here.
 */
@Immutable
data class VistulaTokens(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val quiet: Color,
    val line: Color,
    val accent: Color,
    val accentMark: Color,
    val accentSoft: Color,
    val paper: Color,
    val ink: Color,
    val fill: Color,
    val fillSoft: Color,
    val navBg: Color,
    val scrim: Color
)

internal val DarkTokens = VistulaTokens(
    isDark = true,
    bg = Color(0xFF0C0D0E), surface = Color(0xFF151618), surface2 = Color(0xFF1B1C1F),
    text = Color(0xFFEEF1F3), muted = Color(0xFF8B9297), quiet = Color(0xFF82898F),
    line = Color(0xFFFFFFFF).copy(alpha = 0.07f),
    accent = Color(0xFF1FC6EF), accentMark = Color(0xFF1FC6EF), accentSoft = Color(0xFF1FC6EF).copy(alpha = 0.10f),
    paper = Color(0xFFF4F5F6), ink = Color(0xFF0C0D0E),
    fill = Color(0xFFFFFFFF).copy(alpha = 0.10f), fillSoft = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    navBg = Color(0xFF141516).copy(alpha = 0.92f), scrim = Color(0xFF000000).copy(alpha = 0.6f)
)

internal val LightTokens = VistulaTokens(
    isDark = false,
    bg = Color(0xFFEEF0F2), surface = Color(0xFFFBFBFC), surface2 = Color(0xFFFFFFFF),
    text = Color(0xFF1B1E21), muted = Color(0xFF5B6268), quiet = Color(0xFF626A70),
    line = Color(0xFF14181C).copy(alpha = 0.08f),
    accent = Color(0xFF07708F), accentMark = Color(0xFF12A7D4), accentSoft = Color(0xFF07708F).copy(alpha = 0.09f),
    paper = Color(0xFF1B1E21), ink = Color(0xFFFFFFFF),
    fill = Color(0xFF14181C).copy(alpha = 0.08f), fillSoft = Color(0xFF14181C).copy(alpha = 0.05f),
    navBg = Color(0xFFFBFBFC).copy(alpha = 0.94f), scrim = Color(0xFF14181C).copy(alpha = 0.28f)
)

private val OledTokens = DarkTokens.copy(bg = Color(0xFF000000), navBg = Color(0xFF0A0B0C).copy(alpha = 0.94f))

val LocalVistulaTokens = staticCompositionLocalOf { DarkTokens }

/** Access to the prototype tokens from any composable inside [VistulaTheme]. */
object VistulaDesign {
    val tokens: VistulaTokens
        @Composable get() = LocalVistulaTokens.current
}

val LightColors = lightColorScheme(
    primary = Color(0xFF1B1E21),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE0E2),
    onPrimaryContainer = Color(0xFF1B1E21),
    secondary = Color(0xFF5B6268),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE0E2),
    onSecondaryContainer = Color(0xFF1B1E21),
    tertiary = Color(0xFF07708F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEBF0),
    onTertiaryContainer = Color(0xFF07708F),
    background = Color(0xFFEEF0F2),
    onBackground = Color(0xFF1B1E21),
    surface = Color(0xFFFBFBFC),
    onSurface = Color(0xFF1B1E21),
    surfaceVariant = Color(0xFFE6E8EA),
    onSurfaceVariant = Color(0xFF5B6268),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFC),
    surfaceContainer = Color(0xFFFBFBFC),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFC9CED1),
    outlineVariant = Color(0xFFE2E5E7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F5F6),
    onPrimary = Color(0xFF0C0D0E),
    primaryContainer = Color(0xFF26282A),
    onPrimaryContainer = Color(0xFFEEF1F3),
    secondary = Color(0xFF8B9297),
    onSecondary = Color(0xFF0C0D0E),
    secondaryContainer = Color(0xFF26282A),
    onSecondaryContainer = Color(0xFFEEF1F3),
    tertiary = Color(0xFF1FC6EF),
    onTertiary = Color(0xFF04222B),
    tertiaryContainer = Color(0xFF10262C),
    onTertiaryContainer = Color(0xFF1FC6EF),
    background = Color(0xFF0C0D0E),
    onBackground = Color(0xFFEEF1F3),
    surface = Color(0xFF151618),
    onSurface = Color(0xFFEEF1F3),
    surfaceVariant = Color(0xFF1B1C1F),
    onSurfaceVariant = Color(0xFF8B9297),
    surfaceContainerLowest = Color(0xFF0C0D0E),
    surfaceContainerLow = Color(0xFF151618),
    surfaceContainer = Color(0xFF151618),
    surfaceContainerHigh = Color(0xFF1B1C1F),
    surfaceContainerHighest = Color(0xFF1B1C1F),
    outline = Color(0xFF3A3D40),
    outlineVariant = Color(0xFF232527),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val OledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF151618),
    surfaceVariant = Color(0xFF1B1C1F)
)

object VistulaContrast {
    private fun channelLuminance(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(color: Color): Double =
        0.2126 * channelLuminance(color.red) +
            0.7152 * channelLuminance(color.green) +
            0.0722 * channelLuminance(color.blue)

    fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}

val VistulaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val VistulaTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 37.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.16.em),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun VistulaTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        "light" -> LightColors
        "dark" -> DarkColors
        "oled" -> OledColors
        else -> if (systemDark) DarkColors else LightColors
    }
    val tokens = when (themeMode) {
        "light" -> LightTokens
        "dark" -> DarkTokens
        "oled" -> OledTokens
        else -> if (systemDark) DarkTokens else LightTokens
    }
    CompositionLocalProvider(LocalVistulaTokens provides tokens) {
        MaterialTheme(
            colorScheme = colors,
            shapes = VistulaShapes,
            typography = VistulaTypography,
            content = content
        )
    }
}
