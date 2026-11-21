package teikengstudio.xmpro.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackUi(
    val title: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val active: Boolean get() = title != null
}

/**
 * Thin client of [PlaybackService]. The service owns the player, so navigation,
 * backgrounding and process-level recreation never interrupt audio.
 */
class PlaybackController(private val context: Context) {
    private val _state = MutableStateFlow(PlaybackUi())
    val state: StateFlow<PlaybackUi> = _state.asStateFlow()

    /** A player failure with where playback was when it happened. */
    class Failure(val message: String, val positionMs: Long)

    /** Last player failure; surfaced to the UI instead of failing silently. */
    private val _error = MutableStateFlow<Failure?>(null)
    val error: StateFlow<Failure?> = _error.asStateFlow()

    /**
     * Emits the media id of the item that ran to its end.
     *
     * The id matters: swapping media items makes the player report ENDED for the
     * outgoing item, and advancing on that would skip the episode just chosen.
     */
    private val _ended = MutableSharedFlow<String?>(extraBufferCapacity = 4)
    val ended: SharedFlow<String?> = _ended.asSharedFlow()

    private var controller: MediaController? = null
    private var playbackStarted: (() -> Unit)? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val httpCode = error.cause?.let { cause ->
                (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            }
            val where = (controller?.currentPosition ?: _state.value.positionMs).coerceAtLeast(0)
            _error.value = Failure(
                message = when {
                    httpCode != null -> "播放失败：服务端返回 HTTP $httpCode"
                    else -> "播放失败：${error.errorCodeName}"
                },
                positionMs = where,
            )
            _state.update { it.copy(playing = false) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _ended.tryEmit(controller?.currentMediaItem?.mediaId)
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            _state.update {
                it.copy(
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = duration,
                )
            }
        }
    }

    /** Connects once; safe to call from every Activity start. */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val mediaController = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = mediaController
            mediaController.addListener(listener)
            pending?.let {
                pending = null
                it(mediaController)
            }
        }, context.mainExecutor)
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /**
     * [startPositionMs] is applied while the item is queued. Seeking right
     * after prepare() is dropped by ExoPlayer because the player is still
     * buffering, which left the intro trim silently ignored.
     */
    fun start(
        url: String,
        title: String,
        artist: String = "",
        artworkUrl: String? = null,
        mediaId: String = "",
        durationMs: Long = 0,
        startPositionMs: Long = 0,
        onStarted: () -> Unit,
    ) {
        playbackStarted = onStarted
        withController { media ->
            _error.value = null
            media.setMediaItem(
                mediaItemFor(
                    url = url,
                    mediaId = mediaId.ifBlank { title },
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                ),
                startPositionMs.coerceAtLeast(0),
            )
            media.prepare()
            media.play()
            _state.update {
                it.copy(
                    title = title,
                    playing = true,
                    positionMs = startPositionMs.coerceAtLeast(0),
                    durationMs = 0,
                )
            }
            playbackStarted?.invoke()
            playbackStarted = null
        }
    }

    /**
     * Play/pause from the UI or the notification.
     *
     * A finished item has to be rewound first: ExoPlayer ignores plain play()
     * at STATE_ENDED, which made the button look broken right after an episode
     * ran out.
     */
    fun toggle() = withController { media ->
        if (media.isPlaying) {
            media.pause()
        } else {
            if (media.playbackState == Player.STATE_ENDED) media.seekTo(0)
            media.play()
        }
    }

    fun currentPosition(): Long = controller?.currentPosition ?: _state.value.positionMs

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun skipBy(deltaMs: Long) = withController { media ->
        val target = (media.currentPosition + deltaMs).coerceIn(0, maxOf(media.duration, 0))
        media.seekTo(target)
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /** App-level volume, independent of the device stream volume. */
    fun setVolume(volume: Float) = withController { it.volume = volume.coerceIn(0f, 1f) }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
        _state.update { PlaybackUi() }
    }

    private inline fun withController(crossinline action: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null) {
            action(existing)
            return
        }
        pending = { action(it) }
        connect()
    }
}
