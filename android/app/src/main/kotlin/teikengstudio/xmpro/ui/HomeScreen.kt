package teikengstudio.xmpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import teikengstudio.xmpro.model.Album
import teikengstudio.xmpro.model.HomeSection
import teikengstudio.xmpro.model.Track

@Composable
fun HomeScreen(
    state: UiState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onSearch: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onSelectTab: (Int) -> Unit,
) {
    var query by remember { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val showingSearch = state.query.isNotEmpty() && (state.tracks.isNotEmpty() || state.albums.isNotEmpty() || state.searching)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("搜索专辑或声音") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                onSearch(query)
            }),
        )
        if (state.channels.isNotEmpty() && !showingSearch) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
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
        if (state.tabs.isNotEmpty() && !showingSearch) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
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
        if (state.homeLoading || state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        val error = state.searchError ?: state.homeError
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
        ) {
            if (showingSearch) {
                items(state.albums, key = { "album-${it.id}" }) { album ->
                    ListItem(
                        headlineContent = { Text(album.title) },
                        supportingContent = {
                            Text(listOfNotNull(album.artist.takeIf { a -> a.isNotEmpty() }, "${album.total} 集").joinToString(" · "))
                        },
                        leadingContent = { Cover(album.coverUrl, 48) },
                        modifier = Modifier.clickable { onOpenAlbum(album.id) },
                    )
                    HorizontalDivider()
                }
                items(state.tracks, key = { "track-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        progress = state.downloadProgress[track.number],
                        onPlay = { onPlay(track) },
                        onDownload = { onDownload(track) },
                    )
                    HorizontalDivider()
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            if (section.isRanking) {
                Text(
                    "  榜单",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        for (pair in section.albums.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                for (album in pair) {
                    AlbumCard(
                        album = album,
                        score = section.stats[album.id]?.score,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenAlbum(album.id) },
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, score: Double?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(4.dp)) {
        Cover(album.coverUrl, 64)
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                album.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.artist.ifEmpty { "${album.total} 集" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            score?.let {
                Text(
                    "%.1f 分".format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun Cover(url: String?, size: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
fun TrackRow(track: Track, progress: Float?, onPlay: () -> Unit, onDownload: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title.ifEmpty { track.number }) },
        supportingContent = {
            val minutes = (track.duration / 60).toInt()
            val seconds = (track.duration % 60).toInt()
            Text(listOfNotNull(track.artist.takeIf { it.isNotEmpty() }, "%d:%02d".format(minutes, seconds)).joinToString(" · "))
        },
        leadingContent = { Cover(track.coverUrl, 44) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (progress != null) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = "下载") }
                }
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = "播放") }
            }
        },
    )
}
