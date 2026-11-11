package teikengstudio.xmpro.api

/**
 * Tracks how far the device clock is from Ximalaya's server clock.
 *
 * Every signed request embeds a millisecond timestamp, and the server rejects
 * anything outside a narrow window with `ret=-400 参数错误`. Emulators and
 * freshly-flashed phones commonly drift by hours, which used to break the
 * listening-time query even though the signature itself was correct.
 *
 * The offset is learned from the `Date` header and the `timestamp` field that
 * every JSON response carries, then added to the local clock for all outgoing
 * timestamps. Observations that would move the clock by more than a day are
 * ignored so a malformed response cannot poison it.
 */
object ServerClock {
    private const val MAX_DRIFT_MS = 24L * 60 * 60 * 1000

    @Volatile
    private var offsetMs: Long = 0

    /** True once a server timestamp has been observed in this process. */
    val synced: Boolean get() = offsetMs != 0L

    /** Current time as the server would report it. */
    fun now(): Long = System.currentTimeMillis() + offsetMs

    fun observeMillis(raw: Long) {
        if (raw <= 0) return
        // Some endpoints report seconds; normalise before comparing.
        val millis = if (raw < 100_000_000_000L) raw * 1000 else raw
        val drift = millis - System.currentTimeMillis()
        if (kotlin.math.abs(drift) > MAX_DRIFT_MS) return
        // Ignore sub-second jitter so the offset stays stable.
        if (kotlin.math.abs(drift) < 1_000) return
        offsetMs = drift
    }

    /** Reads `timestamp` (milliseconds) when a payload carries one. */
    fun observePayload(payload: org.json.JSONObject) {
        observeMillis(payload.optLong("timestamp", 0))
    }

    /** Reads an RFC 1123 `Date` header. */
    fun observeDateHeader(value: String?) {
        if (value.isNullOrBlank()) return
        val parsed = runCatching {
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .parse(value, java.time.Instant::from)
                .toEpochMilli()
        }.getOrNull() ?: return
        observeMillis(parsed)
    }
}
