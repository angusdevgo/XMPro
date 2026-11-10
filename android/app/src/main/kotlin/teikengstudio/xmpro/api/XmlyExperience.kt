package teikengstudio.xmpro.api

import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.HomeAlbumStats
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeContent
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.HomeTab
import teikengstudio.xmpro.model.XmlyException
import teikengstudio.xmpro.model.coverUrl
import teikengstudio.xmpro.model.publicRaw
import teikengstudio.xmpro.model.xmlyIdentifier

/**
 * Lite-client home surfaces (channels, module feeds, rankings).
 *
 * These endpoints are public, so the home screen works before an account is
 * imported; only subscription/favourite mutations need authentication.
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

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val payload = client.getMobile(path, query)
        return payload.optJSONObject("data") ?: throw invalid
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
