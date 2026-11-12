package teikengstudio.xmpro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/** Mirrors AppAppearance.defaultSeedColor from the previous Flutter theme. */
val DefaultSeedColor = Color(0xFFE57373)

/** Seed presets offered in 外观；the first entry is the historical default. */
val SeedPresets: List<Pair<String, Color>> = listOf(
    "珊瑚" to Color(0xFFE57373),
    "樱粉" to Color(0xFFF06292),
    "琥珀" to Color(0xFFFB8C00),
    "森绿" to Color(0xFF43A047),
    "青瓷" to Color(0xFF26A69A),
    "静谧蓝" to Color(0xFF42A5F5),
    "紫罗兰" to Color(0xFF9575CD),
)

/**
 * Builds a Material 3 style scheme from a single seed colour.
 *
 * Compose Material3 has no `ColorScheme.fromSeed`, so the tonal roles are
 * derived in HSL space: the seed keeps its hue and chroma, while lightness is
 * pinned per role. That reproduces the tonal-palette look closely enough for
 * hand-picked presets without shipping a 50-swatch table.
 */
fun seededScheme(seed: Color, dark: Boolean): ColorScheme {
    val h = seed.hue()
    val s = seed.saturation().coerceIn(0.25f, 0.9f)
    fun tone(lightness: Float, chroma: Float = s): Color = hsl(h, chroma, lightness)
    return if (dark) {
        darkColorScheme(
            primary = tone(0.78f, chroma = (s * 0.7f).coerceIn(0.2f, 0.6f)),
            onPrimary = tone(0.20f, chroma = 0.5f),
            primaryContainer = tone(0.36f, chroma = (s * 0.6f).coerceIn(0.18f, 0.5f)),
            onPrimaryContainer = tone(0.90f, chroma = 0.35f),
            secondary = tone(0.72f, chroma = 0.22f),
            onSecondary = tone(0.22f, chroma = 0.25f),
            secondaryContainer = tone(0.32f, chroma = 0.22f),
            onSecondaryContainer = tone(0.88f, chroma = 0.25f),
            tertiary = tone(0.76f, chroma = 0.25f),
            tertiaryContainer = tone(0.34f, chroma = 0.25f),
            onTertiaryContainer = tone(0.90f, chroma = 0.25f),
            background = tone(0.09f, chroma = 0.10f),
            onBackground = tone(0.90f, chroma = 0.08f),
            surface = tone(0.09f, chroma = 0.10f),
            onSurface = tone(0.90f, chroma = 0.08f),
            surfaceVariant = tone(0.30f, chroma = 0.14f),
            onSurfaceVariant = tone(0.80f, chroma = 0.10f),
            surfaceContainerLowest = tone(0.06f, chroma = 0.10f),
            surfaceContainerLow = tone(0.12f, chroma = 0.11f),
            surfaceContainer = tone(0.14f, chroma = 0.11f),
            surfaceContainerHigh = tone(0.18f, chroma = 0.12f),
            surfaceContainerHighest = tone(0.22f, chroma = 0.12f),
            outline = tone(0.62f, chroma = 0.10f),
            outlineVariant = tone(0.30f, chroma = 0.12f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.42f, chroma = (s * 0.85f).coerceIn(0.3f, 0.7f)),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f, chroma = (s * 0.55f).coerceIn(0.2f, 0.5f)),
            onPrimaryContainer = tone(0.24f, chroma = 0.45f),
            secondary = tone(0.44f, chroma = 0.22f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.88f, chroma = 0.25f),
            onSecondaryContainer = tone(0.24f, chroma = 0.25f),
            tertiary = tone(0.45f, chroma = 0.25f),
            tertiaryContainer = tone(0.89f, chroma = 0.28f),
            onTertiaryContainer = tone(0.24f, chroma = 0.3f),
            background = tone(0.985f, chroma = 0.06f),
            onBackground = tone(0.16f, chroma = 0.08f),
            surface = tone(0.985f, chroma = 0.06f),
            onSurface = tone(0.16f, chroma = 0.08f),
            surfaceVariant = tone(0.90f, chroma = 0.12f),
            onSurfaceVariant = tone(0.38f, chroma = 0.10f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.962f, chroma = 0.09f),
            surfaceContainer = tone(0.942f, chroma = 0.10f),
            surfaceContainerHigh = tone(0.915f, chroma = 0.11f),
            surfaceContainerHighest = tone(0.888f, chroma = 0.12f),
            outline = tone(0.50f, chroma = 0.08f),
            outlineVariant = tone(0.84f, chroma = 0.10f),
        )
    }
}

private fun Color.hsl(): Triple<Float, Float, Float> {
    val argb = toArgb()
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f)
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } / 6f
    return Triple(hue, saturation, lightness)
}

private fun Color.hue(): Float = hsl().first

private fun Color.saturation(): Float = hsl().second

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    if (saturation <= 0f) {
        val v = (lightness * 255f).toInt().coerceIn(0, 255)
        return Color(v, v, v)
    }
    val q = if (lightness < 0.5f) {
        lightness * (1f + saturation)
    } else {
        lightness + saturation - lightness * saturation
    }
    val p = 2f * lightness - q
    fun channel(t: Float): Float {
        var value = t
        if (value < 0f) value += 1f
        if (value > 1f) value -= 1f
        val result = when {
            value < 1f / 6f -> p + (q - p) * 6f * value
            value < 1f / 2f -> q
            value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
            else -> p
        }
        return result.coerceIn(0f, 1f)
    }
    return Color(channel(hue + 1f / 3f), channel(hue), channel(hue - 1f / 3f))
}

/**
 * @param oledBlack pure black surfaces, used when the user picks the OLED theme.
 * @param dynamicColor Android 12+ wallpaper palette; ignored on older releases.
 * @param seed user picked preset colour, used when [dynamicColor] is off.
 */
@Composable
fun XmproTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledBlack: Boolean = false,
    dynamicColor: Boolean = false,
    seed: Color = DefaultSeedColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val wallpapered = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = remember(darkTheme, wallpapered, seed) {
        when {
            wallpapered && darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicDarkColorScheme(context)

            wallpapered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(context)

            else -> seededScheme(seed, darkTheme)
        }
    }
    val scheme = if (darkTheme && oledBlack) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF202020),
            surfaceVariant = base.surfaceContainerHigh,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0E0B0A),
            surfaceContainer = Color(0xFF15100F),
            surfaceContainerHigh = Color(0xFF1E1817),
            surfaceContainerHighest = Color(0xFF28201E),
            outlineVariant = Color(0xFF3A3230),
        )
    } else {
        base
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Establishes the theme text colours for a screen that paints its own background.
 *
 * Screens built from a plain [androidx.compose.foundation.layout.Box] with a
 * background modifier never see a [androidx.compose.material3.Surface], so
 * `LocalContentColor` keeps Compose's default black. Every `Text` that does not
 * set its own colour then stays black in dark mode. Wrapping the screen body in
 * this provider hands those defaults back to the theme.
 */
@Composable
fun ThemedContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
        content = content,
    )
}
