package teikengstudio.xmpro.api

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException

/**
 * SMS login against the passport service.
 *
 * The phone number is RSA encrypted per request, challenges never touch disk,
 * and no POST is ever replayed: a timeout may have already sent an SMS or spent
 * the captcha, so the user has to confirm before trying again.
 */
class XmlyLogin(
    private val client: OkHttpClient = defaultClient(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    /**
     * Device id used for the login cookie. Passing a per-install id keeps the
     * server's device binding (and therefore the listening quota) stable across
     * re-logins instead of treating every login as a new device.
     */
    deviceOverride: String? = null,
) {
    private class Pending(
        val id: String,
        val encryptedMobile: String,
        val deviceId: String,
        val expiresAt: Long,
        var attempts: Int = 0,
    )

    private val random = SecureRandom()
    private val deviceId: String = deviceOverride?.takeIf { it.isNotBlank() } ?: uuid4()
    private val lastSms = mutableMapOf<String, Long>()
    private val usedLots = mutableMapOf<String, Long>()

    @Volatile
    private var pending: Pending? = null

    @Volatile
    private var generation = 0

    /** Clears every in-memory challenge; safe to call from a dismissed dialog. */
    fun clear() {
        synchronized(this) {
            generation++
            pending = null
        }
    }

    fun config(): Map<String, Any> = mapOf(
        "captcha_id" to CAPTCHA_ID,
        "biz" to 1,
        "captcha_fields" to FIELDS.sorted(),
    )

    /**
     * Sends one SMS after a GeeTest challenge and returns the challenge id.
     *
     * [geetest] must carry the five official v4 fields.
     */
    fun sendSms(mobile: String, geetest: Map<String, String>, device: String = ""): String {
        if (!Regex("^\\+?[0-9]{6,18}$").matches(mobile)) {
            throw XmlyException("invalid_mobile", "手机号格式无效", definitive = true)
        }
        for (key in FIELDS) {
            val value = geetest[key] ?: throw XmlyException("invalid_captcha", "安全验证字段无效", definitive = true)
            if (!Regex("^[\\x21-\\x7e]{1,8192}$").matches(value)) {
                throw XmlyException("invalid_captcha", "安全验证字段无效", definitive = true)
            }
        }
        if (!geetest.getValue("captcha_id").equals(CAPTCHA_ID, ignoreCase = true)) {
            throw XmlyException("invalid_captcha", "安全验证字段无效", definitive = true)
        }
        if (device.length > 128 || device.any { it.code <= 32 || it.code >= 127 || it in ";&,\"\\" }) {
            throw XmlyException("invalid_device", "登录设备标识无效", definitive = true)
        }
        val lot = sha256(geetest.getValue("lot_number"))
        val phone = sha256(mobile.replaceFirst(Regex("^\\+"), ""))
        return synchronized(this) {
            val now = clock()
            usedLots.entries.removeAll { now - it.value >= 600_000 }
            lastSms.entries.removeAll { now - it.value >= 180_000 }
            val lotSeen = usedLots[lot]
            if (lotSeen != null && now - lotSeen < 600_000) {
                throw XmlyException("captcha_reused", "本次安全验证已使用，请重新验证", definitive = true)
            }
            val last = lastSms[phone]
            if (last != null && now - last < 180_000) {
                throw XmlyException(
                    "sms_cooldown",
                    "请在 ${((180_000 - (now - last)) + 999) / 1000} 秒后重新发送短信",
                    definitive = true,
                )
            }
            usedLots[lot] = now
            lastSms[phone] = now
            pending = null
            val currentGeneration = generation
            val target = device.ifEmpty { deviceId }
            val encrypted = encryptMobile(mobile)
            val nonce = nonce(target)
            if (currentGeneration != generation) {
                throw XmlyException("challenge_invalid", "短信验证已取消", definitive = true)
            }
            val values = linkedMapOf(
                "mobile" to encrypted,
                "sendType" to "1",
                "biz" to "1",
                "nonce" to nonce,
                "fdsOtp" to JSONObject(FIELDS.associateWith { geetest.getValue(it) }).toString(),
            )
            post("mobile/sms/v3/send", target, values, "sms_send")
            if (currentGeneration != generation) {
                throw XmlyException("challenge_invalid", "短信验证已取消", definitive = true)
            }
            val id = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteArray(32).also(random::nextBytes))
            pending = Pending(id, encrypted, target, now + 600_000)
            id
        }
    }

    /** Verifies the SMS code and exchanges it for credentials plus profile. */
    fun verifySms(challengeId: String, code: String): teikengstudio.xmpro.model.LoginResult {
        if (!Regex("^[A-Za-z0-9_-]{1,128}$").matches(challengeId)) {
            throw XmlyException("challenge_invalid", "需要有效的短信验证请求", definitive = true)
        }
        if (!Regex("^[0-9]{4,8}$").matches(code)) {
            throw XmlyException("invalid_code", "短信验证码必须为 4 至 8 位数字", definitive = true)
        }
        return synchronized(this) {
            val active = pending
            if (active == null || active.id != challengeId) {
                throw XmlyException("challenge_invalid", "短信验证不存在或已完成", definitive = true)
            }
            checkActive(active)
            active.attempts++
            try {
                val nonce = nonce(active.deviceId)
                checkActive(active)
                val key = post(
                    "mobile/sms/v2/verify",
                    active.deviceId,
                    linkedMapOf("mobile" to active.encryptedMobile, "code" to code, "nonce" to nonce),
                    "sms_verify",
                ).let { result ->
                    val primary = result.opt("bizKey")
                    val value = if (primary == null || primary == "" || primary == false || primary == 0) {
                        result.opt("smsKey")
                    } else {
                        primary
                    }
                    val text = value as? String
                    if (text.isNullOrEmpty() || text.length > 8192) {
                        throw XmlyException("invalid_response", "登录响应缺少有效短信密钥")
                    }
                    text
                }
                checkActive(active)
                val loginNonce = nonce(active.deviceId)
                checkActive(active)
                val login = post(
                    "mobile/login/quick/v2",
                    active.deviceId,
                    linkedMapOf(
                        "mobile" to active.encryptedMobile,
                        "smsKey" to key,
                        "nonce" to loginNonce,
                    ),
                    "quick_login",
                )
                checkActive(active)
                android.util.Log.d("XmlyLoginKeys", login.keys().asSequence().toList().toString())
                val nested = login.optJSONObject("data")
                nested?.let { android.util.Log.d("XmlyLoginKeys", "data:" + it.keys().asSequence().toList().toString()) }
                val rawUid = login.opt("uid")?.takeIf { it != "" && it != false && it != 0 } ?: nested?.opt("uid")
                val rawToken = login.opt("token")?.takeIf { it != "" && it != false && it != 0 } ?: nested?.opt("token")
                val uid = rawUid?.toString()
                val token = rawToken as? String
                if (uid == null || !Regex("^[1-9][0-9]{0,19}$").matches(uid) ||
                    token == null || !Regex("^[A-Za-z0-9._~+/=\\-]{1,8192}$").matches(token)
                ) {
                    throw XmlyException("invalid_response", "登录响应缺少有效账号凭据")
                }
                pending = null
                // Profile fields may sit at any depth of the response, so the
                // whole tree is searched by key name.
                fun field(vararg keys: String): String = findString(login, keys.toSet()) ?: ""

                teikengstudio.xmpro.model.LoginResult(
                    credentials = Credentials("$uid&$token", active.deviceId),
                    nickname = field("nickname", "userName", "nickName"),
                    avatarUrl = field("avatar", "avatarUrl", "logoPic", "smallLogo")
                        .takeIf { it.isNotEmpty() },
                    mobile = field("mobile", "phone", "mobilePhone"),
                )
            } finally {
                if (active.attempts >= 5 && pending === active) pending = null
            }
        }
    }

    /** Breadth-first lookup for the first non-blank string under [keys]. */
    private fun findString(root: JSONObject, keys: Set<String>): String? {
        val queue = ArrayDeque<Any>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 2000) {
            visited++
            when (val node = queue.removeFirst()) {
                is JSONObject -> {
                    for (key in node.keys()) {
                        if (key in keys) {
                            val value = node.opt(key) as? String
                            if (!value.isNullOrBlank()) return value
                        }
                    }
                    for (key in node.keys()) {
                        when (val child = node.opt(key)) {
                            is JSONObject, is org.json.JSONArray -> queue.add(child)
                        }
                    }
                }

                is org.json.JSONArray -> for (index in 0 until node.length()) {
                    when (val child = node.opt(index)) {
                        is JSONObject, is org.json.JSONArray -> queue.add(child)
                    }
                }
            }
        }
        return null
    }

    private fun checkActive(value: Pending) {
        if (pending !== value) {
            throw XmlyException("challenge_invalid", "短信验证已取消或被替换", definitive = true)
        }
        if (clock() >= value.expiresAt) {
            pending = null
            throw XmlyException("challenge_expired", "短信验证已过期，请重新发送", definitive = true)
        }
    }

    private fun nonce(device: String): String {
        val result = request("GET", "mobile/nonce/${clock()}", device, null)
        if (result.has("ret")) check(result, "nonce")
        val nonce = result.opt("nonce") as? String
        if (nonce.isNullOrEmpty() || nonce.length > 1024) {
            throw XmlyException("invalid_response", "登录响应缺少有效 nonce")
        }
        return nonce
    }

    private fun post(
        path: String,
        device: String,
        values: Map<String, String>,
        operation: String,
    ): JSONObject {
        val payload = JSONObject(values as Map<*, *>)
        payload.put("signature", signature(values))
        val result = request("POST", path, device, payload.toString())
        check(result, operation)
        return result
    }

    private fun check(result: JSONObject, operation: String) {
        val ret = result.opt("ret")?.toString()
        if (ret == null || !Regex("^-?[0-9]{1,9}$").matches(ret)) {
            throw XmlyException("invalid_response", "登录响应状态无效")
        }
        if (ret != "0") {
            throw XmlyException(
                "${operation}_rejected",
                "登录请求未成功（错误码 $ret），请重新验证",
                definitive = true,
            )
        }
    }

    private fun request(method: String, path: String, device: String, body: String?): JSONObject {
        val url = "$PASSPORT/$path"
        val cookie = "1&_device=android&$device&9.5.1;channel=and-d10;" +
            "impl=com.ximalaya.ting.android;qimei36=;osversion=31;" +
            "device_model=M2102J2SC;XIM=;net-mode=WIFI;manufacturer=Xiaomi;"
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "ting_9.5.1(M2102J2SC,Android31)")
            .header("Cookie", cookie)
            .header("Cookie2", "\$version=1")
            .header("Accept", "application/json, text/plain, */*")
        builder.method(
            method,
            body?.toRequestBody("application/json".toMediaType()),
        )
        val attemptCount = if (method == "GET") 4 else 1
        var lastError: Exception? = null
        for (attempt in 0 until attemptCount) {
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (text.length > MAX_BYTES) {
                        throw XmlyException("response_too_large", "登录响应超出大小限制")
                    }
                    if (response.code != 200) {
                        throw XmlyException(
                            "http_error",
                            "登录服务请求失败（HTTP ${response.code}）",
                            definitive = response.code in 400..499 && response.code != 429,
                        )
                    }
                    return JSONObject(text)
                }
            } catch (error: XmlyException) {
                if (error.code != "http_error" || error.definitive || attempt == attemptCount - 1) throw error
                lastError = error
            } catch (error: Exception) {
                // An unknown POST outcome may already have sent the SMS.
                if (method != "GET" || attempt == attemptCount - 1) {
                    throw XmlyException("network_error", "登录连接失败或超时，请勿自动重发短信")
                }
                lastError = error
            }
            Thread.sleep(500L shl attempt)
        }
        throw XmlyException(
            "network_error",
            lastError?.message ?: "登录连接失败或超时，请勿自动重发短信",
        )
    }

    companion object {
        const val CAPTCHA_ID = "3723312ce42a04b5c0b40e605a882037"
        const val PASSPORT = "https://passport.ximalaya.com"
        val FIELDS = listOf("captcha_id", "lot_number", "pass_token", "gen_time", "captcha_output")
        private const val MAX_BYTES = 512 * 1024
        private const val SALT =
            "MOBILE-V1-PRODUCT-7D74899B338B4F348E2383970CC09991E8E8D8F2BC744EF0BEE94D76D718C089"
        private const val MODULUS =
            "9585a4773abeecb949701d49762f2dfab9599ba19dfe1e1a2fa200e32e0444f426da528912" +
                "d9ea8669515f6f1014c454e1343b97abf7c10fe49d520a6999c66b230e0730c3f8" +
                "02d136a892501ff2b13d699b5c7ecbbfef428ac36d3d83a5bd627f18746a7fdc77" +
                "4c12a38de2760a3b95c653c10d7eb7f84722976251f649556b"

        /** Sorted `k=v&` pairs upper-cased, then SHA-1 with the shared salt. */
        fun signature(values: Map<String, String>): String {
            val canonical = buildString {
                for (key in values.keys.sorted()) {
                    append(key).append('=').append(values[key]).append('&')
                }
            }.uppercase()
            return MessageDigest.getInstance("SHA-1")
                .digest((canonical + SALT).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        /** RSA/ECB/PKCS#1 v1.5 with fresh non-zero padding on every call. */
        fun encryptMobile(mobile: String): String {
            val message = mobile.toByteArray(Charsets.UTF_8)
            val random = SecureRandom()
            val block = ByteArray(128)
            block[0] = 0
            block[1] = 2
            for (index in 2 until (128 - message.size - 1)) {
                var value = 0
                while (value == 0) value = random.nextInt(256)
                block[index] = value.toByte()
            }
            block[128 - message.size - 1] = 0
            message.copyInto(block, 128 - message.size)
            val value = BigInteger(1, block).modPow(
                BigInteger.valueOf(65537),
                BigInteger(MODULUS, 16),
            )
            val hex = value.toString(16).padStart(256, '0')
            val bytes = ByteArray(128) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            return Base64.getEncoder().encodeToString(bytes)
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun uuid4(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
            val hex = bytes.joinToString("") { "%02x".format(it) }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}"
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(25))
            .writeTimeout(java.time.Duration.ofSeconds(25))
            .build()
    }
}
