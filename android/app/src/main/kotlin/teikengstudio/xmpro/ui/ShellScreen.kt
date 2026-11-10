package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import teikengstudio.xmpro.data.AppContainer

private enum class Destination(val label: String) {
    Home("主页"),
    Listening("听过"),
    Downloads("下载"),
    Settings("设置"),
}

private val navItems = listOf(
    FloatingNavItem("主页", Icons.Filled.Home, Icons.Outlined.Home),
    FloatingNavItem("听过", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    FloatingNavItem("下载", Icons.Filled.Download, Icons.Outlined.Download),
    FloatingNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ShellScreen(container: AppContainer, oledBlack: Boolean, onOledBlackChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel { MainViewModel(context, container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var themeMode by remember { mutableStateOf(viewModel.themeMode()) }
    val snackbar = remember { SnackbarHostState() }
    val selected = Destination.entries[selectedIndex]
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val playerVisible = state.playingTitle != null
    // The content scrolls under the floating bars, so it needs matching bottom room.
    val contentBottom = navBottom + 88.dp + if (playerVisible) 76.dp else 0.dp

    LaunchedEffect(Unit) {
        if (state.channels.isEmpty()) viewModel.loadHome()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = contentBottom),
        ) {
            when (selected) {
                Destination.Home -> if (state.album != null || state.albumLoading || state.albumError != null) {
                    AlbumScreen(
                        state = state,
                        onBack = viewModel::closeAlbum,
                        onPlay = viewModel::play,
                        onDownload = viewModel::download,
                    )
                } else {
                    HomeScreen(
                        state = state,
                        onSearch = viewModel::search,
                        onOpenAlbum = viewModel::openAlbum,
                        onPlay = viewModel::play,
                        onDownload = viewModel::download,
                        onSelectChannel = viewModel::selectChannel,
                        onSelectTab = viewModel::selectTab,
                    )
                }

                Destination.Listening -> Column(Modifier.padding(24.dp)) {
                    Text("听过", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "听时记录与云进度将在下一阶段接入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Destination.Downloads -> DownloadsScreen(
                    state = state,
                    onPlayFile = viewModel::playFile,
                    onDelete = viewModel::deleteDownload,
                    onRefresh = viewModel::refreshDownloads,
                )

                Destination.Settings -> SettingsScreen(
                    state = state,
                    themeMode = themeMode,
                    oledBlack = oledBlack,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onThemeMode = {
                        themeMode = it
                        viewModel.setThemeMode(it)
                    },
                    onOledBlack = onOledBlackChange,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentBottom),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerBar(
                state = state,
                onToggle = viewModel::togglePlayback,
                onStop = viewModel::stopPlayback,
            )
            FloatingBottomBar(
                items = navItems,
                selectedIndex = selectedIndex,
                onItemSelected = { index ->
                    if (Destination.entries[index] != Destination.Home) viewModel.closeAlbum()
                    selectedIndex = index
                },
            )
        }
    }
}
