package teikengstudio.xmpro.api

import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.FavoritePage
import teikengstudio.xmpro.model.HomeAlbumStats
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeContent
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.HomeTab
import teikengstudio.xmpro.model.SubscriptionItem
import teikengstudio.xmpro.model.SubscriptionPage
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.UpdateItem
import teikengstudio.xmpro.model.UpdatePage
import teikengstudio.xmpro.model.XmlyException
import teikengstudio.xmpro.model.coverUrl
import teikengstudio.xmpro.model.publicRaw
import teikengstudio.xmpro.model.xmlyIdentifier

/**
 * Lite-client home and library surfaces (channels, module feeds, subscriptions,
 * favourites, updates).
 *
 * Home endpoints are public; once an account is imported the same calls carry
 * its cookie so the returned feed is personalised.
 */
class XmlyExperience(private val client: XmlyClient) {
    private val invalid = XmlyException("invalid_home", "首页数据无效", definitive = true)

    fun channels(): List<HomeChannel> {
        val data = get("/lite-mobile/index/v1/subPages/${stamp()}", mapOf(
            "latestTheaterViewTimestamp" to "0",
            "theaterDefaultMark" to "0",
        ))
        val seen = mutableSetOf<Int>()
        return rows(data.opt("subPages")).mapNotNull { row ->
            val id = integer(row.opt("pageId")) ?: throw invalid
            val title = text(row.opt("title")).trim()
            if (id <= 0 || title.isEmpty() || !seen.add(id)) throw invalid
            HomeChannel(id, title)
        }
    }

    fun home(pageId: Int, tabId: Int? = null): HomeContent {
        if (pageId <= 0) throw XmlyException("invalid_page", "频道无效", definitive = true)
        val query = linkedMapOf("pageId" to pageId.toString())
        tabId?.let { query["tabId"] = it.toString() }
        val data = get("/lite-mobile/index/v1/modules/${stamp()}", query)
        val modules = data.optJSONObject("modules")?.opt("list") ?: throw invalid
        val sections = mutableListOf<HomeSection>()
        val tabs = mutableListOf<HomeTab>()
        val tabIds = mutableSetOf<Int>()
        for (rawModule in rows(modules)) {
            when (rawModule.opt("moduleCode")) {
                "pageTab" -> for (rawTab in rows(rawModule.opt("list"))) {
                    val id = integer(rawTab.opt("tabId")) ?: throw invalid
                    val title = text(rawTab.opt("title")).trim()
                    if (id <= 0 || title.isEmpty() || !tabIds.add(id)) throw invalid
                    tabs += HomeTab(id, title)
                }

                "rankingLists" -> for (rawRanking in rows(rawModule.opt("list"))) {
                    if (rawRanking.opt("isDefault") != true) continue
                    val rows = rows(rawRanking.opt("list"))
                    if (rows.isNotEmpty()) {
                        sections += section(text(rawRanking.opt("rankingListName")), rows, ranking = true)
                    }
                }

                "feedStream" -> {
                    val albums = mutableListOf<JSONObject>()
                    for (rawItem in rows(rawModule.opt("list"))) {
                        if (rawItem.opt("itemType") != "ALBUM") continue
                        albums += rawItem.optJSONObject("item") ?: throw invalid
                    }
                    if (albums.isNotEmpty()) sections += section(text(rawModule.opt("title")), albums)
                }

                "contentPool" -> {
                    val rows = rows(rawModule.opt("list"))
                    if (rows.isNotEmpty()) sections += section(text(rawModule.opt("title")), rows)
                }
            }
        }
        return HomeContent(sections, tabs)
    }

