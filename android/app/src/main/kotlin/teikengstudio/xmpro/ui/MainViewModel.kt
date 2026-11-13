package teikengstudio.xmpro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.AlbumPage
import teikengstudio.xmpro.model.FavoritePage
import teikengstudio.xmpro.model.HistoryRecord
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.HomeTab
import teikengstudio.xmpro.model.SubscriptionItem
import teikengstudio.xmpro.model.SubscriptionPage
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.UpdateItem
import teikengstudio.xmpro.model.UpdatePage
import teikengstudio.xmpro.model.XmlyException
import teikengstudio.xmpro.player.PlaybackQueueBridge

/** Result bundle of the four independent library requests. */
private data class ListeningLoad(
    val subscriptions: Result<SubscriptionPage>,
    val updates: Result<UpdatePage>,
    val history: Result<List<HistoryRecord>>,
    val favorites: Result<FavoritePage>,
)

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
    /** Episode window currently shown for the open album, if limited. */
    val albumRangeStart: Int? = null,
    val albumRangeEnd: Int? = null,
    val albumRangeBusy: Boolean = false,
    val albumLoading: Boolean = false,
    val albumError: String? = null,
    val playing: Track? = null,
    val playingTitle: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val message: String? = null,
    val channels: List<HomeChannel> = emptyList(),
    /** Channels the user removed from the header; editable in the channel sheet. */
    val hiddenChannels: List<HomeChannel> = emptyList(),
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
    val autoClaim: Boolean = true,    val accountBusy: Boolean = false,
    /** Playback queue and the index of the track that is loaded. */
    val queue: List<Track> = emptyList(),
    /** The unfiltered queue, kept so a range can be re-picked. */
    val queueAll: List<Track> = emptyList(),
    /** Active slice of the queue, if the user narrowed it. */
    val queueFrom: Int? = null,
    val queueTo: Int? = null,
    val queueIndex: Int = 0,
    val speed: Float = 1f,
    val sleepAtMillis: Long? = null,
    val bookmarks: List<teikengstudio.xmpro.data.Bookmark> = emptyList(),
    /** Requested audio quality (auto / m4a_128 / m4a_64 / mp3_32). */
    val quality: String = "auto",
    /** Trim points as per-thousand of each track, so they fit any episode length. */
    /** App-level playback volume, 0..1. */
    val playVolume: Float = 1f,
    /** About page: mirror choice and the update probe result. */
    val updateMirror: String = "",
    val updateChecking: Boolean = false,
    val updateResult: String? = null,
    val updateUrl: String? = null,
    /** In-app download progress 0..100, or -1 while a mirror hides the length. */
    val updateProgress: Int = -1,
    /** True once the APK is downloaded and waiting for the installer. */
    val updateReady: Boolean = false,
    /** A newer release was found on start-up; the shell offers it as a dialog. */
    val updatePrompt: Boolean = false,
    /** Version label shown in that dialog. */
    val updatePromptTag: String? = null,
    /** Release notes with markdown markers stripped, for the dialog body. */
    val updateNotes: String? = null,
    /** About page switch: probe for a new release on start-up. */
    val autoCheckUpdate: Boolean = true,
    val skipTrimEnabled: Boolean = false,
    val skipTrimMode: String = teikengstudio.xmpro.player.SmartSkip.MODE_SMART,
    val introSkipPermille: Int = teikengstudio.xmpro.data.DEFAULT_SKIP_PERMILLE,
    val outroSkipPermille: Int = teikengstudio.xmpro.data.DEFAULT_SKIP_PERMILLE,
    val dynamicColor: Boolean = false,
    val seedColor: Int = 0xFFE57373.toInt(),
    /** Open social sheet: null closed, otherwise 关注 / 粉丝 / 订阅 / 收藏. */
    /** Anchor homepage overlay opened from a following / fans row. */
    val userPage: teikengstudio.xmpro.model.UserPage? = null,
    val userLoading: Boolean = false,
    val userError: String? = null,
    val socialKind: String? = null,
    val socialUsers: List<teikengstudio.xmpro.model.SocialUser> = emptyList(),
    val socialLoading: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    /** Comments sheet for the playing track. */
    val commentsOpen: Boolean = false,
    val comments: List<teikengstudio.xmpro.model.Comment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsError: String? = null,
    /** Last page that loaded successfully; 0 before the first reply. */
    val commentsPage: Int = 0,
    val commentsTotal: Int = 0,
    val commentsHasMore: Boolean = false,
)

/**
 * Episodes the album screen should show: everything, or only the selected
 * `albumRangeStart..albumRangeEnd` window.
 */
fun UiState.albumTracks(): List<teikengstudio.xmpro.model.Track> {
    val page = album ?: return emptyList()
    val from = albumRangeStart ?: return page.tracks
    val to = albumRangeEnd ?: Int.MAX_VALUE
    return page.tracks.filterIndexed { index, track ->
        (track.orderNo ?: (index + 1)) in from..to
    }
}

/** Playback speed bounds, shared by the player sheet and the stored setting. */
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 3f
const val SPEED_STEP = 0.05f

/**
 * Clamps a speed to the supported range and snaps it to the sheet's step, so a
 * value restored from storage can never differ from what the slider shows.
 */
fun normalizeSpeed(raw: Float): Float {
    if (!raw.isFinite()) return 1f
    val clamped = raw.coerceIn(MIN_SPEED, MAX_SPEED)
    val snapped = kotlin.math.round(clamped / SPEED_STEP) * SPEED_STEP
    return snapped.coerceIn(MIN_SPEED, MAX_SPEED)
}

/**
 * Turns a release body into plain lines: heading markers and bullet dashes
 * read badly inside a dialog.
 */
internal fun formatReleaseNotes(notes: String): String = notes
    .lineSequence()
    .map { line ->
        when {
            line.startsWith("#") -> ""
            line.startsWith("- ") || line.startsWith("* ") -> "· " + line.drop(2)
            else -> line.trim()
        }
    }
    .filter { it.isNotEmpty() }
    .joinToString("\n")
    .trim()

