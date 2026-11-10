package teikengstudio.xmpro.model

/** Minimal track view; artwork/metadata caching arrives with the catalogue port. */
class Track(
    rawId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    albumId: String = "",
    val duration: Double = 0.0,
    val orderNo: Int? = null,
    val episode: Int? = null,
    val isPaid: Boolean? = null,
    val isAuthorized: Boolean? = null,
    val coverUrl: String? = null,
    val intro: String = "",
    val raw: Map<String, Any?> = emptyMap(),
) {
    /** Canonical `xmly:<id>` form used by storage and the ledger. */
    val id: String = "xmly:${xmlyIdentifier(rawId)}"
    val albumKey: String = if (albumId.isEmpty()) "" else xmlyIdentifier(albumId)
    val number: String get() = id.substringAfter(':')

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "title" to title,
        "artist" to artist,
        "album" to album,
        "album_id" to albumKey,
        "duration" to duration,
        "order_no" to orderNo,
        "episode" to episode,
        "is_paid" to isPaid,
        "is_authorized" to isAuthorized,
        "cover_url" to coverUrl,
        "intro" to intro,
        "raw" to raw,
    )
}

class Album(
    rawId: String,
    val title: String = "",
    val artist: String = "",
    val intro: String = "",
    val coverUrl: String? = null,
    val total: Int = 0,
    val raw: Map<String, Any?> = emptyMap(),
) {
    val id: String = xmlyIdentifier(rawId)
}

class AlbumPage(
    val album: Album,
    val tracks: List<Track>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

class SearchPage(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val kind: String,
)

/** Requests 200 items per album page, matching `xmlyAlbumPageSize`. */
const val XMLY_ALBUM_PAGE_SIZE = 200
