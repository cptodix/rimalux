package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

private val RimaluxDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF042126),
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = MutedCyanSecondary,
    onSecondary = Color(0xFF142124),
    secondaryContainer = MutedCyanContainer,
    onSecondaryContainer = OnMutedCyanContainer,
    tertiary = MutedCyanSecondary,
    onTertiary = Color(0xFF142124),
    tertiaryContainer = MutedCyanContainer,
    onTertiaryContainer = OnMutedCyanContainer,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = ObsidianVoid,
    onBackground = TextPrimaryDark,
    surface = ObsidianVoid,
    onSurface = TextPrimaryDark,
    surfaceDim = ObsidianVoid,
    surfaceBright = ObsidianSurfaceBright,
    surfaceContainerLowest = ObsidianLowest,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = Color.Transparent,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = ObsidianSurfaceLow,
    inversePrimary = DeepCobalt,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    scrim = Color.Black
)

private val RimaluxLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = Color.White,
    primaryContainer = DeepCobaltContainer,
    onPrimaryContainer = Color(0xFF001F25),
    secondary = WarmTerra,
    onSecondary = Color.White,
    secondaryContainer = WarmTerraContainer,
    onSecondaryContainer = Color(0xFF331200),
    background = StudioPaperBg,
    onBackground = TextPrimaryLight,
    surface = StudioPaperBg,
    onSurface = TextPrimaryLight,
    surfaceDim = StudioPaperHigh,
    surfaceBright = StudioPaperLowest,
    surfaceContainerLowest = StudioPaperLowest,
    surfaceContainerLow = StudioPaperLow,
    surfaceContainer = StudioPaperContainer,
    surfaceContainerHigh = StudioPaperHigh,
    surfaceContainerHighest = StudioPaperHighest,
    surfaceVariant = StudioPaperContainer,
    onSurfaceVariant = TextSecondaryLight,
    inverseSurface = ObsidianSurfaceHigh,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = ElectricCyan,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Color.Black
)

