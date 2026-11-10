package teikengstudio.xmpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import teikengstudio.xmpro.model.Track

@Composable
fun AlbumScreen(
    state: UiState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val page = state.album
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text(page?.album?.title ?: "专辑") },
            supportingContent = {
                val album = page?.album
                if (album != null) {
                    Text(listOfNotNull(album.artist.takeIf { it.isNotEmpty() }, "${album.total} 集").joinToString(" · "))
                }
            },
            leadingContent = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
            ) {
                items(page.tracks, key = { it.id }) { track ->
                    ListItem(
                        headlineContent = { Text(track.title.ifEmpty { track.number }) },
                        supportingContent = {
                            val minutes = (track.duration / 60).toInt()
                            val seconds = (track.duration % 60).toInt()
                            Text("%d:%02d".format(minutes, seconds))
                        },
                        leadingContent = {
                            AsyncImage(
                                model = track.coverUrl ?: page.album.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        },
                        trailingContent = {
                            androidx.compose.foundation.layout.Row {
                                IconButton(onClick = { onDownload(track) }) {
                                    Icon(Icons.Filled.Download, contentDescription = "下载")
                                }
                                IconButton(onClick = { onPlay(track) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                                }
                            }
                        },
                        modifier = Modifier.clickable { onPlay(track) },
                    )
                }
            }
        }
    }
}
