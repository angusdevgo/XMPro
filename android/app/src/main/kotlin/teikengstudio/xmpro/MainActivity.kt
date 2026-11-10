package teikengstudio.xmpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import teikengstudio.xmpro.ui.ShellScreen
import teikengstudio.xmpro.ui.theme.XmproTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            XmproTheme {
                ShellScreen()
            }
        }
    }
}
