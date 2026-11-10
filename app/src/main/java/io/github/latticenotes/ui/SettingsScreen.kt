package io.github.latticenotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.latticenotes.data.AppSettings
import io.github.latticenotes.data.CredentialVault
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.data.OnlineSyncManager
import io.github.latticenotes.data.SettingsRepository
import io.github.latticenotes.data.WebDavClient
import io.github.latticenotes.data.WebDavConfig
import io.github.latticenotes.util.PinHasher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: NotesRepository,
    settingsRepository: SettingsRepository,
    credentialVault: CredentialVault,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pinDialog by remember { mutableStateOf(false) }
    var disablePinDialog by remember { mutableStateOf(false) }
    var webDavDialog by remember { mutableStateOf(false) }
    var webDavRestoreDialog by remember { mutableStateOf(false) }

    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            val count = uris.mapNotNull { repository.importMarkdown(it) }.size
            snackbar.showSnackbar("已导入 $count 个 Markdown 文件")
        }
    }
    val effectiveWebDavUrl = remember(settings.webDavUrl) {
        if (settings.webDavUrl.isBlank()) {
            ""
        } else {
            runCatching {
                WebDavConfig(settings.webDavUrl, "", "").directoryUrl().toString()
            }.getOrDefault(settings.webDavUrl)
        }
    }

    fun webDavConfig(): WebDavConfig = WebDavConfig(
        fileUrl = settings.webDavUrl,
        username = settings.webDavUsername,
        password = credentialVault.loadWebDavPasswordOrDefault(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    "外观",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色",
                ).forEach { (value, label) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = settings.themeMode == value,
                            onClick = { scope.launch { settingsRepository.setThemeMode(value) } },
                        )
                        Text(label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("字号  ${(settings.fontScale * 100).toInt()}%")
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { scope.launch { settingsRepository.setFontScale(it) } },
                        valueRange = 0.8f..1.5f,
                        steps = 6,
                    )
                }
            }
            item {
                Text(
                    "默认笔记格式",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    NoteFormat.entries.forEach { format ->
                        TextButton(
                            onClick = { scope.launch { settingsRepository.setDefaultFormat(format) } },
                        ) {
                            Text(
                                text = when (format) {
                                    NoteFormat.TEXT -> "文本"
                                    NoteFormat.MARKDOWN -> "Markdown"
                                    NoteFormat.CHECKLIST -> "待办"
                                },
                                color = if (settings.defaultFormat == format.name) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    headlineContent = { Text("导入 Markdown 文件") },
                    supportingContent = { Text("可一次选择多个 .md 文件，文件名作为标题") },
                    modifier = Modifier.clickableItem {
                        markdownLauncher.launch(
                            arrayOf(
                                "text/markdown",
                                "text/x-markdown",
                                "text/plain",
                                "application/octet-stream",
                            ),
                        )
                    },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Lock, null) },
                    headlineContent = { Text("PIN 应用锁") },
                    supportingContent = {
                        Text(if (settings.appLockEnabled) "已启用" else "未启用")
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) pinDialog = true else disablePinDialog = true
                            },
                        )
                    },
                )
            }
            item {
                Text(
                    "WebDAV 在线备份",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    headlineContent = { Text("配置 WebDAV") },
                    supportingContent = {
                        Column {
                            Text(
                                if (settings.webDavUrl.isBlank()) {
                                    "尚未配置 WebDAV 地址"
                                } else {
                                    settings.webDavUrl
                                },
                            )
                            if (
                                effectiveWebDavUrl.isNotBlank() &&
                                effectiveWebDavUrl != settings.webDavUrl
                            ) {
                                Text("Markdown 文件夹：$effectiveWebDavUrl")
                            }
                        }
                    },
                    modifier = Modifier.clickableItem { webDavDialog = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("自动同步（前台每 30 秒）") },
                    supportingContent = {
                        Text("启动时立即同步；之后只处理有变化的 Markdown 文件")
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.webDavAutoSync,
                            onCheckedChange = {
                                scope.launch { settingsRepository.setWebDavAutoSync(it) }
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    headlineContent = { Text("测试 WebDAV 连接") },
                    supportingContent = { Text("建议先测试配置，再上传或恢复") },
                    modifier = Modifier.clickableItem {
                        scope.launch {
                            runCatching { WebDavClient(webDavConfig()).testConnection() }
                                .onSuccess { snackbar.showSnackbar("WebDAV 连接成功") }
                                .onFailure {
                                    snackbar.showSnackbar("连接失败：${it.message}")
                                }
                        }
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    headlineContent = { Text("立即同步 Markdown 记事") },
                    supportingContent = { Text("每篇记事保存为独立的 note-ID.md 文件") },
                    modifier = Modifier.clickableItem {
                        scope.launch {
                            snackbar.showSnackbar(
                                OnlineSyncManager.sync(
                                    repository,
                                    settings,
                                    credentialVault,
                                    force = true,
                                ) ?: "WebDAV 自动同步未启用",
                            )
                        }
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    headlineContent = { Text("从 WebDAV 下载 Markdown 记事") },
                    supportingContent = { Text("按 note-ID.md 合并较新的记事") },
                    modifier = Modifier.clickableItem { webDavRestoreDialog = true },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    headlineContent = { Text("曲阳便签 0.5.0") },
                    supportingContent = { Text("MIT License · 本地优先 · 无广告") },
                )
            }
            item {
                Text(
                    "本项目为独立原创实现，不包含第三方专有应用的代码、资源或品牌。",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pinDialog) {
        PinSetupDialog(
            onDismiss = { pinDialog = false },
            onSave = { pin ->
                scope.launch {
                    settingsRepository.setPin(pin)
                    pinDialog = false
                    snackbar.showSnackbar("PIN 已启用")
                }
            },
        )
    }
    if (disablePinDialog) {
        PinVerifyDialog(
            settings = settings,
            onDismiss = { disablePinDialog = false },
            onVerified = {
                scope.launch {
                    settingsRepository.clearPin()
                    disablePinDialog = false
                    snackbar.showSnackbar("PIN 已关闭")
                }
            },
        )
    }
    if (webDavDialog) {
        WebDavConfigDialog(
            initialUrl = settings.webDavUrl,
            initialUsername = settings.webDavUsername,
            passwordConfigured = credentialVault.hasWebDavPassword(),
            onDismiss = { webDavDialog = false },
            onSave = { url, username, password ->
                scope.launch {
                    settingsRepository.setWebDav(url, username)
                    credentialVault.saveWebDavPassword(password)
                    webDavDialog = false
                    snackbar.showSnackbar("WebDAV 配置已保存")
                }
            },
            onClear = {
                scope.launch {
                    settingsRepository.clearWebDav()
                    credentialVault.clearWebDavPassword()
                    webDavDialog = false
                    snackbar.showSnackbar("WebDAV 配置已清除")
                }
            },
        )
    }
    if (webDavRestoreDialog) {
        AlertDialog(
            onDismissRequest = { webDavRestoreDialog = false },
            title = { Text("从 WebDAV 下载 Markdown 记事？") },
            text = {
                Text("将下载远端 note-ID.md 文件，并合并较新的记事。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        webDavRestoreDialog = false
                        scope.launch {
                            runCatching {
                                OnlineSyncManager.sync(
                                    repository,
                                    settings,
                                    credentialVault,
                                    force = true,
                                ) ?: error("WebDAV 自动同步未启用")
                            }.onSuccess { snackbar.showSnackbar("WebDAV Markdown 同步完成") }
                                .onFailure { snackbar.showSnackbar("恢复失败：${it.message}") }
                        }
                    },
                ) { Text("下载并恢复") }
            },
            dismissButton = {
                TextButton(onClick = { webDavRestoreDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun WebDavConfigDialog(
    initialUrl: String,
    initialUsername: String,
    passwordConfigured: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 在线备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("WebDAV Markdown 文件夹 URL") },
                    placeholder = {
                        Text("https://dav.jianguoyun.com/dav/biji")
                    },
                    supportingText = {
                        Text(
                            "目录中每篇记事会保存为独立的 note-ID.md 文件",
                        )
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(if (passwordConfigured) "密码（留空则不修改）" else "密码")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onSave(url.trim(), username.trim(), password) },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initialUrl.isNotBlank() || initialUsername.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length >= 4 && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置 PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("至少 4 位数字") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(12) },
                    label = { Text("再次输入") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != pin,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(pin) }) { Text("启用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun PinVerifyDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关闭 PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(12)
                    error = false
                },
                label = { Text("输入当前 PIN") },
                visualTransformation = PasswordVisualTransformation(),
                isError = error,
                supportingText = { if (error) Text("PIN 不正确") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4,
                onClick = {
                    if (PinHasher.verify(pin, settings.pinSalt, settings.pinHash)) {
                        onVerified()
                    } else {
                        error = true
                    }
                },
            ) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
