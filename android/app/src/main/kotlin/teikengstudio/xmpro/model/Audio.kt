package teikengstudio.xmpro.model

import java.time.Instant

/** Remaining listening seconds for one account. */
data class Quota(
    val remainingSeconds: Int,
    val newDevice: Boolean,
    val checkedAt: Instant,
) {
    init {
        require(remainingSeconds >= 0) { "remainingSeconds must not be negative" }
    }
}

/** One resolved playable URL and the billing flag that governs it. */
class ResolvedAudio(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val requiresReward: Boolean,
    val track: Track,
    val actualQuality: String,
    val accountIdentity: String,
)

/** Bound on what the ledger accepts, matching the previous implementation. */
const val MAX_USAGE_SECONDS = 24 * 60 * 60
