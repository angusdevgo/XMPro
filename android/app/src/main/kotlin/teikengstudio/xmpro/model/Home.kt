package teikengstudio.xmpro.model

/** A home channel ("推荐", "小说", …) shown as a top tab. */
data class HomeChannel(val pageId: Int, val title: String)

/** A secondary tab inside one channel. */
data class HomeTab(val tabId: Int, val title: String)

data class HomeAlbumStats(
    val score: Double? = null,
    val playCount: Int? = null,
    val subscribeCount: Int? = null,
    val finished: Boolean = false,
)

class HomeSection(
    val title: String,
    val albums: List<Album>,
    val isRanking: Boolean = false,
    val stats: Map<String, HomeAlbumStats> = emptyMap(),
)

class HomeContent(
    val sections: List<HomeSection>,
    val tabs: List<HomeTab> = emptyList(),
)

/** One subscribed album plus its newest episode and unread count. */
class SubscriptionItem(
    val album: Album,
    val latestTitle: String,
    val unreadCount: Int,
    val updatedAt: Long,
)

class SubscriptionPage(
    val items: List<SubscriptionItem>,
    val hasMore: Boolean,
)

class FavoritePage(
    val items: List<Track>,
    val hasMore: Boolean,
)

/** A newly published episode in the "listening updates" feed. */
class UpdateItem(
    val track: Track,
    val publishedAt: Long,
)

class UpdatePage(
    val items: List<UpdateItem>,
    val hasMore: Boolean,
    val nextTimeline: Long?,
)

/** One cloud listening record: where playback stopped for a track. */
class HistoryRecord(
    val track: Track,
    val positionSeconds: Double,
    val updatedAt: Long,
)
