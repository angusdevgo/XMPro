package teikengstudio.xmpro.ui

/**
 * When the silent update check is allowed to run.
 *
 * Every entry into the app asks GitHub once, so a published release shows up the
 * next time the user opens the app instead of hours later. [GAP_MS] only stops
 * the two entry points (cold start and the first foreground event) from firing
 * the same check twice in a row.
 */
object UpdateCheck {
    /** De-duplication window for the launch pair, not a rate limit. */
    const val GAP_MS = 10_000L

    fun due(enabled: Boolean, lastCheckedAt: Long, now: Long): Boolean {
        if (!enabled) return false
        // A clock that jumped backwards must not lock the check out.
        val elapsed = now - lastCheckedAt
        if (elapsed < 0) return true
        return elapsed >= GAP_MS
    }
}
