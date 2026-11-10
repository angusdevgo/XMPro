package teikengstudio.xmpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mirrors AppAppearance.defaultSeedColor from the previous Flutter theme. */
val DefaultSeedColor = Color(0xFFE57373)

private val LightColors = lightColorScheme(
    primary = Color(0xFF904A43),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF3B0806),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231918),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF561E17),
    primaryContainer = Color(0xFF73342D),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DEDB),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DEDB),
)

/**
 * @param oledBlack pure black surfaces, used when the user picks the OLED theme.
 */
@Composable
fun XmproTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme && oledBlack) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF171110),
        )
    } else {
        base
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
