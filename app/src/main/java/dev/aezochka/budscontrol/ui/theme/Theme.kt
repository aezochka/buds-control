package dev.aezochka.budscontrol.ui.theme

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

// Палитра ровно как в согласованном макете: лаймовый акцент на почти чёрном.
val Lime = Color(0xFFB8E86B)
val OnLime = Color(0xFF1F3000)
val LimeContainer = Color(0xFF395200)
val OnLimeContainer = Color(0xFFD3FF87)
val Rose = Color(0xFFFFB0C8)
val OnRose = Color(0xFF5C1133)
val Surface = Color(0xFF0C0C0B)
val OnSurface = Color(0xFFEAEAE5)
val ScLow = Color(0xFF151614)
val Sc = Color(0xFF1B1C19)
val ScHigh = Color(0xFF242520)
val ScHighest = Color(0xFF30312B)
val OnSurfaceVariant = Color(0xFFC8C9C0)
val Outline = Color(0xFF93948A)
val OutlineVariant = Color(0xFF474840)

private val Dark = darkColorScheme(
    primary = Lime, onPrimary = OnLime,
    primaryContainer = LimeContainer, onPrimaryContainer = OnLimeContainer,
    secondary = Rose, onSecondary = OnRose,
    secondaryContainer = Color(0xFF57404A), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFF2BE8C), onTertiary = Color(0xFF4A2800),
    background = Surface, onBackground = OnSurface,
    surface = Surface, onSurface = OnSurface,
    surfaceVariant = ScHighest, onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF080807),
    surfaceContainerLow = ScLow,
    surfaceContainer = Sc,
    surfaceContainerHigh = ScHigh,
    surfaceContainerHighest = ScHighest,
    outline = Outline, outlineVariant = OutlineVariant,
)

// Светлая нужна только чтобы система не подсунула дефолт; приложение тёмное.
private val Light = lightColorScheme(primary = Color(0xFF4C6600), onPrimary = Color.White)

private fun w(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

// Expressive-шкала: акцент на весе, а не только на кегле.
val BudsType = Typography(
    displayLarge = w(46, 50, FontWeight.Bold, -0.7),
    displayMedium = w(40, 44, FontWeight.Bold, -0.6),
    headlineLarge = w(32, 38, FontWeight.Bold, -0.4),
    headlineSmall = w(26, 32, FontWeight.SemiBold),
    titleLarge = w(22, 28, FontWeight.SemiBold),
    titleMedium = w(17, 23, FontWeight.SemiBold, 0.1),
    titleSmall = w(15, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = w(16, 22, FontWeight.Normal, 0.2),
    bodyMedium = w(15, 21, FontWeight.Normal, 0.2),
    bodySmall = w(13, 18, FontWeight.Normal, 0.3),
    labelLarge = w(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = w(12, 16, FontWeight.SemiBold, 0.5),
    labelSmall = w(11, 15, FontWeight.Bold, 1.1),
)

@Composable
fun BudsControlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Dark, typography = BudsType, content = content)
}
