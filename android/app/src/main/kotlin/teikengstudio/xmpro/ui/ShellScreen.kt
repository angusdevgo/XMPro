package teikengstudio.xmpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import teikengstudio.xmpro.data.AppContainer

private enum class Destination { Home, Listening, Settings }

private val navItems = listOf(
    FloatingNavItem("主页", Icons.Filled.Home, Icons.Outlined.Home),
    FloatingNavItem("听书", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    FloatingNavItem("我的", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ShellScreen(
    container: AppContainer,
    oledBlack: Boolean,
    onOledBlackChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit = {},
    onAppearanceChange: (dynamic: Boolean, seed: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel { MainViewModel(context, container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(viewModel.themeMode()) }
    val snackbar = remember { SnackbarHostState() }
    val selected = Destination.entries[selectedIndex]
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The mini player only exists while a track is loaded, so an untouched app
    // has no idle placeholder bar floating above the nav pill.
    val playerVisible = state.playingTitle != null
    // An opened album is an overlay above whichever tab started it, so 听过 can
    // open albums too and come back to its own list.
    val albumOpen = state.album != null || state.albumLoading || state.albumError != null
    val userOpen = state.userPage != null || state.userLoading || state.userError != null
    // The content scrolls under the floating bars, so it needs matching bottom room.
    val contentBottom = navBottom + 88.dp + if (playerVisible) 76.dp else 0.dp

    // PiliPlus style: bars retract while the user reads down a list and return
    // on the first upward drag (or when playback starts / a page changes).
    var barsHidden by remember { mutableStateOf(false) }
    val barsAlpha by animateFloatAsState(
        targetValue = if (barsHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "barsAlpha",
    )
    val barsOffset by animateFloatAsState(
        targetValue = if (barsHidden) 140f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "barsOffset",
    )
    val barsVisible = !barsHidden

    LaunchedEffect(Unit) {
        if (state.channels.isEmpty()) viewModel.loadHome()
        viewModel.refreshListening()
        viewModel.refreshBookmarks()
    }

    LaunchedEffect(state.dynamicColor, state.seedColor) {
        onAppearanceChange(state.dynamicColor, state.seedColor)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    // Any navigation resets the retracted state so the bar never stays lost.
    LaunchedEffect(selectedIndex, albumOpen, userOpen, playerOpen) {
        barsHidden = false
    }

    BackHandler(enabled = playerOpen || albumOpen || userOpen || selected != Destination.Home) {
        when {
            playerOpen -> playerOpen = false
            albumOpen -> viewModel.closeAlbum()
            userOpen -> viewModel.closeUser()
            selected != Destination.Home -> selectedIndex = 0
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // The window draws behind the status bar and the notch, so the root
            // paints the theme colour there instead of leaving it to the system.
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // safeDrawing covers both the status bar and any display cutout.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                },
                label = "tabTransition",
            ) { destination ->
                when (destination) {
                    Destination.Home -> HomeScreen(
                        state = state,
                        bottomInset = contentBottom,
                        onSearch = viewModel::search,
                        onClearSearch = viewModel::clearSearch,
                        onRefreshHome = viewModel::loadHome,
                        onOpenAlbum = viewModel::openAlbum,
                        onPlay = { viewModel.playInAlbum(it) },
                        onSelectChannel = viewModel::selectChannel,
                        onSelectTab = viewModel::selectTab,
                        onMoveChannel = viewModel::moveChannel,
                        onHideChannel = viewModel::hideChannel,
                        onShowChannel = viewModel::showChannel,
                        onResetChannels = viewModel::resetChannels,
                        barsVisible = barsVisible,
                        onBarsHidden = { barsHidden = it },
                    )

                    Destination.Listening -> ListeningScreen(
                        state = state,
                        bottomInset = contentBottom,
                        onRefresh = viewModel::refreshListening,
                        onOpenAlbum = viewModel::openAlbum,
                        onPlay = { track, positionMs ->
                            if (positionMs > 0) {
                                // History entries carry a saved position.
                                viewModel.playInAlbum(track, positionMs)
                            } else {
                                viewModel.playUpdates(track)
                            }
                        },
                        onUnsubscribe = { viewModel.setSubscribed(it, false) },
                        onUnfavorite = { viewModel.setFavorite(it, false) },
                        onLoadMoreUpdates = viewModel::loadMoreUpdates,
                        onLoadMoreSubscriptions = viewModel::loadMoreSubscriptions,
                        onLoadMoreFavorites = viewModel::loadMoreFavorites,
                        onRemoveBookmark = viewModel::removeBookmark,
                        barsVisible = barsVisible,
                        onBarsHidden = { barsHidden = it },
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
                            onThemeModeChange(it)
                            viewModel.setThemeMode(it)
                        },
                        onOledBlack = onOledBlackChange,
                        onSetPrimary = viewModel::setPrimaryAccount,
                        onRemoveAccount = viewModel::removeAccount,
                        onQueryQuota = viewModel::queryQuota,
                        onClaimOnce = viewModel::claimOnce,
                        onResumeRewards = viewModel::resumeRewards,
                        onAutoClaim = viewModel::setAutoClaim,
                        onSyncProfile = { viewModel.syncProfiles(manual = true) },
                        onRenameAccount = viewModel::renameAccount,
                        onQuality = viewModel::setQuality,
                        onSkipTrim = viewModel::setSkipTrim,
                        onCheckUpdate = viewModel::checkUpdate,
                        onMirror = viewModel::setUpdateMirror,
                        onOpenLink = viewModel::openLink,
                        onDismissUpdate = viewModel::clearUpdateResult,
                        onDynamicColor = viewModel::setDynamicColor,
                        onSeedColor = viewModel::setSeedColor,
                        onOpenSocial = viewModel::openSocial,
                        onCloseSocial = viewModel::closeSocial,
                        onOpenUser = viewModel::openUser,
                        onExportBackup = viewModel::exportBackup,
                        onImportBackup = viewModel::importBackup,
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

            AnimatedVisibility(
                visible = userOpen,
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 260),
                    initialOffsetX = { it / 4 },
                ) + fadeIn(tween(200)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 200),
                    targetOffsetX = { it / 4 },
                ) + fadeOut(tween(160)),
            ) {
                UserScreen(
                    state = state,
                    bottomInset = contentBottom,
                    onBack = viewModel::closeUser,
                    onOpenAlbum = { albumId ->
                        viewModel.closeUser()
                        viewModel.openAlbum(albumId)
                    },
                )
            }

            AnimatedVisibility(
                visible = albumOpen,
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 260),
                    initialOffsetX = { it / 4 },
                ) + fadeIn(tween(200)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 200),
                    targetOffsetX = { it / 4 },
                ) + fadeOut(tween(160)),
            ) {
                AlbumScreen(
                    state = state,
                    bottomInset = contentBottom,
                    onBack = viewModel::closeAlbum,
                    onPlay = viewModel::playAlbum,
                    onSubscribe = viewModel::setSubscribed,
                    onSelectRange = viewModel::setAlbumRange,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentBottom),
        )

        if (playerOpen) {
            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetY = { it },
                ) + fadeIn(tween(200)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetY = { it },
                ) + fadeOut(tween(160)),
            ) {
                PlayerSheet(
                    state = state,
                    onDismiss = { playerOpen = false },
                    onToggle = viewModel::togglePlayback,
                    onStop = {
                        viewModel.stopPlayback()
                        playerOpen = false
                    },
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSkip = viewModel::skipBy,
                    onSeek = viewModel::seekTo,
                    onCycleSpeed = viewModel::cycleSpeed,
                    onFavorite = viewModel::toggleFavoriteOfPlaying,
                    isFavorite = state.playing?.let { track -> track.id in state.favoriteIds } == true,
                    onBookmark = viewModel::saveBookmark,
                    onSleepTimer = viewModel::setSleepTimer,
                    onVolume = viewModel::setVolume,
                    onSelectQueue = { index ->
                        viewModel.playQueue(state.queue, index)
                    },
                    onOpenAlbum = { albumId ->
                        playerOpen = false
                        viewModel.openAlbum(albumId)
                    },
                    onSkipTrim = viewModel::setSkipTrim,
                )
            }
        }

        // The now-playing page owns the screen: the mini player and the nav pill
        // would only cover its transport controls.
        if (!playerOpen) Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = barsOffset / 1.6f
                    alpha = barsAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (playerVisible) PlayerBar(
                state = state,
                onToggle = viewModel::togglePlayback,
                onStop = viewModel::stopPlayback,
                onOpenPlayer = { if (state.playingTitle != null) playerOpen = true },
            )
            FloatingBottomBar(
                items = navItems,
                topPadding = if (playerVisible && !playerOpen) 2.dp else 12.dp,
                selectedIndex = selectedIndex,
                onItemSelected = { index ->
                    if (Destination.entries[index] != Destination.Home) viewModel.closeAlbum()
                    selectedIndex = index
                },
            )
        }
    }
}
