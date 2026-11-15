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
    fun `only the launch pair is de-duplicated`() {
        // Cold start and the first foreground event arrive together.
        assertFalse(UpdateCheck.due(true, now - 2_000L, now))
        // Coming back to the app a moment later asks again: there is no window.
        assertTrue(UpdateCheck.due(true, now - 60_000L, now))
        assertTrue(UpdateCheck.due(true, now - UpdateCheck.GAP_MS, now))
    }

    @Test
    fun `a clock that moved backwards does not lock the check out`() {
        assertTrue(UpdateCheck.due(true, now + 86_400_000L, now))
    }
}
