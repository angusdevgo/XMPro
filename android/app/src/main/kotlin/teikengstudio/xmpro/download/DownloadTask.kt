package teikengstudio.xmpro.download

import org.json.JSONObject
import teikengstudio.xmpro.model.Track

enum class DownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    FAILED,
    ;

    companion object {
        fun of(name: String?): DownloadState = entries.firstOrNull { it.name == name } ?: QUEUED
    }
}

/**
 * One offline episode.
 *
 * Only the fields needed to play the file and draw the row are kept: the raw
 * payload would bloat the state file, and a downloaded episode never needs to
 * be looked up again.
 */
class DownloadTask(
    val track: Track,
    val path: String,
    /** Quality the user picked for this episode; null follows the global one. */
    val requestQuality: String? = null,
    /** Quality the service actually served, filled in while downloading. */
    val quality: String = "",
    val received: Long = 0,
    val total: Long = 0,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
) {
    val id: String get() = track.id

    val progress: Float
        get() = when {
            state == DownloadState.DONE -> 1f
            total > 0 -> (received.toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }

    fun copy(
        path: String = this.path,
        received: Long = this.received,
        total: Long = this.total,
        state: DownloadState = this.state,
        error: String? = this.error,
        quality: String = this.quality,
    ) = DownloadTask(track, path, requestQuality, quality, received, total, state, error)

    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("quality", quality)
        put("request_quality", requestQuality ?: JSONObject.NULL)
        put("received", received)
        put("total", total)
        put("state", state.name)
        put("error", error ?: JSONObject.NULL)
        put(
            "track",
            JSONObject().apply {
                put("number", track.number)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("album_id", track.albumKey)
                put("duration", track.duration)
                put("cover_url", track.coverUrl ?: JSONObject.NULL)
            },
        )
    }

    companion object {
        fun fromJson(value: JSONObject): DownloadTask? {
            val path = value.optString("path")
            val json = value.optJSONObject("track") ?: return null
            val number = json.optString("number")
            if (path.isEmpty() || number.isEmpty()) return null
            val track = Track(
                rawId = number,
                title = json.optString("title"),
                artist = json.optString("artist"),
                album = json.optString("album"),
                albumId = json.optString("album_id"),
                duration = json.optDouble("duration", 0.0),
                coverUrl = json.optString("cover_url").takeIf { it.isNotEmpty() },
            )
            return DownloadTask(
                track = track,
                path = path,
                requestQuality = value.optString("request_quality").takeIf { it.isNotEmpty() },
                quality = value.optString("quality"),
                received = value.optLong("received"),
                total = value.optLong("total"),
                state = DownloadState.of(value.optString("state")),
                error = value.optString("error").takeIf { it.isNotEmpty() },
            )
        }
    }
}
