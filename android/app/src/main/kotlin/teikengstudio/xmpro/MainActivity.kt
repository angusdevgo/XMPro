package teikengstudio.xmpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import teikengstudio.xmpro.ui.ShellScreen
import teikengstudio.xmpro.ui.theme.XmproTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The window theme paints both system bars with the theme background, so
        // the app never shows a black bar above or below the Compose surface.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                // Transparent bars inherit the app background; their icons must
                // follow the theme or they become invisible on light surfaces.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            XmproTheme(darkTheme = dark) {
                ShellScreen()
            }
        }
    }
}
