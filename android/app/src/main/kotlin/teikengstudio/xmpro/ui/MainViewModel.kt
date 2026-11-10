package teikengstudio.xmpro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import teikengstudio.xmpro.api.XmlyExperience
import teikengstudio.xmpro.api.XmlyHistory
import teikengstudio.xmpro.api.XmlyLogin
import teikengstudio.xmpro.data.AccountSummary
import teikengstudio.xmpro.data.AppContainer
import teikengstudio.xmpro.download.DownloadTask
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.AlbumPage
import teikengstudio.xmpro.model.HistoryRecord
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.HomeTab
import teikengstudio.xmpro.model.SubscriptionItem
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.UpdateItem
import teikengstudio.xmpro.model.XmlyException

data class UiState(
    val signedIn: Boolean = false,
    val account: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchKind: String = "album",
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val album: AlbumPage? = null,
    val albumLoading: Boolean = false,
    val albumError: String? = null,
    val playing: Track? = null,
    val playingTitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val downloads: List<File> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadTasks: List<DownloadTask> = emptyList(),
    val message: String? = null,
    val channels: List<HomeChannel> = emptyList(),
    val channelId: Int = 0,
    val tabs: List<HomeTab> = emptyList(),
    val tabId: Int? = null,
    val sections: List<HomeSection> = emptyList(),
    val homeLoading: Boolean = false,
    val homeError: String? = null,
    val subscriptions: List<SubscriptionItem> = emptyList(),
    val moreSubscriptions: Boolean = false,
    val updates: List<UpdateItem> = emptyList(),
    val moreUpdates: Boolean = false,
    val nextTimeline: Long? = null,
    val history: List<HistoryRecord> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val moreFavorites: Boolean = false,
    val favoritePage: Int = 0,
    val subscriptionPage: Int = 0,
    val listeningLoading: Boolean = false,
    val listeningError: String? = null,
    val accounts: List<AccountSummary> = emptyList(),
    val autoClaim: Boolean = false,
    val accountBusy: Boolean = false,
)

class MainViewModel(context: Context, private val container: AppContainer) : ViewModel() {
    private var ticker: Job? = null
    private val playback = container.playback
    private val login = XmlyLogin()

    private var sessionTrack: Track? = null
    private var sessionStartedAt = 0L

