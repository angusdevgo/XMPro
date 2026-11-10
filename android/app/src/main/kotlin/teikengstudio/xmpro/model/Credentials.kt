package teikengstudio.xmpro.model

import java.security.MessageDigest
import java.util.Base64

/**
 * Ximalaya account credential: `<uid>&<token>` plus the original device id.
 *
 * The token is never logged; [toString] is redacted on purpose.
 */
class Credentials(rawToken: String, rawDeviceId: String) {
    val token: String = rawToken.trim().replaceFirst(Regex("^1&_token="), "")
    val deviceId: String = rawDeviceId.trim()

    init {
        if (!TOKEN_PATTERN.matches(token)) {
            throw XmlyException("invalid_credentials", "账号凭据必须是数字 UID 与登录令牌", definitive = true)
        }
        if (!DEVICE_PATTERN.matches(deviceId)) {
            throw XmlyException("invalid_credentials", "请填写原设备 ID", definitive = true)
        }
    }

    val uid: String get() = token.substringBefore('&')

    /** Stable account key used by storage and the usage ledger. */
    val identity: String get() = sha256Hex("$uid|$deviceId")

    /** Cache key that also covers the token revision and client profile. */
    val cacheIdentity: String get() = sha256Hex("$token|android&$deviceId&3.4.10")

    val cookie: String
        get() = "1&_token=$token; 1&_device=android&$deviceId&3.4.10; " +
            "channel=and-f5; impl=com.ximalaya.ting.lite; appid=1463"

    fun toJson(): Map<String, String> = mapOf("token" to token, "device_id" to deviceId)

    override fun toString(): String = "Credentials(redacted)"

    companion object {
        private val TOKEN_PATTERN = Regex("^[1-9][0-9]{0,19}&[A-Za-z0-9._~+/=\\-]{1,8192}$")
        private val DEVICE_PATTERN = Regex("^[A-Za-z0-9_.:\\-]{1,128}$")
        private val COOKIE_PAIR =
            Regex("""(?:^|[;:\s"'`])1&_(token|device)\s*=\s*([^;\s"'`\\,]+)""")

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
                "%02x".format(it)
            }

        /**
         * Accepts the same shapes as the previous implementation: a bare cookie
         * string, a JSON object, or a nested `xmly`/`config` wrapper.
         */
        fun parse(input: Any): Credentials {
            var value: Any? = input
            if (value is String) {
                val text = value.trim()
                value = if (text.startsWith("{")) {
                    runCatching { org.json.JSONObject(text) }.getOrElse {
                        throw XmlyException("invalid_credentials", "登录信息 JSON 格式错误", definitive = true)
                    }
                } else {
                    mapOf("cookie" to text)
                }
            }
            val source = when (value) {
                is org.json.JSONObject -> value.toMap()
                is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
                else -> throw XmlyException("invalid_credentials", "登录信息格式错误", definitive = true)
            }
            var map = source
            (map["xmly"] as? Map<*, *>)?.let { map = it.entries.associate { e -> e.key.toString() to e.value } }
            (map["config"] as? Map<*, *>)?.let { map = it.entries.associate { e -> e.key.toString() to e.value } }
            val headers = (map["headers"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            val cookie = headers["cookie"] ?: headers["Cookie"] ?: map["cookie"]
            var token: String? = map["token"] as? String
            var device: String? = (map["device_id"] ?: map["deviceId"]) as? String
            if (cookie is String) {
                val parts = linkedMapOf<String, String>()
                for (match in COOKIE_PAIR.findAll(cookie)) {
                    val key = "1&_${match.groupValues[1]}"
                    val value = match.groupValues[2]
                    val existing = parts[key]
                    if (existing != null && existing != value) {
                        throw XmlyException(
                            "invalid_credentials",
                            "包含多组登录信息，请只保留一个账号的 Cookie",
                            definitive = true,
                        )
                    }
                    parts[key] = value
                }
                token = token ?: parts["1&_token"]
                val fields = (parts["1&_device"] ?: "").split('&')
                if (fields.size > 1) device = device ?: fields[1]
            }
            if (token == null || device == null) {
                throw XmlyException("invalid_credentials", "缺少账号令牌或原设备 ID", definitive = true)
            }
            return Credentials(token, device)
        }

        /** Decodes the persisted `{token, device_id}` shape. */
        fun fromJson(map: Map<*, *>): Credentials {
            val token = map["token"] as? String
            val device = (map["device_id"] ?: map["deviceId"]) as? String
            if (token == null || device == null) {
                throw XmlyException("invalid_credentials", "登录信息缺少令牌或设备 ID", definitive = true)
            }
            return Credentials(token, device)
        }

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

private fun org.json.JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key -> jsonToPlain(opt(key)) }

/** Recursively converts org.json containers so nested payloads stay inspectable. */
private fun jsonToPlain(value: Any?): Any? = when (value) {
    null, org.json.JSONObject.NULL -> null
    is org.json.JSONObject -> value.toMap()
    is org.json.JSONArray -> (0 until value.length()).map { jsonToPlain(value.opt(it)) }
    else -> value
}
