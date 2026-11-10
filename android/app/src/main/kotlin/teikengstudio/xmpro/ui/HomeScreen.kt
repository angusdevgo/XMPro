package teikengstudio.xmpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.HomeAlbumStats
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
) {
    var query by remember { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val showingSearch = state.query.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("搜索专辑或声音") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            onClearSearch()
                        }) { Icon(Icons.Filled.Close, contentDescription = "清空") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSearch(query, state.searchKind)
                }),
            )
            IconButton(onClick = onRefreshHome) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新首页")
            }
        }
        if (!showingSearch && state.channels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.channels, key = { it.pageId }) { channel ->
                    FilterChip(
                        selected = channel.pageId == state.channelId,
                        onClick = { onSelectChannel(channel.pageId) },
                        label = { Text(channel.title) },
                    )
                }
            }
        }
        if (!showingSearch && state.tabs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tabs, key = { it.tabId }) { tab ->
                    FilterChip(
                        selected = tab.tabId == state.tabId,
                        onClick = { onSelectTab(tab.tabId) },
                        label = { Text(tab.title) },
                    )
                }
            }
        }
        if (showingSearch) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("album" to "专辑", "track" to "声音")) { (kind, label) ->
                    FilterChip(
                        selected = state.searchKind == kind,
                        onClick = { onSearch(state.query, kind) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (state.homeLoading && !showingSearch || state.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val error = if (showingSearch) state.searchError else state.homeError
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset),
        ) {
            if (showingSearch) {
                if (state.searchKind == "album") {
                    items(state.albums, key = { "album-${it.id}" }) { album ->
                        ListItem(
                            headlineContent = { Text(album.title) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        album.artist.takeIf { artist -> artist.isNotEmpty() },
                                        "${album.total} 集",
                                    ).joinToString(" · "),
                                )
                            },
                            leadingContent = { Cover(album.coverUrl, 48) },
                            modifier = Modifier.clickable { onOpenAlbum(album.id) },
                        )
                        HorizontalDivider()
                    }
                } else {
                    items(state.tracks, key = { "track-${it.id}" }) { track ->
                        TrackRow(track = track, onPlay = { onPlay(track) })
                        HorizontalDivider()
                    }
                }
            } else {
                items(state.sections, key = { it.title }) { section ->
                    SectionBlock(section, onOpenAlbum)
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(section: HomeSection, onOpenAlbum: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (section.isRanking) "排行榜 · ${section.title}" else section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (section.isRanking) {
                Text(
                    "TOP ${section.albums.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Phones keep one column so titles stay readable; wider screens
            // show the two/three-up grid the desktop client uses.
            val columns = when {
                maxWidth >= 840.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (row in section.albums.chunked(columns)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        for (album in row) {
                            val index = section.albums.indexOf(album)
                            AlbumCard(
                                album = album,
                                stats = section.stats[album.id],
                                rank = if (section.isRanking) index + 1 else null,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenAlbum(album.id) },
                            )
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
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
    OutlinedCard(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(10.dp)) {
            Box {
                Cover(album.coverUrl, if (rank == null) 76 else 60)
                if (rank != null) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                            .background(if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$rank",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (album.artist.isNotEmpty()) {
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (rank == null && album.intro.isNotEmpty()) {
                    Text(
                        album.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    stats?.score?.let { score ->
                        Text(
                            "%.1f 分".format(score),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (stats?.finished == true) {
                        Text(
                            "  完结",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                val details = buildList {
                    if (album.total > 0) add("${album.total} 集")
                    stats?.playCount?.let { add("${count(it)} 播放") }
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

@Composable
fun Cover(url: String?, size: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun TrackRow(track: Track, onPlay: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title.ifEmpty { track.number }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    track.artist.takeIf { it.isNotEmpty() },
                    formatDuration(track.duration),
                ).joinToString(" · "),
            )
        },
        leadingContent = { Cover(track.coverUrl, 44) },
        trailingContent = {
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
        },
    )
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
