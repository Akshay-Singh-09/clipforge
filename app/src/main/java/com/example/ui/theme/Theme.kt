package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimaryDark,
    onPrimary = Color(0xFF210407),
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = CobaltSecondaryDark,
    onSecondary = Color(0xFF061B44),
    secondaryContainer = CobaltContainerDark,
    onSecondaryContainer = Color(0xFFD8E8FF),
    tertiary = EmeraldTertiaryDark,
    onTertiary = Color(0xFF032318),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFB7FBE0),
    background = StudioNightBg,
    onBackground = StudioNightBone,
    surface = StudioNightCard,
    onSurface = StudioNightBone,
    surfaceVariant = StudioNightSurfaceVariant,
    onSurfaceVariant = StudioNightPewter,
    surfaceContainerLowest = StudioNightBg,
    surfaceContainerLow = Color(0xFF0E1420),
    surfaceContainer = StudioNightCard,
    surfaceContainerHigh = Color(0xFF172032),
    surfaceContainerHighest = Color(0xFF1E2940),
    outline = StudioNightOutline,
    outlineVariant = Color(0xFF1D283D),
    error = CrimsonErrorDark,
    onError = Color(0xFF2C0606),
    errorContainer = CrimsonErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD9)
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = Color(0xFF6B0F18),
    secondary = CobaltSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainerLight,
    onSecondaryContainer = Color(0xFF102A6B),
    tertiary = EmeraldTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF044430),
    background = StudioDayBg,
    onBackground = StudioDayInk,
    surface = StudioDayCard,
    onSurface = StudioDayInk,
    surfaceVariant = StudioDaySurfaceVariant,
    onSurfaceVariant = StudioDaySlate,
    surfaceContainerLowest = StudioDayCard,
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = StudioDayBg,
    surfaceContainerHigh = Color(0xFFEDF1F8),
    surfaceContainerHighest = Color(0xFFE2E8F4),
    outline = StudioDayOutline,
    outlineVariant = Color(0xFFE2E8F2),
    error = CrimsonErrorLight,
    onError = Color.White,
    errorContainer = CrimsonErrorContainerLight,
    onErrorContainer = Color(0xFF650D0D)
)

val ClipForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ClipForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipForgeTypography,
        shapes = ClipForgeShapes,
        content = content
    )
}