class MainViewModel(private val context: Context, private val container: AppContainer) : ViewModel() {
    private var ticker: Job? = null
    private val playback = container.playback
    private val login = XmlyLogin(deviceOverride = container.deviceId)

    /**
     * Bumped on every play request. A slow resolve from an earlier tap must not
     * start over the track the user picked afterwards.
     */
    private var playRequest = 0

    private var sessionTrack: Track? = null
    private var sessionStartedAt = 0L

    /**
     * Account that started the current session. Progress must be reported to the
     * account that consumed the listening time, not to whatever is active later.
     */
    private var sessionIdentity: String? = null
    private var sleepJob: Job? = null

    private val initialAccounts = container.accounts()

    private val _state = MutableStateFlow(
        UiState(
            // Any stored account counts as signed in; the active one may be unset
            // after a partial import and must not look like a logout.
            signedIn = initialAccounts.isNotEmpty(),
            account = container.activeAccount ?: initialAccounts.firstOrNull()?.identity,
            accounts = initialAccounts,
            autoClaim = container.autoClaim(),
            bookmarks = container.bookmarks(),
            quality = container.playbackQuality(),
            playVolume = container.playVolume(),
            speed = normalizeSpeed(container.playSpeed()),
            updateMirror = container.updateMirror(),
            autoCheckUpdate = container.autoCheckUpdate(),
            skipTrimEnabled = container.skipTrimEnabled(),
            skipTrimMode = container.skipTrimMode(),
            introSkipPermille = container.introSkipPermille(),
            outroSkipPermille = container.outroSkipPermille(),
            dynamicColor = container.dynamicColor(),
            seedColor = container.seedColor(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Playback position, published at the tick rate.
     *
     * It sits outside [UiState] on purpose: the ticker runs twice a second while
     * audio plays, and copying the whole UI state that often invalidated every
     * screen that receives it, including the home feed.
     */
    private val _position = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _position.asStateFlow()

    init {
        // The service owns the player; mirror its state into the UI model.
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
                        durationMs = player.durationMs,
                    )
                }
                publishPosition()
                if (player.playing) startTicker() else ticker?.cancel()
            }
        }
        // The notification and Bluetooth controls live in the service, which owns
        // no queue: episode changes have to come back through the bridge.
        viewModelScope.launch {
            PlaybackQueueBridge.commands.collect { command ->
                when (command) {
                    PlaybackQueueBridge.Command.Next -> next()
                    PlaybackQueueBridge.Command.Previous -> previous()
                }
            }
        }
        // A track that runs to its end stops the player; roll on to the queue's
        // next episode instead of stopping silently.
        viewModelScope.launch { playback.ended.collect { onTrackEnded() } }
        viewModelScope.launch { autoClaimLoop() }
        // Silent, at most once a day: the shell turns a hit into a dialog.
        viewModelScope.launch {
            delay(2500)
            if (!container.autoCheckUpdate()) return@launch
            val last = container.updateCheckedAt()
            if (System.currentTimeMillis() - last < 12 * 60 * 60 * 1000L) return@launch
            checkUpdate(silent = true)
        }
        if (initialAccounts.isNotEmpty()) syncProfiles()
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
                if (channels.isEmpty()) throw XmlyException("invalid_home", "首页频道为空")
                val (visible, hidden) = splitChannels(channels)
                val selected = _state.value.channelId.takeIf { id -> visible.any { it.pageId == id } }
                    ?: visible.first().pageId
                _state.update {
                    it.copy(channels = visible, hiddenChannels = hidden, channelId = selected)
                }
                loadChannel(experience, selected, null)
            } catch (error: Exception) {
                _state.update { it.copy(homeLoading = false, homeError = describe(error)) }
            }
        }
    }

    /**
     * Applies the stored order/hidden lists to the freshly fetched channels.
     * A channel the server dropped is simply ignored, and hiding everything is
     * treated as "show all" so the header can never end up empty.
     */
    private fun splitChannels(all: List<HomeChannel>): Pair<List<HomeChannel>, List<HomeChannel>> {
        val (order, hidden) = container.channelPreferences()
        val byId = all.associateBy { it.pageId }
        val ordered = order.mapNotNull { byId[it] } + all.filter { it.pageId !in order }
        val visible = ordered.filter { it.pageId !in hidden }
        if (visible.isEmpty()) return ordered to emptyList()
        return visible to ordered.filter { it.pageId in hidden }
    }

    private fun persistChannelLayout(visible: List<HomeChannel>, hidden: List<HomeChannel>) {
        runCatching {
            container.setChannelPreferences(
                order = (visible + hidden).map { it.pageId },
                hidden = hidden.map { it.pageId }.toSet(),
            )
        }
    }

    /** Moves a header channel by [delta] slots (-1 left, +1 right). */
    fun moveChannel(pageId: Int, delta: Int) {
        val current = _state.value
        val list = current.channels.toMutableList()
        val index = list.indexOfFirst { it.pageId == pageId }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.add(target, list.removeAt(index))
        persistChannelLayout(list, current.hiddenChannels)
        _state.update { it.copy(channels = list) }
    }

    /** Removes a channel from the header; the last one cannot be removed. */
    fun hideChannel(pageId: Int) {
        val current = _state.value
        if (current.channels.size <= 1) {
            _state.update { it.copy(message = "至少保留一个频道") }
            return
        }
        val channel = current.channels.firstOrNull { it.pageId == pageId } ?: return
        val visible = current.channels - channel
        val hidden = current.hiddenChannels + channel
        persistChannelLayout(visible, hidden)
        _state.update { it.copy(channels = visible, hiddenChannels = hidden) }
        if (current.channelId == pageId) selectChannel(visible.first().pageId)
    }

    fun showChannel(pageId: Int) {
        val current = _state.value
        val channel = current.hiddenChannels.firstOrNull { it.pageId == pageId } ?: return
        val hidden = current.hiddenChannels - channel
        val visible = current.channels + channel
        persistChannelLayout(visible, hidden)
        _state.update { it.copy(channels = visible, hiddenChannels = hidden) }
    }

    /** Drops the stored customisation and restores the server order. */
    fun resetChannels() {
        runCatching { container.setChannelPreferences(emptyList(), emptySet()) }
        loadHome()
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

    fun closeAlbum() = _state.update {
        it.copy(
            album = null,
            albumError = null,
            albumLoading = false,
            albumRangeStart = null,
            albumRangeEnd = null,
            albumRangeBusy = false,
        )
    }

    /**
     * Limits the episode list to `[start]..[end]` and loads whatever album pages
     * that window needs. Long serials run to thousands of episodes while the
     * album endpoint pages 200 at a time, so jumping to 900..1000 has to fetch.
     */
    fun setAlbumRange(start: Int?, end: Int?) {
        val page = _state.value.album ?: return
        if (start == null || end == null) {
            _state.update { it.copy(albumRangeStart = null, albumRangeEnd = null) }
            return
        }
        val total = maxOf(page.total, page.tracks.size)
        val from = start.coerceIn(1, total)
        val to = end.coerceIn(from, total)
        if (page.tracks.size >= to) {
            _state.update { it.copy(albumRangeStart = from, albumRangeEnd = to) }
            return
        }
        _state.update { it.copy(albumRangeStart = from, albumRangeEnd = to, albumRangeBusy = true) }
        viewModelScope.launch {
            try {
                val size = teikengstudio.xmpro.model.XMLY_ALBUM_PAGE_SIZE
                val firstPage = (page.tracks.size / size) + 1
                val lastPage = (to + size - 1) / size
                val merged = withContext(Dispatchers.IO) {
                    val client = container.client(container.activeAccount)
                    var tracks = page.tracks
                    for (index in firstPage..lastPage) {
                        val next = client.album(page.album.id, page = index)
                        tracks = (tracks + next.tracks).distinctBy { track -> track.id }
                    }
                    tracks.sortedBy { track -> track.orderNo ?: Int.MAX_VALUE }
                }
                _state.update { current ->
                    val existing = current.album ?: return@update current
                    current.copy(
                        album = teikengstudio.xmpro.model.AlbumPage(
                            album = existing.album,
                            tracks = merged,
                            page = existing.page,
                            pageSize = existing.pageSize,
                            total = existing.total,
                        ),
                        albumRangeBusy = false,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(albumRangeBusy = false, message = describe(error)) }
            }
        }
    }

    // ------------------------------------------------------------------ player

    fun play(track: Track, resumePositionMs: Long = 0) = playQueue(listOf(track), 0, resumePositionMs)

    /**
     * Plays [track] together with the album page it belongs to.
     *
     * Both history entries and search results only carry a single episode, so
     * playing them directly left the queue with one item: next/previous did
     * nothing and playback stopped at the end. The containing page is loaded
     * instead, which keeps neighbouring episodes reachable; the page is found
     * from the episode ordinal so no extra lookup is needed first.
     */
    fun playInAlbum(track: Track, resumePositionMs: Long = 0) {
        val album = track.albumKey
        if (album.isEmpty()) {
            play(track, resumePositionMs)
            return
        }
        viewModelScope.launch {
            val located = runCatching {
                withContext(Dispatchers.IO) {
                    val client = container.client(container.activeAccount)
                    val data = client.albumPageWith(album, track.id) ?: return@withContext null
                    val index = data.tracks.indexOfFirst { it.id == track.id }
                    if (index >= 0) data.tracks to index else null
                }
            }.getOrNull()
            if (located != null) {
                playQueue(located.first, located.second, resumePositionMs)
            } else {
                // Album lookup failed or the episode is gone: keep the single
                // track playable rather than dropping the tap.
                play(track, resumePositionMs)
            }
        }
    }

    /** Plays [track] inside [queue] so next/previous walk the same list. */
    fun playQueue(queue: List<Track>, index: Int, resumePositionMs: Long = 0) {
        val track = queue.getOrNull(index) ?: return
        // Report the outgoing track before the player swaps media.
        if (sessionTrack != null && sessionTrack?.id != track.id) syncProgress()
        _state.update {
            it.copy(
                message = null,
                queue = queue,
                queueAll = queue,
                queueFrom = null,
                queueTo = null,
                queueIndex = index,
            )
        }
        val request = ++playRequest
        viewModelScope.launch {
            try {
                val quality = _state.value.quality
                val resolved = withContext(Dispatchers.IO) {
                    container.client(container.activeAccount).resolve(track.number, quality)
                }
                // A newer tap already won; drop this result instead of overriding it.
                if (request != playRequest) return@launch
                val startAt = if (resumePositionMs > 0) {
                    resumePositionMs
                } else {
                    introSeekMs(resolved.track.duration)
                }
                playback.setVolume(_state.value.playVolume)
                playback.setSpeed(_state.value.speed)
                // The resolve payload does not always carry the length, but the
                // catalogue entry does; the notification needs it to show a total.
                val durationSeconds = resolved.track.duration.takeIf { it > 0 }
                    ?: queue.getOrNull(index)?.duration
                    ?: 0.0
                playback.start(
                    url = resolved.url,
                    title = resolved.track.title,
                    artist = listOfNotNull(
                        resolved.track.album.takeIf { it.isNotEmpty() },
                        resolved.track.artist.takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    artworkUrl = resolved.track.coverUrl,
                    mediaId = resolved.track.id,
                    durationMs = (durationSeconds * 1000).toLong(),
                    startPositionMs = startAt,
                ) {
                    if (request != playRequest) return@start
                    sessionTrack = resolved.track
                    sessionStartedAt = System.currentTimeMillis()
                    sessionIdentity = container.activeAccount
                    _state.update { it.copy(playing = resolved.track) }
                    publishQueue()
                    // Record the switch immediately: leaving the app straight
                    // after picking another episode must still move the cloud
                    // history, and the exit write may not survive the process.
                    syncProgress()
                }
            } catch (error: Exception) {
                if (request == playRequest) {
                    _state.update { it.copy(message = describe(error)) }
                }
            }
        }
    }

    /** Plays the currently opened album from [track]'s position. */
    /**
     * Restricts the play queue to one slice of episodes.
     *
     * The queue keeps the slice it was built from, so a second pick still works
     * after the first one narrowed it. Playback stays on the same episode when it
     * falls inside the new slice, otherwise it starts at the slice's first item.
     */
    fun setQueueRange(start: Int?, end: Int?) {
        val current = _state.value
        val full = current.queueAll.ifEmpty { current.queue }
        if (full.isEmpty()) return
        if (start == null || end == null) {
            val index = full.indexOfFirst { it.id == current.playing?.id }.coerceAtLeast(0)
            _state.update { it.copy(queueAll = full, queue = full, queueIndex = index, queueFrom = null, queueTo = null) }
            return
        }
        val window = full.filter { track ->
            val order = track.orderNo ?: (full.indexOf(track) + 1)
            order in start..end
        }
        if (window.isEmpty()) return
        val index = window.indexOfFirst { it.id == current.playing?.id }.coerceAtLeast(0)
        _state.update {
            it.copy(queueAll = full, queue = window, queueIndex = index, queueFrom = start, queueTo = end)
        }
    }

    fun playAlbum(track: Track) {
        val tracks = _state.value.albumTracks().ifEmpty { listOf(track) }
        playQueue(tracks, tracks.indexOf(track).coerceAtLeast(0))
    }

    /** Plays the "listening updates" feed from [track]'s position. */
    fun playUpdates(track: Track) {
        val tracks = _state.value.updates.map { it.track }
        playQueue(tracks, tracks.indexOf(track).coerceAtLeast(0))
    }

    fun next() {
        val current = _state.value
        val index = current.queueIndex + 1
        if (index >= current.queue.size) {
            _state.update { it.copy(message = "已是最后一集") }
            return
        }
        playQueue(current.queue, index)
    }

    fun previous() {
        val current = _state.value
        if (playback.currentPosition() > 5000) {
            playback.seekTo(0)
            return
        }
        val index = current.queueIndex - 1
        if (index < 0) {
            playback.seekTo(0)
            return
        }
        playQueue(current.queue, index)
    }

    fun skipBy(deltaMs: Long) {
        playback.skipBy(deltaMs)
        publishPosition()
    }

    /** Mirrors the queue into the service so notification buttons know the bounds. */
    private fun publishQueue() {
        val current = _state.value
        PlaybackQueueBridge.publish(
            PlaybackQueueBridge.Snapshot(
                index = current.queueIndex,
                size = current.queue.size,
            ),
        )
    }

    private var lastAdvanceAt = 0L

    /**
     * Rolls onto the next episode when one finishes on its own.
     *
     * The trim ticker advances at the tail as well, so the first caller wins and
     * the other one is dropped instead of skipping an episode.
     */
    private fun onTrackEnded() {
        val current = _state.value
        if (current.queue.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastAdvanceAt < 2000L) return
        lastAdvanceAt = now
        syncProgress()
        if (current.queueIndex < current.queue.lastIndex) {
            playQueue(current.queue, current.queueIndex + 1)
        } else {
            _state.update { it.copy(isPlaying = false) }
        }
    }

    // ------------------------------------------------------------------ comments

    /** Opens the comment sheet for whatever is playing. */
    fun openComments() {
        val track = _state.value.playing ?: return
        _state.update {
            it.copy(
                commentsOpen = true,
                comments = emptyList(),
                commentsPage = 0,
                commentsTotal = 0,
                commentsHasMore = false,
                commentsError = null,
            )
        }
        loadComments(track.number, 1)
    }

    fun closeComments() { _state.update { it.copy(commentsOpen = false) } }

    fun loadMoreComments() {
        val current = _state.value
        if (current.commentsLoading || !current.commentsHasMore) return
        val track = current.playing ?: return
        loadComments(track.number, current.commentsPage + 1)
    }

    fun retryComments() {
        val track = _state.value.playing ?: return
        loadComments(track.number, 1)
    }

    private fun loadComments(number: String, page: Int) {
        val identity = container.activeAccount
        _state.update { it.copy(commentsLoading = true, commentsError = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    container.client(identity).trackComments(number, page)
                }
                _state.update { state ->
                    state.copy(
                        comments = if (page <= 1) result.comments else state.comments + result.comments,
                        commentsPage = result.page,
                        commentsTotal = result.total,
                        commentsHasMore = result.hasMore,
                        commentsLoading = false,
                        commentsError = null,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(commentsLoading = false, commentsError = describe(error))
                }
            }
        }
    }

    /** Applies a speed picked in the player sheet and remembers it. */
    fun setSpeed(speed: Float) {
        val value = normalizeSpeed(speed)
        playback.setSpeed(value)
        container.setPlaySpeed(value)
        _state.update { it.copy(speed = value) }
    }

    /**
     * Writes the current progress before the app leaves the foreground.
     *
     * The cloud history was only written on pause, on a track change and by the
     * 60 second ticker, so closing the app during the first minute of an episode
     * recorded nothing at all.
     */
    fun onAppBackgrounded() {
        if (sessionTrack == null) return
        syncProgress()
    }

    /**
     * Reloads only the cloud history.
     *
     * The history tab shows what other episodes and albums were just played, so
     * it has to be re-read when the tab opens or the app returns; the full
     * listening refresh would also re-download subscriptions and favourites.
     */
    fun refreshHistory() {
        if (!_state.value.signedIn) return
        val identity = container.activeAccount ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { XmlyHistory(container.client(identity)).records() } }
                .onSuccess { records -> _state.update { it.copy(history = records) } }
        }
    }

    /** Reloads the cloud history after the app returns to the foreground. */
    fun onAppForegrounded() = refreshHistory()

    /** [minutes] == null cancels the timer. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(sleepAtMillis = null) }
            return
        }
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAtMillis = deadline, message = "${minutes} 分钟后停止播放") }
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            stopPlayback()
            _state.update { it.copy(sleepAtMillis = null, message = "定时关闭已触发") }
        }
    }

    /** Adds or removes the playing track from favourites. */
    fun toggleFavoriteOfPlaying() {
        val track = _state.value.playing ?: return
        setFavorite(track, track.id !in _state.value.favoriteIds)
    }

    fun togglePlayback() {
        val wasPlaying = _state.value.isPlaying
        playback.toggle()
        if (wasPlaying) {
            // Pausing ends one listening interval; resuming starts a new one.
            syncProgress()
            sessionStartedAt = System.currentTimeMillis()
        }
    }

    fun seekTo(positionMs: Long) {
        playback.seekTo(positionMs)
        // The ticker is cancelled while paused, so the bar would freeze otherwise.
        publishPosition()
    }

    /** Current position snapshot for the progress bar. */
    private fun publishPosition() {
        val position = playback.currentPosition()
        if (position != _position.value) _position.value = position
    }

    fun stopPlayback() {
        lastAdvanceAt = 0L
        publishQueue()
        // Cancel any in-flight resolve so stopping cannot be undone by a late reply.
        playRequest++
        syncProgress()
        sessionTrack = null
        sessionStartedAt = 0
        sessionIdentity = null
        playback.stop()
        ticker?.cancel()
        _position.value = 0L
        _state.update { it.copy(playing = null) }
    }

    // ---------------------------------------------------------------- settings

    fun setQuality(quality: String) {
        runCatching { container.setPlaybackQuality(quality) }
        _state.update { it.copy(quality = quality) }
    }

    fun setSkipTrim(
        enabled: Boolean = _state.value.skipTrimEnabled,
        intro: Int = _state.value.introSkipPermille,
        outro: Int = _state.value.outroSkipPermille,
        mode: String = _state.value.skipTrimMode,
    ) {
        val head = intro.coerceIn(0, teikengstudio.xmpro.data.MAX_SKIP_PERMILLE)
        val tail = outro.coerceIn(0, teikengstudio.xmpro.data.MAX_SKIP_PERMILLE)
        val trimmed = mode.ifEmpty { teikengstudio.xmpro.player.SmartSkip.MODE_SMART }
        runCatching { container.setSkipTrim(enabled, head, tail, trimmed) }
        // Smart mode is enforced inside the audio sink, manual mode by seeking.
        teikengstudio.xmpro.player.SmartSkip.setMode(trimmed)
        teikengstudio.xmpro.player.SmartSkip.setEnabled(enabled)
        _state.update {
            it.copy(
                skipTrimEnabled = enabled,
                skipTrimMode = trimmed,
                introSkipPermille = head,
                outroSkipPermille = tail,
            )
        }
    }

    fun setUpdateMirror(mirror: String) {
        runCatching { container.setUpdateMirror(mirror) }
        _state.update { it.copy(updateMirror = mirror, updateResult = null, updateUrl = null) }
    }

    /** Probes the project's GitHub releases for a newer build. */
    /**
     * Looks for a newer release.
     *
     * @param silent automatic check on start-up: no busy indicator, and a
     * failure stays invisible instead of reporting a network problem.
     */
    fun checkUpdate(silent: Boolean = false) {
        if (_state.value.updateChecking) return
        if (!silent) _state.update { it.copy(updateChecking = true, updateResult = null, updateUrl = null) }
        val mirror = _state.value.updateMirror
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        }.getOrDefault("0")
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { teikengstudio.xmpro.updater.Updater.latest(current, mirror) }
            }
            val release = outcome.getOrNull()
            container.setUpdateCheckedAt(System.currentTimeMillis())
            _state.update {
                it.copy(
                    updateChecking = false,
                    updateResult = when {
                        release != null -> {
                            val suffix = if (release.notes.isBlank()) "" else "\n\n" + release.notes
                            "发现新版本 ${release.tag}" + suffix
                        }

                        // A silent check never reports its own failure.
                        silent -> it.updateResult
                        else -> outcome.exceptionOrNull()?.message ?: "检查失败"
                    },
                    updateUrl = release?.apkUrl ?: release?.pageUrl,
                    updateProgress = -1,
                    updateReady = false,
                    updatePrompt = release != null,
                    updatePromptTag = release?.tag,
                    updateNotes = release?.notes?.let(::formatReleaseNotes),
                )
            }
            pendingRelease = release
            pendingFile = null
        }
    }

    fun clearUpdateResult() = _state.update {
        it.copy(updateResult = null, updateUrl = null, updateProgress = -1, updateReady = false)
    }

    private var pendingRelease: teikengstudio.xmpro.updater.Updater.Release? = null
    private var pendingFile: java.io.File? = null

    /**
     * Downloads the new APK in-app and verifies its SHA-256 against the release's
     * SHA256SUMS before the installer ever sees it.
     */
    /**
     * Turns the start-up probe on or off.
     *
     * Re-enabling clears the throttle so the next launch checks right away
     * instead of waiting out the previous window.
     */
    fun setAutoCheckUpdate(enabled: Boolean) {
        container.setAutoCheckUpdate(enabled)
        if (enabled) container.setUpdateCheckedAt(0L)
        _state.update {
            it.copy(
                autoCheckUpdate = enabled,
                message = if (enabled) "已开启自动检查更新" else "已关闭自动检查更新",
            )
        }
    }

    fun dismissUpdatePrompt() =
        _state.update { it.copy(updatePrompt = false, updatePromptTag = null) }

    fun downloadUpdate() {
        val release = pendingRelease ?: return
        val url = release.apkUrl ?: return
        if (_state.value.updateProgress >= 0) return
        _state.update { it.copy(updateProgress = 0, updateReady = false) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val expected = release.sumsUrl
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { sumsUrl -> teikengstudio.xmpro.updater.Updater.checksums(sumsUrl)[release.apkName] }
                teikengstudio.xmpro.updater.Updater.download(
                    context = context,
                    url = url,
                    tag = release.tag,
                    expectedSha = expected,
                ) { percent ->
                    _state.update { state ->
                        if (state.updateProgress == percent) state else state.copy(updateProgress = percent)
                    }
                }
            }
            when (outcome) {
                is teikengstudio.xmpro.updater.Updater.Download.Done -> {
                    pendingFile = outcome.file
                    _state.update {
                        it.copy(
                            updateReady = true,
                            updateProgress = 100,
                            updateResult = if (outcome.verified) {
                                "已下载并通过 SHA-256 校验，点「安装」继续。"
                            } else {
                                "已下载，但该版本没有提供校验值，无法确认文件完整。"
                            },
                        )
                    }
                }

                is teikengstudio.xmpro.updater.Updater.Download.Failed -> {
                    _state.update { it.copy(updateProgress = -1, updateResult = outcome.message) }
                }
            }
        }
    }

    /** Hands the verified file to the system installer. */
    fun installUpdate() {
        val file = pendingFile ?: return
        val opened = runCatching {
            teikengstudio.xmpro.updater.Updater.install(context, file)
        }.getOrElse { failure ->
            _state.update { it.copy(updateResult = "无法启动安装：" + (failure.message ?: "未知错误")) }
            return
        }
        if (!opened) {
            _state.update {
                it.copy(updateResult = "请在系统设置里允许 XMPro 安装应用，然后回来再点一次「安装」。")
            }
        }
    }

    fun openLink(url: String) = teikengstudio.xmpro.updater.Updater.open(context, url)

    /**
     * Copies the account's login cookie so it can be pasted into another device.
     *
     * Marked sensitive so the system does not preview the token on screen.
     */
    fun copyCookie(identity: String) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { container.exportCookie(identity) }
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("XMPro 登录信息", text).apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        description.extras = android.os.PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                }
                clipboard.setPrimaryClip(clip)
                _state.update {
                    it.copy(message = "已复制登录信息，可粘贴到其他设备的 Cookie 导入")
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    /** Applies the volume immediately and remembers it for the next track. */
    fun setVolume(volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        runCatching { container.setPlayVolume(level) }
        playback.setVolume(level)
        _state.update { it.copy(playVolume = level) }
    }

    fun setDynamicColor(enabled: Boolean) {
        runCatching { container.setDynamicColor(enabled) }
        _state.update { it.copy(dynamicColor = enabled) }
    }

    // -------------------------------------------------------------------- user

    /** Opens an anchor homepage; needs no session. */
    fun openUser(uid: String) {
        if (uid.isEmpty()) return
        _state.update { it.copy(userPage = null, userLoading = true, userError = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    XmlyExperience(container.client(container.activeAccount)).userPage(uid)
                }
            }
            _state.update {
                it.copy(
                    userPage = result.getOrNull(),
                    userLoading = false,
                    userError = result.exceptionOrNull()?.let { error -> describe(error) },
                )
            }
        }
    }

    fun closeUser() {
        _state.update { it.copy(userPage = null, userLoading = false, userError = null) }
    }

    // ------------------------------------------------------------------ social

    /** Opens the 我的 sheet for [kind] and fetches its list. */
    fun openSocial(kind: String) {
        _state.update {
            it.copy(socialKind = kind, socialUsers = emptyList(), socialLoading = kind == "following" || kind == "fans")
        }
        if (kind != "following" && kind != "fans") return
        val uid = _state.value.accounts.firstOrNull { it.active }?.uid
            ?: _state.value.accounts.firstOrNull { it.primary }?.uid
            ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val experience = XmlyExperience(container.client(container.activeAccount))
                    if (kind == "following") experience.following(uid) else experience.fans(uid)
                }
            }
            _state.update {
                it.copy(
                    socialUsers = result.getOrDefault(emptyList()),
                    socialLoading = false,
                    message = result.exceptionOrNull()?.let { error -> describe(error) },
                )
            }
        }
    }

    fun closeSocial() {
        _state.update { it.copy(socialKind = null, socialUsers = emptyList(), socialLoading = false) }
    }

    // ------------------------------------------------------------------ backup

    fun exportBackup(uri: android.net.Uri, includeCredentials: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    teikengstudio.xmpro.data.BackupManager(container)
                        .export(context.contentResolver, uri, includeCredentials)
                }
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { bytes -> "已导出备份（${bytes / 1024} KB）" },
                        onFailure = { error -> "导出失败：${describe(error)}" },
                    ),
                )
            }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    teikengstudio.xmpro.data.BackupManager(container)
                        .import(context.contentResolver, uri)
                }
            }
            result.onSuccess {
                refreshAccounts()
                refreshBookmarks()
                refreshListening()
            }
            _state.update {
                it.copy(
                    accounts = container.accounts(),
                    message = result.fold(
                        onSuccess = { value -> "已恢复备份（${value.accounts} 个账号）" },
                        onFailure = { error -> "恢复失败：${describe(error)}" },
                    ),
                    seedColor = container.seedColor(),
                    quality = container.playbackQuality(),
                    playVolume = container.playVolume(),
            updateMirror = container.updateMirror(),
                    skipTrimEnabled = container.skipTrimEnabled(),
                    skipTrimMode = container.skipTrimMode(),
                    introSkipPermille = container.introSkipPermille(),
                    outroSkipPermille = container.outroSkipPermille(),
                )
            }
        }
    }

    fun setSeedColor(argb: Int) {
        runCatching { container.setSeedColor(argb) }
        _state.update { it.copy(seedColor = argb) }
    }

    /**
     * Pushes the current position of [sessionTrack] to the cloud history.
     * Called on pause, track change, and from the ticker, so a killed process
     * loses at most one interval of progress.
     */
    private fun syncProgress() {
        val track = sessionTrack ?: return
        val started = sessionStartedAt
        if (started <= 0) return
        val position = playback.currentPosition() / 1000.0
        val endedAt = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    XmlyHistory(container.client(sessionIdentity ?: container.activeAccount))
                        .sync(track, position, started.coerceAtLeast(1), endedAt)
                }
            }.onFailure { error ->
                // A missing or expired login is the one failure the user can act
                // on; anything else stays silent so playback is never interrupted.
                val code = (error as? XmlyException)?.code
                if (code in setOf("auth_required", "login_required", "login_expired")) {
                    _state.update {
                        it.copy(message = "登录信息已失效，播放进度未同步")
                    }
                }
            }
        }
    }

    /**
     * Where the configured intro trim lands, or 0 when it is off.
     *
     * [trackSeconds] is the catalogue duration, which is known before playback
     * starts; the running player has no duration yet at that point.
     */
    private fun introSeekMs(trackSeconds: Double): Long {
        val current = _state.value
        if (!current.skipTrimEnabled) return 0L
        if (current.skipTrimMode != teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL) return 0L
        if (current.introSkipPermille <= 0) return 0L
        val basis = if (trackSeconds > 1) (trackSeconds * 1000).toLong() else current.durationMs
        return basis * current.introSkipPermille / 1000L
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            var sinceSync = 0
            while (isActive) {
                val position = playback.currentPosition()
                if (position != _position.value) _position.value = position
                val current = _state.value
                val duration = current.durationMs
                val manual = current.skipTrimEnabled &&
                    current.skipTrimMode == teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL
                val outro = if (manual) duration * current.outroSkipPermille / 1000L else 0L
                if (outro > 0 && duration > outro + 2000L && position >= duration - outro) {
                    // Skip the credits by rolling to the next episode.
                    if (current.queueIndex < current.queue.lastIndex) {
                        lastAdvanceAt = System.currentTimeMillis()
                        playQueue(current.queue, current.queueIndex + 1)
                    } else {
                        playback.toggle()
                    }
                }
                sinceSync += 500
                if (sinceSync >= 60_000) {
                    sinceSync = 0
                    syncProgress()
                }
                delay(500)
            }
        }
    }

    // ------------------------------------------------- listening / subscriptions

    fun refreshListening() {
        if (!_state.value.signedIn) {
            _state.update { it.copy(subscriptions = emptyList(), updates = emptyList(), history = emptyList(), favorites = emptyList()) }
            return
        }
        _state.update { it.copy(listeningLoading = true, listeningError = null) }
        viewModelScope.launch {
            val identity = container.activeAccount
            if (identity == null) {
                // Without this the spinner stays up for ever on a half-signed-in state.
                _state.update { it.copy(listeningLoading = false, listeningError = "登录信息已失效") }
                return@launch
            }
            val experience = XmlyExperience(container.client(identity))
            val historyClient = XmlyHistory(container.client(identity))
            // The four sections are independent, so they share one round trip
            // window instead of queueing behind each other.
            val (subscriptions, updates, records, favorites) = coroutineScope {
                val subs = async(Dispatchers.IO) { runCatching { experience.subscriptions(1) } }
                val feed = async(Dispatchers.IO) { runCatching { experience.updates() } }
                val history = async(Dispatchers.IO) { runCatching { historyClient.records() } }
                val favs = async(Dispatchers.IO) { runCatching { experience.favorites(1) } }
                ListeningLoad(subs.await(), feed.await(), history.await(), favs.await())
            }
            // Naming the section makes a failure actionable instead of a bare
            // "首页数据无效" over a screen that mostly loaded.
            val failure = listOf(
                "订阅" to subscriptions,
                "更新" to updates,
                "历史" to records,
                "收藏" to favorites,
            ).firstOrNull { it.second.isFailure }
                ?.let { (label, result) -> "${label}加载失败：${describe(result.exceptionOrNull()!!)}" }
            _state.update { state ->
                state.copy(
                    subscriptions = subscriptions.getOrNull()?.items ?: state.subscriptions,
                    moreSubscriptions = subscriptions.getOrNull()?.hasMore ?: state.moreSubscriptions,
                    subscriptionPage = if (subscriptions.isSuccess) 1 else state.subscriptionPage,
                    updates = updates.getOrNull()?.items ?: state.updates,
                    moreUpdates = updates.getOrNull()?.hasMore ?: state.moreUpdates,
                    nextTimeline = updates.getOrNull()?.nextTimeline ?: state.nextTimeline,
                    history = records.getOrNull() ?: state.history,
                    favorites = favorites.getOrNull()?.items ?: state.favorites,
                    moreFavorites = favorites.getOrNull()?.hasMore ?: state.moreFavorites,
                    favoritePage = if (favorites.isSuccess) 1 else state.favoritePage,
                    favoriteIds = favorites.getOrNull()?.items?.map { track -> track.id }?.toSet()
                        ?: state.favoriteIds,
                    listeningLoading = false,
                    listeningError = failure,
                )
            }
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
                        favoriteIds = it.favoriteIds + result.items.map { track -> track.id },
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
                        favoriteIds = if (favorite) it.favoriteIds + track.id else it.favoriteIds - track.id,
                        message = if (favorite) "已收藏" else "已取消收藏",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    fun setSubscribed(albumId: String, subscribed: Boolean) {
        val open = _state.value.album?.album?.takeIf { it.id == albumId }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val identity = container.activeAccount ?: throw XmlyException("login_required", "请先登录")
                    XmlyExperience(container.client(identity)).setSubscribed(albumId, subscribed)
                }
                // Subscribing used to leave the list untouched, so the album page
                // kept reading "not subscribed" until the whole app reloaded.
                _state.update { state ->
                    val rows = if (subscribed) {
                        if (state.subscriptions.any { it.album.id == albumId }) {
                            state.subscriptions
                        } else {
                            listOf(
                                SubscriptionItem(
                                    album = open ?: Album(rawId = albumId),
                                    latestTitle = "",
                                    unreadCount = 0,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            ) + state.subscriptions
                        }
                    } else {
                        state.subscriptions.filterNot { it.album.id == albumId }
                    }
                    state.copy(
                        subscriptions = rows,
                        message = if (subscribed) "已订阅" else "已取消订阅",
                    )
                }
                refreshSubscriptions(if (subscribed) open else null)
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            }
        }
    }

    /**
     * Re-reads the first subscription page so the optimistic row is replaced.
     *
     * [keep] survives the merge: a new subscription can fall outside the first
     * page, and dropping it there would flip the album back to "not subscribed".
     */
    private suspend fun refreshSubscriptions(keep: Album? = null) {
        val identity = container.activeAccount ?: return
        runCatching {
            withContext(Dispatchers.IO) { XmlyExperience(container.client(identity)).subscriptions(1) }
        }.onSuccess { page ->
            val items = if (keep != null && page.items.none { it.album.id == keep.id }) {
                listOf(SubscriptionItem(keep, "", 0, System.currentTimeMillis())) + page.items
            } else {
                page.items
            }
            _state.update {
                it.copy(subscriptions = items, moreSubscriptions = page.hasMore, subscriptionPage = 1)
            }
        }
    }

    // ---------------------------------------------------------------- bookmarks

    fun refreshBookmarks() = _state.update { it.copy(bookmarks = container.bookmarks()) }

    /** Saves the current playback position as a bookmark. */
    fun saveBookmark() {
        val track = _state.value.playing ?: return
        val position = playback.currentPosition()
        runCatching {
            val list = container.addBookmark(track, position)
            _state.update { it.copy(bookmarks = list, message = "已保存书签") }
        }.onFailure { error -> _state.update { it.copy(message = describe(error)) } }
    }

    fun removeBookmark(trackId: String) {
        runCatching {
            val list = container.removeBookmark(trackId)
            _state.update { it.copy(bookmarks = list, message = "已删除书签") }
        }.onFailure { error -> _state.update { it.copy(message = describe(error)) } }
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

    /** Pulls nickname/avatar for every stored account; silent unless manual. */
    fun syncProfiles(manual: Boolean = false) {
        viewModelScope.launch {
            val accounts = container.accounts()
            var failure: String? = null
            for (account in accounts) {
                runCatching { withContext(Dispatchers.IO) { container.refreshProfile(account.identity) } }
                    .onFailure { failure = it.message }
            }
            val refreshed = container.accounts()
            val named = refreshed.any { it.nickname.isNotEmpty() }
            _state.update {
                it.copy(
                    accounts = refreshed,
                    message = when {
                        !manual -> it.message
                        named -> "已同步账号资料"
                        else -> failure ?: "该账号没有可同步的公开资料"
                    },
                )
            }
        }
    }

    fun renameAccount(identity: String, nickname: String) {
        runCatching { container.renameAccount(identity, nickname) }
            .onSuccess { _state.update { it.copy(accounts = container.accounts(), message = "已更新账号名") } }
            .onFailure { error -> _state.update { it.copy(message = describe(error)) } }
    }

    fun refreshAccounts() {
        val list = container.accounts()
        _state.update {
            it.copy(
                accounts = list,
                autoClaim = container.autoClaim(),
                signedIn = list.isNotEmpty(),
                account = container.activeAccount ?: list.firstOrNull()?.identity,
            )
        }
    }

    fun setPrimaryAccount(identity: String) = accountAction({ container.setPrimaryAccount(identity) }, "已设为主账号")

    /**
     * Switches the account in use and reloads everything that belongs to it.
     *
     * The previous account's lists are cleared first, otherwise its subscriptions
     * and favourites stay on screen until each request happens to come back.
     */
    fun switchAccount(identity: String) {
        if (identity == _state.value.account) return
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            try {
                withContext(Dispatchers.IO) { container.switchAccount(identity) }
                _state.update {
                    it.copy(
                        signedIn = true,
                        account = identity,
                        accounts = container.accounts(),
                        subscriptions = emptyList(),
                        moreSubscriptions = false,
                        subscriptionPage = 0,
                        updates = emptyList(),
                        moreUpdates = false,
                        nextTimeline = null,
                        history = emptyList(),
                        favorites = emptyList(),
                        moreFavorites = false,
                        favoritePage = 0,
                        favoriteIds = emptySet(),
                        bookmarks = container.bookmarks(),
                        message = "已切换账号",
                    )
                }
                refreshListening()
                loadHome()
            } catch (error: Exception) {
                _state.update { it.copy(message = describe(error)) }
            } finally {
                _state.update { it.copy(accountBusy = false) }
            }
        }
    }

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
                val result = withContext(Dispatchers.IO) { login.verifySms(challengeId, code) }
                val identity = withContext(Dispatchers.IO) { container.signIn(result, makePrimary) }
                val credentials = result.credentials
                _state.update {
                    it.copy(
                        signedIn = true,
                        account = identity,
                        accounts = container.accounts(),
                        autoClaim = container.autoClaim(),
                        message = "已登录 " + result.nickname.ifEmpty { "账号 ${credentials.uid}" },
                    )
                }
                refreshListening()
                loadHome()
                syncProfiles()
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

    /** Background claim pass; runs on start and then every few minutes. */
    private suspend fun autoClaimLoop() {
        while (true) {
            if (container.autoClaim()) {
                val identity = container.activeAccount
                if (identity != null) {
                    runCatching { withContext(Dispatchers.IO) { container.refreshRewards(identity) } }
                    _state.update { it.copy(accounts = container.accounts()) }
                }
            }
            delay(5 * 60 * 1000L)
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        sleepJob?.cancel()
        login.clear()
        super.onCleared()
    }

    private fun describe(error: Throwable): String =
        if (error is XmlyException) error.message else (error.message ?: "操作失败")
}
