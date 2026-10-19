package pl.vistula.checker.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val LightColors = lightColorScheme(
    primary = Color(0xFF17634C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F2EB),
    onPrimaryContainer = Color(0xFF153C30),
    secondary = Color(0xFF545C68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEFF3),
    onSecondaryContainer = Color(0xFF20242B),
    tertiary = Color(0xFF875000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF4E2600),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF20242B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF20242B),
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF545C68),
    outline = Color(0xFFDFE3E8),
    outlineVariant = Color(0xFFE4E7EC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF86D8B5),
    onPrimary = Color(0xFF101F17),
    primaryContainer = Color(0xFF17382C),
    onPrimaryContainer = Color(0xFFB5F2D6),
    secondary = Color(0xFFB3BAC5),
    onSecondary = Color(0xFF20252C),
    secondaryContainer = Color(0xFF22272F),
    onSecondaryContainer = Color(0xFFF0F2F5),
    tertiary = Color(0xFFF3C477),
    onTertiary = Color(0xFF3E2400),
    tertiaryContainer = Color(0xFF583400),
    onTertiaryContainer = Color(0xFFFFDDAE),
    background = Color(0xFF0B0E12),
    onBackground = Color(0xFFF0F2F5),
    surface = Color(0xFF11151A),
    onSurface = Color(0xFFF0F2F5),
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = Color(0xFFB3BAC5),
    outline = Color(0xFF303640),
    outlineVariant = Color(0xFF292F38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val OledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0D10),
    surfaceVariant = Color(0xFF14181E)
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
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val VistulaTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )
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
    MaterialTheme(
        colorScheme = colors,
        shapes = VistulaShapes,
        typography = VistulaTypography,
        content = content
    )
}
