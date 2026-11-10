package teikengstudio.xmpro.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
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

    private var controller: MediaController? = null
    private var playbackStarted: (() -> Unit)? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val listener = object : Player.Listener {
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

    fun start(url: String, title: String, onStarted: () -> Unit) {
        playbackStarted = onStarted
        withController { media ->
            media.setMediaItem(mediaItemFor(url))
            media.prepare()
            media.play()
            _state.update { it.copy(title = title, playing = true, positionMs = 0, durationMs = 0) }
            playbackStarted?.invoke()
            playbackStarted = null
        }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }

    fun currentPosition(): Long = controller?.currentPosition ?: _state.value.positionMs

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

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
