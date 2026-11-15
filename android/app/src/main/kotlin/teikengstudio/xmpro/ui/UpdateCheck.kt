package teikengstudio.xmpro.ui

/**
 * When the silent update check is allowed to run.
 *
 * Kept apart from the view model so the boundary can be tested: a bad throttle
 * either spams the API or hides releases for days, and both are invisible in
 * manual testing.
 */
object UpdateCheck {
    /** At most twice a day, matching the about page's wording. */
    const val INTERVAL_MS = 12 * 60 * 60 * 1000L

    fun due(enabled: Boolean, lastCheckedAt: Long, now: Long): Boolean {
        if (!enabled) return false
        // A clock that jumped backwards must not lock the check out forever.
        val elapsed = now - lastCheckedAt
        if (elapsed < 0) return true
        return elapsed >= INTERVAL_MS
    }
}
