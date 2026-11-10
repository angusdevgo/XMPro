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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import teikengstudio.xmpro.data.AccountSummary

private enum class SettingsPage { Root, Account, Appearance, About }

/**
 * Grouped settings: a menu root with one page per topic, mirroring the
 * previous client's information architecture instead of one long scroll.
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
    onRemoveAccount: (String) -> Unit,
    onQueryQuota: (String) -> Unit,
    onClaimOnce: (String) -> Unit,
    onResumeRewards: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    smsLogin: SmsLoginCallbacks,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    var showCookie by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }
    val current = state.accounts.firstOrNull { it.active }
        ?: state.accounts.firstOrNull { it.primary }
        ?: state.accounts.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        when (page) {
            SettingsPage.Root -> SettingsRoot(
                state = state,
                current = current,
                themeMode = themeMode,
                bottomInset = bottomInset,
                onNavigate = { page = it },
            )

            SettingsPage.Account -> {
                PageHeader("账号", onBack = { page = SettingsPage.Root })
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AccountContent(
                        state = state,
                        current = current,
                        onSignOut = onSignOut,
                        onSetPrimary = onSetPrimary,
                        onRemoveAccount = onRemoveAccount,
                        onQueryQuota = onQueryQuota,
                        onClaimOnce = onClaimOnce,
                        onResumeRewards = onResumeRewards,
                        onAutoClaim = onAutoClaim,
                        onAddCookie = { showCookie = true },
                        onAddSms = { showSms = true },
                    )
                    Spacer(Modifier.height(bottomInset))
                }
            }

            SettingsPage.Appearance -> {
                PageHeader("外观", onBack = { page = SettingsPage.Root })
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("主题") },
                        supportingContent = { Text(themeLabel(themeMode)) },
                        trailingContent = {
                            Row {
                                for (mode in listOf("system", "light", "dark")) {
                                    TextButton(onClick = { onThemeMode(mode) }) {
                                        Text(if (themeMode == mode) "● ${themeLabel(mode)}" else themeLabel(mode))
                                    }
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("OLED 纯黑") },
                        supportingContent = { Text("暗色模式下使用纯黑背景") },
                        trailingContent = { Switch(checked = oledBlack, onCheckedChange = onOledBlack) },
                    )
                    Spacer(Modifier.height(bottomInset))
                }
            }

            SettingsPage.About -> {
                PageHeader("关于", onBack = { page = SettingsPage.Root })
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("XMPro") },
                        supportingContent = { Text("喜马拉雅第三方客户端 · Kotlin + Compose 重构版") },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("数据目录") },
                        supportingContent = { Text("应用专属外部存储，卸载后一并删除") },
                    )
                    ListItem(
                        headlineContent = { Text("许可") },
                        supportingContent = { Text("AGPL-3.0-or-later；界面参考 PiliPlus（GPL-3.0）") },
                    )
                    Spacer(Modifier.height(bottomInset))
                }
            }
        }
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
            signedIn = state.signedIn,
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
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        SettingsRow(
            title = "账号",
            subtitle = current?.let { "账号 ${it.uid}${if (it.primary) " · 主账号" else ""}" } ?: "未登录",
            icon = Icons.Filled.Person,
            onClick = { onNavigate(SettingsPage.Account) },
        )
        SettingsRow(
            title = "外观",
            subtitle = themeLabel(themeMode),
            icon = Icons.Filled.Palette,
            onClick = { onNavigate(SettingsPage.Appearance) },
        )
        SettingsRow(
            title = "关于",
            subtitle = "版本信息与许可",
            icon = Icons.Filled.Info,
            onClick = { onNavigate(SettingsPage.About) },
        )
        Spacer(Modifier.height(bottomInset))
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, end = 12.dp),
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
    onRemoveAccount: (String) -> Unit,
    onQueryQuota: (String) -> Unit,
    onClaimOnce: (String) -> Unit,
    onResumeRewards: (String) -> Unit,
    onAutoClaim: (Boolean) -> Unit,
    onAddCookie: () -> Unit,
    onAddSms: () -> Unit,
) {
    if (current == null) {
        LoginEntry(onAddCookie = onAddCookie, onAddSms = onAddSms)
        return
    }
    ProfileCard(
        account = current,
        state = state,
        busy = state.accountBusy,
        onQueryQuota = onQueryQuota,
        onClaim = onClaimOnce,
        onResume = onResumeRewards,
    )
    if (state.accounts.size > 1) {
        SectionHeader("其他账号")
        for (account in state.accounts.filter { it.identity != current.identity }) {
            AccountCard(
                account = account,
                busy = state.accountBusy,
                onSetPrimary = onSetPrimary,
                onRemove = onRemoveAccount,
                onQueryQuota = onQueryQuota,
                onClaim = onClaimOnce,
                onResume = onResumeRewards,
            )
        }
    }
    SectionHeader("账号管理")
    ActionRow(
        title = "添加 Cookie 账号",
        subtitle = "粘贴极速版 Cookie 或登录信息 JSON",
        onClick = onAddCookie,
    )
    ActionRow(
        title = "短信登录新账号",
        subtitle = "手机号 + 安全验证",
        onClick = onAddSms,
    )
    ActionRow(
        title = "退出当前账号",
        subtitle = "仅清除本机登录信息，听时记录保留",
        onClick = onSignOut,
    )
    SectionHeader("听时")
    ListItem(
        headlineContent = { Text("自动领取听时") },
        supportingContent = {
            Text("不足 2 小时补充至 4 小时，对所有账号生效；领取失败会自动暂停该账号")
        },
        trailingContent = {
            Switch(
                checked = state.autoClaim,
                enabled = !state.accountBusy,
                onCheckedChange = onAutoClaim,
            )
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    if (current.paused) {
        ListItem(
            headlineContent = { Text("该账号的自动领取已暂停") },
            supportingContent = { Text("请先在官方 App 核对上一次领取结果，再恢复自动领取") },
            trailingContent = {
                TextButton(
                    onClick = { onResumeRewards(current.identity) },
                    enabled = !state.accountBusy,
                ) { Text("恢复") }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LoginEntry(onAddCookie: () -> Unit, onAddSms: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("还没有登录账号", style = MaterialTheme.typography.titleMedium)
                Text(
                    "登录后可同步订阅、听更新与云历史，并查看可用听时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddCookie, modifier = Modifier.weight(1f)) { Text("Cookie 登录") }
                    OutlinedButton(onClick = onAddSms, modifier = Modifier.weight(1f)) { Text("短信登录") }
                }
                Text(
                    "主账号用于默认请求",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    account: AccountSummary,
    state: UiState,
    busy: Boolean,
    onQueryQuota: (String) -> Unit,
    onClaim: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    val balance = account.balance
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        account.uid.take(1).ifEmpty { "我" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(account.uid, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "设备 ${account.deviceHint}" + if (account.primary) " · 主账号" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 12.dp),
            ) {
                Stat(
                    value = if (balance == null) "—" else formatListeningTime(balance.remainingSeconds),
                    label = "可用听时",
                    modifier = Modifier.weight(1.4f),
                )
                Stat("${state.subscriptions.size}", "订阅", Modifier.weight(1f))
                Stat("${state.favorites.size}", "收藏", Modifier.weight(1f))
            }

            val meta = when {
                balance != null ->
                    "核验于 " + SimpleDateFormat("M-d HH:mm", Locale.CHINA)
                        .format(Date(balance.checkedAt.toEpochMilli()))

                account.quotaRejected -> "听时核验被拒绝，可重新查询"
                else -> "尚未核验听时"
            }
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onQueryQuota(account.identity) }, enabled = !busy) {
                    Text("查询听时")
                }
                OutlinedButton(onClick = { onClaim(account.identity) }, enabled = !busy && !account.paused) {
                    Text("领取一次")
                }
                if (account.paused) {
                    TextButton(onClick = { onResume(account.identity) }, enabled = !busy) {
                        Text("恢复领取")
                    }
                }
            }
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
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun AccountCard(
    account: AccountSummary,
    busy: Boolean,
    onSetPrimary: (String) -> Unit,
    onRemove: (String) -> Unit,
    onQueryQuota: (String) -> Unit,
    onClaim: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    "账号 ${account.uid.ifEmpty { account.identity.take(8) }}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (account.primary) {
                    Text(
                        "主账号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                "设备 ${account.deviceHint} · " +
                    (account.balance?.let { formatListeningTime(it.remainingSeconds) } ?: "听时未核验"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!account.primary) {
                    TextButton(onClick = { onSetPrimary(account.identity) }, enabled = !busy) {
                        Text("设为主账号")
                    }
                }
                TextButton(onClick = { onQueryQuota(account.identity) }, enabled = !busy) {
                    Text("查询听时")
                }
                TextButton(onClick = { onClaim(account.identity) }, enabled = !busy && !account.paused) {
                    Text("领取一次")
                }
                if (account.paused) {
                    TextButton(onClick = { onResume(account.identity) }, enabled = !busy) {
                        Text("恢复领取")
                    }
                }
                TextButton(onClick = { onRemove(account.identity) }, enabled = !busy) {
                    Text("移除")
                }
            }
        }
    }
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
    signedIn: Boolean,
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedButton(
                    onClick = { showCaptcha = true },
                    enabled = !busy && challenge == null && phone.length >= 6,
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

    LaunchedEffect(signedIn) {
        if (signedIn) onSignedIn()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}
