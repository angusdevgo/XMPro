package teikengstudio.xmpro.model

/** Ximalaya identifiers are positive integers, optionally prefixed with `xmly:`. */
private val ID_PATTERN = Regex("^(?:xmly:)?([1-9][0-9]{0,18})$")
private val DIGITS = Regex("^[0-9]+$")
private val COVER_KEYS = listOf(
    "coverLarge",
    "coverMiddle",
    "coverSmall",
    "cover_path",
    "album_cover_path",
    "cover_url",
    "albumImage",
)

fun xmlyIdentifier(value: String): String =
    ID_PATTERN.find(value)?.groupValues?.get(1)
        ?: throw XmlyException("invalid_id", "喜马拉雅 ID 必须是正整数或 xmly:正整数", definitive = true)

fun xmlyPositive(value: Any?): Int? {
    val text = when (value) {
        is String -> value
        is Int -> value.toString()
        else -> return null
    }
    if (!DIGITS.matches(text)) return null
    val number = text.toLongOrNull() ?: return null
    return if (number in 1..Int.MAX_VALUE) number.toInt() else null
}

/** Accepts only absolute http(s) media URLs without embedded credentials. */
fun mediaUri(value: String): String {
    if (value.isEmpty() || value.length > 16384) throw XmlyException("invalid_media", "音频地址无效")
    if (value.any { it.code <= 0x20 }) throw XmlyException("invalid_media", "音频地址无效")
    val uri = runCatching { java.net.URI(value) }.getOrNull()
        ?: throw XmlyException("invalid_media", "音频地址无效")
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") throw XmlyException("invalid_media", "音频地址无效")
    if (uri.host.isNullOrEmpty() || uri.userInfo != null) throw XmlyException("invalid_media", "音频地址无效")
    return value
}

/** Resolves an album or track cover, upgrading plain xmcdn links to https. */
fun coverUrl(raw: Map<String, Any?>): String? {
    for (key in COVER_KEYS) {
        val value = raw[key] as? String ?: continue
        if (value.isEmpty() || value.length > 16384) continue
        if (value.any { it.code <= 0x20 }) continue
        val normalized = if (value.startsWith("//")) "https:$value" else value
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: continue
        val scheme = uri.scheme?.lowercase() ?: continue
        if (scheme != "http" && scheme != "https") continue
        if (uri.host.isNullOrEmpty() || uri.userInfo != null) continue
        val upgraded = if (scheme == "http" && (uri.host == "xmcdn.com" || uri.host.endsWith(".xmcdn.com"))) {
            normalized.replaceFirst("http://", "https://")
        } else {
            normalized
        }
        return upgraded
    }
    return null
}

/** Keeps only the public metadata the app persists, never credentials. */
private val PUBLIC_RAW_KEYS = listOf(
    "id", "trackId", "albumId", "album_id", "title", "nickname", "albumTitle", "album_title",
    "duration", "orderNo", "orderNum", "order_no", "episode", "sequence", "index", "officialPosition",
    "isPaid", "is_paid", "isAuthorized", "intro", "coverLarge", "coverMiddle", "coverSmall",
    "cover_path", "album_cover_path", "cover_url", "albumImage", "tracks", "totalCount", "total",
    "year", "date",
)

fun publicRaw(raw: Map<String, Any?>): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    for (key in PUBLIC_RAW_KEYS) {
        when (val value = raw[key]) {
            is String, is Boolean -> result[key] = value
            is Int, is Long, is Double, is Float -> result[key] = value
            else -> Unit
        }
    }
    val nickname = (raw["userInfo"] as? Map<*, *>)?.get("nickname")
    if (nickname is String) result["userInfo"] = mapOf("nickname" to nickname)
    return result
}
