package teikengstudio.xmpro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: UiState,
    themeMode: String,
    oledBlack: Boolean,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onThemeMode: (String) -> Unit,
    onOledBlack: (Boolean) -> Unit,
) {
    var showImport by remember { mutableStateOf(false) }
    var cookieText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("账号")
        if (state.signedIn) {
            ListItem(
                headlineContent = { Text("已导入账号") },
                supportingContent = { Text(state.account?.take(16)?.plus("…") ?: "") },
                trailingContent = { TextButton(onClick = onSignOut) { Text("退出") } },
            )
        } else {
            ListItem(
                headlineContent = { Text("未登录") },
                supportingContent = { Text("导入极速版 Cookie 或登录信息 JSON") },
                trailingContent = { Button(onClick = { showImport = true }) { Text("导入") } },
            )
        }
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
            headlineContent = { Text("数据目录") },
            supportingContent = { Text("应用专属外部存储，卸载后一并删除") },
        )
        ListItem(
            headlineContent = { Text("许可") },
            supportingContent = { Text("AGPL-3.0-or-later；界面参考 PiliPlus（GPL-3.0）") },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入登录信息") },
            text = {
                OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    label = { Text("Cookie 或 JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSignIn(cookieText.trim())
                        showImport = false
                        cookieText = ""
                    },
                ) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } },
        )
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
