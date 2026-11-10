package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import teikengstudio.xmpro.data.AccountSummary

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
    var showCookie by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("账号")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { showCookie = true }, modifier = Modifier.weight(1f)) {
                Text("Cookie 导入")
            }
            OutlinedButton(onClick = { showSms = true }, modifier = Modifier.weight(1f)) {
                Text("手机号登录")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.accounts.isEmpty()) {
            ListItem(
                headlineContent = { Text("未登录") },
                supportingContent = { Text("导入极速版 Cookie，或用手机号短信登录") },
            )
        }
        for (account in state.accounts) {
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
        if (state.signedIn) {
            ListItem(
                headlineContent = { Text("退出当前账号") },
                supportingContent = { Text("仅清除当前设备上的登录信息") },
                trailingContent = { TextButton(onClick = onSignOut) { Text("退出") } },
            )
        }
        HorizontalDivider()
        SectionTitle("听时")
        ListItem(
            headlineContent = { Text("自动领取听时") },
            supportingContent = { Text("可用听时不足 2 小时时尝试领取至 4 小时；失败会自动暂停该账号领取") },
            trailingContent = {
                Switch(
                    checked = state.autoClaim,
                    enabled = !state.accountBusy,
                    onCheckedChange = onAutoClaim,
                )
            },
        )
        HorizontalDivider()
        SectionTitle("外观")
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
        ListItem(
            headlineContent = { Text("OLED 纯黑") },
            supportingContent = { Text("暗色模式下使用纯黑背景") },
            trailingContent = { Switch(checked = oledBlack, onCheckedChange = onOledBlack) },
        )
        HorizontalDivider()
        SectionTitle("关于")
        ListItem(
            headlineContent = { Text("XMPro") },
            supportingContent = { Text("喜马拉雅第三方客户端 · Kotlin + Compose 重构版") },
        )
        ListItem(
            headlineContent = { Text("许可") },
            supportingContent = { Text("AGPL-3.0-or-later；界面参考 PiliPlus（GPL-3.0）") },
        )
        Spacer(Modifier.height(bottomInset))
    }

    if (showCookie) {
        CookieDialog(
            onDismiss = { showCookie = false },
            onConfirm = {
                onSignIn(it)
                showCookie = false
            },
        )
    }
    if (showSms) {
        SmsLoginDialog(
            callbacks = smsLogin,
            signedIn = state.signedIn,
            onDismiss = { showSms = false },
            onSignedIn = { showSms = false },
        )
    }
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
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    "账号 ${account.uid.ifEmpty { account.identity.take(8) }}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (account.primary) {
                    Text("主账号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "设备 ${account.deviceHint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val balance = account.balance
            Text(
                if (balance == null) {
                    "可用听时：未核验"
                } else {
                    "可用听时：${balance.remainingSeconds / 3600} 小时 " +
                        "${(balance.remainingSeconds % 3600) / 60} 分 ${balance.remainingSeconds % 60} 秒"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (balance != null) {
                Text(
                    "核验于 ${SimpleDateFormat("M-d HH:mm", Locale.CHINA).format(Date(balance.checkedAt.toEpochMilli()))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (account.quotaRejected) {
                Text(
                    "听时核验被服务端拒绝，可点「查询听时」重试；播放与下载仍会校验余额。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        Text("恢复自动领取")
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

    // The view model owns the network call and flips `signedIn` on success.
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
