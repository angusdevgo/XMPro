package teikengstudio.xmpro.model

/** One person in a following / fans list. */
data class SocialUser(
    val uid: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val followers: Int = 0,
    val albums: Int = 0,
    val intro: String = "",
)

/**
 * Ximalaya returns CDN paths in several shapes: protocol-relative, absolute,
 * and bare object keys. The app only loads https, so everything is normalised
 * to a full URL here.
 */
fun cdnUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.length > 16384) return null
    if (value.any { it.code <= 0x20 }) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        value.startsWith("https://") -> value
        else -> "https://imagev2.xmcdn.com/" + value.trimStart('/')
    }
}
