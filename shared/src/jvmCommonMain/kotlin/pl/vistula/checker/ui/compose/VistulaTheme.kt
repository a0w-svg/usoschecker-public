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
    primary = Color(0xFF08768A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F1F4),
    onPrimaryContainer = Color(0xFF123D46),
    secondary = Color(0xFF485156),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E8E9),
    onSecondaryContainer = Color(0xFF202326),
    tertiary = Color(0xFF875000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF4E2600),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF202326),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202326),
    surfaceVariant = Color(0xFFF0F2F3),
    onSurfaceVariant = Color(0xFF485156),
    outline = Color(0xFFE1E4E5),
    outlineVariant = Color(0xFFE6E9EA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF5AC4D8),
    onPrimary = Color(0xFF09252B),
    primaryContainer = Color(0xFF102B31),
    onPrimaryContainer = Color(0xFFB7EDF6),
    secondary = Color(0xFFB7BEC2),
    onSecondary = Color(0xFF202325),
    secondaryContainer = Color(0xFF2B2E30),
    onSecondaryContainer = Color(0xFFEDEFF0),
    tertiary = Color(0xFFF3C477),
    onTertiary = Color(0xFF3E2400),
    tertiaryContainer = Color(0xFF583400),
    onTertiaryContainer = Color(0xFFFFDDAE),
    background = Color(0xFF0B0D0E),
    onBackground = Color(0xFFEDEFF0),
    surface = Color(0xFF151718),
    onSurface = Color(0xFFEDEFF0),
    surfaceVariant = Color(0xFF1B1E20),
    onSurfaceVariant = Color(0xFFB7BEC2),
    outline = Color(0xFF242729),
    outlineVariant = Color(0xFF25282A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val OledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF101213),
    surfaceVariant = Color(0xFF1B1E20)
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
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp
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
