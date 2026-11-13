package teikengstudio.xmpro.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sound presets offered in the player settings.
 *
 * The ids are persisted, so they must stay stable; the labels are what the user
 * reads and can change freely.
 */
enum class EffectPreset(val id: String, val label: String) {
    Off("off", "关闭"),
    Vocal("vocal", "人声增强"),
    Bass("bass", "重低音"),
    Surround("surround", "3D 环绕"),
    Loudness("loudness", "音量均衡"),
    ;

    companion object {
        fun of(id: String?): EffectPreset = entries.firstOrNull { it.id == id } ?: Off
    }
}

/** One effect setting pair, shared between the settings screen and the service. */
data class EffectState(val preset: EffectPreset = EffectPreset.Off, val strength: Int = 50)

/**
 * Settings bridge.
 *
 * The player lives in [PlaybackService] and owns the audio session, while the
 * choices are made in the UI; this object is the only thing they share, which
 * keeps the service free of storage access.
 */
object EffectBus {
    private val _state = MutableStateFlow(EffectState())
    val state: StateFlow<EffectState> = _state.asStateFlow()

    fun publish(preset: EffectPreset, strength: Int) {
        _state.value = EffectState(preset, strength.coerceIn(0, 100))
    }
}

/**
 * Applies [EffectState] to the running audio session.
 *
 * Every effect is optional at the platform level: a device may ship without a
 * virtualizer or refuse an equalizer on a given session. Those cases are simply
 * skipped, so the setting never breaks playback.
 */
class AudioEffects {

    private val active = mutableListOf<AudioEffect>()

    /** Applies [state] to [sessionId]; a session id of 0 tears the chain down. */
    fun apply(sessionId: Int, state: EffectState) {
        release()
        if (sessionId <= 0) return
        if (state.preset == EffectPreset.Off) return
        val level = state.strength.coerceIn(0, 100) / 100f
        runCatching {
            when (state.preset) {
                EffectPreset.Vocal -> equalizer(sessionId) { frequency ->
                    // Trim the rumble, lift the presence range.
                    when {
                        frequency < 250 -> -6 * level
                        frequency in 250..800 -> -2 * level
                        frequency in 800..4000 -> 6 * level
                        else -> 3 * level
                    }
                }

                EffectPreset.Bass -> {
                    equalizer(sessionId) { frequency ->
                        when {
                            frequency < 120 -> 9 * level
                            frequency < 300 -> 4 * level
                            frequency > 4000 -> -3 * level
                            else -> 0f
                        }
                    }
                    val boost = BassBoost(0, sessionId).apply {
                        enabled = true
                        setStrength((level * 800).toInt().toShort())
                    }
                    active += boost
                }

                EffectPreset.Surround -> {
                    val virtualizer = Virtualizer(0, sessionId).apply {
                        enabled = true
                        setStrength((level * 1000).toInt().toShort())
                    }
                    active += virtualizer
                }

                EffectPreset.Loudness -> {
                    val enhancer = LoudnessEnhancer(sessionId).apply {
                        enabled = true
                        setTargetGain((level * 600).toInt())
                    }
                    active += enhancer
                }

                EffectPreset.Off -> Unit
            }
        }
    }

    fun release() {
        for (effect in active) runCatching { effect.enabled = false }
        for (effect in active) runCatching { effect.release() }
        active.clear()
    }

    /** Builds an equalizer whose band gains come from [gainFor] (dB at [gainFor]'s Hz). */
    private fun equalizer(sessionId: Int, gainFor: (Int) -> Float) {
        val eq = Equalizer(0, sessionId)
        active += eq
        eq.enabled = true
        val range = runCatching { eq.bandLevelRange }.getOrNull()
        for (index in 0 until eq.numberOfBands) {
            val band = index.toShort()
            val frequency = runCatching { eq.getCenterFreq(band) / 1000 }.getOrDefault(0)
            if (frequency <= 0) continue
            // Band levels are millibels, so dB has to be scaled by a hundred.
            var millibels = (gainFor(frequency) * 100).toInt().toShort()
            if (range != null) {
                millibels = millibels.coerceIn(range[0], range[1])
            }
            runCatching { eq.setBandLevel(band, millibels) }
        }
    }
}
