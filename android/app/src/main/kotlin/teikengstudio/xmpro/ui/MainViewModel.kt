package teikengstudio.xmpro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import okhttp3.OkHttpClient
import okhttp3.Request
import teikengstudio.xmpro.data.AppContainer
import teikengstudio.xmpro.api.XmlyExperience
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.HomeTab
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.AlbumPage
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException

data class UiState(
    val signedIn: Boolean = false,
    val account: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val searchError: String? = null,
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
    val message: String? = null,
    val channels: List<HomeChannel> = emptyList(),
    val channelId: Int = 0,
    val tabs: List<HomeTab> = emptyList(),
    val tabId: Int? = null,
    val sections: List<HomeSection> = emptyList(),
    val homeLoading: Boolean = false,
    val homeError: String? = null,
)

class MainViewModel(context: Context, private val container: AppContainer) : ViewModel() {
    private val appContext = context.applicationContext
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _state.update { it.copy(isPlaying = playing) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = duration.coerceAtLeast(0)) }
                }
            }
        })
    }
    private val http = OkHttpClient()
    private var ticker: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            signedIn = container.isSignedIn(container.activeAccount),
            account = container.activeAccount,
            downloads = container.downloads(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

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
                val content = withContext(Dispatchers.IO) { experience.home(selected) }
                _state.update {
                    it.copy(
                        channels = channels,
                        channelId = selected,
                        tabs = content.tabs,
                        tabId = null,
                        sections = content.sections,
                        homeLoading = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    fun selectChannel(pageId: Int) {
        if (_state.value.channelId == pageId) return
        _state.update { it.copy(channelId = pageId, homeLoading = true, homeError = null, sections = emptyList()) }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount)).home(pageId)
                }
                _state.update {
                    it.copy(sections = content.sections, tabs = content.tabs, tabId = null, homeLoading = false)
                }
            } catch (error: Exception) {
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    fun selectTab(tabId: Int) {
        val pageId = _state.value.channelId
        if (pageId <= 0) return
        _state.update { it.copy(tabId = tabId, homeLoading = true, homeError = null) }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount)).home(pageId, tabId)
                }
                _state.update { it.copy(sections = content.sections, homeLoading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    // ------------------------------------------------------------------ search

    fun search(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(query = query, searching = true, searchError = null, album = null) }
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { container.client(container.activeAccount).search(query) }
                _state.update {
                    it.copy(searching = false, tracks = page.tracks, albums = page.albums)
                }
            } catch (error: Exception) {
                _state.update { it.copy(searching = false, searchError = describe(error)) }
            }
        }
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

    fun play(track: Track) {
        _state.update { it.copy(message = null) }
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).resolve(track.number)
                }
                player.setMediaItem(MediaItem.fromUri(resolved.url))
                player.prepare()
                player.play()
                _state.update {
                    it.copy(
                        playing = resolved.track,
                        playingTitle = resolved.track.title,
                        durationMs = 0,
                        positionMs = 0,
                    )
                }
                startTicker()
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun playFile(file: File) {
        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.play()
        _state.update {
            it.copy(playingTitle = file.nameWithoutExtension, playing = null, positionMs = 0, durationMs = 0)
        }
        startTicker()
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun stopPlayback() {
        player.stop()
        ticker?.cancel()
        _state.update { it.copy(playing = null, playingTitle = null, isPlaying = false, positionMs = 0, durationMs = 0) }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                _state.update {
                    it.copy(positionMs = player.currentPosition.coerceAtLeast(0))
                }
                delay(500)
            }
        }
    }

    // --------------------------------------------------------------- downloads

    fun download(track: Track) {
        val key = track.number
        if (_state.value.downloadProgress.containsKey(key)) return
        _state.update { it.copy(downloadProgress = it.downloadProgress + (key to 0f)) }
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).resolve(track.number)
                }
                val target = File(container.downloadRoot(), fileName(resolved.track, resolved.url))
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(resolved.url).build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw XmlyException("http", "下载失败 HTTP ${response.code}")
                        val body = response.body ?: throw XmlyException("http", "下载响应为空")
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var copied = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                    if (total > 0) {
                                        val progress = (copied * 100 / total).toInt() / 100f
                                        _state.update {
                                            it.copy(downloadProgress = it.downloadProgress + (key to progress))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                _state.update {
                    it.copy(
                        downloadProgress = it.downloadProgress - key,
                        downloads = container.downloads(),
                        message = "已保存 ${target.name}",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(downloadProgress = it.downloadProgress - key, message = describe(error))
                }
            }
        }
    }

    fun refreshDownloads() = _state.update { it.copy(downloads = container.downloads()) }

    fun deleteDownload(file: File) {
        file.delete()
        _state.update { it.copy(downloads = container.downloads()) }
    }

    // ----------------------------------------------------------------- account

    fun signIn(raw: String) {
        viewModelScope.launch {
            try {
                val identity = withContext(Dispatchers.IO) { container.signIn(raw) }
                _state.update { it.copy(signedIn = true, account = identity, message = "已导入账号") }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun signOut() {
        val identity = _state.value.account
        container.signOut(identity)
        _state.update { it.copy(signedIn = false, account = null, message = "已退出账号") }
    }

    fun themeMode(): String = container.themeMode()

    fun setThemeMode(mode: String) = container.setThemeMode(mode)

    fun oledBlack(): Boolean = container.oledBlack()

    fun setOledBlack(enabled: Boolean) = container.setOledBlack(enabled)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        ticker?.cancel()
        player.release()
        super.onCleared()
    }

    private fun describe(error: Exception): String =
        if (error is XmlyException) error.message else (error.message ?: "操作失败")

    private fun fileName(track: Track, url: String): String {
        val extension = url.substringBefore('?').substringAfterLast('.', "m4a").take(5).ifEmpty { "m4a" }
        val safe = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifEmpty { "audio" }
        return "$safe [${track.number}].$extension"
    }
}
