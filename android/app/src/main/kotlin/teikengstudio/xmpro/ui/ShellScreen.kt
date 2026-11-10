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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

private enum class Destination { Home, Listening, Downloads, Settings }

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
    // An opened album is an overlay above whichever tab started it, so 听过 and
    // 下载 can open albums too and come back to their own list.
    val albumOpen = state.album != null || state.albumLoading || state.albumError != null
    // The content scrolls under the floating bars, so it needs matching bottom room.
    val contentBottom = navBottom + 88.dp + if (playerVisible) 76.dp else 0.dp

    LaunchedEffect(Unit) {
        if (state.channels.isEmpty()) viewModel.loadHome()
        viewModel.refreshListening()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    Box(Modifier.fillMaxSize()) {
        // Content fills the screen and scrolls under the floating bars; each
        // screen adds `contentBottom` as list padding so the last row clears them.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (albumOpen) {
                AlbumScreen(
                    state = state,
                    bottomInset = contentBottom,
                    onBack = viewModel::closeAlbum,
                    onPlay = { viewModel.play(it) },
                    onDownload = viewModel::download,
                    onSubscribe = viewModel::setSubscribed,
                    onSubscribeAll = { 
                        state.album?.tracks?.firstOrNull()?.let(viewModel::download)
                    },
                )
            } else when (selected) {
                Destination.Home -> {
                    HomeScreen(
                        state = state,
                        bottomInset = contentBottom,
                        onSearch = viewModel::search,
                        onClearSearch = viewModel::clearSearch,
                        onRefreshHome = viewModel::loadHome,
                        onOpenAlbum = viewModel::openAlbum,
                        onPlay = { viewModel.play(it) },
                        onDownload = viewModel::download,
                        onSelectChannel = viewModel::selectChannel,
                        onSelectTab = viewModel::selectTab,
                    )
                }

                Destination.Listening -> ListeningScreen(
                    state = state,
                    bottomInset = contentBottom,
                    onRefresh = viewModel::refreshListening,
                    onOpenAlbum = viewModel::openAlbum,
                    onPlay = { track, positionMs -> viewModel.play(track, positionMs) },
                    onDownload = viewModel::download,
                    onUnsubscribe = { viewModel.setSubscribed(it, false) },
                    onUnfavorite = { viewModel.setFavorite(it, false) },
                    onLoadMoreUpdates = viewModel::loadMoreUpdates,
                    onLoadMoreSubscriptions = viewModel::loadMoreSubscriptions,
                    onLoadMoreFavorites = viewModel::loadMoreFavorites,
                )

                Destination.Downloads -> DownloadsScreen(
                    state = state,
                    bottomInset = contentBottom,
                    onPlayFile = viewModel::playFile,
                    onDelete = viewModel::deleteDownload,
                    onRefresh = viewModel::refreshDownloads,
                    onPauseTask = viewModel::pauseDownload,
                    onResumeTask = viewModel::resumeDownload,
                    onCancelTask = viewModel::cancelDownload,
                )

                Destination.Settings -> SettingsScreen(
                    state = state,
                    bottomInset = contentBottom,
                    themeMode = themeMode,
                    oledBlack = oledBlack,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onThemeMode = {
                        themeMode = it
                        viewModel.setThemeMode(it)
                    },
                    onOledBlack = onOledBlackChange,
                    onSetPrimary = viewModel::setPrimaryAccount,
                    onRemoveAccount = viewModel::removeAccount,
                    onQueryQuota = viewModel::queryQuota,
                    onClaimOnce = viewModel::claimOnce,
                    onResumeRewards = viewModel::resumeRewards,
                    onAutoClaim = viewModel::setAutoClaim,
                    smsLogin = remember(viewModel) {
                        SmsLoginCallbacks(
                            captchaId = viewModel.geetestConfig()["captcha_id"] as String,
                            sendSms = { mobile, fields, done ->
                                viewModel.sendSms(mobile, fields, done)
                            },
                            verifySms = { challenge, code, primary, done ->
                                viewModel.verifySms(challenge, code, primary, done)
                            },
                            cancel = viewModel::cancelSmsLogin,
                        )
                    },
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
