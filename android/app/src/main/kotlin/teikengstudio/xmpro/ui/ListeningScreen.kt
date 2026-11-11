package teikengstudio.xmpro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
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
    barsVisible: Boolean = true,
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val filtered = { text: String -> query.isBlank() || text.lowercase().contains(query.lowercase()) }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = barsVisible || query.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapsuleSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "筛选更新、订阅、历史与收藏",
                    onClear = { query = "" },
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onRefresh,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs.size) { index ->
                    PillChip(
                        selected = pagerState.currentPage == index,
                        label = tabs[index],
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }
            }
        }

        if (state.listeningLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .clip(CircleShape),
            )
        }
        state.listeningError?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (!state.signedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        "登录后查看订阅、更新与云历史",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                }
            }
        }

        if (state.signedIn) HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> UpdatesTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onPlay = onPlay,
                    onLoadMore = onLoadMoreUpdates,
                    onBarsHidden = onBarsHidden,
                )

                1 -> SubscriptionsTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onUnsubscribe = onUnsubscribe,
                    onLoadMore = onLoadMoreSubscriptions,
                    onBarsHidden = onBarsHidden,
                )

                2 -> HistoryTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onPlay = onPlay,
                    onBarsHidden = onBarsHidden,
                )

                3 -> FavoritesTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onPlay = onPlay,
                    onUnfavorite = onUnfavorite,
                    onLoadMore = onLoadMoreFavorites,
                    onBarsHidden = onBarsHidden,
                )

                else -> BookmarksTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onPlay = onPlay,
                    onRemoveBookmark = onRemoveBookmark,
                    onBarsHidden = onBarsHidden,
                )
            }
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
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val visible = state.updates.filter { filter("${it.track.title} ${it.track.album} ${it.track.artist}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
    ) {
        if (state.subscriptions.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(
                            "常听订阅",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.subscriptions.take(20), key = { it.album.id }) { item ->
                                Column(
                                    Modifier
                                        .width(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenAlbum(item.album.id) }
                                        .padding(vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box {
                                        Cover(item.album.coverUrl, 64)
                                        if (item.unreadCount > 0) {
                                            Badge(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp),
                                            ) {
                                                Text("${item.unreadCount.coerceAtMost(99)}")
                                            }
                                        }
                                    }
                                    Text(
                                        item.album.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        datedItems(visible, { it.publishedAt }, { item ->
            TrackLine(
                track = item.track,
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
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val visible = state.subscriptions.filter {
        filter("${it.album.title} ${it.latestTitle} ${it.album.artist}")
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
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
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val visible = state.history.filter { filter("${it.track.title} ${it.track.album}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
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
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val visible = state.favorites.filter { filter("${it.title} ${it.album} ${it.artist}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
    ) {
        items(visible, key = { it.id }) { track ->
            TrackLine(
                track = track,
                onPlay = { onPlay(track, 0) },
                onOpenAlbum = onOpenAlbum,
                onRemove = { onUnfavorite(track) },
            )
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
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val rows = state.bookmarks.filter { filter("${it.track.title} ${it.track.album}") }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
    ) {
        items(rows, key = { it.track.id }) { bookmark ->
            Surface(
                onClick = { onPlay(bookmark.track, bookmark.positionMs) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(bookmark.track.coverUrl, 52)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            bookmark.track.title.ifEmpty { bookmark.track.number },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            ) {
                                Text(
                                    "书签 ${formatDuration(bookmark.positionMs / 1000.0)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (bookmark.track.album.isNotEmpty()) {
                                Text(
                                    bookmark.track.album,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onRemoveBookmark(bookmark.track.id) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "删除书签",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onPlay(bookmark.track, bookmark.positionMs) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "续播",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (rows.isEmpty()) item { Empty("暂无书签") }
    }
}

/** Retracts the floating chrome once the list leaves its first row. */
@Composable
private fun ScrollHideEffect(
    state: androidx.compose.foundation.lazy.LazyListState,
    onBarsHidden: (Boolean) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(state) {
        var anchor = 0
        androidx.compose.runtime.snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val position = index * 100_000 + offset
            when {
                position > anchor + 120 -> {
                    anchor = position
                    onBarsHidden(true)
                }

                position < anchor - 120 -> {
                    anchor = position
                    onBarsHidden(false)
                }
            }
        }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        day,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    onPlay: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        onClick = {
            if (track.albumKey.isNotEmpty()) onOpenAlbum(track.albumKey) else onPlay()
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(track.coverUrl, 52)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    track.title.ifEmpty { track.number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        track.album.takeIf { it.isNotEmpty() },
                        formatDuration(track.duration),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "取消收藏",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SubscriptionLine(
    item: SubscriptionItem,
    onOpenAlbum: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
) {
    Surface(
        onClick = { onOpenAlbum(item.album.id) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(item.album.coverUrl, 56)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.album.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.unreadCount > 0) {
                        Badge(Modifier.padding(start = 6.dp)) { Text("${item.unreadCount}") }
                    }
                }
                Text(
                    item.latestTitle.ifEmpty { "${item.album.total} 集" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = { onUnsubscribe(item.album.id) }) {
                Text("退订", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HistoryLine(record: HistoryRecord, onPlay: () -> Unit) {
    val progress = if (record.track.duration > 0) {
        (record.positionSeconds / record.track.duration).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(record.track.coverUrl, 52)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    record.track.title.ifEmpty { record.track.number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        record.track.album.takeIf { it.isNotEmpty() },
                        "已听 ${formatDuration(record.positionSeconds)} / ${formatDuration(record.track.duration)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "续播",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadMore(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onClick, shape = CircleShape) { Text(label) }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
