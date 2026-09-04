package dev.aezochka.budscontrol.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.aezochka.budscontrol.data.Accent

private fun w(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

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

/**
 * Тёмная схема с выбираемым акцентом. onPrimary считается из яркости акцента,
 * поэтому подписи на активных плитках читаются при любом цвете.
 */
private fun schemeFor(accent: Accent, customArgb: Long = 0L) = run {
    // Свой цвет перебивает пресет, если задан.
    val primary = if (customArgb != 0L) Color(customArgb) else Color(accent.seed)
    val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF16210A) else Color(0xFFFFFFFF)
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.28f).compositeOverDark(),
        onPrimaryContainer = primary,
        secondary = Color(0xFFFFB0C8), onSecondary = Color(0xFF5C1133),
        tertiary = Color(0xFFF2BE8C), onTertiary = Color(0xFF4A2800),
        background = Color(0xFF0C0C0B), onBackground = Color(0xFFEAEAE5),
        surface = Color(0xFF0C0C0B), onSurface = Color(0xFFEAEAE5),
        surfaceVariant = Color(0xFF30312B), onSurfaceVariant = Color(0xFFC8C9C0),
        surfaceContainerLowest = Color(0xFF080807),
        surfaceContainerLow = Color(0xFF151614),
        surfaceContainer = Color(0xFF1B1C19),
        surfaceContainerHigh = Color(0xFF242520),
        surfaceContainerHighest = Color(0xFF30312B),
        outline = Color(0xFF93948A), outlineVariant = Color(0xFF474840),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    )
}

private fun Color.compositeOverDark(): Color {
    val bg = Color(0xFF0C0C0B)
    return Color(
        red = red * alpha + bg.red * (1 - alpha),
        green = green * alpha + bg.green * (1 - alpha),
        blue = blue * alpha + bg.blue * (1 - alpha),
    )
}

@Composable
fun BudsControlTheme(
    accentKey: String = "lime",
    customAccent: Long = 0L,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(ctx)
        else -> schemeFor(Accent.from(accentKey), customAccent)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = BudsType,
        content = content,
    )
}
