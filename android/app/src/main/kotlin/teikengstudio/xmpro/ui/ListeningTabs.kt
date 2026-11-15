package teikengstudio.xmpro.ui

/**
 * The chips at the top of the 听书 tab.
 *
 * The stored order is the source of truth, but it is never trusted: an older
 * build may have written keys that no longer exist, and a newer one may have
 * added keys the saved list knows nothing about. [normalize] resolves both.
 */
object ListeningTabs {
    const val UPDATES = "updates"
    const val SUBSCRIPTIONS = "subscriptions"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val BOOKMARKS = "bookmarks"

    /** Every tab, in the order a fresh install shows them. */
    val all: List<Pair<String, String>> = listOf(
        UPDATES to "听更新",
        SUBSCRIPTIONS to "订阅",
        HISTORY to "历史",
        FAVORITES to "收藏",
        BOOKMARKS to "书签",
    )

    val defaultOrder: List<String> = all.map { it.first }

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: key

    /**
     * Saved keys first (unknown ones dropped), then any tab the saved list is
     * missing, so an upgrade can never hide a tab from the user.
     */
    fun normalize(order: List<String>): List<String> {
        val known = all.map { it.first }
        val kept = order.filter { it in known }.distinct()
        return kept + known.filterNot { it in kept }
    }

    /** Moves the tab at [from] by [delta]; out of range moves are ignored. */
    fun move(order: List<String>, from: Int, delta: Int): List<String> {
        val target = from + delta
        if (from !in order.indices || target !in order.indices) return order
        return order.toMutableList().apply {
            val moved = removeAt(from)
            add(target, moved)
        }
    }
}
