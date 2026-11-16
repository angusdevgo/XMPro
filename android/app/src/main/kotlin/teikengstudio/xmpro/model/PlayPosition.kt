package teikengstudio.xmpro.model

import org.json.JSONObject

/**
 * One place inside an album that was listened to on this device.
 *
 * The cloud history keeps a single row per album, so going back to an earlier
 * position there is impossible. The last few positions per album are therefore
 * remembered locally, and the history tab lists them under the album row.
 */
class PlayPosition(
    val track: Track,
    val positionSeconds: Double,
    val at: Long,
) {
    val albumKey: String get() = track.albumKey

    fun toJson(): JSONObject = JSONObject().apply {
        put("number", track.number)
        put("title", track.title)
        put("album", track.album)
        put("album_id", track.albumKey)
        put("cover_url", track.coverUrl ?: "")
        put("position", positionSeconds)
        put("at", at)
    }

    companion object {
        fun fromJson(value: JSONObject): PlayPosition? {
            val number = value.optString("number")
            if (number.isEmpty()) return null
            val track = Track(
                rawId = number,
                title = value.optString("title"),
                album = value.optString("album"),
                albumId = value.optString("album_id"),
                coverUrl = value.optString("cover_url").takeIf { it.isNotEmpty() },
            )
            return PlayPosition(
                track = track,
                positionSeconds = value.optDouble("position", 0.0),
                at = value.optLong("at", 0L),
            )
        }
    }
}
