package teikengstudio.xmpro.api

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.XmlyException

/**
 * Listening-time welfare: query the task list and claim more minutes.
 *
 * Every claim POST is single-shot; an unknown outcome must be checked in the
 * official app rather than replayed, otherwise minutes could be double counted.
 */
class XmlyRewards(
    private val client: XmlyClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile
    var lastError: String = ""
        private set

    private fun uid(): String {
        val account = client.credentials
            ?: throw XmlyException("login_expired", "领取或查询听时需要有效的登录态", definitive = true)
        if (!Regex("^[1-9][0-9]*$").matches(account.uid)) {
            throw XmlyException("auth_required", "登录态缺少有效账号标识", definitive = true)
        }
        return account.uid
    }

    private fun result(response: JSONObject): JSONObject {
        val data = response.optJSONObject("data")
            ?: throw XmlyException("invalid_response", "福利响应缺少明确结果")
        val success = data.opt("success") as? Boolean
            ?: throw XmlyException("invalid_response", "福利响应缺少明确结果")
        if (!success) {
            throw XmlyException("reward_rejected", "福利请求未成功，请在官方 App 检查", definitive = true)
        }
        return data
    }

    /** Reads the current welfare task list (ad-free tasks included as returned). */
    fun tasks(): JSONObject {
        uid()
        val response = client.request(
            "GET",
            XmlyClient.WELFARE,
            "$PATH/queryVipDurationTask/ts-${clock()}",
        )
        return JSONObject().put("data", result(response))
    }

    /** Claims once, using only fields the caller actually observed. */
    fun claim(fields: Map<String, Any?> = emptyMap()): JSONObject {
        val account = client.credentials
            ?: throw XmlyException("login_expired", "领取听时需要有效的登录态", definitive = true)
        val owner = uid()
        val payload = normalize(fields)
        val requestId = UUID.randomUUID().toString()
        val timestamp = clock()
        val parts = StringBuilder("$requestId&$owner&$timestamp")
        payload["isSecondPhaseCompleted"]?.let { parts.append("&").append(it) }
        payload["requestId"] = requestId
        payload["uid"] = owner.toLong()
        payload["ts"] = timestamp
        payload["signature"] = md5("$parts&$SALT")
        payload["ticket"] = XmlyCrypto.freshTicket(
            account,
            attributes = "b=lite&s=lite_time_reward&u=$owner",
        )
        val response = client.request(
            "POST",
            XmlyClient.WELFARE,
            "$PATH/rewardDuration/ts-$timestamp",
            json = payload,
            readOnly = false,
        )
        return JSONObject().put("data", result(response))
    }

    /**
     * Optional automatic top-up: from below 2 h up to 4 h, at most 64 claims.
     *
     * Any failure stops the loop and marks [lastError] so the account is paused
     * until a human checks the official app.
     */
    fun refresh(enabled: Boolean, quota: () -> Quota): Quota {
        var current = quota()
        lastError = ""
        if (!enabled || current.remainingSeconds >= LOW_WATER) return current
        for (round in 0 until 64) {
            if (!enabled || current.remainingSeconds >= TARGET) break
            val before = current.remainingSeconds
            try {
                claim()
                current = quota()
            } catch (_: Exception) {
                lastError = "领取听时未完成，已停止自动领取"
                break
            }
            if (current.remainingSeconds <= before) break
        }
        return current
    }

    private fun normalize(fields: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val payload = linkedMapOf<String, Any?>()
        for (key in FIELDS) {
            if (!fields.containsKey(key)) continue
            val value = fields[key]
            if (key != "sourceName" && value == null) continue
            if (key == "verifyStr" && (value == null || value == "" || value == false)) continue
            when (value) {
                is String -> if (value.length > 8192) {
                    throw XmlyException("invalid_reward", "福利请求字段过长", definitive = true)
                }

                is Double -> if (!value.isFinite()) {
                    throw XmlyException("invalid_reward", "福利请求字段格式无效", definitive = true)
                }

                is Number, is Boolean, null -> Unit
                else -> throw XmlyException("invalid_reward", "福利请求字段格式无效", definitive = true)
            }
            payload[key] = value
        }
        return payload
    }

    companion object {
        private const val PATH = "/lite-earn/ting/welfare"
        private const val SALT = "0ab3443b71834b33be675af744d76ef0aaa74cd2b60c4b67"
        private val FIELDS = listOf(
            "adId",
            "adResponseId",
            "isUnisdk",
            "ecpm",
            "verifyStr",
            "isSecondPhaseCompleted",
            "sourceName",
        )
        /** Below two hours the refill loop starts. */
        const val LOW_WATER = 7200
        /** It stops once four hours are available. */
        const val TARGET = 14400

        internal fun md5(value: String): String =
            MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
