package teikengstudio.xmpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
    onOpenAlbum: (String) -> Unit = {},
    onSkipTrim: (Boolean, Int, Int, String) -> Unit = { _, _, _, _ -> },
) {
    val track = state.playing
    var queueOpen by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    var trimOpen by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(-1f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler {
        when {
            queueOpen -> queueOpen = false
            timerOpen -> timerOpen = false
            trimOpen -> trimOpen = false
            else -> onDismiss()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "正在收听",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { queueOpen = true }) {
                    Icon(Icons.Filled.PlaylistPlay, contentDescription = "播放列表")
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cover = track?.coverUrl
                if (cover != null) {
                    Box(
                        Modifier
                            .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), clip = false),
                    ) {
                        Cover(cover, 260)
                    }
                } else {
                    Box(
                        Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
            }

            Text(
                track?.title ?: "尚未选择声音",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                listOfNotNull(track?.album?.takeIf { it.isNotEmpty() }, track?.artist?.takeIf { it.isNotEmpty() })
                    .joinToString(" · "),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .then(
                        if (track?.albumKey?.isNotEmpty() == true) {
                            Modifier.clickable { onOpenAlbum(track.albumKey) }
                        } else {
                            Modifier
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerActionButton(
                    label = "%.1f×".format(state.speed),
                    active = state.speed != 1f,
                    onClick = onCycleSpeed,
                )
                PlayerActionIcon(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (isFavorite) "已收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = track != null,
                    onClick = onFavorite,
                )
                PlayerActionIcon(
                    icon = Icons.Filled.BookmarkBorder,
                    label = "书签",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = track != null,
                    onClick = onBookmark,
                )
                PlayerActionIcon(
                    icon = Icons.Filled.Timer,
                    label = state.sleepAtMillis?.let { "已定时" } ?: "定时",
                    tint = if (state.sleepAtMillis != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    enabled = true,
                    onClick = { timerOpen = true },
                )
                // Tap toggles the trim, long press opens the sliders for it.
                PlayerActionIcon(
                    icon = if (state.skipTrimEnabled) {
                        Icons.Filled.TaskAlt
                    } else {
                        Icons.Filled.SkipNext
                    },
                    label = "跳过头尾",
                    tint = if (state.skipTrimEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    enabled = track != null,
                    onClick = {
                        val turningOn = !state.skipTrimEnabled
                        onSkipTrim(
                            turningOn,
                            state.introSkipPermille,
                            state.outroSkipPermille,
                            state.skipTrimMode,
                        )
                        // Switching on also opens the sliders, so the trim can
                        // be adjusted straight away instead of hunting for it
                        // in settings.
                        if (turningOn) trimOpen = true
                    },
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
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
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
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
                    modifier = Modifier.size(74.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一集")
                }
                IconButton(onClick = { onSkip(30_000) }) {
                    Icon(Icons.Filled.Forward30, contentDescription = "前进 30 秒")
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { queueOpen = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "待播列表",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.queue.size} 集",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (queueOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false }, sheetState = sheetState) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待播列表 · ${state.queue.size} 集",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onStop) { Text("清空") }
            }
            LazyColumn(Modifier.navigationBarsPadding()) {
                itemsIndexed(state.queue) { index, item ->
                    val active = index == state.queueIndex
                    Surface(
                        onClick = {
                            onSelectQueue(index)
                            queueOpen = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(item.coverUrl, 42)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    item.title.ifEmpty { item.number },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    listOfNotNull(
                                        item.album.takeIf { it.isNotEmpty() },
                                        formatDuration(item.duration),
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (active) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "正在播放",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (trimOpen) {
        ModalBottomSheet(
            onDismissRequest = { trimOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TrimSheet(
                enabled = state.skipTrimEnabled,
                mode = state.skipTrimMode,
                intro = state.introSkipPermille,
                outro = state.outroSkipPermille,
                onChange = { on, head, tail, next -> onSkipTrim(on, head, tail, next) },
            )
        }
    }

    if (timerOpen) {
        AlertDialog(
            onDismissRequest = { timerOpen = false },
            title = { Text("定时关闭") },
            text = { Text("到达时间后停止播放；当前设置会显示在操作栏。") },
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

@Composable
private fun PlayerActionButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlayerActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // A plain Box rather than IconButton: IconButton swallows the long
        // press, so the trim editor could never be reached from here.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Compact preview used by tests/previews. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title.ifEmpty { track.number },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDuration(track.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Head/tail trim editor: automatic silence skipping, or manual percentages. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrimSheet(
    enabled: Boolean,
    mode: String,
    intro: Int,
    outro: Int,
    onChange: (Boolean, Int, Int, String) -> Unit,
) {
    val smart = mode == teikengstudio.xmpro.player.SmartSkip.MODE_SMART
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "跳过头尾",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { onChange(it, intro, outro, mode) },
            )
        }
        Text(
            if (smart) {
                "自动识别开头的空白与结尾的静音并跳过，无需设置。"
            } else {
                "按每集时长比例裁剪，百分比会自动换算成本集的秒数。"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
        ) {
            listOf(
                teikengstudio.xmpro.player.SmartSkip.MODE_SMART to "智能",
                teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL to "手动",
            ).forEach { (value, label) ->
                val active = mode == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                        .clickable { onChange(enabled, intro, outro, value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        if (!smart) {
            TrimSlider(
                title = "片头",
                permille = intro,
                enabled = enabled,
                onChange = { onChange(enabled, it, outro, mode) },
            )
            TrimSlider(
                title = "片尾",
                permille = outro,
                enabled = enabled,
                onChange = { onChange(enabled, intro, it, mode) },
            )
        }
        TextButton(
            onClick = { onChange(false, intro, outro, mode) },
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text("关闭跳过头尾")
        }
    }
}

@Composable
private fun TrimSlider(
    title: String,
    permille: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                if (permille == 0) {
                    "不跳过"
                } else {
                    "%.1f%%".format(permille / 10.0)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = permille.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..teikengstudio.xmpro.data.MAX_SKIP_PERMILLE.toFloat(),
            enabled = enabled,
        )
    }
}
