package teikengstudio.xmpro.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Hosts the single ExoPlayer instance for the process so playback keeps running
 * in the background and keeps a MediaSession for notifications and lockscreen.
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    private val effects = AudioEffects()
    private val effectScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioSessionId = 0

    private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)
    private val previousCommand = SessionCommand(ACTION_PREVIOUS, Bundle.EMPTY)

    private val callback = object : MediaSession.Callback {
        /**
         * Adds episode skipping to the notification layout.
         *
         * The player holds one item at a time, so its own next/previous are not
         * available and the default layout would only offer play/pause.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(previousCommand)
                        .add(nextCommand)
                        .build(),
                )
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build(),
                )
                .setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName("上一集")
                            .setIconResId(android.R.drawable.ic_media_previous)
                            .setSessionCommand(previousCommand)
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("下一集")
                            .setIconResId(android.R.drawable.ic_media_next)
                            .setSessionCommand(nextCommand)
                            .build(),
                    ),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_NEXT -> {
                    PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Next)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_PREVIOUS -> {
                    PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Previous)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Routes track skipping from Bluetooth, headsets and Android Auto to the
         * queue owner. Forwarding these to the player would do nothing, since the
         * player never holds more than one item.
         */
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int = when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> {
                PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Next)
                SessionResult.RESULT_SUCCESS
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> {
                PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Previous)
                SessionResult.RESULT_SUCCESS
            }

            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setRenderersFactory(SmartSkip.renderersFactory(this))
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
            }
        // The session id only exists once an audio renderer is attached, and it
        // changes when the output device does; both cases re-apply the preset.
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                this@PlaybackService.audioSessionId = audioSessionId
                effects.apply(audioSessionId, EffectBus.state.value)
            }
        })
        effectScope.launch {
            EffectBus.state.collect { state -> effects.apply(audioSessionId, state) }
        }
        player = exo
        session = MediaSession.Builder(this, exo)
            .setCallback(callback)
            // The default loader already fetches https artwork, which is what the
            // notification and lock screen need.
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player ?: return
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        effectScope.cancel()
        effects.release()
        session?.run {
            player.clearMediaItems()
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_NEXT = "teikengstudio.xmpro.action.NEXT"
        const val ACTION_PREVIOUS = "teikengstudio.xmpro.action.PREVIOUS"
    }
}

/**
 * Builds the playing item, including the metadata the notification and lock
 * screen display. Without it only the stream URL was known and both surfaces
 * showed an empty title.
 */
internal fun mediaItemFor(
    url: String,
    mediaId: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    durationMs: Long = 0L,
): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            // The catalogue length is known before playback starts; without it
            // the media card has no total to show until the player is prepared,
            // and on some launchers no time at all.
            .setDurationMs(durationMs.takeIf { it > 0 })
            .setArtworkUri(
                artworkUrl
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?.let { android.net.Uri.parse(it) },
            )
            .build(),
    )
    .build()
