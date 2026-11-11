package teikengstudio.xmpro.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check against the project's own GitHub releases.
 *
 * GitHub is often slow or unreachable on mainland networks, so the check and
 * the download can be routed through a mirror. The mirror is a prefix that is
 * prepended to the plain GitHub URL, which is how the common accelerators work.
 */
object Updater {
    private const val OWNER = "angusdevgo"
    private const val REPO = "XMPro"
    private const val LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val WEB_URL = "https://github.com/$OWNER/$REPO"

    /** Common prefixes; users can also type their own. */
    val MIRRORS = listOf(
        "" to "直连 GitHub",
        "https://ghfast.top/" to "ghfast.top",
        "https://gh-proxy.com/" to "gh-proxy.com",
        "https://ghproxy.net/" to "ghproxy.net",
    )

    class Release(
        val tag: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    )

    private fun proxied(url: String, mirror: String): String =
        if (mirror.isBlank()) url else mirror + url

    /**
     * Returns the newest release, or null when it is not newer than [current].
     *
     * The API call stays direct: the mirrors only forward release downloads,
     * so proxying `api.github.com` through them just times out. [mirror] is
     * applied to the APK link instead.
     */
    fun latest(current: String, mirror: String): Release {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "XMPro")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("无法连接 GitHub（HTTP ${connection.responseCode}），请稍后重试")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONObject(body)
            val tag = payload.optString("tag_name").trim()
            if (tag.isEmpty()) error("更新信息无效")
            val newer = compare(tag.removePrefix("v"), current.removePrefix("v")) > 0
            if (!newer) error("已是最新版本")
            val assets = payload.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        apk = proxied(asset.optString("browser_download_url"), mirror)
                        break
                    }
                }
            }
            return Release(
                tag = tag,
                notes = payload.optString("body").take(1500),
                apkUrl = apk,
                pageUrl = payload.optString("html_url").ifEmpty { WEB_URL },
            )
        } catch (error: Exception) {
            throw IllegalStateException(friendly(error), error)
        } finally {
            connection.disconnect()
        }
    }

    /** Turns socket noise into something a user can act on. */
    private fun friendly(error: Exception): String = when {
        error is java.net.SocketTimeoutException -> "连接 GitHub 超时，请检查网络后重试"
        error is java.net.UnknownHostException -> "无法解析 GitHub 地址，请检查网络"
        error is java.net.ConnectException -> "连接 GitHub 失败，请检查网络或换用镜像"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "检查更新失败"
    }

    /** Numeric dotted comparison; a missing segment counts as 0. */
    fun compare(left: String, right: String): Int {
        val a = left.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
