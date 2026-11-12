package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedTest {

    @Test
    fun `stores values snapped to the slider step`() {
        assertEquals(0.55f, normalizeSpeed(0.53f))
        assertEquals(0.55f, normalizeSpeed(0.55f))
        assertEquals(1.35f, normalizeSpeed(1.34f))
    }

    @Test
    fun `clamps to the offered range`() {
        assertEquals(MIN_SPEED, normalizeSpeed(0.1f))
        assertEquals(MAX_SPEED, normalizeSpeed(9f))
        assertEquals(MIN_SPEED, normalizeSpeed(-2f))
    }

    @Test
    fun `falls back to normal speed for a corrupt value`() {
        assertEquals(1f, normalizeSpeed(Float.NaN))
        assertEquals(1f, normalizeSpeed(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `keeps the default untouched`() {
        assertEquals(1f, normalizeSpeed(1f))
    }
}
