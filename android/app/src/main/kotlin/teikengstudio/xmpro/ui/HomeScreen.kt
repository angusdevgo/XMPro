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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.HomeAlbumStats
import teikengstudio.xmpro.model.HomeChannel
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.Track

@Composable
fun HomeScreen(
    state: UiState,
    bottomInset: Dp,
    onSearch: (String, String) -> Unit,
    onClearSearch: () -> Unit,
    onRefreshHome: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onSelectTab: (Int) -> Unit,
    onMoveChannel: (Int, Int) -> Unit = { _, _ -> },
    onHideChannel: (Int) -> Unit = {},
    onShowChannel: (Int) -> Unit = {},
    onResetChannels: () -> Unit = {},
    barsVisible: Boolean = true,
    onBarsHidden: (Boolean) -> Unit = {},
) {
    var editingChannels by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    ScrollHideEffect(listState, onBarsHidden)
    var query by remember { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val showingSearch = state.query.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = barsVisible || showingSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CapsuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索专辑或声音",
                        onClear = {
                            query = ""
                            onClearSearch()
                        },
                        onSearch = {
                            keyboard?.hide()
                            onSearch(query, state.searchKind)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onRefreshHome,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "刷新首页",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (!showingSearch && state.channels.isNotEmpty()) {
                    // Few pills centre themselves; extra pills keep centring and
                    // the row simply scrolls, so the header stays symmetric.
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        items(state.channels, key = { it.pageId }) { channel ->
                            PillChip(
                                selected = channel.pageId == state.channelId,
                                label = channel.title,
                                onClick = { onSelectChannel(channel.pageId) },
                            )
                        }
                        item(key = "edit-channels") {
                            Surface(
                                onClick = { editingChannels = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = "编辑频道",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (!showingSearch && state.tabs.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.tabs, key = { it.tabId }) { tab ->
                            PillChip(
                                selected = tab.tabId == state.tabId,
                                label = tab.title,
                                compact = true,
                                onClick = { onSelectTab(tab.tabId) },
                            )
                        }
                    }
                }
                if (showingSearch) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(listOf("album" to "专辑", "track" to "声音")) { (kind, label) ->
                            PillChip(
                                selected = state.searchKind == kind,
                                label = label,
                                onClick = { onSearch(state.query, kind) },
                            )
                        }
                    }
                }
            }
        }
        if (state.homeLoading && !showingSearch || state.searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .clip(CircleShape),
            )
        }
        val error = if (showingSearch) state.searchError else state.homeError
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
        ) {
            if (showingSearch) {
                if (state.searchKind == "album") {
                    items(state.albums, key = { "album-${it.id}" }) { album ->
                        AlbumCard(
                            album = album,
                            stats = null,
                            rank = null,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                } else {
                    items(state.tracks, key = { "track-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            onPlay = { onPlay(track) },
                        )
                    }
                }
            } else {
                items(state.sections, key = { it.title }) { section ->
                    SectionBlock(section, onOpenAlbum)
                }
            }
        }
    }

    if (editingChannels) {
        ChannelEditorSheet(
            visible = state.channels,
            hidden = state.hiddenChannels,
            onMove = onMoveChannel,
            onHide = onHideChannel,
            onShow = onShowChannel,
            onReset = onResetChannels,
            onDismiss = { editingChannels = false },
        )
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

@Composable
fun CapsuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.height(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch?.invoke() },
                        onDone = { onSearch?.invoke() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty() && onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PillChip(
    selected: Boolean,
    label: String,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
        )
    }
}

/**
 * Lets the user reorder and remove header channels. Hidden channels stay in the
 * list so they can be restored without leaving the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChannelEditorSheet(
    visible: List<HomeChannel>,
    hidden: List<HomeChannel>,
    onMove: (Int, Int) -> Unit,
    onHide: (Int) -> Unit,
    onShow: (Int) -> Unit,
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
                    "编辑频道",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "用箭头调整顺序，点叉号从顶部移除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset) { Text("恢复默认") }
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            itemsIndexed(visible) { index, channel ->
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
                        channel.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(
                        onClick = { onMove(channel.pageId, -1) },
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
                        onClick = { onMove(channel.pageId, 1) },
                        enabled = index < visible.lastIndex,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "后移",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onHide(channel.pageId) },
                        enabled = visible.size > 1,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "隐藏",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item {
                    Text(
                        "已隐藏",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(hidden, key = { "hidden-${it.pageId}" }) { channel ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            channel.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        TextButton(onClick = { onShow(channel.pageId) }) { Text("恢复") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(section: HomeSection, onOpenAlbum: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (section.isRanking) "排行榜 · ${section.title}" else section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (section.isRanking) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        "TOP ${section.albums.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 840.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (row in section.albums.chunked(columns)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (album in row) {
                            val index = section.albums.indexOf(album)
                            AlbumCard(
                                album = album,
                                stats = section.stats[album.id],
                                rank = if (section.isRanking) index + 1 else null,
                                modifier = Modifier.weight(1f).padding(4.dp),
                                onClick = { onOpenAlbum(album.id) },
                            )
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f).padding(4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    stats: HomeAlbumStats?,
    rank: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val coverSize = if (rank == null) 84 else 68
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                Cover(album.coverUrl, coverSize)
                if (rank != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp))
                            .background(
                                if (rank <= 3) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$rank",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (rank <= 3) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                stats?.playCount?.let { plays ->
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "▶ ${count(plays)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (album.artist.isNotEmpty()) {
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (rank == null && album.intro.isNotEmpty()) {
                    Text(
                        album.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stats?.score?.let { score ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        ) {
                            Text(
                                "%.1f 分".format(score),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (stats?.finished == true) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                        ) {
                            Text(
                                "完结",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    val details = buildList {
                        if (album.total > 0) add("${album.total} 集")
                        if (rank != null) stats?.playCount?.let { add("${count(it)} 播放") }
                        stats?.subscribeCount?.let { add("${count(it)} 订阅") }
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Cover(url: String?, size: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
fun TrackRow(track: Track, onPlay: () -> Unit) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(track.coverUrl, 48)
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
                        track.artist.takeIf { it.isNotEmpty() },
                        formatDuration(track.duration),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
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

fun formatDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0) return "--:--"
    val total = seconds.toInt()
    val hours = total / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, (total % 3600) / 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
}

fun count(value: Int): String = when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

/** Unused import guard for colours referenced by previews. */
private val transparent = Color.Transparent
