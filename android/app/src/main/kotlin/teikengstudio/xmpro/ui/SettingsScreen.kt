package teikengstudio.xmpro.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import teikengstudio.xmpro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import teikengstudio.xmpro.data.AccountSummary
import teikengstudio.xmpro.ui.theme.SeedPresets

private enum class SettingsPage { Root, Account, Playback, Appearance, Backup, About }

/**
 * Start-up update offer: download in place, then hand over to the installer.
 *
 * The same download path as the about page is used, so the SHA-256 check and the
 * progress reporting stay in one place.
 */
@Composable
fun UpdatePromptDialog(
    tag: String,
    progress: Int,
    ready: Boolean,
    notes: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = progress in 0..99 && !ready
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本 $tag") },
        text = {
            Column {
                Text(
                    when {
                        ready -> "安装包已下载并通过校验。"
                        downloading -> "正在下载安装包…"
                        else -> notes?.takeIf { it.isNotBlank() } ?: "可以下载并安装这个版本。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { (progress.coerceAtLeast(0) / 100f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text(
                        if (progress < 0) "下载中…" else "$progress%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            when {
                ready -> TextButton(onClick = onInstall) { Text("安装") }
                downloading -> TextButton(onClick = {}, enabled = false) { Text("下载中") }
                else -> TextButton(onClick = onDownload) { Text("立即更新") }
            }
        },
        dismissButton = {
            if (!ready) {
                TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后") }
            }
        },
    )
}

/**
 * "我的" tab: the personal summary on top, then one page per topic. Every page
 * keeps the same rounded card grouping so the whole tab reads as one surface.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    bottomInset: Dp,
    themeMode: String,
    oledBlack: Boolean,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onThemeMode: (String) -> Unit,
    onOledBlack: (Boolean) -> Unit,
    onSetPrimary: (String) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onQueryQuota: (String) -> Unit,
    onClaimOnce: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    onResumeRewards: (String) -> Unit,
    onSyncProfile: () -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onQuality: (String) -> Unit,
    onEffect: (String) -> Unit = {},
    onEffectStrength: (Int) -> Unit = {},
    onSkipTrim: (Boolean, Int, Int, String) -> Unit,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onMirror: (String) -> Unit = {},
    onAutoCheckUpdate: (Boolean) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onDynamicColor: (Boolean) -> Unit,
    onSeedColor: (Int) -> Unit,
    onOpenSocial: (String) -> Unit,
    onCloseSocial: () -> Unit,
    onOpenUser: (String) -> Unit,
    onExportBackup: (android.net.Uri, Boolean) -> Unit,
    onImportBackup: (android.net.Uri) -> Unit,
    smsLogin: SmsLoginCallbacks,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    var showCookie by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    val current = state.accounts.firstOrNull { it.active }
        ?: state.accounts.firstOrNull { it.primary }
        ?: state.accounts.firstOrNull()

    BackHandler(enabled = page != SettingsPage.Root) { page = SettingsPage.Root }

    Column(Modifier.fillMaxSize()) {
        when (page) {
            SettingsPage.Root -> SettingsRoot(
                state = state,
                current = current,
                themeMode = themeMode,
                bottomInset = bottomInset,
                onNavigate = { page = it },
                onQueryQuota = onQueryQuota,
                onClaimOnce = onClaimOnce,
                onAutoClaim = onAutoClaim,
                onSwitchAccount = onSwitchAccount,
                onCopyCookie = onCopyCookie,
                onOpenSocial = onOpenSocial,
            )

            SettingsPage.Account -> {
                PageHeader("账号与听时", onBack = { page = SettingsPage.Root })
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    AccountContent(
                        state = state,
                        current = current,
                        onSignOut = onSignOut,
                        onSetPrimary = onSetPrimary,
                        onSwitchAccount = onSwitchAccount,
                        onCopyCookie = onCopyCookie,
                        onRemoveAccount = onRemoveAccount,
                        onQueryQuota = onQueryQuota,
                        onResumeRewards = onResumeRewards,
                        onAutoClaim = onAutoClaim,
                        onSyncProfile = onSyncProfile,
                        onRename = { renameTarget = it },
                        onAddCookie = { showCookie = true },
                        onAddSms = { showSms = true },
                    )
                    Spacer(Modifier.height(bottomInset))
                }
            }

            SettingsPage.Playback -> {
                PageHeader("播放设置", onBack = { page = SettingsPage.Root })
                val smart = state.skipTrimMode == teikengstudio.xmpro.player.SmartSkip.MODE_SMART
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    SettingsGroup {
                        GroupTitle("音质")
                        ChoiceRow(
                            options = listOf(
                                "auto" to "自动",
                                "m4a_128" to "高清",
                                "m4a_64" to "标准",
                                "mp3_64" to "省流",
                                "mp3_32" to "流畅",
                            ),
                            selected = state.quality,
                            onSelect = onQuality,
                        )
                        Text(
                            "高清优先使用 AAC 128k；会员/听时权限不足时回落到可用码率。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    SettingsGroup {
                        GroupTitle("音效")
                        ChoiceRow(
                            options = teikengstudio.xmpro.player.EffectPreset.entries
                                .map { it.id to it.label },
                            selected = state.effectPreset,
                            onSelect = onEffect,
                        )
                        if (state.effectPreset != teikengstudio.xmpro.player.EffectPreset.Off.id) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("强度", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = state.effectStrength / 100f,
                                    onValueChange = { onEffectStrength((it * 100).roundToInt()) },
                                    steps = 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                )
                                Text(
                                    "${state.effectStrength}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "音效由系统音频效果实现，部分机型不支持某一档时会自动跳过。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    SettingsGroup {
                        SettingSwitchRow(
                            title = "跳过头尾",
                            subtitle = if (smart) {
                                "自动跳过开头的空白与结尾的静音"
                            } else {
                                "按比例裁掉开头与结尾"
                            },
                            checked = state.skipTrimEnabled,
                            onCheckedChange = {
                                onSkipTrim(it, state.introSkipPermille, state.outroSkipPermille, state.skipTrimMode)
                            },
                        )
                        if (state.skipTrimEnabled) {
                            GroupDivider()
                            ChoiceRow(
                                options = listOf(
                                    teikengstudio.xmpro.player.SmartSkip.MODE_SMART to "智能",
                                    teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL to "手动",
                                ),
                                selected = state.skipTrimMode,
                                onSelect = {
                                    onSkipTrim(true, state.introSkipPermille, state.outroSkipPermille, it)
                                },
                            )
                        }
                    }
                    if (state.skipTrimEnabled && !smart) {
                        SettingsGroup {
                            SkipSlider(
                                title = "片头",
                                permille = state.introSkipPermille,
                                onValueChange = {
                                    onSkipTrim(true, it, state.outroSkipPermille, state.skipTrimMode)
                                },
                            )
                            GroupDivider()
                            SkipSlider(
                                title = "片尾",
                                permille = state.outroSkipPermille,
                                onValueChange = {
                                    onSkipTrim(true, state.introSkipPermille, it, state.skipTrimMode)
                                },
                            )
                            Text(
                                "百分比按每集实际时长换算；片尾到点后自动切下一集。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomInset))
                }
            }

            SettingsPage.Appearance -> {
                PageHeader("外观", onBack = { page = SettingsPage.Root })
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    SettingsGroup {
                        GroupTitle("主题模式")
                        ChoiceRow(
                            options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                            selected = themeMode,
                            onSelect = onThemeMode,
                        )
                    }
                    SettingsGroup {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "使用系统壁纸配色（Android 12 及以上）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColor)
                        }
                    }
                    SettingsGroup {
                        GroupTitle("预设主题")
                        SeedSwatches(
                            selected = state.seedColor,
                            enabled = !state.dynamicColor,
                            onSelect = onSeedColor,
                        )
                    }
                    SettingsGroup {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("OLED 纯黑", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "暗色模式下使用纯黑背景",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Switch(checked = oledBlack, onCheckedChange = onOledBlack)
                        }
                    }
                    Spacer(Modifier.height(bottomInset))
                }
            }

            SettingsPage.Backup -> {
                PageHeader("备份与恢复", onBack = { page = SettingsPage.Root })
                BackupPage(
                    bottomInset = bottomInset,
                    onExport = onExportBackup,
                    onImport = onImportBackup,
                )
            }

            SettingsPage.About -> {
                PageHeader("关于", onBack = { page = SettingsPage.Root })
                AboutPage(
                    state = state,
                    bottomInset = bottomInset,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onMirror = onMirror,
                    onAutoCheckUpdate = onAutoCheckUpdate,
                    onOpenLink = onOpenLink,
                    onDismissResult = onDismissUpdate,
                )
            }
        }
    }

    state.socialKind?.let { kind ->
        SocialSheet(kind = kind, state = state, onDismiss = onCloseSocial, onOpenUser = onOpenUser)
    }

    renameTarget?.let { identity ->
        RenameDialog(
            initial = state.accounts.firstOrNull { it.identity == identity }?.nickname ?: "",
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenameAccount(identity, it)
                renameTarget = null
            },
        )
    }
    if (showCookie) {
        CookieDialog(
            onDismiss = { showCookie = false },
            onConfirm = {
                onSignIn(it)
                showCookie = false
                page = SettingsPage.Account
            },
        )
    }
    if (showSms) {
        SmsLoginDialog(
            callbacks = smsLogin,
            onDismiss = { showSms = false },
            onSignedIn = {
                showSms = false
                page = SettingsPage.Account
            },
        )
    }
}

@Composable
private fun SettingsRoot(
    state: UiState,
    current: AccountSummary?,
    themeMode: String,
    bottomInset: Dp,
    onNavigate: (SettingsPage) -> Unit,
    onQueryQuota: (String) -> Unit,
    onClaimOnce: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
    onOpenSocial: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 10.dp),
        )
        SettingsGroup {
            ProfileHeader(
                state = state,
                account = current,
                onQueryQuota = onQueryQuota,
                onClaimOnce = onClaimOnce,
                onAutoClaim = onAutoClaim,
                onSwitchAccount = onSwitchAccount,
                onCopyCookie = onCopyCookie,
                onOpenSocial = onOpenSocial,
            )
        }
        SettingsGroup {
            SettingsRow(
                title = "账号与听时",
                subtitle = if (current == null) "未登录" else "多账号、听时领取与登录方式",
                icon = Icons.Filled.Person,
                onClick = { onNavigate(SettingsPage.Account) },
            )
            GroupDivider()
            SettingsRow(
                title = "播放设置",
                subtitle = "音质 · 跳过头尾",
                icon = Icons.Filled.PlayCircleOutline,
                onClick = { onNavigate(SettingsPage.Playback) },
            )
            GroupDivider()
            SettingsRow(
                title = "外观",
                subtitle = themeLabel(themeMode),
                icon = Icons.Filled.Palette,
                onClick = { onNavigate(SettingsPage.Appearance) },
            )
            GroupDivider()
            SettingsRow(
                title = "备份与恢复",
                subtitle = "导出订阅、书签与登录信息",
                icon = Icons.Filled.SaveAlt,
                onClick = { onNavigate(SettingsPage.Backup) },
            )
            GroupDivider()
            SettingsRow(
                title = "关于",
                subtitle = "版本信息与许可",
                icon = Icons.Filled.Info,
                onClick = { onNavigate(SettingsPage.About) },
            )
        }
        Spacer(Modifier.height(bottomInset))
    }
}

@Composable
private fun ProfileHeader(
    state: UiState,
    account: AccountSummary?,
    onQueryQuota: (String) -> Unit,
    onClaimOnce: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
    onOpenSocial: (String) -> Unit,
) {
    var switchOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = account?.avatarUrl
                if (avatar != null) {
                    coil.compose.AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (account != null) {
                    Text(
                        (account.nickname.take(1).ifEmpty { account.uid.take(1) }).ifEmpty { "我" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    account?.nickname?.ifEmpty { "喜马拉雅账号" } ?: "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (account == null) {
                        "登录后同步订阅、听更新与云历史"
                    } else {
                        listOfNotNull(
                            account.mobile.takeIf { it.isNotEmpty() },
                            "UID ${account.uid}",
                            if (account.primary) "主账号" else null,
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (account != null && state.accounts.size > 1) {
                FilledTonalButton(
                    onClick = { switchOpen = true },
                    enabled = !state.accountBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "切换",
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 12.dp),
        ) {
            Stat(
                account?.following?.toString() ?: "—",
                "关注",
                Modifier.weight(1f).clickable { onOpenSocial("following") },
            )
            Stat(
                account?.followers?.toString() ?: "—",
                "粉丝",
                Modifier.weight(1f).clickable { onOpenSocial("fans") },
            )
            Stat(
                account?.subscriptionCount?.toString() ?: "${state.subscriptions.size}",
                "订阅",
                Modifier.weight(1f).clickable { onOpenSocial("subscriptions") },
            )
            Stat(
                "${state.favorites.size}",
                "收藏",
                Modifier.weight(1f).clickable { onOpenSocial("favorites") },
            )
        }

        if (account != null) {
            val balance = account.balance
            Text(
                listOfNotNull(
                    balance?.let { "本机可用听时 " + formatListeningTime(it.remainingSeconds) },
                    balance?.let {
                        "核验于 " + SimpleDateFormat("M-d HH:mm", Locale.CHINA)
                            .format(Date(it.checkedAt.toEpochMilli()))
                    } ?: if (account.quotaRejected) "听时核验被拒绝" else "尚未核验听时",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Neutral like its neighbours: the balance check is not the
                // primary action on this card.
                FilledTonalButton(
                    onClick = { onQueryQuota(account.identity) },
                    enabled = !state.accountBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("查询时长", maxLines = 1, softWrap = false) }
                FilledTonalButton(
                    onClick = { onClaimOnce(account.identity) },
                    enabled = !state.accountBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("单次领取", maxLines = 1, softWrap = false) }
                // Mirrors the switch further down, shown here because this is
                // where the balance it affects lives.
                FilledTonalButton(
                    onClick = { onAutoClaim(!state.autoClaim) },
                    enabled = !state.accountBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.autoClaim) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.autoClaim) "自动领取·开" else "自动领取·关",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }

    if (switchOpen) {
        AccountPicker(
            accounts = state.accounts,
            current = account,
            onPick = { identity ->
                switchOpen = false
                onSwitchAccount(identity)
            },
            onDismiss = { switchOpen = false },
        )
    }
}

/** Picks which account is in use; welfare actions only apply after switching. */
@Composable
private fun AccountPicker(
    accounts: List<AccountSummary>,
    current: AccountSummary?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换账号") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                for (row in accounts) {
                    val active = row.identity == current?.identity
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !active) { onPick(row.identity) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.nickname.ifEmpty { "账号 ${row.uid.ifEmpty { row.identity.take(8) }}" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "UID ${row.uid.ifEmpty { "—" }} · " +
                                    (row.balance?.let { formatListeningTime(it.remainingSeconds) } ?: "听时未核验"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (active) {
                            Text(
                                "使用中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun BackupPage(
    bottomInset: Dp,
    onExport: (android.net.Uri, Boolean) -> Unit,
    onImport: (android.net.Uri) -> Unit,
) {
    var includeCredentials by remember { mutableStateOf(true) }
    var pendingExport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExport(uri, includeCredentials)
        pendingExport = false
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SettingsGroup {
            Column(Modifier.padding(16.dp)) {
                Text("导出备份", style = MaterialTheme.typography.titleMedium)
                Text(
                    "订阅、云历史、书签与外观设置打包为一个 JSON 文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("包含登录信息", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "备份文件将含账号令牌，请勿分享给他人",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Switch(
                        checked = includeCredentials,
                        onCheckedChange = { includeCredentials = it },
                    )
                }
                Button(
                    onClick = {
                        pendingExport = true
                        exportLauncher.launch("xmpro-backup.json")
                    },
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("选择保存位置") }
            }
        }
        SettingsGroup {
            Column(Modifier.padding(16.dp)) {
                Text("从备份恢复", style = MaterialTheme.typography.titleMedium)
                Text(
                    "会覆盖当前的订阅、书签与设置；若备份含登录信息，账号也会一并恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("选择备份文件") }
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SeedSwatches(
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((_, color) in SeedPresets) {
            val argb = color.toArgb()
            val active = argb == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (active) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .alpha(if (enabled) 1f else 0.4f)
                    .clickable(enabled = enabled) { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            // Deliberately not themed: a QR code is only scannable as black on white.
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { if (!active) onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
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
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, end = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccountContent(
    state: UiState,
    current: AccountSummary?,
    onSignOut: () -> Unit,
    onSetPrimary: (String) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onQueryQuota: (String) -> Unit,
    onResumeRewards: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    onSyncProfile: () -> Unit,
    onRename: (String) -> Unit,
    onAddCookie: () -> Unit,
    onAddSms: () -> Unit,
) {
    if (current == null) {
        LoginEntry(onAddCookie = onAddCookie, onAddSms = onAddSms)
        return
    }
    if (state.accounts.size > 1) {
        SettingsSectionLabel("其他账号 · 点击即可切换")
        for (account in state.accounts.filter { it.identity != current.identity }) {
            SettingsGroup {
                AccountCard(
                    account = account,
                    busy = state.accountBusy,
                    onSetPrimary = onSetPrimary,
                    onSwitch = onSwitchAccount,
                    onRemove = onRemoveAccount,
                    onCopyCookie = onCopyCookie,
                )
            }
        }
    }
    SettingsSectionLabel("账号管理")
    SettingsGroup {
        ActionRow(
            title = "修改账号名",
            subtitle = "同步失败时可自定义显示名称",
            onClick = { onRename(current.identity) },
        )
        GroupDivider()
        ActionRow(
            title = "同步账号资料",
            subtitle = "从喜马拉雅拉取昵称、头像与关注粉丝数",
            onClick = onSyncProfile,
        )
        GroupDivider()
        ActionRow(
            title = "复制登录信息",
            subtitle = "导出本账号 Cookie，可粘贴到其他设备的 Cookie 导入",
            onClick = { onCopyCookie(current.identity) },
        )
        GroupDivider()
        ActionRow(
            title = "添加 Cookie 账号",
            subtitle = "粘贴极速版 Cookie 或登录信息 JSON",
            onClick = onAddCookie,
        )
        GroupDivider()
        ActionRow(
            title = "短信登录新账号",
            subtitle = "手机号 + 安全验证",
            onClick = onAddSms,
        )
        GroupDivider()
        ActionRow(
            title = "退出当前账号",
            subtitle = "仅清除本机登录信息，听时记录保留",
            danger = true,
            onClick = onSignOut,
        )
    }
    SettingsSectionLabel("听时")
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动领取听时", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "后台自动领取可用的听时，无需手动操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.autoClaim,
                enabled = !state.accountBusy,
                onCheckedChange = onAutoClaim,
            )
        }
        if (current.paused) {
            GroupDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "该账号的自动领取已暂停",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "请先在官方 App 核对上一次领取结果，再恢复自动领取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TextButton(
                    onClick = { onResumeRewards(current.identity) },
                    enabled = !state.accountBusy,
                ) { Text("恢复") }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun LoginEntry(onAddCookie: () -> Unit, onAddSms: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    SettingsGroup {
        Column(Modifier.padding(20.dp)) {
            Text("还没有登录账号", style = MaterialTheme.typography.titleMedium)
            Text(
                "登录后可同步订阅、听更新与云历史，并查看本机可用听时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddCookie,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text("Cookie 登录") }
                FilledTonalButton(
                    onClick = onAddSms,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text("短信登录") }
            }
            Text(
                "主账号用于默认请求",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    account: AccountSummary,
    busy: Boolean,
    onSetPrimary: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onSwitch(account.identity) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                account.nickname.ifEmpty { "账号 ${account.uid.ifEmpty { account.identity.take(8) }}" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (account.primary) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "主账号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "切换到此账号",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
        Text(
            "设备 ${account.deviceHint} · " +
                (account.balance?.let { formatListeningTime(it.remainingSeconds) } ?: "听时未核验"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (account.paused) {
            Text(
                "领取已暂停，请先在官方 App 核对上一次结果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Wrapping row: labels stay on one line each and move down as a whole
            // instead of being squeezed into two-character columns.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!account.primary) {
                    TextButton(onClick = { onSetPrimary(account.identity) }, enabled = !busy) {
                        Text("设为主账号", maxLines = 1, softWrap = false)
                    }
                }
                // Welfare actions belong to the account in use, so an inactive
                // account only offers switching.
                TextButton(onClick = { onSwitch(account.identity) }, enabled = !busy) {
                    Text("切换到该账号", maxLines = 1, softWrap = false)
                }
                TextButton(onClick = { onCopyCookie(account.identity) }, enabled = !busy) {
                    Text("复制登录信息", maxLines = 1, softWrap = false)
                }
                // Destructive action is an icon so it never competes for label width.
                IconButton(onClick = { onRemove(account.identity) }, enabled = !busy) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "移除账号",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SocialSheet(
    kind: String,
    state: UiState,
    onDismiss: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val title = when (kind) {
        "following" -> "\u5173\u6ce8"
        "fans" -> "\u7c89\u4e1d"
        "subscriptions" -> "\u8ba2\u9605"
        else -> "\u6536\u85cf"
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                when (kind) {
                    "subscriptions" -> "\u5171 " + state.subscriptions.size + " \u4e2a"
                    "favorites" -> "\u5171 " + state.favorites.size + " \u6761"
                    else -> "\u5171 " + state.socialUsers.size + " \u4f4d"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.socialLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(2.dp)
                    .clip(CircleShape),
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 24.dp)) {
            when (kind) {
                "following", "fans" -> {
                    items(state.socialUsers, key = { it.uid }) { user ->
                        SocialRow(
                            title = user.nickname.ifEmpty { "\u7528\u6237 " + user.uid },
                            subtitle = formatCount(user.followers) + " \u7c89\u4e1d \u00b7 " +
                                user.albums + " \u4e13\u8f91",
                            avatar = user.avatarUrl,
                            onClick = {
                                onDismiss()
                                onOpenUser(user.uid)
                            },
                        )
                    }
                    if (!state.socialLoading && state.socialUsers.isEmpty()) {
                        item { SheetEmpty("\u6682\u65e0" + title) }
                    }
                }

                "subscriptions" -> {
                    items(state.subscriptions, key = { it.album.id }) { item ->
                        SocialRow(
                            title = item.album.title,
                            subtitle = listOfNotNull(
                                item.album.artist.takeIf { it.isNotEmpty() },
                                item.album.total.toString() + " \u96c6",
                            ).joinToString(" \u00b7 "),
                            avatar = item.album.coverUrl,
                        )
                    }
                    if (state.subscriptions.isEmpty()) item { SheetEmpty("\u6682\u65e0\u8ba2\u9605") }
                }

                else -> {
                    items(state.favorites, key = { it.id }) { track ->
                        SocialRow(
                            title = track.title.ifEmpty { track.number },
                            subtitle = track.album.ifEmpty { track.artist },
                            avatar = track.coverUrl,
                        )
                    }
                    if (state.favorites.isEmpty()) item { SheetEmpty("\u6682\u65e0\u6536\u85cf") }
                }
            }
        }
    }
}

@Composable
private fun SocialRow(
    title: String,
    subtitle: String,
    avatar: String?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = avatar, fallback = title, size = 44)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Avatar(url: String?, fallback: String, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                fallback.take(1).ifEmpty { "?" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCount(value: Int): String = when {
    value >= 100_000_000 -> "%.1f\u4ebf".format(value / 100_000_000.0)
    value >= 10_000 -> "%.1f\u4e07".format(value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改账号名") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                label = { Text("显示名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CookieDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cookie 导入") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Cookie 或登录信息 JSON") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("核验并保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Everything the SMS dialog needs from the view model. */
class SmsLoginCallbacks(
    val captchaId: String,
    val sendSms: (String, Map<String, String>, (String?, String?) -> Unit) -> Unit,
    val verifySms: (String, String, Boolean, (String?) -> Unit) -> Unit,
    val cancel: () -> Unit,
)

@Composable
private fun SmsLoginDialog(
    callbacks: SmsLoginCallbacks,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCaptcha by remember { mutableStateOf(false) }
    var makePrimary by remember { mutableStateOf(true) }

    fun verify() {
        val active = challenge ?: return
        busy = true
        error = null
        callbacks.verifySms(active, code, makePrimary) { failure ->
            busy = false
            if (failure == null) onSignedIn() else error = failure
        }
    }

    LaunchedEffect(Unit) {
        callbacks.cancel()
    }

    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                callbacks.cancel()
                onDismiss()
            }
        },
        title = { Text("手机号登录") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "先完成安全验证，再由你确认发送短信。登录后会核验极速版可用听时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(19) },
                    label = { Text("手机号") },
                    singleLine = true,
                    enabled = !busy && challenge == null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedButton(
                    onClick = { showCaptcha = true },
                    enabled = !busy && challenge == null && phone.length >= 6,
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(if (challenge == null) "安全验证并发送短信" else "短信已发送")
                }
                if (challenge != null) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(8) },
                        label = { Text("短信验证码") },
                        singleLine = true,
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!busy && code.length >= 4) verify()
                        }),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text(
                        "验证码 10 分钟内有效，最多尝试 5 次。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    TextButton(onClick = { makePrimary = !makePrimary }) {
                        Text(if (makePrimary) "☑ 设为主账号" else "☐ 设为主账号")
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (challenge != null) {
                TextButton(
                    onClick = { if (!busy && code.length >= 4) verify() },
                    enabled = !busy && code.length >= 4,
                ) { Text(if (busy) "登录中…" else "验证并登录") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    callbacks.cancel()
                    onDismiss()
                },
                enabled = !busy,
            ) { Text("取消") }
        },
    )

    if (showCaptcha) {
        GeetestDialog(
            captchaId = callbacks.captchaId,
            onDismiss = { showCaptcha = false },
            onSuccess = { fields ->
                showCaptcha = false
                busy = true
                error = null
                callbacks.sendSms(phone, fields) { result, failure ->
                    busy = false
                    if (result != null) {
                        challenge = result
                    } else {
                        error = failure ?: "短信发送未完成，请重试"
                    }
                }
            },
        )
    }

    // The dialog used to close itself whenever the app was already signed in,
    // so adding a second account flashed and vanished. Closing is driven by the
    // verify callback below instead.
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

/** Row with a title, an explanation and a switch. */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Trim point as a share of the track rather than a fixed number of seconds, so
 * one setting fits a 3-minute clip and a 40-minute episode alike.
 */
@Composable
private fun SkipSlider(title: String, permille: Int, onValueChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                percentLabel(permille),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = permille.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..teikengstudio.xmpro.data.MAX_SKIP_PERMILLE.toFloat(),
        )
    }
}

/** `30` per-mille reads as "+0:18 / 10 分钟 · 3%". */
private fun percentLabel(permille: Int): String {
    val percent = permille / 10.0
    val seconds = permille * 6 / 10 // per-mille of a 10-minute reference episode
    return if (permille == 0) {
        "不跳过"
    } else {
        "%.1f%% · 10 分钟约 %d 秒".format(percent, seconds)
    }
}

/**
 * About page: identity at the top, then the three things a user actually needs
 * here — check for updates, pick a GitHub mirror, open the repository.
 */
@Composable
private fun AboutPage(
    state: UiState,
    bottomInset: Dp,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onMirror: (String) -> Unit,
    onAutoCheckUpdate: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onDismissResult: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    var mirrorOpen by remember { mutableStateOf(false) }
    val mirrorLabel = teikengstudio.xmpro.updater.Updater.MIRRORS
        .firstOrNull { it.first == state.updateMirror }
        ?.second
        ?: state.updateMirror.ifBlank { "直连 GitHub" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Text(
                "XMPro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                if (version.isEmpty()) "版本未知" else "版本 $version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "by Angus",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        GroupTitle("信息")
        SettingsGroup {
            SettingSwitchRow(
                title = "自动检查更新",
                subtitle = "启动时最多每 12 小时检测一次，发现新版本会弹窗提示",
                checked = state.autoCheckUpdate,
                onCheckedChange = onAutoCheckUpdate,
            )
            GroupDivider()
            SettingsRow(
                title = "检查更新",
                subtitle = if (state.updateChecking) "正在连接 GitHub…" else "连接官方仓库获取最新版本",
                icon = Icons.Filled.Refresh,
                onClick = { if (!state.updateChecking) onCheckUpdate() },
            )
            GroupDivider()
            SettingsRow(
                title = "反代加速",
                subtitle = mirrorLabel,
                icon = Icons.Filled.SwapHoriz,
                onClick = { mirrorOpen = true },
            )
            GroupDivider()
            SettingsRow(
                title = "GitHub 仓库",
                subtitle = "查看源码与提交记录",
                icon = Icons.Filled.Code,
                onClick = { onOpenLink(teikengstudio.xmpro.updater.Updater.WEB_URL) },
            )
        }
        Spacer(Modifier.height(bottomInset))
    }

    if (mirrorOpen) {
        AlertDialog(
            onDismissRequest = { mirrorOpen = false },
            title = { Text("反代加速") },
            text = {
                Column {
                    Text(
                        "检查更新与下载安装包时可走镜像，直连较慢时再切换。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    teikengstudio.xmpro.updater.Updater.MIRRORS.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onMirror(value)
                                    mirrorOpen = false
                                }
                                .padding(vertical = 11.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.updateMirror == value) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mirrorOpen = false }) { Text("关闭") }
            },
        )
    }

    state.updateResult?.let { message ->
        val downloading = state.updateProgress >= 0 && !state.updateReady
        AlertDialog(
            onDismissRequest = { if (!downloading) onDismissResult() },
            title = {
                Text(
                    when {
                        state.updateReady -> "可以安装"
                        downloading -> "正在下载"
                        state.updateUrl != null -> "发现更新"
                        else -> "检查结果"
                    },
                )
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (downloading) {
                        if (state.updateProgress > 0) {
                            LinearProgressIndicator(
                                progress = { state.updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${state.updateProgress}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = if (downloading) 12.dp else 0.dp),
                    )
                }
            },
            confirmButton = {
                when {
                    state.updateReady ->
                        TextButton(onClick = onInstallUpdate) { Text("安装") }

                    downloading -> Unit

                    state.updateUrl != null ->
                        TextButton(onClick = onDownloadUpdate) { Text("立即更新") }

                    else -> Unit
                }
            },
            dismissButton = {
                if (!downloading) {
                    TextButton(onClick = onDismissResult) { Text("关闭") }
                }
            },
        )
    }
}

/** Welfare tiers with their last grant time, plus the raw payload on demand. */
