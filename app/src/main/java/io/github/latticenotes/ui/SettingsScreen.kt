package io.github.latticenotes.ui

import android.net.Uri
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
import io.github.latticenotes.BuildConfig
import io.github.latticenotes.backup.EncryptedBackupCodec
import io.github.latticenotes.data.AppSettings
import io.github.latticenotes.data.CredentialVault
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.data.OnlineSyncManager
import io.github.latticenotes.data.SettingsRepository
import io.github.latticenotes.data.WebDavClient
import io.github.latticenotes.data.WebDavConfig
import io.github.latticenotes.reminder.ReminderScheduler
import io.github.latticenotes.update.AppUpdate
import io.github.latticenotes.update.UpdateManager
import io.github.latticenotes.util.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    var exportBackupUri by remember { mutableStateOf<Uri?>(null) }
    var importBackupUri by remember { mutableStateOf<Uri?>(null) }
    var exportPasswordDialog by remember { mutableStateOf(false) }
    var importPasswordDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            exportBackupUri = uri
            exportPasswordDialog = true
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importBackupUri = uri
            importPasswordDialog = true
        }
    }

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
            item {
                Text(
                    "完整备份",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    headlineContent = { Text("导出加密完整备份") },
                    supportingContent = {
                        Text("包含记事、文件夹、提醒、图片和附件")
                    },
                    modifier = Modifier.clickableItem {
                        exportBackupLauncher.launch(
                            "quyang-backup-${LocalDate.now()}.qbak",
                        )
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    headlineContent = { Text("导入加密完整备份") },
                    supportingContent = {
                        Text("按更新时间合并，较新的本地记事保持不变")
                    },
                    modifier = Modifier.clickableItem {
                        importBackupLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/json",
                                "text/plain",
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
                                Text("同步目录：$effectiveWebDavUrl")
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
                        Text("本地、远端与上次记录三方比较；支持批量文件和删除保护")
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
                    supportingContent = { Text("仅上传变化文件，每篇记事保存为独立 Markdown") },
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
                Text(
                    "在线更新",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("启动时自动检查更新") },
                    supportingContent = {
                        Text("每天最多检查一次；发现新版本后提示下载并进入系统安装")
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.autoUpdateCheck,
                            onCheckedChange = {
                                scope.launch { settingsRepository.setAutoUpdateCheck(it) }
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    headlineContent = { Text("检查在线更新") },
                    supportingContent = {
                        Text(
                            if (checkingUpdate) {
                                "正在检查 GitHub Release…"
                            } else {
                                "当前版本 ${BuildConfig.VERSION_NAME}"
                            },
                        )
                    },
                    modifier = if (checkingUpdate) Modifier else Modifier.clickableItem {
                        checkingUpdate = true
                        scope.launch {
                            runCatching { UpdateManager.checkForUpdate() }
                                .onSuccess { update ->
                                    if (update == null) {
                                        snackbar.showSnackbar("当前已是最新版本")
                                    } else {
                                        availableUpdate = update
                                    }
                                }
                                .onFailure {
                                    snackbar.showSnackbar("检查更新失败：${it.message}")
                                }
                            checkingUpdate = false
                        }
                    },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    headlineContent = { Text("曲阳便签 ${BuildConfig.VERSION_NAME}") },
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
                            }.onSuccess { snackbar.showSnackbar(it) }
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
    if (exportPasswordDialog) {
        BackupPasswordDialog(
            title = "设置备份密码",
            supportingText = "密码至少 8 个字符。忘记密码后无法恢复备份。",
            confirmLabel = "加密并导出",
            requireConfirmation = true,
            onDismiss = {
                exportPasswordDialog = false
                exportBackupUri = null
            },
            onConfirm = { password ->
                val uri = exportBackupUri
                exportPasswordDialog = false
                exportBackupUri = null
                if (uri == null) {
                    scope.launch { snackbar.showSnackbar("无法创建备份文件") }
                } else {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val payload = repository.getAllForBackup()
                                val encoded = EncryptedBackupCodec.encode(payload, password)
                                require(encoded.size <= MAX_BACKUP_BYTES) { "备份文件过大" }
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    output.write(encoded)
                                } ?: error("无法写入所选文件")
                            }
                        }.onSuccess {
                            snackbar.showSnackbar("加密完整备份已导出")
                        }.onFailure {
                            snackbar.showSnackbar("导出失败：${it.message}")
                        }
                    }
                }
            },
        )
    }
    if (importPasswordDialog) {
        BackupPasswordDialog(
            title = "输入备份密码",
            supportingText = "将读取加密备份并合并到当前记事，不会删除现有内容。",
            confirmLabel = "解密并导入",
            requireConfirmation = false,
            onDismiss = {
                importPasswordDialog = false
                importBackupUri = null
            },
            onConfirm = { password ->
                val uri = importBackupUri
                importPasswordDialog = false
                importBackupUri = null
                if (uri == null) {
                    scope.launch { snackbar.showSnackbar("无法读取备份文件") }
                } else {
                    scope.launch {
                        runCatching {
                            val payload = withContext(Dispatchers.IO) {
                                val encoded = context.contentResolver.openInputStream(uri)
                                    ?.use { input -> readLimitedBytes(input, MAX_BACKUP_BYTES) }
                                    ?: error("无法读取所选文件")
                                EncryptedBackupCodec.decode(encoded, password)
                            }
                            repository.mergeBackup(payload)
                            val now = System.currentTimeMillis()
                            payload.notes.forEach { imported ->
                                val current = repository.getNote(imported.id) ?: return@forEach
                                if (
                                    current.updatedAt == imported.updatedAt &&
                                    current.reminderAt != null &&
                                    current.reminderAt > now &&
                                    !current.trashed
                                ) {
                                    ReminderScheduler.schedule(
                                        context,
                                        current.id,
                                        current.title,
                                        current.reminderAt,
                                    )
                                }
                            }
                        }.onSuccess {
                            snackbar.showSnackbar("完整备份已合并")
                        }.onFailure {
                            snackbar.showSnackbar("导入失败：${it.message}")
                        }
                    }
                }
            },
        )
    }
    availableUpdate?.let { update ->
        UpdateAvailableDialog(update = update, onDismiss = { availableUpdate = null })
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    supportingText: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length >= 8 &&
        (!requireConfirmation || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(supportingText)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text(if (requireConfirmation) "密码" else "备份密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.take(128) },
                        label = { Text("再次输入密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmation.isNotEmpty() && confirmation != password,
                        supportingText = {
                            if (confirmation.isNotEmpty() && confirmation != password) {
                                Text("两次输入的密码不一致")
                            }
                        },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(password) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun readLimitedBytes(
    input: java.io.InputStream,
    maxBytes: Int,
): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "备份文件过大" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_BACKUP_BYTES = 128 * 1024 * 1024

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
                    label = { Text("WebDAV 根地址") },
                    placeholder = {
                        Text("https://dav.jianguoyun.com/dav/biji")
                    },
                    supportingText = {
                        Text(
                            "填写 /dav/ 根地址会创建「曲阳便签」；填写 /dav/biji/ 等具体目录时直接同步该目录",
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
