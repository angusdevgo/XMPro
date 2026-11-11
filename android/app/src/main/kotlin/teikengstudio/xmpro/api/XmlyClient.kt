package teikengstudio.xmpro.api

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.data.QuotaSource
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.AlbumPage
import teikengstudio.xmpro.model.ResolvedAudio
import teikengstudio.xmpro.model.Credentials
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.SearchPage
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XMLY_ALBUM_PAGE_SIZE
import teikengstudio.xmpro.model.XmlyException
import teikengstudio.xmpro.model.coverUrl
import teikengstudio.xmpro.model.mediaUri
import teikengstudio.xmpro.model.publicRaw
import teikengstudio.xmpro.model.xmlyIdentifier
import teikengstudio.xmpro.model.xmlyPositive

/**
 * Read-only Ximalaya API surface for one account (or anonymous use).
 *
 * Billing calls live here too because the signature scheme is account scoped;
 * [UsageLedger] decides when they run.
 */
class XmlyClient(
    private val http: XmlyHttp = XmlyHttp(),
    val credentials: Credentials? = null,
    private val clock: () -> Instant = { Instant.ofEpochMilli(ServerClock.now()) },
    private val mobile: String = MOBILE,
    private val searchOrigin: String = XmlyHttp.SEARCH_ORIGIN,
) : QuotaSource {
    val identity: String = credentials?.identity ?: ANONYMOUS
    private val albums = LinkedHashMap<String, Album>()
    private val metadata = object : LinkedHashMap<String, Track>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Track>?): Boolean = size > 512
    }

    /** Unauthenticated mobile GET used by the public index endpoints. */
    fun getMobile(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        http.get(mobile, path, query = query)

    fun accountAvailable(): Boolean = credentials != null

    /**
     * Lite-client request bound to this client's account (or anonymous).
     *
     * Writes are never retried by [XmlyHttp], so an unknown outcome surfaces as
     * `uncertain` instead of being replayed.
     */
    fun request(
        method: String,
        origin: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
        json: Map<String, Any?>? = null,
        account: Boolean = true,
        readOnly: Boolean = true,
        signed: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val bound = if (account) credentials else null
        if (account && bound == null) {
            throw XmlyException("login_required", "请先登录", definitive = true)
        }
        val merged = LinkedHashMap<String, String>()
        if (origin == COLLECTOR) {
            merged["User-Agent"] = "ting_3.4.10.3(Android,30)"
            merged["Accept"] = "*/*"
        }
        merged.putAll(headers)
        val limit = if (origin == COLLECTOR) 8 * 1024 * 1024 else XmlyHttp.MAX_RESPONSE_BYTES
        return when {
            method == "GET" -> http.get(
                origin = origin,
                path = path,
                query = query,
                credentials = bound,
                signed = signed,
                readOnly = readOnly,
                headers = merged,
                responseLimit = limit,
            )

            form != null -> http.postForm(
                origin = origin,
                path = path,
                query = query,
                form = form,
                credentials = bound,
                readOnly = readOnly,
            )

            else -> http.postJson(
                origin = origin,
                path = path,
                query = query,
                json = json ?: emptyMap(),
                credentials = bound,
                readOnly = readOnly,
            )
        }
    }

    /**
     * Cookie shaped for the main-app user endpoints. The lite cookie is
     * answered with an empty 404 by the mobile-user routes.
     */
    fun mainAppCookie(): String? {
        val account = credentials ?: return null
        return "1&_token=${account.token}; 1&_device=android&${account.deviceId}&9.5.1; " +
            "channel=and-d10; impl=com.ximalaya.ting.android; appid=1463"
    }

    /** Account identity fields required by the history signature. */
    fun context(): Map<String, String> {
        val account = credentials ?: throw XmlyException("login_required", "历史同步需要登录", definitive = true)
        return mapOf(
            "uid" to account.uid,
            "token" to account.token.substringAfter('&'),
            "device" to "android",
            "deviceId" to account.deviceId,
            "version" to "3.4.10",
            "channel" to "and-f5",
            "impl" to "com.ximalaya.ting.lite",
        )
    }

    // ---------------------------------------------------------------- catalogue

    fun search(query: String, kind: String = "track", page: Int = 1, pageSize: Int = 20): SearchPage {
        if (query.isBlank() || query.length > 200 || kind !in setOf("track", "album")) {
            throw XmlyException("invalid_search", "请输入 1..200 字的关键词", definitive = true)
        }
        paging(page, pageSize)
        val payload = http.get(
            searchOrigin,
            "/revision/search",
            query = mapOf(
                "kw" to query.trim(),
                "core" to kind,
                "page" to page.toString(),
                "rows" to pageSize.toString(),
                "device" to "iPhone",
                "condition" to "relation",
                "spellchecker" to "true",
            ),
        )
        val values = payload.optJSONObject("data")
            ?.optJSONObject("result")
            ?.optJSONObject("response")
            ?: throw XmlyException("invalid_catalogue", "搜索结果无效")
        val docs = values.optJSONArray("docs") ?: throw XmlyException("invalid_catalogue", "搜索结果无效")
        val tracks = mutableListOf<Track>()
        val found = mutableListOf<Album>()
        for (index in 0 until minOf(docs.length(), pageSize)) {
            val raw = docs.optJSONObject(index) ?: continue
            val map = raw.toPlainMap()
            if (kind == "album") {
                val id = map["albumId"] ?: map["id"] ?: continue
                found += albumFromApi(map, xmlyIdentifier(id.toString()))
            } else {
                val track = trackFromApi(map)
                if (track != null) tracks += track
            }
        }
        val total = values.optInt("numFound", docs.length())
        return SearchPage(tracks, found, page, pageSize, total, kind)
    }

    /**
     * Loads the album page that contains [trackId], or null when it is not found
     * within [maxPages].
     *
     * The catalogue endpoint ignores a `trackId` filter, so the only reliable way
     * to find where an episode lives is to walk the pages. The first page is
     * checked alone because most taps land there; searching further is bounded so
     * a bogus id cannot walk a 3000-episode album.
     */
    fun albumPageWith(albumId: String, trackId: String, maxPages: Int = 20): AlbumPage? {
        val wanted = trackId.substringAfter(':')
        var page = 1
        var total = 0
        while (page <= maxPages) {
            val data = album(albumId, page = page)
            total = data.total
            if (data.tracks.any { it.id == "xmly:$wanted" }) return data
            if (page * data.pageSize >= total) return null
            page++
        }
        return null
    }

    fun album(albumId: String, page: Int = 1, pageSize: Int = XMLY_ALBUM_PAGE_SIZE, ascending: Boolean = true): AlbumPage {
        val number = xmlyIdentifier(albumId)
        paging(page, pageSize, XMLY_ALBUM_PAGE_SIZE)
        val payload = http.get(
            mobile,
            "/mobile/v1/album/track/v2",
            query = mapOf(
                "albumId" to number,
                "pageId" to page.toString(),
                "pageSize" to pageSize.toString(),
                "isAsc" to ascending.toString(),
                "device" to "android",
                "isQueryInvitationBrand" to "true",
            ),
        )
        val data = payload.optJSONObject("data")
            ?: throw XmlyException("invalid_catalogue", "专辑列表无效")
        val total = total(data.opt("totalCount"), "专辑分页总数无效")
        val list = data.optJSONArray("list") ?: throw XmlyException("invalid_catalogue", "专辑列表无效")
        if (data.has("pageId") && total(data.opt("pageId"), "专辑分页无效") != page) {
            throw XmlyException("invalid_catalogue", "专辑分页无效")
        }
        if (data.has("pageSize") && total(data.opt("pageSize"), "专辑分页无效") != pageSize) {
            throw XmlyException("invalid_catalogue", "专辑分页无效")
        }
        val expected = (total - (page - 1) * pageSize).coerceIn(0, pageSize)
        if (list.length() > pageSize || list.length() != expected) {
            throw XmlyException("incomplete_catalogue", "专辑分页不完整，请刷新重试")
        }
        payload.optJSONObject("album")?.opt("albumId")?.let {
            if (xmlyIdentifier(it.toString()) != number) {
                throw XmlyException("id_mismatch", "专辑信息不匹配")
            }
        }
        data.opt("albumId")?.let {
            if (xmlyIdentifier(it.toString()) != number) throw XmlyException("id_mismatch", "专辑信息不匹配")
        }
        val seen = mutableSetOf<String>()
        var previousOrder: Int? = null
        val tracks = mutableListOf<Track>()
        for (index in 0 until list.length()) {
            val raw = list.optJSONObject(index) as? JSONObject
                ?: throw XmlyException("invalid_catalogue", "专辑声音信息无效")
            val map = raw.toPlainMap()
            for (key in listOf("albumId", "album_id")) {
                val value = map[key] ?: continue
                if (xmlyIdentifier(value.toString()) != number) {
                    throw XmlyException("id_mismatch", "专辑包含其他专辑的声音")
                }
            }
            val id = xmlyIdentifier((map["trackId"] ?: map["id"] ?: "").toString())
            if (!seen.add(id)) throw XmlyException("duplicate_page", "专辑声音重复，请刷新重试")
            val order = albumOrder(map)
            if (order != null) {
                val monotonic = previousOrder?.let { if (ascending) order > it else order < it } ?: true
                if (!monotonic) throw XmlyException("invalid_catalogue", "专辑声音序号冲突")
                previousOrder = order
            }
            val position = (page - 1) * pageSize + index + 1
            val official = if (ascending) position else if (total > 0) total - position + 1 else null
            trackFromApi(map, albumId = number, officialPosition = official)?.let(tracks::add)
        }
        // Some responses omit the album object; fall back to the track rows so
        // the header never renders an empty title.
        val albumMap = payload.optJSONObject("album")?.toPlainMap()
            ?: data.toPlainMap()
        val detail = albumDetail(number)
        val album = albumFromApi(albumMap + detail, number, total).let { parsed ->
            if (parsed.title.isNotEmpty()) {
                parsed
            } else {
                val fromTrack = tracks.firstOrNull { it.album.isNotEmpty() }?.album ?: ""
                if (fromTrack.isEmpty()) {
                    parsed
                } else {
                    Album(
                        rawId = parsed.id,
                        title = fromTrack,
                        artist = parsed.artist,
                        intro = parsed.intro,
                        coverUrl = parsed.coverUrl,
                        total = parsed.total,
                        raw = parsed.raw,
                    )
                }
            }
        }
        albums[number] = album
        while (albums.size > 512) albums.remove(albums.keys.first())
        for (track in tracks) metadata[track.number] = track
        return AlbumPage(album, tracks, page, pageSize, total)
    }

    /**
     * Album header metadata; the track page alone returns no album object, so
     * the title, anchor and intro come from the detail endpoint.
     */
    private fun albumDetail(album: String): Map<String, Any?> {
        val payload = runCatching {
            http.get(
                mobile,
                "/mobile-album/album/detail",
                query = mapOf("albumId" to album, "device" to "android"),
            )
        }.getOrNull() ?: return emptyMap()
        val data = payload.optJSONObject("data") ?: return emptyMap()
        val info = data.optJSONObject("album") ?: return emptyMap()
        val plain = info.toPlainMap().toMutableMap()
        data.optJSONObject("user")?.let { user ->
            (user.opt("nickname") as? String)?.let { plain["nickname"] = it }
            (user.opt("avatar") as? String)?.let { plain["avatar"] = it }
        }
        data.optJSONObject("detail")?.let { detail ->
            (detail.opt("intro") as? String)?.takeIf { it.isNotEmpty() }?.let { plain["intro"] = it }
        }
        // `title` is the detail endpoint's name for the album title.
        (plain["title"] as? String)?.let { if (it.isNotEmpty()) plain["albumTitle"] = it }
        return plain
    }

    // -------------------------------------------------------------------- track

    fun track(number: String): Track {
        val id = xmlyIdentifier(number)
        val map = trackInfoMap(id)
        return trackFromApi(map) ?: throw XmlyException("missing_track", "声音信息缺失")
    }

    /**
     * Resolves the best playable URL for [number].
     *
     * Paid tracks are only resolved for a logged-in account with listening time
     * available; the caller must treat `requiresReward` as a billing obligation.
     */
    fun resolve(number: String, quality: String = "auto"): ResolvedAudio {
        val requested = if (quality == "auto") "auto" else quality.lowercase()
        if (requested != "auto" && requested !in QUALITIES) {
            throw XmlyException("invalid_quality", "音质设置无效", definitive = true)
        }
        val id = xmlyIdentifier(number)
        var info = trackInfoMap(id)
        var requiresReward = false
        if (info["isPaid"] != false && info["isAuthorized"] != true) {
            val account = credentials
                ?: throw XmlyException("login_required", "请先导入极速版登录信息", definitive = true)
            val current = activity("config", null)
            if (!current.newDevice && current.remainingSeconds <= 0) {
                throw XmlyException("insufficient_quota", "听时不足，请前往官方 App 获取听时", definitive = true)
            }
            requiresReward = true
            info = trackInfoMap(id, current)
            if (info["isPaid"] == false || info["isAuthorized"] == true) requiresReward = false
            if (account.uid.isEmpty()) throw XmlyException("login_required", "请先登录", definitive = true)
        }
        if (info["isPreview"] == true || info["isTrial"] == true) {
            throw XmlyException("preview_only", "仅获得试听音频，不能下载完整声音", definitive = true)
        }
        val candidates = LinkedHashMap<String, String>()
        fun add(type: String, value: Any?, encrypted: Boolean) {
            val text = value as? String ?: return
            if (text.isEmpty() || candidates.containsKey(type)) return
            val decoded = if (encrypted && !text.startsWith("http://") && !text.startsWith("https://")) {
                XmlyCrypto.decrypt(text, XmlyCrypto.playKey)
            } else {
                text
            }
            candidates[type] = mediaUri(decoded)
        }
        val accountPresent = credentials != null
        val listOnly = accountPresent
        if (!listOnly || candidates.isEmpty()) {
            add("mp3_64", info["playUrl64"], encrypted = true)
            add("mp3_32", info["playUrl32"], encrypted = true)
        }
        if (accountPresent) {
            val rawList = info["playUrlList"]
            val list = when (rawList) {
                null -> null
                is String -> runCatching { JSONArray(rawList) }.getOrElse {
                    throw XmlyException("invalid_media", "音频列表格式无效")
                }
                is JSONArray -> rawList
                is List<*> -> JSONArray(rawList.toString())
                else -> throw XmlyException("invalid_media", "音频列表格式无效")
            }
            if (list != null) {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val type = item.optString("type").lowercase()
                    if (type !in QUALITIES) continue
                    add(type, item.opt("url"), encrypted = true)
                }
            }
        }
        if (candidates.isEmpty()) {
            throw XmlyException(
                "permission_denied",
                "当前没有获得完整音频地址，请检查听时与版权权限",
                definitive = true,
            )
        }
        val chosen = if (requested != "auto" && candidates.containsKey(requested)) {
            requested
        } else {
            QUALITIES.first { candidates.containsKey(it) }
        }
        val track = trackFromApi(info) ?: throw XmlyException("missing_track", "声音信息缺失")
        var url = candidates.getValue(chosen)
        // API-host URLs are redirects to the CDN; resolve them here so the
        // player only ever sees a final https object URL.
        if (!url.contains(".xmcdn.com/")) {
            url = mediaUri(http.followRedirect(url))
        }
        return ResolvedAudio(
            url = url,
            requiresReward = requiresReward,
            track = track,
            actualQuality = chosen,
            accountIdentity = identity,
        )
    }

    private fun trackInfoMap(id: String, reward: Quota? = null): Map<String, Any?> {
        val account = credentials
        if (account == null) {
            // Anonymous callers use the public endpoint; the signed mobile one
            // rejects unauthenticated requests with HTTP 405.
            val payload = http.get(
                mobile,
                "/mobile/track/$id",
                query = mapOf("device" to "android"),
            )
            // The public endpoint returns the track object at the top level;
            // the signed one wraps it in `data`.
            return matchingInfo(payload.optJSONObject("data") ?: payload, id)
        }
        val params = linkedMapOf<String, Any>("device" to "android", "trackId" to id)
        if (reward != null) {
            params["a1"] = if (reward.newDevice) "1" else reward.remainingSeconds.toString()
            params["a2"] = clock().toEpochMilli().toString()
            params["a3"] = "0"
        }
        val encrypted = XmlyCrypto.encrypt(JSONObject(params).toString(), XmlyCrypto.playKey)
        val payload = http.postJson(
            mobile,
            "/fmobile-track/fmobile/track/v2/baseInfo",
            json = mapOf("data" to encrypted),
            credentials = account,
            signed = true,
            readOnly = true,
        )
        val body = payload.opt("data") as? String
            ?: throw XmlyException("invalid_authorization", "授权声音信息无效")
        val decoded = try {
            JSONObject(XmlyCrypto.decrypt(body, XmlyCrypto.playKey))
        } catch (_: Exception) {
            throw XmlyException("invalid_authorization", "授权声音信息无效")
        }
        var info: Any? = decoded.opt("trackInfo")
        if (info is String) info = runCatching { JSONObject(info as String) }.getOrNull()
        val objectInfo = info as? JSONObject
            ?: throw XmlyException("invalid_authorization", "授权声音信息无效")
        return matchingInfo(objectInfo, id)
    }

    private fun matchingInfo(data: JSONObject?, id: String): Map<String, Any?> {
        if (data == null) throw XmlyException("missing_track", "声音信息缺失")
        val map = data.toPlainMap()
        val returned = (map["trackId"] ?: map["id"] ?: "").toString()
        if (returned.isNotEmpty() && xmlyIdentifier(returned) != id) {
            throw XmlyException("id_mismatch", "声音信息不匹配")
        }
        return map
    }

    // ------------------------------------------------------------------ billing

    override fun quota(identity: String): Quota = activity("config", null)

    override fun debit(identity: String, seconds: Int, operationId: String): Quota {
        if (seconds < 1) throw XmlyException("invalid_duration", "听时必须为正整数秒", definitive = true)
        return activity("report", seconds)
    }

    private fun activity(name: String, seconds: Int?): Quota {
        val account = credentials ?: throw XmlyException("login_required", "请先登录", definitive = true)
        val params = linkedMapOf(
            "deviceId" to account.deviceId,
            "timestamp" to clock().toEpochMilli().toString(),
            "uid" to account.uid,
        )
        seconds?.let { params["duration"] = it.toString() }
        val signature = XmlyCrypto.activitySignature(params)
        // The lite client posts this endpoint as JSON; a form body is rejected
        // with ret=-100 even when the signature is correct.
        val body = linkedMapOf<String, Any?>()
        params.forEach { (key, value) -> if (key != "uid") body[key] = value }
        body["signature"] = signature
        val payload = http.postJson(
            mobile,
            "/lite-mobile/audi/func/v1/$name",
            json = body,
            credentials = account,
            readOnly = name == "config",
            responseLimit = XmlyHttp.MAX_RESPONSE_BYTES,
        )
        val data = payload.optJSONObject("data") ?: throw XmlyException("invalid_quota", "听时信息缺失或已过期")
        fun decode(field: String): String {
            val raw = data.opt(field) as? String ?: throw XmlyException("invalid_quota", "听时信息缺失或已过期")
            return try {
                val plain = XmlyCrypto.decrypt(raw, XmlyCrypto.durationKey)
                val divider = plain.lastIndexOf('|')
                if (divider < 0) throw IllegalArgumentException("no divider")
                val stamp = plain.substring(divider + 1).toLong()
                if (kotlin.math.abs(clock().toEpochMilli() - stamp) >= 180_000) {
                    throw IllegalArgumentException("stale")
                }
                plain.substring(0, divider)
            } catch (_: Exception) {
                throw XmlyException("invalid_quota", "听时信息缺失或已过期")
            }
        }
        var newDevice = false
        if (name == "config") {
            val flag = decode("newDevice")
            if (flag != "true" && flag != "false") throw XmlyException("invalid_quota", "设备授权信息无效")
            newDevice = flag == "true"
        }
        val remaining = if (newDevice && !data.has("availableListenDuration")) 0 else decode("availableListenDuration").toIntOrNull()
        if (remaining == null || remaining < 0) throw XmlyException("invalid_quota", "听时余额无效")
        return Quota(remaining, newDevice, clock())
    }

    // ------------------------------------------------------------------ helpers

    private fun paging(page: Int, pageSize: Int, maxPageSize: Int = 50) {
        if (page < 1 || page > 10000 || pageSize < 1 || pageSize > maxPageSize) {
            throw XmlyException("invalid_page", "页码范围为 1..10000，每页 1..$maxPageSize", definitive = true)
        }
    }

    private fun total(value: Any?, message: String): Int {
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: throw XmlyException("invalid_page", message)
        if (!number.isFinite() || number < 0 || number != kotlin.math.floor(number)) {
            throw XmlyException("invalid_page", message)
        }
        return number.toInt()
    }

    private fun albumOrder(raw: Map<String, Any?>): Int? {
        var order: Int? = null
        for (key in listOf("orderNo", "orderNum", "order_no")) {
            val value = xmlyPositive(raw[key]) ?: continue
            if (order != null && order != value) throw XmlyException("invalid_catalogue", "专辑声音序号冲突")
            order = value
        }
        return order
    }

    private fun albumFromApi(raw: Map<String, Any?>, id: String, total: Int = 0): Album = Album(
        rawId = id,
        title = (raw["albumTitle"] ?: raw["album_title"] ?: raw["title"] ?: "").toString(),
        artist = (raw["nickname"] ?: raw["artist"] ?: "").toString(),
        intro = (raw["intro"] ?: "").toString(),
        coverUrl = coverUrl(raw),
        total = total,
        raw = publicRaw(raw),
    )

    private fun trackFromApi(
        raw: Map<String, Any?>,
        albumId: String = "",
        officialPosition: Int? = null,
    ): Track? {
        val id = (raw["trackId"] ?: raw["id"] ?: "").toString()
        if (id.isEmpty()) return null
        val rawMap = LinkedHashMap<String, Any?>(raw)
        if (officialPosition != null) rawMap["officialPosition"] = officialPosition
        val resolvedAlbum = albumId.ifEmpty { (raw["albumId"] ?: raw["album_id"] ?: "").toString() }
        return Track(
            rawId = id,
            title = (raw["title"] ?: "").toString(),
            artist = (raw["nickname"] ?: "").toString(),
            album = (raw["albumTitle"] ?: raw["album_title"] ?: "").toString(),
            albumId = resolvedAlbum,
            duration = (raw["duration"] as? Number)?.toDouble() ?: 0.0,
            orderNo = xmlyPositive(raw["orderNo"] ?: raw["orderNum"] ?: raw["order_no"]),
            episode = xmlyPositive(raw["episode"]),
            isPaid = raw["isPaid"] as? Boolean ?: raw["is_paid"] as? Boolean,
            isAuthorized = raw["isAuthorized"] as? Boolean,
            coverUrl = coverUrl(raw),
            intro = (raw["intro"] ?: "").toString(),
            raw = publicRaw(rawMap),
        ).also { metadata[it.number] = it }
    }

    companion object {
        const val MOBILE = "https://mobile.ximalaya.com"
        const val COLLECTOR = "https://xdcs-collector.ximalaya.com"
        const val WELFARE = "https://m.ximalaya.com"
        const val ANONYMOUS = "anonymous"
        val QUALITIES = listOf("m4a_128", "m4a_64", "mp3_64", "mp3_32", "m4a_24")
    }
}

/** Converts the JSON tree into plain maps so the model layer stays JSON free. */
internal fun JSONObject.toPlainMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key ->
        when (val value = opt(key)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toPlainMap()
            is JSONArray -> (0 until value.length()).map { value.opt(it) }
            else -> value
        }
    }
