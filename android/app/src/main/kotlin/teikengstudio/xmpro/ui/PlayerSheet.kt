package teikengstudio.xmpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import teikengstudio.xmpro.model.Track

/**
 * Full-screen "now playing" page: artwork, transport controls, speed, sleep
 * timer and the play queue.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onFavorite: () -> Unit,
    onBookmark: () -> Unit,
    isFavorite: Boolean,
    onSleepTimer: (Int?) -> Unit,
    onSelectQueue: (Int) -> Unit,
) {
    val track = state.playing
    var queueOpen by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(-1f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Scrollable so every control stays reachable on short screens.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起")
                }
                Text(
                    "正在收听",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { queueOpen = true }) {
                    Icon(Icons.Filled.PlaylistPlay, contentDescription = "播放列表")
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cover = track?.coverUrl
                if (cover != null) {
                    Cover(cover, 232)
                } else {
                    Box(
                        Modifier
                            .size(232.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                }
            }
            Text(
                track?.title ?: "尚未选择声音",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                listOfNotNull(track?.album?.takeIf { it.isNotEmpty() }, track?.artist?.takeIf { it.isNotEmpty() })
                    .joinToString(" · "),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCycleSpeed) {
                    Text("%.2f×".format(state.speed).replace(".00", ".0").replace(".50", ".5"))
                }
                IconButton(onClick = onFavorite, enabled = track != null) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onBookmark, enabled = track != null) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = "保存书签")
                }
            }

            val total = state.durationMs.coerceAtLeast(0)
            val shown = if (dragPosition >= 0f) dragPosition.toLong() else state.positionMs
            Slider(
                value = if (total > 0) (shown.toFloat() / total).coerceIn(0f, 1f) else 0f,
                onValueChange = { value ->
                    if (total > 0) dragPosition = value * total
                },
                onValueChangeFinished = {
                    if (dragPosition >= 0f) {
                        onSeek(dragPosition.toLong())
                        dragPosition = -1f
                    }
                },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(shown / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(total / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSkip(-10_000) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "后退 10 秒")
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集")
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一集")
                }
                IconButton(onClick = { onSkip(30_000) }) {
                    Icon(Icons.Filled.Forward30, contentDescription = "前进 30 秒")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { timerOpen = true }) {
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  定时关闭", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { queueOpen = true }) {
                    Icon(Icons.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  播放列表", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (queueOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false }, sheetState = sheetState) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "待播列表 · ${state.queue.size} 集",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onStop) { Text("清空") }
            }
            LazyColumn(Modifier.navigationBarsPadding()) {
                itemsIndexed(state.queue) { index, item ->
                    ListItem(
                        headlineContent = {
                            Text(
                                item.title.ifEmpty { item.number },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == state.queueIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    item.album.takeIf { it.isNotEmpty() },
                                    formatDuration(item.duration),
                                ).joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = if (index == state.queueIndex) {
                            { Text("正在播放", style = MaterialTheme.typography.labelSmall) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable {
                            onSelectQueue(index)
                            queueOpen = false
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (timerOpen) {
        AlertDialog(
            onDismissRequest = { timerOpen = false },
            title = { Text("定时关闭") },
            text = { Text("到达时间后停止播放；当前设置会显示在下方。") },
            confirmButton = {
                TextButton(onClick = {
                    onSleepTimer(30)
                    timerOpen = false
                }) { Text("30 分钟") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onSleepTimer(null)
                        timerOpen = false
                    }) { Text("关闭定时") }
                    TextButton(onClick = {
                        onSleepTimer(60)
                        timerOpen = false
                    }) { Text("60 分钟") }
                }
            },
        )
    }
}

/** Compact preview used by tests/previews. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueRow(track: Track, active: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title.ifEmpty { track.number }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(formatDuration(track.duration)) },
        modifier = Modifier.clickable(onClick = onClick).padding(0.dp),
        trailingContent = if (active) {
            { Icon(Icons.Filled.PlayArrow, contentDescription = "正在播放") }
        } else {
            null
        },
    )
}
