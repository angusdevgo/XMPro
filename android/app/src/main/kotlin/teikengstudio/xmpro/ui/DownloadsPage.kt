package teikengstudio.xmpro.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import teikengstudio.xmpro.download.DownloadState
import teikengstudio.xmpro.download.DownloadTask
import teikengstudio.xmpro.model.Track

/**
 * Offline library: what is downloading, what is paused, what can be played with
 * no network. Kept in "我的" rather than the tab strip so the main surfaces stay
 * about listening rather than storage.
 */
@Composable
fun DownloadsPage(
    tasks: List<DownloadTask>,
    bottomInset: Dp,
    onPlay: (Track) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val done = tasks.filter { it.state == DownloadState.DONE }
    val used = done.sumOf { it.total.coerceAtLeast(it.received) }
    val working = tasks.count { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }
    // Batch progress is measured in episodes, not bytes: a queue of a thousand
    // episodes is easier to read as "37/1314" than as a byte ratio.
    val settled = tasks.count { it.state == DownloadState.DONE || it.state == DownloadState.FAILED }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "已下载 ${done.size} 集 · ${formatBytes(used)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (working > 0) {
                    Text(
                        if (tasks.size == working) {
                            "队列中 $working 集"
                        } else {
                            "队列进度 $settled/${tasks.size} 集"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (tasks.isNotEmpty()) {
                TextButton(onClick = { confirming = true }) { Text("清空") }
            }
        }
        if (working > 0 && tasks.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { settled.toFloat() / tasks.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .height(3.dp)
                    .clip(CircleShape),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
        ) {
            items(tasks, key = { it.id }) { task ->
                DownloadRow(
                    task = task,
                    onPlay = { onPlay(task.track) },
                    onPause = { onPause(task.id) },
                    onResume = { onResume(task.id) },
                    onRemove = { onRemove(task.id) },
                )
            }
            if (tasks.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有下载。专辑页和剧集右侧都有下载入口。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("清空下载") },
            text = { Text("将删除 ${tasks.size} 个任务的离线文件，包括未完成的下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onClearAll()
                    },
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = { if (task.state == DownloadState.DONE) onPlay() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(task.track.coverUrl, 52)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    task.track.title.ifEmpty { task.track.number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        task.track.album.takeIf { it.isNotEmpty() },
                        when (task.state) {
                            DownloadState.DONE -> formatBytes(task.total.coerceAtLeast(task.received))
                            DownloadState.RUNNING -> progressLabel(task)
                            DownloadState.QUEUED -> "等待下载"
                            DownloadState.PAUSED -> "已暂停"
                            DownloadState.FAILED -> task.error ?: "下载失败"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.state == DownloadState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (task.state == DownloadState.RUNNING || task.state == DownloadState.PAUSED) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                    )
                }
            }
            when (task.state) {
                DownloadState.DONE -> IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放离线音频",
                        modifier = Modifier.size(20.dp),
                    )
                }

                DownloadState.RUNNING -> IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = "暂停下载",
                        modifier = Modifier.size(20.dp),
                    )
                }

                else -> IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = if (task.state == DownloadState.FAILED) "重试" else "继续下载",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Small "已下载" chip shown next to an episode that plays from disk. */
@Composable
fun DownloadBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Icon(
                Icons.Filled.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "已下载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun progressLabel(task: DownloadTask): String {
    val received = formatBytes(task.received)
    return if (task.total > 0) {
        "$received / ${formatBytes(task.total)}"
    } else {
        received
    }
}

/** Rounds to KB / MB / GB; download rows never need more precision than that. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
