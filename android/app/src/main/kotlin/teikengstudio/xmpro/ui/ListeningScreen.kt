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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/** Chip keys in the order the user chose; see [ListeningTabs]. */
private fun orderedTabs(order: List<String>): List<String> = ListeningTabs.normalize(order)

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
    onDeleteHistory: (List<HistoryRecord>) -> Unit = {},
    onTabOrder: (List<String>) -> Unit = {},
    barsVisible: Boolean = true,
    onBarsHidden: (Boolean) -> Unit = {},
    onRefreshHistory: () -> Unit = {},
) {
    val tabs = orderedTabs(state.listeningTabOrder)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editingOrder by remember { mutableStateOf(false) }
    val filtered = { text: String -> query.isBlank() || text.lowercase().contains(query.lowercase()) }

    // The history page moves with the user's order, so it is looked up by key.
    LaunchedEffect(pagerState.currentPage, tabs) {
        if (tabs.getOrNull(pagerState.currentPage) == ListeningTabs.HISTORY) onRefreshHistory()
    }

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
                        label = ListeningTabs.label(tabs[index]),
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        onLongClick = { editingOrder = true },
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
            when (tabs[page]) {
                ListeningTabs.UPDATES -> UpdatesTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onPlay = onPlay,
                    onLoadMore = onLoadMoreUpdates,
                    onBarsHidden = onBarsHidden,
                )

                ListeningTabs.SUBSCRIPTIONS -> SubscriptionsTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onUnsubscribe = onUnsubscribe,
                    onLoadMore = onLoadMoreSubscriptions,
                    onBarsHidden = onBarsHidden,
                )

                ListeningTabs.HISTORY -> HistoryTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onPlay = onPlay,
                    onDelete = onDeleteHistory,
                    onBarsHidden = onBarsHidden,
                )

                ListeningTabs.FAVORITES -> FavoritesTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onOpenAlbum = onOpenAlbum,
                    onPlay = onPlay,
                    onUnfavorite = onUnfavorite,
                    onLoadMore = onLoadMoreFavorites,
                    onBarsHidden = onBarsHidden,
                )

                ListeningTabs.BOOKMARKS -> BookmarksTab(
                    state = state,
                    bottomInset = bottomInset,
                    filter = filtered,
                    onPlay = onPlay,
                    onRemoveBookmark = onRemoveBookmark,
                    onBarsHidden = onBarsHidden,
                )
            }
        }

        if (editingOrder) {
            ListeningTabsSheet(
                order = tabs,
                onMove = { from, delta -> onTabOrder(ListeningTabs.move(tabs, from, delta)) },
                onReset = { onTabOrder(ListeningTabs.defaultOrder) },
                onDismiss = { editingOrder = false },
            )
        }
    }
}

/**
 * Reorder sheet for the 听书 chips.
 *
 * Arrow buttons rather than drag and drop: the list is five rows tall and the
 * pager underneath is swipeable, so a drag would fight the horizontal gesture.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ListeningTabsSheet(
    order: List<String>,
    onMove: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "标签排序",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "用箭头调整顺序，长按顶部标签可再次打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset) { Text("恢复默认") }
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            itemsIndexed(order) { index, key ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        ListeningTabs.label(key),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(
                        onClick = { onMove(index, -1) },
                        enabled = index > 0,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "前移",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onMove(index, 1) },
                        enabled = index < order.lastIndex,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "后移",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
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
    onDelete: (List<HistoryRecord>) -> Unit,
    onBarsHidden: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    val visible = state.history.filter { filter("${it.track.title} ${it.track.album}") }
    var editing by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var confirming by remember { mutableStateOf(false) }
    // Rows can disappear after a delete, so dead selections are dropped rather
    // than blocking the next delete on a record that no longer exists.
    LaunchedEffect(visible) {
        val alive = visible.map { it.track.id }.toSet()
        selected.retainAll { it in alive }
        if (visible.isEmpty()) editing = false
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
    ) {
        if (visible.isNotEmpty()) {
            item(key = "history-actions") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (editing) "已选 ${selected.size} 条" else "共 ${visible.size} 条记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (editing) {
                        TextButton(
                            onClick = { confirming = true },
                            enabled = selected.isNotEmpty(),
                        ) {
                            Text("删除")
                        }
                    }
                    TextButton(
                        onClick = {
                            editing = !editing
                            if (!editing) selected.clear()
                        },
                    ) {
                        Text(if (editing) "完成" else "编辑")
                    }
                }
            }
        }
        datedItems(visible, { it.updatedAt }, { record ->
            HistoryLine(
                record = record,
                editing = editing,
                selected = record.track.id in selected,
                downloaded = record.track.id in state.downloadedIds,
                older = state.albumPositions[record.track.albumKey]
                    .orEmpty()
                    .filter { it.track.id != record.track.id },
                onResumeOlder = { place -> onPlay(place.track, (place.positionSeconds * 1000).toLong()) },
                onPlay = { onPlay(record.track, (record.positionSeconds * 1000).toLong()) },
                onToggle = {
                    val id = record.track.id
                    if (id in selected) selected.remove(id) else selected.add(id)
                },
            )
        })
        if (visible.isEmpty()) {
            item {
                // An empty list means two different things: nothing listened to
                // yet, or no account to read the cloud history from.
                Empty(if (state.signedIn) "暂无云历史记录" else "登录后可查看云端历史")
            }
        }
    }

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("删除历史记录") },
            text = {
                Text("将从云端一并删除 ${selected.size} 条记录，喜马拉雅其他客户端也会同步消失。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = state.history.filter { it.track.id in selected }
                        confirming = false
                        editing = false
                        selected.clear()
                        onDelete(chosen)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消") }
            },
        )
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
                downloaded = track.id in state.downloadedIds,
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
                            if (bookmark.track.id in state.downloadedIds) {
                                DownloadBadge()
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
    downloaded: Boolean = false,
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
                if (downloaded) {
                    DownloadBadge(Modifier.padding(top = 5.dp))
                }
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
private fun HistoryLine(
    record: HistoryRecord,
    editing: Boolean = false,
    selected: Boolean = false,
    downloaded: Boolean = false,
    /** Earlier places of the same album, remembered on this device. */
    older: List<teikengstudio.xmpro.model.PlayPosition> = emptyList(),
    onResumeOlder: (teikengstudio.xmpro.model.PlayPosition) -> Unit = {},
    onPlay: () -> Unit,
    onToggle: () -> Unit = {},
) {
    var olderOpen by remember { mutableStateOf(false) }
    val progress = if (record.track.duration > 0) {
        (record.positionSeconds / record.track.duration).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
    Surface(
        onClick = if (editing) onToggle else onPlay,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "取消选择" else "选择",
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
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
                if (downloaded) {
                    DownloadBadge(Modifier.padding(top = 5.dp))
                }
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
            if (!editing) {
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
    // The cloud keeps one row per album; the places this device also visited are
    // tucked under the row instead of cluttering the list.
    if (!editing && older.isNotEmpty()) {
        TextButton(
            onClick = { olderOpen = !olderOpen },
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
        ) {
            Text(
                if (olderOpen) "收起这本书的其它位置" else "这本书还有 ${older.size} 个位置",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (olderOpen) {
            older.forEach { place ->
                Surface(
                    onClick = { onResumeOlder(place) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            place.track.title.ifEmpty { place.track.number },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatDuration(place.positionSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "从这里续播",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
