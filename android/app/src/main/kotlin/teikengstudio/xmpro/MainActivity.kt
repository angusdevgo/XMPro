package teikengstudio.xmpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import teikengstudio.xmpro.ui.ShellScreen
import teikengstudio.xmpro.ui.theme.XmproTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as XmproApp).container
        // Media notifications are how playback survives backgrounding on 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        setContent {
            var oledBlack by remember { mutableStateOf(container.oledBlack()) }
            var themeMode by remember { mutableStateOf(container.themeMode()) }
            var dynamicColor by remember { mutableStateOf(container.dynamicColor()) }
            var seedColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color(container.seedColor())) }
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            XmproTheme(
                darkTheme = dark,
                oledBlack = oledBlack,
                dynamicColor = dynamicColor,
                seed = seedColor,
            ) {
                ShellScreen(
                    container = container,
                    oledBlack = oledBlack,
                    onOledBlackChange = {
                        oledBlack = it
                        container.setOledBlack(it)
                    },
                    onAppearanceChange = { useDynamic, seed ->
                        dynamicColor = useDynamic
                        seedColor = androidx.compose.ui.graphics.Color(seed)
                    },
                )
            }
        }
    }
}
