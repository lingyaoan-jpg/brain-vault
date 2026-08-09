package app.brain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SeedPrimary,
    onPrimary = SeedOnPrimary,
    primaryContainer = SeedPrimaryContainer,
    onPrimaryContainer = SeedOnPrimaryContainer,
    secondary = SeedSecondary,
    onSecondary = SeedOnSecondary,
    secondaryContainer = SeedPrimaryContainer,
    onSecondaryContainer = SeedOnPrimaryContainer,
    background = SeedBackground,
    surface = SeedSurface,
    surfaceVariant = SeedSurfaceVariant,
    onSurface = SeedOnSurface,
    onSurfaceVariant = SeedOnSurfaceVariant,
    outline = SeedOutline,
    outlineVariant = SeedOutline,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAFE),
    surfaceContainer = Color(0xFFF8F5FC),
    surfaceContainerHigh = Color(0xFFF4F0F9),
    surfaceContainerHighest = Color(0xFFEFEAF5),
)

private val DarkColorScheme = darkColorScheme(
    primary = SeedPrimaryContainer,
    onPrimary = SeedOnPrimaryContainer,
    primaryContainer = SeedPrimary,
    onPrimaryContainer = Color(0xFFEFE6FA),
    secondaryContainer = SeedPrimary,
    onSecondaryContainer = Color(0xFFEFE6FA),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF262230),
    onSurface = Color(0xFFE4E0EA),
    onSurfaceVariant = Color(0xFFB9B3C4),
    outline = Color(0xFF3C3646),
    outlineVariant = Color(0xFF2C2736),
    surfaceContainerLowest = Color(0xFF0D0F0D),
    surfaceContainerLow = Color(0xFF1C1920),
    surfaceContainer = Color(0xFF211D26),
    surfaceContainerHigh = Color(0xFF2B2634),
    surfaceContainerHighest = Color(0xFF363040),
)

@Composable
fun BrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
