package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun DownloadsScreen(
    state: UiState,
    onPlayFile: (File) -> Unit,
    onDelete: (File) -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("已下载 ${state.downloads.size} 个", modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
        }
        if (state.downloads.isEmpty()) {
            Text(
                "还没有下载内容。搜索专辑后点下载按钮即可保存到本机。",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.downloads, key = { it.absolutePath }) { file ->
                androidx.compose.material3.ListItem(
                    headlineContent = { Text(file.nameWithoutExtension) },
                    supportingContent = { Text("${file.length() / 1024 / 1024} MB") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onPlayFile(file) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                            }
                            IconButton(onClick = { onDelete(file) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    },
                )
            }
        }
    }
}
