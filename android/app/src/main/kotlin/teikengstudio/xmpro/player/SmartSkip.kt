package teikengstudio.xmpro.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

/**
 * Smart head/tail skipping.
 *
 * The official client relies on Media3's silence skipper rather than a fixed
 * offset: it trims quiet lead-in and trailing music automatically, which is why
 * it needs no configuration. The same processor is wired into the audio sink,
 * and [setEnabled] flips it at runtime without rebuilding the player.
 *
 * Manual trimming (a share of the track) stays available for the episodes where
 * automatic detection gets it wrong.
 */
object SmartSkip {
    const val MODE_SMART = "smart"
    const val MODE_MANUAL = "manual"

    private const val MIN_SILENCE_US = 300_000L
    private const val MAX_KEEP_US = 100_000L

    /** Created once so it can be re-pointed every time the player is rebuilt. */
    private var processor: SilenceSkippingAudioProcessor? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var mode = MODE_SMART

    fun renderersFactory(context: Context): DefaultRenderersFactory {
        val silence = SilenceSkippingAudioProcessor(MIN_SILENCE_US, MAX_KEEP_US, 0x200.toShort())
        processor = silence
        silence.setEnabled(enabled && mode == MODE_SMART)
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray<androidx.media3.common.audio.AudioProcessor>(),
                        silence,
                        androidx.media3.common.audio.SonicAudioProcessor(),
                    ),
                )
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
    }

    /** Applies the current settings; safe to call at any time. */
    fun setEnabled(on: Boolean) {
        enabled = on
        processor?.setEnabled(on && mode == MODE_SMART)
    }

    fun setMode(next: String) {
        mode = if (next == MODE_MANUAL) MODE_MANUAL else MODE_SMART
        processor?.setEnabled(enabled && mode == MODE_SMART)
    }

    val isSmartActive: Boolean get() = enabled && mode == MODE_SMART
}
