package teikengstudio.xmpro.api

import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.XmlyException

/**
 * HTTP boundary for the Ximalaya mobile API.
 *
 * Mirrors the previous client contract: bounded responses, no redirects, a
 * per-origin cooldown after 429, and a strict distinction between read-only
 * requests (safe to retry) and writes (whose outcome is unknown on failure).
 */
class XmlyHttp(
    private val client: OkHttpClient = defaultClient(),
    private val clock: () -> Instant = { Instant.now() },
    private val searchOrigin: String = SEARCH_ORIGIN,
) {
    private val cooldowns = ConcurrentHashMap<String, Instant>()

    class Outcome(val status: Int, val body: String, val url: String, val retryAfter: String? = null)

    fun get(
        origin: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        credentials: Credentials? = null,
        signed: Boolean = false,
        readOnly: Boolean = true,
        headers: Map<String, String> = emptyMap(),
        responseLimit: Int = MAX_RESPONSE_BYTES,
    ): JSONObject = execute(
        method = "GET",
        origin = origin,
        path = path,
        query = query,
        body = null,
        form = false,
        credentials = credentials,
        signed = signed,
        readOnly = readOnly,
        responseLimit = responseLimit,
        headers = headers,
    )

    /**
     * Follows a media redirect to its final CDN URL.
     *
     * Paid tracks come back as `https://mobile.ximalaya.com/mobile/redirect/...`
     * which answers 302 with an http CDN object; ExoPlayer refuses that
     * cross-protocol hop, so each hop is followed by hand and rewritten to
     * https (the CDN serves the same signed object over TLS).
     */
    fun followRedirect(url: String): String {
        var current = url
        val plain = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        repeat(4) {
            val request = Request.Builder()
                .url(current)
                .header("Range", "bytes=0-0")
                .header("Accept", "*/*")
                .build()
            try {
                plain.newCall(request).execute().use { response ->
                    response.body?.close()
                    if (response.code !in 300..399) return current
                    val location = response.header("Location") ?: return current
                    current = if (location.startsWith("http://")) {
                        location.replaceFirst("http://", "https://")
                    } else {
                        location
                    }
                }
            } catch (_: IOException) {
                return current
            }
        }
        return current
    }

    fun postJson(
        origin: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        json: Map<String, Any?> = emptyMap(),
        credentials: Credentials? = null,
        signed: Boolean = false,
        readOnly: Boolean = true,
        responseLimit: Int = MAX_RESPONSE_BYTES,
    ): JSONObject = execute(
        method = "POST",
        origin = origin,
        path = path,
        query = query,
        body = JSONObject(json).toString(),
        form = false,
        credentials = credentials,
        signed = signed,
        readOnly = readOnly,
        responseLimit = responseLimit,
        headers = emptyMap(),
    )

    fun postForm(
        origin: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>,
        credentials: Credentials? = null,
        signed: Boolean = false,
        readOnly: Boolean = true,
        responseLimit: Int = MAX_RESPONSE_BYTES,
    ): JSONObject = execute(
        method = "POST",
        origin = origin,
        path = path,
        query = query,
        body = form.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        },
        form = true,
        credentials = credentials,
        signed = signed,
        readOnly = readOnly,
        responseLimit = responseLimit,
        headers = emptyMap(),
    )

    private fun execute(
        method: String,
        origin: String,
        path: String,
        query: Map<String, String>,
        body: String?,
        form: Boolean,
        credentials: Credentials?,
        signed: Boolean,
        readOnly: Boolean,
        responseLimit: Int,
        headers: Map<String, String>,
    ): JSONObject {
        if (signed && credentials == null) {
            throw XmlyException("login_required", "签名请求需要登录", definitive = true)
        }
        var waitTotal = Duration.ZERO
        for (attempt in 0 until MAX_ATTEMPTS) {
            awaitCooldown(origin)
            try {
                val outcome = send(method, origin, path, query, body, form, credentials, signed, responseLimit, headers)
                return interpret(outcome, origin, credentials != null, retryAfter(outcome.retryAfter))
            } catch (retry: Retryable) {
                if (!readOnly || attempt == MAX_ATTEMPTS - 1) {
                    throw XmlyException(
                        if (readOnly) "network" else "uncertain",
                        if (readOnly) "喜马拉雅服务暂时不可用" else "请求结果未知，请核对后重试",
                    )
                }
            } catch (io: IOException) {
                if (!readOnly || attempt == MAX_ATTEMPTS - 1) {
                    throw XmlyException(
                        if (readOnly) "network" else "uncertain",
                        if (readOnly) "请求失败或超时" else "请求结果未知，请核对后重试",
                    )
                }
            }
            val backoff = Duration.ofSeconds(1L shl attempt)
            val cooldown = cooldowns[origin]?.let { Duration.between(clock(), it) }
            waitTotal = if (cooldown != null && cooldown > backoff) cooldown else backoff
            sleep(waitTotal)
        }
        throw XmlyException("network", "请求失败")
    }

    private fun send(
        method: String,
        origin: String,
        path: String,
        query: Map<String, String>,
        body: String?,
        form: Boolean,
        credentials: Credentials?,
        signed: Boolean,
        responseLimit: Int,
        headers: Map<String, String>,
    ): Outcome {
        val url: HttpUrl = (origin + path).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Connection", "close")
        headers.forEach { (key, value) -> builder.header(key, value) }
        credentials?.let { builder.header("Cookie", it.cookie) }
        if (signed) {
            val required = credentials ?: throw XmlyException("login_required", "签名请求需要登录", definitive = true)
            builder.header("x-tk", XmlyCrypto.freshTicket(required))
        }
        val mediaType = (if (form) "application/x-www-form-urlencoded" else "application/json").toMediaType()
        if (body != null) {
            builder.method(method, body.toRequestBody(mediaType))
        } else {
            builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { response ->
            val declared = response.header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > responseLimit) {
                throw XmlyException("response_too_large", "响应超过 ${responseLimit / (1024 * 1024)} MiB 上限")
            }
            val stream = response.body?.byteStream()
            val text = stream?.let { readBounded(it, responseLimit) } ?: ""
            return Outcome(response.code, text, url.toString(), response.header("Retry-After"))
        }
    }

    private fun readBounded(stream: java.io.InputStream, limit: Int): String {
        val buffer = ByteArray(16 * 1024)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            if (output.size() + read > limit) {
                throw XmlyException("response_too_large", "响应超过 ${limit / (1024 * 1024)} MiB 上限")
            }
            output.write(buffer, 0, read)
        }
        return output.toString("UTF-8")
    }

    private fun interpret(
        outcome: Outcome,
        origin: String,
        authenticated: Boolean,
        retryAfterHeader: Duration? = null,
    ): JSONObject {
        val status = outcome.status
        if (status == 429) {
            val wait = retryAfterHeader ?: Duration.ofSeconds(30)
            cooldowns[origin] = clock().plus(wait)
            throw XmlyException(
                "rate_limited",
                "请求过于频繁，请稍后重试",
                retryAfter = wait,
                definitive = true,
            )
        }
        if (status == 401 || (status == 403 && authenticated)) {
            throw XmlyException("login_expired", "登录信息已失效，请重新登录", definitive = true)
        }
        if (status in 500..599) throw Retryable()
        if (status != 200) {
            val clientError = status in 400..499
            throw XmlyException(
                if (clientError) "rejected" else "protocol",
                "接口返回 HTTP $status",
                definitive = clientError,
            )
        }
        if (outcome.body.isEmpty()) throw XmlyException("invalid_json", "接口响应为空")
        val payload = try {
            JSONObject(outcome.body)
        } catch (_: Exception) {
            throw XmlyException("invalid_json", "接口返回无效 JSON")
        }
        val expected = if (origin == searchOrigin) "200" else "0"
        val code = payload.opt("ret")?.toString() ?: "0"
        if (code != expected) {
            if (code == "429") {
                cooldowns[origin] = clock().plusSeconds(30)
                throw XmlyException("rate_limited", "请求过于频繁，请稍后重试", Duration.ofSeconds(30), true)
            }
            if (code == "401") {
                throw XmlyException("login_expired", "登录信息已失效，请重新登录", definitive = true)
            }
            if (code in setOf("5", "403", "726")) {
                throw XmlyException(
                    "permission_denied",
                    "未获得本次请求的完整权限，请检查账号、听时和版权限制",
                    definitive = true,
                )
            }
            // The business code is included so a failure can be matched against
            // the official client instead of guessing.
            throw XmlyException("rejected", "喜马拉雅拒绝了请求（ret=$code）", definitive = true)
        }
        return payload
    }

    private fun awaitCooldown(origin: String) {
        val until = cooldowns[origin] ?: return
        val remaining = Duration.between(clock(), until)
        if (!remaining.isNegative && !remaining.isZero) sleep(remaining)
    }

    private fun sleep(duration: Duration) {
        if (duration.isNegative || duration.isZero) return
        try {
            Thread.sleep(duration.toMillis())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw XmlyException("network", "请求已中断")
        }
    }

    private class Retryable : Exception()

    companion object {
        const val SEARCH_ORIGIN = "https://www.ximalaya.com"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_ATTEMPTS = 5

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(25))
            .writeTimeout(Duration.ofSeconds(25))
            .build()

        /** Parses a Retry-After header value as seconds or an HTTP date. */
        fun retryAfter(value: String?): Duration? {
            if (value.isNullOrBlank()) return null
            value.trim().toLongOrNull()?.let { seconds ->
                return if (seconds >= 0) Duration.ofSeconds(seconds) else null
            }
            return runCatching {
                val date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                Duration.between(Instant.now(), date)
            }.getOrNull()?.takeIf { !it.isNegative }
        }
    }
}
