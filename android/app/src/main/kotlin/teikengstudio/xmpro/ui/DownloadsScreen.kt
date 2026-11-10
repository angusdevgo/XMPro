package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import teikengstudio.xmpro.download.DownloadState
import teikengstudio.xmpro.download.DownloadTask

@Composable
fun DownloadsScreen(
    state: UiState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPlayFile: (File) -> Unit,
    onDelete: (File) -> Unit,
    onRefresh: () -> Unit,
    onPauseTask: (DownloadTask) -> Unit,
    onResumeTask: (DownloadTask) -> Unit,
    onCancelTask: (DownloadTask) -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    val active = state.downloadTasks.filter { it.state != DownloadState.completed }
    val completed = state.downloadTasks.filter { it.state == DownloadState.completed }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "下载任务 ${active.size} · 已完成 ${completed.size}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
        }
        if (state.downloadTasks.isEmpty()) {
            Text(
                "还没有下载内容。在专辑里点下载按钮即可保存到本机。",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
        ) {
            items(active, key = { it.id }) { task ->
                Column {
                    ListItem(
                        headlineContent = { Text(task.title.ifEmpty { task.number }) },
                        supportingContent = {
                            Text(
                                when (task.state) {
                                    DownloadState.downloading ->
                                        if (task.totalBytes > 0) "${(task.progress * 100).toInt()}% · ${task.receivedBytes / 1024} KB"
                                        else "下载中…"

                                    DownloadState.queued -> "等待中"
                                    DownloadState.paused -> "已暂停"
                                    DownloadState.failed -> task.error ?: "下载失败"
                                    DownloadState.completed -> "已完成"
                                },
                            )
                        },
                        trailingContent = {
                            Row {
                                if (task.state == DownloadState.downloading || task.state == DownloadState.queued) {
                                    IconButton(onClick = { onPauseTask(task) }) {
                                        Icon(Icons.Filled.Pause, contentDescription = "暂停")
                                    }
                                } else if (task.state != DownloadState.completed) {
                                    IconButton(onClick = { onResumeTask(task) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "继续")
                                    }
                                }
                                IconButton(onClick = { onCancelTask(task) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "取消")
                                }
                            }
                        },
                    )
                    if (task.state == DownloadState.downloading) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(completed, key = { "file-${it.id}" }) { task ->
                ListItem(
                    headlineContent = { Text(task.title.ifEmpty { task.number }) },
                    supportingContent = { Text("已下载到本机") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onPlayFile(File(task.path)) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                            }
                            IconButton(onClick = { onCancelTask(task) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    },
                )
            }
            items(state.downloads, key = { "ext-${it.absolutePath}" }) { file ->
                ListItem(
                    headlineContent = { Text(file.nameWithoutExtension) },
                    supportingContent = { Text("${file.length() / 1024 / 1024} MB") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onPlayFile(file) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                            }
                            TextButton(onClick = { onDelete(file) }) { Text("删除") }
                        }
                    },
                )
            }
        }
    }
}
