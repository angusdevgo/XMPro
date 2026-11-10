package teikengstudio.xmpro.data

import java.time.Instant
import org.json.JSONObject
import teikengstudio.xmpro.model.Quota

/** Serialises a verified balance for `state.json` (no secrets involved). */
fun Quota.toJson(): JSONObject = JSONObject().apply {
    put("remainingSeconds", remainingSeconds)
    put("newDevice", newDevice)
    put("checkedAt", checkedAt.toEpochMilli())
}

/** One imported account as shown in settings; never holds the token itself. */
class AccountSummary(
    val identity: String,
    val uid: String,
    val deviceHint: String,
    quota: JSONObject?,
    val paused: Boolean,
    val primary: Boolean,
    val active: Boolean,
) {
    val balance: Quota? = quota?.let {
        runCatching {
            Quota(
                remainingSeconds = it.optInt("remainingSeconds", -1),
                newDevice = it.optBoolean("newDevice", false),
                checkedAt = Instant.ofEpochMilli(it.optLong("checkedAt", 0L)),
            )
        }.getOrNull()?.takeIf { value -> value.remainingSeconds >= 0 }
    }
}