    private fun section(title: String, rows: List<JSONObject>, ranking: Boolean = false): HomeSection {
        val albums = mutableListOf<Album>()
        val stats = linkedMapOf<String, HomeAlbumStats>()
        val ids = mutableSetOf<String>()
        for (row in rows) {
            val album = album(row)
            if (!ids.add(album.id)) continue
            albums += album
            val score = text(row.opt("albumScore")).toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.0..10.0 }
            stats[album.id] = HomeAlbumStats(
                score = score,
                playCount = integer(row.opt("playCount")),
                subscribeCount = integer(row.opt("subscribeCount")),
                finished = row.opt("isFinished") == true,
            )
        }
        return HomeSection(
            title = title.ifEmpty { if (ranking) "排行榜" else "推荐专辑" },
            albums = albums,
            isRanking = ranking,
            stats = stats,
        )
    }

    private fun album(row: JSONObject): Album {
        val albumId = text(row.opt("albumId"))
        if (albumId.isEmpty()) throw invalid
        val id = xmlyIdentifier(albumId)
        val map = mapOf(
            "albumTitle" to text(row.opt("albumTitle")),
            "albumIntro" to text(row.opt("albumIntro")),
            "nickname" to text(row.opt("nickname")),
            "coverLarge" to text(row.opt("coverLarge")),
            "coverMiddle" to text(row.opt("coverMiddle")),
            "coverSmall" to text(row.opt("coverSmall")),
        )
        val plain = map.toPlainMap()
        return Album(
            rawId = id,
            title = (plain["albumTitle"] ?: "").toString(),
            artist = (plain["nickname"] ?: "").toString(),
            intro = (plain["albumIntro"] ?: "").toString(),
            coverUrl = coverUrl(plain),
            total = integer(row.opt("trackCount")) ?: 0,
            raw = publicRaw(plain),
        )
    }

    private fun subscriptionAlbum(row: JSONObject): Album {
        val albumId = text(row.opt("albumId"))
        if (albumId.isEmpty()) throw invalid
        val map = mapOf(
            "albumTitle" to text(row.opt("albumTitle")),
            "albumIntro" to text(row.opt("albumIntro")),
            "nickname" to text(row.opt("nickname")),
            "coverLarge" to text(row.opt("coverLarge")),
            "coverMiddle" to text(row.opt("coverMiddle")),
            "coverSmall" to text(row.opt("coverSmall")),
            "cover_url" to text(row.opt("albumCover")),
        )
        val plain = map.toPlainMap()
        return Album(
            rawId = xmlyIdentifier(albumId),
            title = (plain["albumTitle"] ?: "").toString(),
            artist = (plain["nickname"] ?: "").toString(),
            intro = (plain["albumIntro"] ?: "").toString(),
            coverUrl = coverUrl(plain),
            total = integer(row.opt("trackCount")) ?: 0,
            raw = publicRaw(plain),
        )
    }

    private fun track(row: JSONObject): Track {
        val id = text(row.opt("trackId")).ifEmpty { text(row.opt("id")) }
        if (id.isEmpty()) throw invalid
        val albumId = text(row.opt("albumId")).ifEmpty { text(row.opt("album_id")) }
        val map = mapOf(
            "trackId" to id,
            "title" to text(row.opt("title")),
            "nickname" to text(row.opt("nickname")),
            "albumTitle" to text(row.opt("albumTitle")),
            "albumId" to albumId,
            "coverLarge" to text(row.opt("coverLarge")),
            "coverMiddle" to text(row.opt("coverMiddle")),
            "coverSmall" to text(row.opt("coverSmall")),
            "cover_url" to text(row.opt("cover")),
        ).toPlainMap()
        val duration = when (val raw = row.opt("duration")) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        return Track(
            rawId = id,
            title = (map["title"] ?: "").toString(),
            artist = (map["nickname"] ?: "").toString(),
            album = (map["albumTitle"] ?: "").toString(),
            albumId = albumId,
            duration = duration,
            isPaid = row.opt("isPaid") as? Boolean ?: row.opt("paid") as? Boolean,
            isAuthorized = row.opt("isAuthorized") as? Boolean ?: row.opt("authorized") as? Boolean,
            coverUrl = coverUrl(map),
            raw = publicRaw(map),
        )
    }

    /** Subscribed albums with unread counts, newest update first. */
    fun subscriptions(page: Int = 1, pageSize: Int = 30): SubscriptionPage {
        requireAccount()
        paging(page, pageSize)
        val data = get(
            "/fsubscribe-web/subscribe/comprehensive/rank/${stamp()}",
            mapOf("pageId" to page.toString(), "sign" to "2", "size" to pageSize.toString()),
        )
        val seen = mutableSetOf<String>()
        val items = rows(data.opt("albumResults")).take(pageSize).map { row ->
            val album = subscriptionAlbum(row)
            if (!seen.add(album.id)) {
                throw XmlyException("repeated_page", "订阅列表重复，请刷新后重试")
            }
            SubscriptionItem(
                album = album,
                latestTitle = text(row.opt("trackTitle")),
                unreadCount = integer(row.opt("unreadNum")) ?: throw invalid,
                updatedAt = time(row.opt("lastUpdateAt") ?: 0),
            )
        }
        val hasMore = (data.opt("hasMore") as? Boolean) ?: (items.size >= pageSize)
        if (hasMore && items.isEmpty()) throw invalid
        return SubscriptionPage(items, hasMore)
    }

    /** Favourite tracks, newest first. */
    fun favorites(page: Int = 1, pageSize: Int = 30): FavoritePage {
        requireAccount()
        paging(page, pageSize)
        val data = get(
            "/lite-mobile/like/v1/user-likes",
            mapOf("pageId" to page.toString(), "pageSize" to pageSize.toString()),
        )
        val maxPage = integer(data.opt("maxPageId")) ?: 0
        val echoedPage = integer(data.opt("pageId")) ?: 0
        val echoedSize = integer(data.opt("pageSize")) ?: 0
        if (echoedPage > 0 && echoedPage != page) throw invalid
        if (echoedSize > 0 && echoedSize != pageSize) throw invalid
        val seen = mutableSetOf<String>()
        val items = rows(data.opt("dataList")).take(pageSize).mapNotNull { row ->
            if (integer(row.opt("resourceType")) != 1) throw invalid
            val track = track(row)
            if (track.albumKey.isEmpty()) throw invalid
            if (!seen.add(track.id)) {
                throw XmlyException("repeated_page", "收藏列表重复，请刷新后重试")
            }
            track
        }
        val hasMore = page < maxPage
        if ((hasMore && items.isEmpty()) || (items.isNotEmpty() && page > maxPage)) throw invalid
        return FavoritePage(items, hasMore)
    }

    /** Newest episodes of subscribed albums; [before] pages backwards. */
    fun updates(before: Long? = null, pageSize: Int = 30): UpdatePage {
        requireAccount()
        paging(1, pageSize)
        if (before != null && (before <= 0 || before > 2115354119000L)) throw invalid
        val query = linkedMapOf("sign" to "2", "size" to pageSize.toString())
        before?.let { query["timeline"] = it.toString() }
        val data = get("/track-feed/v1/track-feed/dynamic/ts-${stamp()}", query)
        val seen = mutableSetOf<String>()
        var cursor: Long? = null
        val items = rows(data.opt("trackResults")).take(pageSize).map { row ->
            val track = track(row)
            if (!seen.add(track.id)) {
                throw XmlyException("repeated_page", "更新列表重复，请刷新后重试")
            }
            val timeline = time(row.opt("timeline"))
            val previous = cursor
            if (previous != null && timeline > previous) throw invalid
            cursor = timeline
            UpdateItem(track, time(row.opt("createdAt") ?: timeline))
        }
        val hasMore = more(data)
        val next = cursor
        if (before != null && next != null && next >= before) {
            throw XmlyException("repeated_page", "暂时无法读取更早的更新，已保留当前列表")
        }
        if (hasMore && (next == null || next <= 0)) throw invalid
        return UpdatePage(items, hasMore, next)
    }

    fun setSubscribed(albumId: String, subscribed: Boolean) {
        requireAccount()
        val id = xmlyIdentifier(albumId)
        val path = if (subscribed) {
            "/subscribe/album-subscribe/and/ask-anchor-info"
        } else {
            "/subscribe/album/subscribe/delete"
        }
        val payload = client.request(
            "POST",
            XmlyClient.MOBILE,
            path,
            form = mapOf("albumId" to id),
            readOnly = false,
        )
        if (integer(payload.opt("ret")) != 0) throw invalid
    }

    fun setFavorite(track: Track, favorite: Boolean) {
        requireAccount()
        val id = xmlyIdentifier(track.id)
        var album = track.albumKey
        if (album.isEmpty()) album = client.track(id).albumKey
        if (album.isEmpty()) throw XmlyException("missing_album", "缺少专辑标识", definitive = true)
        val payload = client.request(
            "POST",
            XmlyClient.MOBILE,
            if (favorite) "/lite-mobile/like/v1/like" else "/lite-mobile/like/v1/dislike",
            form = mapOf("resourceId" to id, "albumId" to album, "resourceType" to "1"),
            readOnly = false,
        )
        if (integer(payload.opt("ret")) != 0) throw invalid
    }

    /**
     * Signed-in profile (nickname, avatar); `/mobile-user/user/detail` requires
     * the account cookie, so this is only called for a stored account.
     */
    fun profile(): teikengstudio.xmpro.model.UserProfile {
        requireAccount()
        val uid = client.context()["uid"] ?: throw invalid
        val payload = profilePayload(uid)
        val nickname = findString(payload, setOf("nickname", "userName", "name", "nickName")) ?: ""
        val avatar = findString(payload, setOf("avatar", "avatarUrl", "logoPic", "smallLogo", "cover"))
        val mobile = findString(payload, setOf("mobile", "phone", "mobilePhone")) ?: ""
        if (nickname.isEmpty() && avatar == null && mobile.isEmpty()) {
            throw XmlyException("profile_unavailable", "账号资料不可用", definitive = true)
        }
        return teikengstudio.xmpro.model.UserProfile(
            uid = uid,
            nickname = nickname,
            avatarUrl = avatar?.takeIf { it.startsWith("http") },
            intro = findString(payload, setOf("personalSignature", "intro", "description")) ?: "",
        )
    }

    /**
     * Main-site endpoint first: the passport cookie is accepted by
     * `revision/my/getCurrentUserInfo`, while the mobile-user routes answer an
     * empty 404 for lite tokens.
     */
    private fun profilePayload(uid: String): JSONObject {
        val attempts = listOf<() -> JSONObject>(
            {
                client.request(
                    "GET",
                    XmlyHttp.SEARCH_ORIGIN,
                    "/revision/my/getCurrentUserInfo",
                )
            },
            {
                client.request(
                    "GET",
                    XmlyClient.MOBILE,
                    "/mobile-user/user/detail",
                    query = mapOf("uid" to uid, "device" to "android"),
                )
            },
        )
        var last: XmlyException? = null
        for (attempt in attempts) {
            try {
                val payload = attempt()
                if (payload.optJSONObject("data") != null || payload.has("uid")) return payload
            } catch (error: XmlyException) {
                last = error
            }
        }
        throw last ?: XmlyException("profile_unavailable", "账号资料不可用", definitive = true)
    }

    /** Breadth-first search for the first non-blank string under [keys]. */
    private fun findString(root: JSONObject, keys: Set<String>): String? {
        val queue = ArrayDeque<Any>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 3000) {
            visited++
            when (val node = queue.removeFirst()) {
                is JSONObject -> {
                    for (key in node.keys()) {
                        if (key in keys) {
                            val value = node.opt(key) as? String
                            if (!value.isNullOrBlank()) return value
                        }
                    }
                    for (key in node.keys()) {
                        when (val child = node.opt(key)) {
                            is JSONObject, is JSONArray -> queue.add(child)
                        }
                    }
                }

                is JSONArray -> for (index in 0 until node.length()) {
                    when (val child = node.opt(index)) {
                        is JSONObject, is JSONArray -> queue.add(child)
                    }
                }
            }
        }
        return null
    }

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val payload = if (client.credentials != null) {
            client.request("GET", XmlyClient.MOBILE, path, query = query)
        } else {
            client.getMobile(path, query)
        }
        return payload.optJSONObject("data") ?: throw invalid
    }

    private fun requireAccount() {
        if (client.credentials == null) {
            throw XmlyException("login_required", "登录后使用订阅、收藏和更新", definitive = true)
        }
    }

    private fun paging(page: Int, size: Int) {
        if (page < 1 || page > 10000 || size < 1 || size > 50) {
            throw XmlyException("invalid_page", "页码范围为 1..10000，每页 1..50", definitive = true)
        }
    }

    private fun more(data: JSONObject): Boolean =
        (data.opt("hasMore") as? Boolean) ?: throw invalid

    private fun time(value: Any?): Long {
        val number = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: throw invalid
        if (number < 0 || number > 2115354119000L) throw invalid
        return number
    }

    private fun rows(value: Any?): List<JSONObject> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("list") ?: return emptyList()
            else -> return emptyList()
        }
        if (array.length() > 500) throw invalid
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun integer(value: Any?): Int? {
        val text = value?.toString() ?: return null
        val number = text.toLongOrNull() ?: return null
        return if (number in 0..Int.MAX_VALUE) number.toInt() else null
    }

    private fun text(value: Any?): String =
        if (value == null || value === JSONObject.NULL) "" else value.toString()

    private fun stamp(): Long = System.currentTimeMillis()
}

private fun Map<String, Any?>.toPlainMap(): Map<String, Any?> =
    entries.associate { (key, value) ->
        key to if (value == null || value === JSONObject.NULL) null else value
    }
