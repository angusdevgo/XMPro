package teikengstudio.xmpro.api

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.HistoryRecord
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException
import teikengstudio.xmpro.model.coverUrl
import teikengstudio.xmpro.model.publicRaw
import teikengstudio.xmpro.model.xmlyIdentifier

/**
 * Cloud listening history: read the merged record list, and write one absolute
 * progress snapshot.
 *
 * The write path is a POST, so it is never replayed after an unknown outcome;
 * [sync] reports success only when the service echoes a sync point.
 */
class XmlyHistory(
    private val client: XmlyClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private fun invalid(message: String) = XmlyException("invalid_history", message)

    private fun context(): Map<String, String> {
        val value = client.context()
        if (!Regex("^[1-9][0-9]*$").matches(value["uid"] ?: "")) {
            throw XmlyException("auth_required", "历史同步需要有效的极速版登录态", definitive = true)
        }
        return value
    }

    /** Newest first, one record per track. */
    fun records(): List<HistoryRecord> {
        val context = context()
        val items = linkedMapOf<String, HistoryRecord>()
        val pages = mutableSetOf<String>()
        for (page in 1..50) {
            val response = client.request(
                "GET",
                XmlyClient.MOBILE,
                "/nyx/v1/history/query/android",
                query = mapOf(
                    "pageId" to page.toString(),
                    "pageSize" to "100",
                    "syncPoint" to "0",
                    "timeStamp" to clock().toString(),
                    "uid" to (context["uid"] ?: ""),
                ),
            )
            val data = data(response)
            val raw = data.optJSONArray("listenModels")
            val more = data.opt("hasMore") as? Boolean
            if (raw == null || more == null || raw.length() > 100) throw invalid("历史分页格式无效")
            val fingerprint = raw.toString()
            if (raw.length() > 0 && !pages.add(fingerprint)) {
                throw XmlyException("repeated_page", "历史服务返回重复分页")
            }
            for (index in 0 until raw.length()) {
                val record = normalize(raw.optJSONObject(index) ?: continue) ?: continue
                val existing = items[record.track.id]
                if (existing == null || record.updatedAt > existing.updatedAt) {
                    items[record.track.id] = record
                }
            }
            if (!more) {
                return items.values.sortedByDescending { it.updatedAt }
            }
            if (raw.length() == 0) throw invalid("历史分页未返回记录")
        }
        throw XmlyException("history_limit", "历史记录超过 5000 条上限")
    }

    fun progress(albumId: String = "", trackId: String = ""): HistoryRecord? {
        if (albumId.isEmpty() && trackId.isEmpty()) {
            throw XmlyException("invalid_history", "请指定声音或专辑", definitive = true)
        }
        val album = if (albumId.isEmpty()) "" else xmlyIdentifier(albumId)
        val track = if (trackId.isEmpty()) "" else xmlyIdentifier(trackId)
        return records().firstOrNull { item ->
            (album.isEmpty() || item.track.albumKey == album) &&
                (track.isEmpty() || item.track.number == track)
        }
    }

    /** Writes one absolute progress snapshot; retries must reuse the bounds. */
    fun sync(track: Track, positionSeconds: Double, startedAtMs: Long, endedAtMs: Long) {
        if (!track.id.startsWith("xmly:")) {
            throw XmlyException("invalid_history", "只能同步喜马拉雅声音", definitive = true)
        }
        var position = positionSeconds
        if (!position.isFinite() || position < 0 || position > 2147483647.0) {
            throw XmlyException("invalid_history", "播放进度无效", definitive = true)
        }
        var ended = endedAtMs
        if (startedAtMs <= 0 || startedAtMs > ended || ended > 2115354119000L) {
            throw XmlyException("invalid_history", "收听起止时间无效", definitive = true)
        }
        if (track.duration.isFinite() && track.duration > 0) {
            position = position.coerceIn(0.0, track.duration)
        }
        if (ended == startedAtMs) ended = maxOf(clock(), startedAtMs)
        val album = track.albumKey
        if (album.isEmpty()) {
            throw XmlyException("invalid_history", "缺少专辑标识", definitive = true)
        }
        val direction = when (val raw = track.raw["direction"]) {
            is Number -> raw.toInt()
            else -> 0
        }
        if (direction != 0 && direction != 1) {
            throw XmlyException("invalid_history", "专辑播放顺序无效", definitive = true)
        }
        val record = JSONObject().apply {
            put("endedAt", ended.toString())
            put("startedAt", startedAtMs.toString())
            put("type", "1")
            put("itemId", album)
            put("childId", track.number)
            put("breakSecond", position.toInt().toString())
            put("direction", direction.toString())
        }
        val params = linkedMapOf("mergeRecords" to JSONArray().put(record).toString())
        val signature = signature(params + context())
        val form = linkedMapOf<String, String>()
        params.forEach { (key, value) -> form[key] = value }
        form["signature"] = signature
        val response = client.request(
            "POST",
            XmlyClient.COLLECTOR,
            "/nyx/v1/history/merge/android",
            form = form,
            readOnly = true,
        )
        val point = data(response).opt("syncPoint")?.toString()?.toLongOrNull() ?: -1
        if (point < 0) throw XmlyException("history_unconfirmed", "未能确认历史进度同步")
    }

    private fun data(response: JSONObject): JSONObject {
        var data: Any? = response.opt("data")
        if (data is String) {
            data = runCatching { JSONObject(data) }.getOrNull()
        }
        return data as? JSONObject ?: throw invalid("历史记录格式无效")
    }

    private fun normalize(raw: JSONObject): HistoryRecord? {
        val type = integer(raw.opt("type"))
        if (type != 1L && type != 9L) return null
        if (integer(raw.opt("deleteTime")) > 0) return null
        val trackId = try {
            xmlyIdentifier("${raw.opt("childId")}")
        } catch (_: Exception) {
            return null
        }
        val albumId = try {
            xmlyIdentifier("${raw.opt("itemId")}")
        } catch (_: Exception) {
            return null
        }
        val duration = integer(raw.opt("length")).coerceIn(0, 2147483647)
        var position = integer(raw.opt("breakSecond")).coerceIn(0, 2147483647).toDouble()
        if (duration > 0) position = position.coerceIn(0.0, duration.toDouble())
        val plain = mapOf(
            "albumId" to albumId,
            "cover_url" to "${raw.opt("itemCoverUrl") ?: ""}",
        )
        val track = Track(
            rawId = trackId,
            title = "${raw.opt("childTitle") ?: ""}",
            album = "${raw.opt("itemTitle") ?: ""}",
            albumId = albumId,
            duration = duration.toDouble(),
            coverUrl = coverUrl(plain),
            raw = publicRaw(plain),
        )
        return HistoryRecord(
            track = track,
            positionSeconds = position,
            updatedAt = integer(raw.opt("endedAt")).coerceIn(0, 2115354119000L),
        )
    }

    private fun integer(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    companion object {
        private const val MATERIAL = "23627d1451047b8d257a96af5db359538f081d651df75b4aa169508547208159"

        /** Keys sorted, `k=v&` concatenated, ASCII-lowercased, then MD5+salt. */
        fun signature(params: Map<String, String>): String {
            val text = buildString {
                for (key in params.keys.sorted()) {
                    append(key).append('=').append(params[key]).append('&')
                }
                append(MATERIAL)
            }
            val lowered = String(
                text.toByteArray(Charsets.UTF_8).map { byte ->
                    if (byte in 65..90) (byte + 32).toByte() else byte
                }.toByteArray(),
                Charsets.UTF_8,
            )
            return MessageDigest.getInstance("MD5")
                .digest(lowered.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
