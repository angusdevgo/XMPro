package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `a never checked install asks immediately`() {
        assertTrue(UpdateCheck.due(enabled = true, lastCheckedAt = 0L, now = now))
    }

    @Test
    fun `the switch turns the check off`() {
        assertFalse(UpdateCheck.due(enabled = false, lastCheckedAt = 0L, now = now))
    }

    @Test
    fun `the window is twelve hours`() {
        assertFalse(UpdateCheck.due(true, now - 11 * 60 * 60 * 1000L, now))
        assertFalse(UpdateCheck.due(true, now - UpdateCheck.INTERVAL_MS + 1_000, now))
        assertTrue(UpdateCheck.due(true, now - UpdateCheck.INTERVAL_MS, now))
    }

    @Test
    fun `a clock that moved backwards does not lock the check out`() {
        assertTrue(UpdateCheck.due(true, now + 86_400_000L, now))
    }
}
