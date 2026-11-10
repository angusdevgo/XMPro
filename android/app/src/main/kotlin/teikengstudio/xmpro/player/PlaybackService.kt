package teikengstudio.xmpro.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Hosts the single ExoPlayer instance for the process so playback keeps running
 * in the background and keeps a MediaSession for notifications and lockscreen.
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build().apply {
            setHandleAudioBecomingNoisy(true)
        }
        player = exo
        session = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player ?: return
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.clearMediaItems()
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

/** Media id used for resolved streams; the URL itself is not persisted. */
internal fun mediaItemFor(url: String): MediaItem = MediaItem.fromUri(url)
