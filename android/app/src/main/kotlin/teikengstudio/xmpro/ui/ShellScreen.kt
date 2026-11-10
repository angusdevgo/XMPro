package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private enum class Destination(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home("主页", Icons.Filled.Home, Icons.Outlined.Home),
    Listening("听过", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    Downloads("下载", Icons.Filled.Download, Icons.Outlined.Download),
    Settings("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** Four-tab shell, matching the Flutter ShellPage layout. */
@Composable
fun ShellScreen() {
    var selected by rememberSaveable { mutableStateOf(Destination.Home) }
    Surface(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val active = destination == selected
                    NavigationBarItem(
                        selected = active,
                        onClick = { selected = destination },
                        icon = {
                            Icon(
                                imageVector = if (active) destination.selectedIcon else destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selected.label)
                Text("迁移中：界面在 M4 完成", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    }
}
