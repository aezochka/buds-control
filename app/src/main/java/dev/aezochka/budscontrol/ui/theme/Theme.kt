package dev.aezochka.budscontrol.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Тёплая палитра (rose/plum), а не дефолтный синий MD3.
private val LightColors = lightColorScheme(
    primary = Color(0xFF8F4A5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3B0718),
    secondary = Color(0xFF75565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF221A1B),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF221A1B),
    surfaceVariant = Color(0xFFF3DDE0),
    onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F1),
    surfaceContainer = Color(0xFFFCEAEC),
    surfaceContainerHigh = Color(0xFFF7E4E6),
    surfaceContainerHighest = Color(0xFFF1DFE0),
    outline = Color(0xFF847374),
    outlineVariant = Color(0xFFD6C2C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C1),
    onPrimary = Color(0xFF561C2C),
    primaryContainer = Color(0xFF723343),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFE4BDC3),
    onSecondary = Color(0xFF43292E),
    secondaryContainer = Color(0xFF5B3F44),
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDCC1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF1DFE0),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF1DFE0),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD6C2C4),
    surfaceContainerLowest = Color(0xFF130C0D),
    surfaceContainerLow = Color(0xFF221A1B),
    surfaceContainer = Color(0xFF261E1F),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3D3335),
    outline = Color(0xFF9F8C8E),
    outlineVariant = Color(0xFF524345),
)

@Composable
fun BudsControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = BudsTypography, content = content)
}
