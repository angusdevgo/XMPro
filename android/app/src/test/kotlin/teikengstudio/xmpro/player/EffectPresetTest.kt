package teikengstudio.xmpro.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EffectPresetTest {

    @Test
    fun `known ids round trip`() {
        for (preset in EffectPreset.entries) {
            assertEquals(preset, EffectPreset.of(preset.id))
        }
    }

    @Test
    fun `unknown or missing ids fall back to off`() {
        assertEquals(EffectPreset.Off, EffectPreset.of(null))
        assertEquals(EffectPreset.Off, EffectPreset.of(""))
        assertEquals(EffectPreset.Off, EffectPreset.of("reverb"))
    }

    @Test
    fun `bus clamps the strength it publishes`() {
        EffectBus.publish(EffectPreset.Bass, 140)
        assertEquals(100, EffectBus.state.value.strength)
        EffectBus.publish(EffectPreset.Bass, -20)
        assertEquals(0, EffectBus.state.value.strength)
        EffectBus.publish(EffectPreset.Off, 50)
        assertEquals(EffectPreset.Off, EffectBus.state.value.preset)
    }
}