    private val _state = MutableStateFlow(
        UiState(
            signedIn = container.isSignedIn(container.activeAccount),
            account = container.activeAccount,
            downloads = container.downloads(),
            accounts = container.accounts(),
            autoClaim = container.autoClaim(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // The service owns the player; mirror its state into the UI model.
        viewModelScope.launch {
            container.downloads.tasks.collect { list ->
                _state.update { it.copy(downloadTasks = list) }
            }
        }
        viewModelScope.launch {
            playback.error.collect { failure ->
                if (failure != null) _state.update { it.copy(message = failure) }
            }
        }
        viewModelScope.launch {
            playback.state.collect { player ->
                _state.update {
                    it.copy(
                        playingTitle = player.title,
                        isPlaying = player.playing,
                        positionMs = player.positionMs,
                        durationMs = player.durationMs,
                    )
                }
                if (player.playing) startTicker() else ticker?.cancel()
            }
        }
        viewModelScope.launch { autoClaimLoop() }
    }

    // -------------------------------------------------------------------- home

    fun loadHome() {
        _state.update { it.copy(homeLoading = it.channels.isEmpty(), homeError = null) }
        viewModelScope.launch {
            try {
                val experience = withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount))
                }
                val channels = withContext(Dispatchers.IO) { experience.channels() }
                val selected = _state.value.channelId.takeIf { id -> channels.any { it.pageId == id } }
                    ?: channels.firstOrNull()?.pageId
                    ?: throw XmlyException("invalid_home", "首页频道为空")
                _state.update { it.copy(channels = channels, channelId = selected) }
                loadChannel(experience, selected, null)
            } catch (error: Exception) {
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    fun selectChannel(pageId: Int) {
        if (_state.value.channelId == pageId) return
        _state.update {
            it.copy(channelId = pageId, homeLoading = true, homeError = null, sections = emptyList(), tabId = null)
        }
        viewModelScope.launch {
            runCatching {
                val experience = withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount))
                }
                loadChannel(experience, pageId, null)
            }.onFailure { error ->
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    fun selectTab(tabId: Int) {
        val pageId = _state.value.channelId
        if (pageId <= 0) return
        _state.update { it.copy(tabId = tabId, homeLoading = true, homeError = null) }
        viewModelScope.launch {
            runCatching {
                val experience = withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount))
                }
                loadChannel(experience, pageId, tabId)
            }.onFailure { error ->
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    /** A channel with tabs loads its first tab, matching the original client. */
    private fun loadChannel(experience: XmlyExperience, pageId: Int, tabId: Int?) {
        viewModelScope.launch {
            try {
                var content = withContext(Dispatchers.IO) { experience.home(pageId, tabId) }
                var selectedTab = tabId
                if (tabId == null && content.tabs.isNotEmpty()) {
                    selectedTab = content.tabs.first().tabId
                    content = withContext(Dispatchers.IO) { experience.home(pageId, selectedTab) }
                }
                _state.update {
                    it.copy(
                        sections = content.sections,
                        tabs = content.tabs.ifEmpty { it.tabs },
                        tabId = selectedTab,
                        homeLoading = false,
                        homeError = null,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(homeLoading = false, homeError = describe(error), sections = emptyList())
                }
            }
        }
    }

    // ------------------------------------------------------------------ search

    fun search(query: String, kind: String = _state.value.searchKind) {
        if (query.isBlank()) return
        _state.update {
            it.copy(query = query, searchKind = kind, searching = true, searchError = null, album = null)
        }
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).search(query, kind = kind)
                }
                _state.update {
                    it.copy(searching = false, tracks = page.tracks, albums = page.albums)
                }
            } catch (error: Exception) {
                _state.update { it.copy(searching = false, searchError = describe(error)) }
            }
        }
    }

    fun clearSearch() = _state.update {
        it.copy(query = "", tracks = emptyList(), albums = emptyList(), searchError = null)
    }

    fun openAlbum(albumId: String) {
        _state.update { it.copy(album = null, albumLoading = true, albumError = null) }
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).album(albumId)
                }
                _state.update { it.copy(album = page, albumLoading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(albumLoading = false, albumError = describe(error)) }
            }
        }
    }

    fun closeAlbum() = _state.update { it.copy(album = null, albumError = null, albumLoading = false) }

    // ------------------------------------------------------------------ player

    fun play(track: Track, resumePositionMs: Long = 0) {
        _state.update { it.copy(message = null) }
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).resolve(track.number)
                }
                playback.start(resolved.url, resolved.track.title) {
                    sessionTrack = resolved.track
                    sessionStartedAt = System.currentTimeMillis()
                    _state.update { it.copy(playing = resolved.track) }
                    if (resumePositionMs > 0) playback.seekTo(resumePositionMs)
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun playFile(file: File) {
        sessionTrack = null
        playback.start(file.toURI().toString(), file.nameWithoutExtension) {
            _state.update { it.copy(playing = null) }
        }
    }

    fun togglePlayback() {
        val wasPlaying = _state.value.isPlaying
        playback.toggle()
        if (wasPlaying) syncHistory()
    }

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun stopPlayback() {
        syncHistory()
        playback.stop()
        ticker?.cancel()
        _state.update { it.copy(playing = null) }
    }

    /** Best-effort cloud progress write; failures are never retried here. */
    private fun syncHistory() {
        val track = sessionTrack ?: return
        val started = sessionStartedAt
        val position = playback.currentPosition()
        sessionTrack = null
        if (started <= 0 || position <= 0) return
        val identity = container.activeAccount ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    XmlyHistory(container.client(identity))
                        .sync(track, position / 1000.0, started, System.currentTimeMillis())
                }
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(positionMs = playback.currentPosition()) }
                delay(500)
            }
        }
    }

    // --------------------------------------------------------------- downloads

    fun download(track: Track) {
        container.downloads.enqueue(track)
    }

    fun pauseDownload(task: DownloadTask) = container.downloads.pause(task.id)

    fun resumeDownload(task: DownloadTask) = container.downloads.resume(task.id)

    fun cancelDownload(task: DownloadTask) = container.downloads.cancel(task.id)

    fun refreshDownloads() = _state.update { it.copy(downloads = container.downloads()) }

    fun deleteDownload(file: File) {
        file.delete()
        _state.update { it.copy(downloads = container.downloads()) }
    }

    // ------------------------------------------------- listening / subscriptions

    fun refreshListening() {
        if (!_state.value.signedIn) {
            _state.update { it.copy(subscriptions = emptyList(), updates = emptyList(), history = emptyList(), favorites = emptyList()) }
            return
        }
        _state.update { it.copy(listeningLoading = true, listeningError = null) }
        viewModelScope.launch {
            val identity = container.activeAccount ?: return@launch
            val experience = XmlyExperience(container.client(identity))
            var failure: String? = null
            runCatching { withContext(Dispatchers.IO) { experience.subscriptions(1) } }
                .onSuccess { page ->
                    _state.update {
                        it.copy(subscriptions = page.items, moreSubscriptions = page.hasMore, subscriptionPage = 1)
                    }
                }
                .onFailure { failure = describe(it) }
            runCatching { withContext(Dispatchers.IO) { experience.updates() } }
                .onSuccess { page ->
                    _state.update {
                        it.copy(updates = page.items, moreUpdates = page.hasMore, nextTimeline = page.nextTimeline)
                    }
                }
                .onFailure { failure = failure ?: describe(it) }
            runCatching { withContext(Dispatchers.IO) { XmlyHistory(container.client(identity)).records() } }
                .onSuccess { records -> _state.update { it.copy(history = records) } }
                .onFailure { failure = failure ?: describe(it) }
            runCatching { withContext(Dispatchers.IO) { experience.favorites(1) } }
                .onSuccess { page ->
                    _state.update { it.copy(favorites = page.items, moreFavorites = page.hasMore, favoritePage = 1) }
                }
                .onFailure { failure = failure ?: describe(it) }
            _state.update { it.copy(listeningLoading = false, listeningError = failure) }
        }
    }

    fun loadMoreSubscriptions() {
        val current = _state.value
        if (!current.moreSubscriptions || current.listeningLoading) return
        val page = current.subscriptionPage + 1
        viewModelScope.launch {
            runCatching {
                val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                withContext(Dispatchers.IO) { XmlyExperience(container.client(identity)).subscriptions(page) }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        subscriptions = it.subscriptions + result.items.filterNot { item ->
                            it.subscriptions.any { known -> known.album.id == item.album.id }
                        },
                        moreSubscriptions = result.hasMore,
                        subscriptionPage = page,
                    )
                }
            }.onFailure { error -> _state.update { it.copy(message = describe(error)) } }
        }
    }

    fun loadMoreUpdates() {
        val current = _state.value
        val before = current.nextTimeline ?: return
        if (!current.moreUpdates || current.listeningLoading) return
        viewModelScope.launch {
            runCatching {
                val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                withContext(Dispatchers.IO) { XmlyExperience(container.client(identity)).updates(before = before) }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        updates = it.updates + result.items.filterNot { item ->
                            it.updates.any { known -> known.track.id == item.track.id }
                        },
                        moreUpdates = result.hasMore,
                        nextTimeline = result.nextTimeline,
                    )
                }
            }.onFailure { error -> _state.update { it.copy(message = describe(error)) } }
        }
    }

    fun loadMoreFavorites() {
        val current = _state.value
        if (!current.moreFavorites || current.listeningLoading) return
        val page = current.favoritePage + 1
        viewModelScope.launch {
            runCatching {
                val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                withContext(Dispatchers.IO) { XmlyExperience(container.client(identity)).favorites(page) }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        favorites = it.favorites + result.items.filterNot { track ->
                            it.favorites.any { known -> known.id == track.id }
                        },
                        moreFavorites = result.hasMore,
                        favoritePage = page,
                    )
                }
            }.onFailure { error -> _state.update { it.copy(message = describe(error)) } }
        }
    }

    fun setFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                    XmlyExperience(container.client(identity)).setFavorite(track, favorite)
                }
                _state.update {
                    it.copy(
                        favorites = if (favorite) it.favorites + track else it.favorites.filterNot { row -> row.id == track.id },
                        message = if (favorite) "已收藏" else "已取消收藏",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun setSubscribed(albumId: String, subscribed: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                    XmlyExperience(container.client(identity)).setSubscribed(albumId, subscribed)
                }
                _state.update {
                    it.copy(
                        subscriptions = if (subscribed) it.subscriptions else it.subscriptions.filterNot { row -> row.album.id == albumId },
                        message = if (subscribed) "已订阅" else "已取消订阅",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    // ----------------------------------------------------------------- account

    fun signIn(raw: String) {
        viewModelScope.launch {
            try {
                val identity = withContext(Dispatchers.IO) { container.signIn(raw) }
                _state.update {
                    it.copy(
                        signedIn = true,
                        account = identity,
                        accounts = container.accounts(),
                        autoClaim = container.autoClaim(),
                        message = "已导入账号",
                    )
                }
                refreshListening()
                loadHome()
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun signOut() {
        val identity = _state.value.account
        container.signOut(identity)
        _state.update {
            it.copy(
                signedIn = false,
                account = null,
                accounts = container.accounts(),
                subscriptions = emptyList(),
                updates = emptyList(),
                history = emptyList(),
                favorites = emptyList(),
                message = "已退出账号",
            )
        }
    }

    fun refreshAccounts() = _state.update {
        it.copy(accounts = container.accounts(), autoClaim = container.autoClaim())
    }

    fun setPrimaryAccount(identity: String) = accountAction({ container.setPrimaryAccount(identity) }, "已切换主账号")

    fun removeAccount(identity: String) = accountAction({ container.removeAccount(identity) }, "已移除账号") {
        _state.update {
            it.copy(signedIn = container.isSignedIn(container.activeAccount), account = container.activeAccount)
        }
    }

    fun queryQuota(identity: String) = accountAction({
        container.refreshQuota(identity)
    }, "听时已更新")

    fun claimOnce(identity: String) = accountAction({
        container.claimOnce(identity)
    }, "已发送领取请求")

    fun resumeRewards(identity: String) = accountAction({
        container.resumeRewards(identity)
    }, "已恢复自动领取")

    fun setAutoClaim(enabled: Boolean) = accountAction({
        container.setAutoClaim(enabled)
    }, if (enabled) "已开启自动领取" else "已关闭自动领取")

    private fun accountAction(block: () -> Unit, success: String, after: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            try {
                withContext(Dispatchers.IO) { block() }
                after()
                _state.update {
                    it.copy(accounts = container.accounts(), autoClaim = container.autoClaim(), message = success)
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error), accounts = container.accounts()) }
            } finally {
                _state.update { it.copy(accountBusy = false) }
            }
        }
    }

    // ------------------------------------------------------------- sms sign-in

    fun geetestConfig(): Map<String, Any> = login.config()

    /** Sends one SMS off the main thread; [onResult] gets the challenge or an error. */
    fun sendSms(
        mobile: String,
        geetest: Map<String, String>,
        onResult: (challenge: String?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val challenge = withContext(Dispatchers.IO) { login.sendSms(mobile, geetest) }
                onResult(challenge, null)
            } catch (error: Exception) {
                onResult(null, describe(error))
            }
        }
    }

    /** Verifies the code and commits the account; the dialog shows the result. */
    fun verifySms(
        challengeId: String,
        code: String,
        makePrimary: Boolean,
        onResult: (error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) { login.verifySms(challengeId, code) }
                val identity = withContext(Dispatchers.IO) { container.signIn(credentials, makePrimary) }
                _state.update {
                    it.copy(
                        signedIn = true,
                        account = identity,
                        accounts = container.accounts(),
                        autoClaim = container.autoClaim(),
                        message = "已登录账号 ${credentials.uid}",
                    )
                }
                refreshListening()
                loadHome()
                onResult(null)
            } catch (error: Exception) {
                onResult(describe(error))
            }
        }
    }

    fun cancelSmsLogin() = login.clear()

    // ---------------------------------------------------------------- settings

    fun themeMode(): String = container.themeMode()

    fun setThemeMode(mode: String) = container.setThemeMode(mode)

    fun oledBlack(): Boolean = container.oledBlack()

    fun setOledBlack(enabled: Boolean) = container.setOledBlack(enabled)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Optional top-up pass; only runs while automatic claiming is enabled. */
    private suspend fun autoClaimLoop() {
        while (true) {
            delay(5 * 60 * 1000L)
            if (!container.autoClaim()) continue
            val identity = container.activeAccount ?: continue
            runCatching { withContext(Dispatchers.IO) { container.refreshRewards(identity) } }
            _state.update { it.copy(accounts = container.accounts()) }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        login.clear()
        super.onCleared()
    }

    private fun describe(error: Throwable): String =
        if (error is XmlyException) error.message else (error.message ?: "操作失败")
}
