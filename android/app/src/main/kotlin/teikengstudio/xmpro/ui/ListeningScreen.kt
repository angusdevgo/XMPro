package teikengstudio.xmpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import teikengstudio.xmpro.model.HistoryRecord
import teikengstudio.xmpro.model.SubscriptionItem
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.UpdateItem

private val tabs = listOf("听更新", "订阅", "历史", "收藏", "书签")

@Composable
fun ListeningScreen(
    state: UiState,
    bottomInset: Dp,
    onRefresh: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (Track, Long) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onUnfavorite: (Track) -> Unit,
    onLoadMoreUpdates: () -> Unit,
    onLoadMoreSubscriptions: () -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val filtered = { text: String -> query.isBlank() || text.lowercase().contains(query.lowercase()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
        ScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 12.dp,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(label) },
                )
            }
        }
        if (state.listeningLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.listeningError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (!state.signedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("登录后查看订阅、更新与云历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        when (selected) {
            0 -> UpdatesTab(
                state = state,
                bottomInset = bottomInset,
                filter = filtered,
                onOpenAlbum = onOpenAlbum,
                onPlay = onPlay,
                onLoadMore = onLoadMoreUpdates,
            )

            1 -> SubscriptionsTab(
                state = state,
                bottomInset = bottomInset,
                filter = filtered,
                onOpenAlbum = onOpenAlbum,
                onUnsubscribe = onUnsubscribe,
                onLoadMore = onLoadMoreSubscriptions,
            )

            2 -> HistoryTab(
                state = state,
                bottomInset = bottomInset,
                filter = filtered,
                onPlay = onPlay,
            )

            3 -> FavoritesTab(
                state = state,
                bottomInset = bottomInset,
                filter = filtered,
                onOpenAlbum = onOpenAlbum,
                onPlay = onPlay,
                onUnfavorite = onUnfavorite,
                onLoadMore = onLoadMoreFavorites,
            )

            else -> BookmarksTab(
                state = state,
                bottomInset = bottomInset,
                filter = filtered,
                onPlay = onPlay,
                onRemoveBookmark = onRemoveBookmark,
            )
        }
    }
}

