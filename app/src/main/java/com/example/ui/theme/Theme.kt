package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VectraDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002025),
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = SolarLime,
    onSecondary = Color(0xFF1A2400),
    secondaryContainer = LimeContainer,
    onSecondaryContainer = OnLimeContainer,
    tertiary = HyperCoral,
    onTertiary = Color.White,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = ObsidianVoid,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1F2536)
)

private val VectraLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = Color.White,
    primaryContainer = DeepCobaltContainer,
    onPrimaryContainer = Color(0xFF001F24),
    secondary = WarmTerra,
    onSecondary = Color.White,
    background = StudioPaperBg,
    onBackground = TextPrimaryLight,
    surface = StudioPaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioPaperContainer,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD2E0)
)

val VectraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun VectraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VectraDarkColorScheme else VectraLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VectraShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    VectraTheme(darkTheme = darkTheme, content = content)
}
