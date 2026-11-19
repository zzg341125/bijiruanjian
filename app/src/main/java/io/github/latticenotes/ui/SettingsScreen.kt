package io.github.latticenotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.latticenotes.BuildConfig
import io.github.latticenotes.R
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
    fun webDavConfig(): WebDavConfig = WebDavConfig(
        fileUrl = settings.webDavUrl,
        username = settings.webDavUsername,
        password = credentialVault.loadWebDavPasswordOrDefault(),
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(R.drawable.smartisan_note_background),
                contentScale = ContentScale.FillBounds,
            ),
        containerColor = Color.Transparent,
        topBar = {
            SmartisanSettingsTitleBar(onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 7.dp,
                bottom = 30.dp,
            ),
        ) {
            item {
                SettingsSectionTitle("外观")
            }
            item {
                SettingsGroup {
                    listOf(
                        "system" to "跟随系统",
                        "light" to "浅色",
                        "dark" to "深色",
                    ).forEachIndexed { index, (value, label) ->
                        SettingsRadioRow(
                            label = label,
                            selected = settings.themeMode == value,
                            onClick = {
                                scope.launch { settingsRepository.setThemeMode(value) }
                            },
                        )
                        if (index < 2) SettingsRowDivider()
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "字号  ${(settings.fontScale * 100).toInt()}%",
                            color = SettingsInk,
                            fontSize = 16.sp,
                        )
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { scope.launch { settingsRepository.setFontScale(it) } },
                        valueRange = 0.8f..1.5f,
                        steps = 6,
                    )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("便签背景颜色")
            }
            item {
                SettingsGroup {
                    NotePaperStyle.entries.forEachIndexed { index, style ->
                        SettingsPaperColorRow(
                            style = style,
                            selected = NotePaperStyle.fromStorage(settings.notePaperStyle) == style,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setNotePaperStyle(style.storageValue)
                                }
                            },
                        )
                        if (index < NotePaperStyle.entries.lastIndex) {
                            SettingsRowDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = "默认笔记格式",
                        value = when (settings.defaultFormat) {
                            NoteFormat.MARKDOWN.name -> "Markdown"
                            NoteFormat.CHECKLIST.name -> "待办"
                            else -> "文本"
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "导入 Markdown 文件",
                        subtitle = "可一次选择多个 .md 文件，文件名作为标题",
                        onClick = {
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
                    SettingsRowDivider()
                    SettingsChoiceRow(
                        values = NoteFormat.entries,
                        selected = NoteFormat.entries.firstOrNull {
                            it.name == settings.defaultFormat
                        } ?: NoteFormat.TEXT,
                        label = {
                            when (it) {
                                NoteFormat.TEXT -> "文本"
                                NoteFormat.MARKDOWN -> "Markdown"
                                NoteFormat.CHECKLIST -> "待办"
                            }
                        },
                        onSelect = {
                            scope.launch { settingsRepository.setDefaultFormat(it) }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("完整备份")
            }
            item {
                SettingsGroup {
                    SettingsActionRow(
                        title = "导出加密完整备份",
                        subtitle = "包含记事、文件夹、提醒、图片和附件",
                        onClick = {
                        exportBackupLauncher.launch(
                            "quyang-backup-${LocalDate.now()}.qbak",
                        )
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "导入加密完整备份",
                        subtitle = "按更新时间合并，较新的本地记事保持不变",
                        onClick = {
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
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("安全")
            }
            item {
                SettingsGroup {
                    SettingsSwitchRow(
                        title = "PIN 应用锁",
                        subtitle = if (settings.appLockEnabled) "已启用" else "未启用",
                        checked = settings.appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) pinDialog = true else disablePinDialog = true
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("WebDAV 在线备份")
            }
            item {
                SettingsGroup {
                    SettingsActionRow(
                        title = "配置 WebDAV",
                        subtitle = if (settings.webDavUrl.isBlank()) {
                            "尚未配置 WebDAV 地址"
                        } else {
                            settings.webDavUrl
                        },
                        onClick = { webDavDialog = true },
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = "自动同步（前台每 30 秒）",
                        subtitle = "本地、远端与上次记录三方比较；支持批量文件和删除保护",
                        checked = settings.webDavAutoSync,
                        onCheckedChange = {
                            scope.launch { settingsRepository.setWebDavAutoSync(it) }
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "测试 WebDAV 连接",
                        subtitle = "建议先测试配置，再上传或恢复",
                        onClick = {
                            scope.launch {
                                runCatching { WebDavClient(webDavConfig()).testConnection() }
                                    .onSuccess { snackbar.showSnackbar("WebDAV 连接成功") }
                                    .onFailure {
                                        snackbar.showSnackbar("连接失败：${it.message}")
                                    }
                            }
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "立即同步 Markdown 记事",
                        subtitle = "仅上传变化文件，每篇记事保存为独立 Markdown",
                        onClick = {
                            scope.launch {
                                OnlineSyncManager.sync(
                                    repository,
                                    settings,
                                    credentialVault,
                                    force = true,
                                )
                            }
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "从 WebDAV 下载 Markdown 记事",
                        subtitle = "按 note-ID.md 合并较新的记事",
                        onClick = { webDavRestoreDialog = true },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("在线更新")
            }
            item {
                SettingsGroup {
                    SettingsSwitchRow(
                        title = "启动时自动检查更新",
                        subtitle = "每天最多检查一次；发现新版本后提示下载并进入系统安装",
                        checked = settings.autoUpdateCheck,
                        onCheckedChange = {
                            scope.launch { settingsRepository.setAutoUpdateCheck(it) }
                        },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        title = "检查在线更新",
                        subtitle = if (checkingUpdate) {
                            "正在检查 GitHub Release…"
                        } else {
                            "当前版本 ${BuildConfig.VERSION_NAME}"
                        },
                        enabled = !checkingUpdate,
                        onClick = {
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
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsSectionTitle("关于")
            }
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = "曲阳便签 ${BuildConfig.VERSION_NAME}",
                        value = "MIT License · 本地优先 · 无广告",
                    )
                }
            }
            item {
                Text(
                    "本项目为独立原创实现，不包含第三方专有应用的代码、资源或品牌。",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    color = Color(0x76000000),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
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
                            OnlineSyncManager.sync(
                                repository,
                                settings,
                                credentialVault,
                                force = true,
                            )
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

private val SettingsInk = Color(0xFF454A5C)
private val SettingsMutedInk = Color(0x8A454A5C)
private val SettingsRowColor = Color(0xCFFBFBFB)
private val SettingsDividerColor = Color(0xFFE2E2E2)
private val SettingsAccent = Color(0xFFC47B28)

@Composable
private fun SmartisanSettingsTitleBar(onBack: () -> Unit) {
    Surface(
        color = Color.Transparent,
        contentColor = Color(0xFFFFFCF7),
        shadowElevation = 0.dp,
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.smartisan_title_bar),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = Color(0xFFFFFCF7),
                        modifier = Modifier.size(25.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = "设置",
                        color = Color(0xFFFFFCF7),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 11.dp, bottom = 6.dp),
        color = Color(0xA6000000),
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SettingsRowColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        shadowElevation = 1.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 18.dp),
        thickness = 0.7.dp,
        color = SettingsDividerColor,
    )
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = SettingsInk,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun SettingsPaperColorRow(
    style: NotePaperStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = notePaperPalette(style)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Surface(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(width = 34.dp, height = 24.dp),
            color = palette.paper,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.gutter),
        ) {}
        Text(
            text = style.displayName,
            modifier = Modifier.padding(start = 10.dp),
            color = SettingsInk,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = SettingsInk.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = SettingsMutedInk,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "›",
            color = SettingsMutedInk,
            fontSize = 25.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = SettingsInk,
            fontSize = 17.sp,
        )
        Text(
            text = value,
            color = SettingsMutedInk,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        values.forEach { value ->
            val active = value == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable { onSelect(value) },
                color = if (active) Color(0xFFFFE7BC) else Color(0xFFF1F1F1),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        text = label(value),
                        color = if (active) SettingsAccent else SettingsMutedInk,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = SettingsInk,
                fontSize = 17.sp,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                color = SettingsMutedInk,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