@Composable
private fun UpdatesTab(
    state: UiState,
    bottomInset: Dp,
    filter: (String) -> Boolean,
    onOpenAlbum: (String) -> Unit,
    onPlay: (Track, Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val visible = state.updates.filter { filter("${it.track.title} ${it.track.album} ${it.track.artist}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        if (state.subscriptions.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.subscriptions.take(20), key = { it.album.id }) { item ->
                        Column(
                            Modifier
                                .width(72.dp)
                                .clickable { onOpenAlbum(item.album.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Cover(item.album.coverUrl, 64)
                            Text(
                                item.album.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        datedItems(visible, { it.publishedAt }, { item ->
            TrackLine(
                track = item.track,
                positionMs = 0,
                onPlay = { onPlay(item.track, 0) },
                onOpenAlbum = onOpenAlbum,
            )
        })
        item {
            if (state.moreUpdates) {
                LoadMore("加载更多更新", onLoadMore)
            } else if (visible.isEmpty()) {
                Empty("暂无更新")
            }
        }
    }
}

@Composable
private fun SubscriptionsTab(
    state: UiState,
    bottomInset: Dp,
    filter: (String) -> Boolean,
    onOpenAlbum: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val visible = state.subscriptions.filter {
        filter("${it.album.title} ${it.latestTitle} ${it.album.artist}")
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        datedItems(visible, { it.updatedAt }, { item ->
            SubscriptionLine(item, onOpenAlbum, onUnsubscribe)
        })
        item {
            if (state.moreSubscriptions) {
                LoadMore("加载更多订阅", onLoadMore)
            } else if (visible.isEmpty()) {
                Empty("暂无订阅")
            }
        }
    }
}

@Composable
private fun HistoryTab(
    state: UiState,
    bottomInset: Dp,
    filter: (String) -> Boolean,
    onPlay: (Track, Long) -> Unit,
) {
    val visible = state.history.filter { filter("${it.track.title} ${it.track.album}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        datedItems(visible, { it.updatedAt }, { record ->
            HistoryLine(record) { onPlay(record.track, (record.positionSeconds * 1000).toLong()) }
        })
        if (visible.isEmpty()) item { Empty("暂无云历史记录") }
    }
}

@Composable
private fun FavoritesTab(
    state: UiState,
    bottomInset: Dp,
    filter: (String) -> Boolean,
    onOpenAlbum: (String) -> Unit,
    onPlay: (Track, Long) -> Unit,
    onUnfavorite: (Track) -> Unit,
    onLoadMore: () -> Unit,
) {
    val visible = state.favorites.filter { filter("${it.title} ${it.album} ${it.artist}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        items(visible, key = { it.id }) { track ->
            TrackLine(
                track = track,
                positionMs = 0,
                onPlay = { onPlay(track, 0) },
                onOpenAlbum = onOpenAlbum,
                onRemove = { onUnfavorite(track) },
            )
            HorizontalDivider()
        }
        item {
            if (state.moreFavorites) {
                LoadMore("加载更多收藏", onLoadMore)
            } else if (visible.isEmpty()) {
                Empty("暂无收藏")
            }
        }
    }
}

@Composable
private fun BookmarksTab(
    state: UiState,
    bottomInset: Dp,
    filter: (String) -> Boolean,
    onPlay: (Track, Long) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    val rows = state.bookmarks.filter { filter("${it.track.title} ${it.track.album}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        items(rows, key = { it.track.id }) { bookmark ->
            ListItem(
                headlineContent = {
                    Text(
                        bookmark.track.title.ifEmpty { bookmark.track.number },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            bookmark.track.album.takeIf { it.isNotEmpty() },
                            "书签 ${formatDuration(bookmark.positionMs / 1000.0)}",
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { Cover(bookmark.track.coverUrl, 48) },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onPlay(bookmark.track, bookmark.positionMs) }) {
                            Text("续播")
                        }
                        TextButton(onClick = { onRemoveBookmark(bookmark.track.id) }) {
                            Text("删除")
                        }
                    }
                },
                modifier = Modifier.clickable { onPlay(bookmark.track, bookmark.positionMs) },
            )
            HorizontalDivider()
        }
        if (rows.isEmpty()) item { Empty("暂无书签") }
    }
}

/** Inserts a date header before the first item of each day. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.datedItems(
    items: List<T>,
    timestamp: (T) -> Long,
    row: @Composable (T) -> Unit,
) {
    var previous: String? = null
    items.forEachIndexed { index, item ->
        val day = dayLabel(timestamp(item))
        if (day != previous) {
            previous = day
            item(key = "day-$day-$index") {
                Text(
                    day,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
        }
        item(key = "row-$index-${timestamp(item)}") { row(item) }
    }
}

private fun dayLabel(timestamp: Long): String {
    if (timestamp <= 0) return "更早"
    val format = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    val date = Date(timestamp)
    val today = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
    return when (SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(date)) {
        today -> "今天"
        SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(System.currentTimeMillis() - 86_400_000L)) -> "昨天"
        else -> format.format(date)
    }
}

@Composable
private fun TrackLine(
    track: Track,
    positionMs: Long,
    onPlay: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(track.title.ifEmpty { track.number }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    track.album.takeIf { it.isNotEmpty() },
                    formatDuration(track.duration),
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Cover(track.coverUrl, 48) },
        trailingContent = { TextButton(onClick = onPlay) { Text("播放") } },
        modifier = Modifier.clickable {
            if (track.albumKey.isNotEmpty()) onOpenAlbum(track.albumKey) else Unit
        },
        overlineContent = onRemove?.let {
            { TextButton(onClick = it) { Text("取消收藏") } }
        },
    )
}

@Composable
private fun SubscriptionLine(
    item: SubscriptionItem,
    onOpenAlbum: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.album.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.unreadCount > 0) {
                    Badge(Modifier.padding(start = 6.dp)) { Text("${item.unreadCount}") }
                }
            }
        },
        supportingContent = {
            Text(
                item.latestTitle.ifEmpty { "${item.album.total} 集" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Cover(item.album.coverUrl, 48) },
        trailingContent = {
            TextButton(onClick = { onUnsubscribe(item.album.id) }) { Text("退订") }
        },
        modifier = Modifier.clickable { onOpenAlbum(item.album.id) },
    )
}

@Composable
private fun HistoryLine(record: HistoryRecord, onPlay: () -> Unit) {
    val progress = if (record.track.duration > 0) {
        (record.positionSeconds / record.track.duration).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    ListItem(
        headlineContent = {
            Text(record.track.title.ifEmpty { record.track.number }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        record.track.album.takeIf { it.isNotEmpty() },
                        "已听 ${formatDuration(record.positionSeconds)} / ${formatDuration(record.track.duration)}",
                    ).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        },
        leadingContent = { Cover(record.track.coverUrl, 48) },
        trailingContent = { TextButton(onClick = onPlay) { Text("续播") } },
        modifier = Modifier.clickable(onClick = onPlay),
    )
}

@Composable
private fun LoadMore(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text("筛选更新、订阅、历史与收藏") },
    )
}