internal fun relativeLuminance(color: Color): Float {
    fun linearize(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f)
        return if (c <= 0.04045f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }
    return 0.2126f * linearize(color.red) +
        0.7152f * linearize(color.green) +
        0.0722f * linearize(color.blue)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val l1 = relativeLuminance(first)
    val l2 = relativeLuminance(second)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    val delta = max - min
    val l = (max + min) * 0.5f

    if (delta < 0.0001f) {
        return floatArrayOf(0f, 0f, l)
    }

    val s = delta / (1f - abs(2f * l - 1f)).coerceAtLeast(0.0001f)
    val rawHue = when (max) {
        r -> ((g - b) / delta) % 6f
        g -> ((b - r) / delta) + 2f
        else -> ((r - g) / delta) + 4f
    } * 60f
    val h = if (rawHue < 0f) rawHue + 360f else rawHue
    return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - c * 0.5f

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

/**
 * Synthesizes a color with the given [hue] and [saturation] whose perceptual WCAG
 * relative luminance matches [targetLuminance], guaranteeing readable M3 contrast
 * regardless of whether the preview palette swatch is deep blue, dark crimson, or pale yellow.
 */
private fun colorAtTargetLuminance(
    hue: Float,
    saturation: Float,
    targetLuminance: Float
): Color {
    val target = targetLuminance.coerceIn(0.0005f, 0.96f)
    var low = 0f
    var high = 1f
    var best = hslToColor(hue, saturation, 0.5f)
    repeat(20) {
        val mid = (low + high) * 0.5f
        val candidate = hslToColor(hue, saturation, mid)
        val lum = relativeLuminance(candidate)
        best = candidate
        if (lum < target) {
            low = mid
        } else {
            high = mid
        }
    }
    return best
}

/**
 * Picks the most representative visible accent swatch from the current wallpaper preview palette,
 * avoiding near-black or gray shadow swatches when chromatic strokes exist in the palette.
 */
private fun selectRepresentativeAccentSeed(paletteColors: List<Long>): Color {
    var bestColor = Color(paletteColors.first())
    var bestScore = -1f

    paletteColors.forEachIndexed { index, argb ->
        val candidate = Color(argb)
        val hsl = rgbToHsl(candidate)
        val saturation = hsl[1]
        val lightness = hsl[2]
        val visibilityWeight = 0.30f + 0.70f * (lightness.coerceIn(0f, 0.85f) / 0.85f)
        val orderWeight = when (index) {
            0 -> 1.12f
            1 -> 1.05f
            else -> 1.0f
        }
        val score = saturation * visibilityWeight * orderWeight
        if (score > bestScore) {
            bestScore = score
            bestColor = candidate
        }
    }
    return bestColor
}

private data class AccentTonalRole(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * Transforms the preview palette seed into a crisp, high-legibility M3 Tone-80 accent
 * (`targetLuminance = 0.52f`) so dark palette swatches never become hard to read against
 * OLED pitch-black surfaces and never lose contrast inside filled controls.
 */
private fun buildPrimaryDarkAccentRole(seed: Color): AccentTonalRole {
    val hsl = rgbToHsl(seed)
    val isAchromatic = hsl[1] < 0.06f
    val hue = if (isAchromatic) 0f else hsl[0]
    val accentSat = if (isAchromatic) 0f else hsl[1].coerceIn(0.48f, 0.80f)
    val onAccentSat = if (isAchromatic) 0f else (accentSat * 0.60f).coerceAtMost(0.50f)
    val containerSat = if (isAchromatic) 0f else (accentSat * 0.46f).coerceIn(0.18f, 0.40f)
    val onContainerSat = if (isAchromatic) 0f else (accentSat * 0.65f).coerceAtMost(0.60f)

    return AccentTonalRole(
        accent = colorAtTargetLuminance(hue, accentSat, targetLuminance = 0.52f),
        onAccent = colorAtTargetLuminance(hue, onAccentSat, targetLuminance = 0.014f),
        container = colorAtTargetLuminance(hue, containerSat, targetLuminance = 0.044f),
        onContainer = colorAtTargetLuminance(hue, onContainerSat, targetLuminance = 0.78f)
    )
}

/**
 * Builds a minimal, muted companion role sharing the exact primary hue so selected chips,
 * segmented controls, and tonal buttons stay cohesive and understated rather than multi-colored.
 */
private fun buildMutedSecondaryDarkRole(seed: Color): AccentTonalRole {
    val hsl = rgbToHsl(seed)
    val isAchromatic = hsl[1] < 0.06f
    val hue = if (isAchromatic) 0f else hsl[0]
    val secondarySat = if (isAchromatic) 0f else (hsl[1] * 0.36f).coerceIn(0.16f, 0.30f)
    val containerSat = if (isAchromatic) 0f else (hsl[1] * 0.30f).coerceIn(0.12f, 0.24f)

    return AccentTonalRole(
        accent = colorAtTargetLuminance(hue, secondarySat, targetLuminance = 0.48f),
        onAccent = colorAtTargetLuminance(hue, secondarySat * 0.5f, targetLuminance = 0.014f),
        container = colorAtTargetLuminance(hue, containerSat, targetLuminance = 0.036f),
        onContainer = colorAtTargetLuminance(hue, secondarySat, targetLuminance = 0.82f)
    )
}

fun createDynamicColorScheme(paletteColors: List<Long>?): ColorScheme {
    if (paletteColors.isNullOrEmpty()) return RimaluxDarkColorScheme

    val representativeSeed = selectRepresentativeAccentSeed(paletteColors)
    val seedHsl = rgbToHsl(representativeSeed)
    val baseHue = seedHsl[0]
    val hasChroma = seedHsl[1] >= 0.06f

    val primaryRole = buildPrimaryDarkAccentRole(representativeSeed)
    val mutedCompanionRole = buildMutedSecondaryDarkRole(representativeSeed)

    val inversePrimary = colorAtTargetLuminance(
        hue = baseHue,
        saturation = if (hasChroma) 0.60f else 0f,
        targetLuminance = 0.12f
    )

    // Keep all background & surface containers pure pitch-black / neutral obsidian (zero hue wash)
    return RimaluxDarkColorScheme.copy(
        primary = primaryRole.accent,
        onPrimary = primaryRole.onAccent,
        primaryContainer = primaryRole.container,
        onPrimaryContainer = primaryRole.onContainer,
        secondary = mutedCompanionRole.accent,
        onSecondary = mutedCompanionRole.onAccent,
        secondaryContainer = mutedCompanionRole.container,
        onSecondaryContainer = mutedCompanionRole.onContainer,
        tertiary = mutedCompanionRole.accent,
        onTertiary = mutedCompanionRole.onAccent,
        tertiaryContainer = mutedCompanionRole.container,
        onTertiaryContainer = mutedCompanionRole.onContainer,
        background = ObsidianVoid,
        onBackground = TextPrimaryDark,
        surface = ObsidianVoid,
        onSurface = TextPrimaryDark,
        surfaceDim = ObsidianVoid,
        surfaceBright = ObsidianSurfaceBright,
        surfaceContainerLowest = ObsidianLowest,
        surfaceContainerLow = ObsidianSurfaceLow,
        surfaceContainer = ObsidianSurfaceContainer,
        surfaceContainerHigh = ObsidianSurfaceHigh,
        surfaceContainerHighest = ObsidianSurfaceHighest,
        surfaceVariant = ObsidianSurfaceContainer,
        onSurfaceVariant = TextSecondaryDark,
        surfaceTint = Color.Transparent,
        inverseSurface = TextPrimaryDark,
        inverseOnSurface = ObsidianSurfaceLow,
        inversePrimary = inversePrimary,
        outline = ObsidianOutline,
        outlineVariant = ObsidianOutlineVariant
    )
}

val RimaluxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RimaluxTheme(
    paletteColors: List<Long>? = null,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (!paletteColors.isNullOrEmpty()) {
        createDynamicColorScheme(paletteColors)
    } else if (darkTheme) {
        RimaluxDarkColorScheme
    } else {
        RimaluxLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RimaluxShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    RimaluxTheme(darkTheme = darkTheme, content = content)
}
