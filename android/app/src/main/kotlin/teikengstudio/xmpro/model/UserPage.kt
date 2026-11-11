package teikengstudio.xmpro.model

/** Public homepage of an anchor: counters plus the first page of albums. */
class UserPage(
    val uid: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val intro: String = "",
    val followers: Int = 0,
    val following: Int = 0,
    val albums: List<Album> = emptyList(),
    val totalAlbums: Int = 0,
)
