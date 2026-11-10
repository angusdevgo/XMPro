package teikengstudio.xmpro.data

import org.json.JSONObject
import teikengstudio.xmpro.model.Track

/** A saved listening position for one track. */
class Bookmark(
    val track: Track,
    val positionMs: Long,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("track", JSONObject(track.toJson().filterValues { it != null }))
        put("position", positionMs)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(value: JSONObject): Bookmark? {
            val raw = value.optJSONObject("track") ?: return null
            val map = raw.toMap()
            val id = (map["id"] ?: "").toString().substringAfter(':')
            if (id.isEmpty()) return null
            return Bookmark(
                track = Track(
                    rawId = id,
                    title = (map["title"] ?: "").toString(),
                    artist = (map["artist"] ?: "").toString(),
                    album = (map["album"] ?: "").toString(),
                    albumId = (map["album_id"] ?: "").toString(),
                    duration = (map["duration"] as? Number)?.toDouble() ?: 0.0,
                    coverUrl = (map["cover_url"] as? String)?.takeIf { it.isNotEmpty() },
                ),
                positionMs = value.optLong("position", 0L),
                createdAt = value.optLong("createdAt", 0L),
            )
        }

        private fun JSONObject.toMap(): Map<String, Any?> =
            keys().asSequence().associateWith { key -> if (isNull(key)) null else opt(key) }
    }
}
