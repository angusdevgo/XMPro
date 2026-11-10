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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import teikengstudio.xmpro.model.Track

@Composable
fun AlbumScreen(
    state: UiState,
    bottomInset: Dp,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    onSubscribe: (String, Boolean) -> Unit,
) {
    val page = state.album
    val album = page?.album
    val subscribed = album != null && state.subscriptions.any { it.album.id == album.id }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    album?.title ?: "专辑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album?.let {
                    Text(
                        listOfNotNull(
                            it.artist.takeIf { artist -> artist.isNotEmpty() },
                            "${it.total} 集",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (album != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { onSubscribe(album.id, !subscribed) },
                    label = { Text(if (subscribed) "已订阅" else "订阅") },
                )
                AssistChip(
                    onClick = { page.tracks.firstOrNull()?.let(onPlay) },
                    label = { Text("播放第 1 集") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (album.intro.isNotEmpty()) {
                Text(
                    album.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (state.albumLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.albumError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (page != null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                items(page.tracks, key = { it.id }) { track ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                track.orderNo?.let { number ->
                                    Text(
                                        "$number. ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    track.title.ifEmpty { track.number },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        supportingContent = { Text(formatDuration(track.duration)) },
                        leadingContent = { Cover(track.coverUrl ?: page.album.coverUrl, 44) },
                        trailingContent = {
                            IconButton(onClick = { onPlay(track) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                            }
                        },
                        modifier = Modifier.clickable { onPlay(track) },
                    )
                    HorizontalDivider()
                }
                item {
                    Text(
                        "共 ${page.total} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        } else if (!state.albumLoading && state.albumError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择专辑查看内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
