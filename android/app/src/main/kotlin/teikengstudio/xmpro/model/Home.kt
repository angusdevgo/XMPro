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
