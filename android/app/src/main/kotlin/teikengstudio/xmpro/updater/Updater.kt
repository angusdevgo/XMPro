package teikengstudio.xmpro.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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
        val apkName: String,
        /** Link to the release's SHA256SUMS asset, when it published one. */
        val sumsUrl: String?,
        val pageUrl: String,
    )

    /** Result of an in-app download: the verified file, or a failure to show. */
    sealed interface Download {
        class Done(val file: File, val verified: Boolean) : Download
        class Failed(val message: String) : Download
    }

    private fun proxied(url: String, mirror: String): String =
        if (mirror.isBlank()) url else mirror + url

    /**
     * Returns the newest release, or null when it is not newer than [current].
     *
     * The API call stays direct: the mirrors only forward release downloads,
     * so proxying `api.github.com` through them just times out. [mirror] is
     * applied to the APK link instead.
     */
    fun latest(current: String, mirror: String): Release? {
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
            // Up to date is a valid answer, not a failure: the caller decides
            // how to say it, and only a real answer may consume the throttle.
            if (compare(tag.removePrefix("v"), current.removePrefix("v")) <= 0) return null
            val assets = payload.optJSONArray("assets")
            var apk: String? = null
            var apkName = ""
            var sums: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    when {
                        name.endsWith(".apk") && apk == null -> {
                            apkName = name
                            apk = proxied(asset.optString("browser_download_url"), mirror)
                        }

                        name == "SHA256SUMS" -> {
                            sums = proxied(asset.optString("browser_download_url"), mirror)
                        }
                    }
                }
            }
            return Release(
                tag = tag,
                notes = payload.optString("body").take(1500),
                apkUrl = apk,
                apkName = apkName,
                sumsUrl = sums,
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

    /**
     * Downloads [url] into the app's private update folder and verifies it.
     *
     * The digest is computed while streaming, so nothing unverified ever sits in
     * a path the installer can reach. [onProgress] receives 0..100, or -1 when
     * the server reports no length (mirrors often strip it).
     */
    fun download(
        context: Context,
        url: String,
        tag: String,
        expectedSha: String?,
        onProgress: (Int) -> Unit,
    ): Download {
        val folder = File(context.filesDir, "update").apply { mkdirs() }
        // Remove leftovers so a failed attempt cannot be installed later.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "xmpro-" + tag.removePrefix("v") + ".apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "XMPro")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return Download.Failed("下载失败（HTTP " + connection.responseCode + "）")
                }
                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            read += count
                            onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            target.delete()
            return Download.Failed(friendly(error))
        }
        if (target.length() <= 0L) {
            target.delete()
            return Download.Failed("下载内容为空，请换用其他镜像")
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expectedSha.isNullOrEmpty()) {
            // Without a published digest the file cannot be verified; say so
            // rather than quietly trusting whatever arrived.
            return Download.Done(target, verified = false)
        }
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            target.delete()
            return Download.Failed("校验值不匹配，文件可能被替换，已删除")
        }
        return Download.Done(target, verified = true)
    }

    /** Reads SHA256SUMS and returns "<file name> -> <digest>" pairs. */
    fun checksums(url: String): Map<String, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "XMPro")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyMap()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val result = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val parts = line.trim().split(WHITESPACE)
                if (parts.size < 2) continue
                val hash = parts[0]
                if (hash.length != 64) continue
                result[parts[1].substringAfterLast('/')] = hash
            }
            return result
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Hands a verified APK to the system installer.
     *
     * Returns false when the user still has to allow "install unknown apps";
     * the matching settings screen is opened in that case.
     */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }

    private val WHITESPACE = Regex("[\\s]+")
}
