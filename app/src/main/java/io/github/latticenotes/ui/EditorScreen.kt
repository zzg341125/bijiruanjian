package io.github.latticenotes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.latticenotes.backup.SingleFileExporter
import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.reminder.ReminderScheduler
import io.github.latticenotes.share.ShareImage
import io.github.latticenotes.util.formatTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long,
    repository: NotesRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val note by repository.observeNote(noteId).collectAsStateWithLifecycle(null)
    val attachments by repository.observeAttachments(noteId).collectAsStateWithLifecycle(emptyList())
    val folders by repository.observeFolders().collectAsStateWithLifecycle(emptyList())

    var loaded by remember(noteId) { mutableStateOf(false) }
    var title by remember(noteId) { mutableStateOf("") }
    var body by remember(noteId) { mutableStateOf("") }
    var format by remember(noteId) { mutableStateOf(NoteFormat.TEXT) }
    var preview by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var attachmentToDelete by remember { mutableStateOf<AttachmentEntity?>(null) }
    val history = remember(noteId) { EditorHistory() }

    LaunchedEffect(note) {
        val value = note ?: return@LaunchedEffect
        if (!loaded) {
            title = value.title
            body = value.body
            format = runCatching { NoteFormat.valueOf(value.format) }.getOrDefault(NoteFormat.TEXT)
            loaded = true
        }
    }

    val currentNote = note
    LaunchedEffect(loaded, title, body, format) {
        if (!loaded || currentNote == null) return@LaunchedEffect
        delay(500)
        repository.save(
            currentNote.copy(
                title = title,
                body = body,
                format = format.name,
            ),
        )
    }

    fun saveNow() {
        currentNote?.let {
            scope.launch {
                repository.save(it.copy(title = title, body = body, format = format.name))
            }
        }
    }

    fun setTitleWithHistory(value: String) {
        if (value == title) return
        history.record(EditorTextSnapshot(title, body))
        title = value
    }

    fun setBodyWithHistory(value: String) {
        if (value == body) return
        history.record(EditorTextSnapshot(title, body))
        body = value
    }

    fun undo() {
        if (!history.canUndo) return
        val previous = history.undo(EditorTextSnapshot(title, body)) ?: return
        title = previous.title
        body = previous.body
    }

    fun redo() {
        if (!history.canRedo) return
        val next = history.redo(EditorTextSnapshot(title, body)) ?: return
        title = next.title
        body = next.body
    }

    fun goBack() {
        saveNow()
        onBack()
    }

    BackHandler(onBack = ::goBack)

    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            uris.forEach { repository.addAttachment(noteId, it) }
        }
    }
    val singleFileExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val exportedAttachments = repository.getAttachmentsForExport(noteId)
                    val html = SingleFileExporter.toHtml(title, body, exportedAttachments)
                    context.contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(html) }
                        ?: error("无法写入导出文件")
                }.onSuccess {
                    Toast.makeText(context, "单文件 HTML 已导出", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::goBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        text = formatTimestamp(currentNote?.updatedAt ?: System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = ::undo, enabled = history.canUndo) {
                        Text("↶", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = ::redo, enabled = history.canRedo) {
                        Text("↷", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = { preview = !preview }) {
                        Text(if (preview) "编辑" else "预览")
                    }
                    IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加附件")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (currentNote?.pinned == true) "取消置顶" else "置顶") },
                                leadingIcon = { Icon(Icons.Outlined.Star, null) },
                                onClick = {
                                    menuOpen = false
                                    currentNote?.let {
                                        scope.launch { repository.setPinned(it.id, !it.pinned) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentNote?.starred == true) "取消加星" else "加星") },
                                leadingIcon = {
                                    Icon(
                                        if (currentNote?.starred == true) {
                                            Icons.Outlined.Star
                                        } else {
                                            Icons.Outlined.Star
                                        },
                                        null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    currentNote?.let {
                                        scope.launch { repository.setStarred(it.id, !it.starred) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("文件夹") },
                                leadingIcon = { Icon(Icons.Outlined.List, null) },
                                onClick = {
                                    menuOpen = false
                                    folderDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("标签") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = {
                                    menuOpen = false
                                    tagDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentNote?.reminderAt != null) "修改提醒" else "设置提醒") },
                                leadingIcon = { Icon(Icons.Outlined.DateRange, null) },
                                onClick = {
                                    menuOpen = false
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermission.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    }
                                    val base = currentNote?.reminderAt ?: (System.currentTimeMillis() + 3_600_000)
                                    val calendar = Calendar.getInstance().apply { timeInMillis = base }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val date = Calendar.getInstance().apply {
                                                set(year, month, day, 9, 0, 0)
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    date.set(Calendar.HOUR_OF_DAY, hour)
                                                    date.set(Calendar.MINUTE, minute)
                                                    val at = date.timeInMillis
                                                    currentNote?.let { note ->
                                                        scope.launch {
                                                            repository.setReminder(note.id, at)
                                                            ReminderScheduler.schedule(
                                                                context,
                                                                note.id,
                                                                title,
                                                                at,
                                                            )
                                                        }
                                                    }
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                true,
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                    ).show()
                                },
                            )
                            if (currentNote?.reminderAt != null) {
                                DropdownMenuItem(
                                    text = { Text("清除提醒") },
                                    leadingIcon = { Icon(Icons.Outlined.Close, null) },
                                    onClick = {
                                        menuOpen = false
                                        currentNote?.let {
                                            scope.launch { repository.setReminder(it.id, null) }
                                            ReminderScheduler.cancel(context, it.id)
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("分享文本") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    val text = title.ifBlank { "无标题" } + "\n\n" + body
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            },
                                            "分享笔记",
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享长图") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    ShareImage.share(context, title, body)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出单文件 HTML") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    singleFileExporter.launch(
                                        "${title.ifBlank { "曲阳便签" }}.html",
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("移到回收站") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    deleteConfirm = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        RuledPaper(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 54.dp, end = 18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NoteFormat.entries.forEach { mode ->
                        FilterChip(
                            selected = format == mode,
                            onClick = {
                                format = mode
                                preview = false
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        NoteFormat.TEXT -> "文本"
                                        NoteFormat.MARKDOWN -> "Markdown"
                                        NoteFormat.CHECKLIST -> "待办"
                                    },
                                )
                            },
                            leadingIcon = {
                                if (mode == NoteFormat.CHECKLIST) {
                                    Icon(Icons.Outlined.List, null, Modifier.size(18.dp))
                                }
                            },
                        )
                    }
                }

                if (!preview) {
                    TextField(
                        value = title,
                        onValueChange = ::setTitleWithHistory,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("标题", style = MaterialTheme.typography.headlineMedium) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = false,
                    )
                    TextField(
                        value = body,
                        onValueChange = ::setBodyWithHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("开始记录…") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    )
                } else {
                    Text(
                        text = title.ifBlank { "无标题" },
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MarkdownPreview(
                        markdown = body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                if (format != NoteFormat.TEXT && !preview) {
                    FormattingBar(
                        onInsert = { prefix, suffix, multiline ->
                            setBodyWithHistory(if (multiline) {
                                if (body.isBlank() || body.endsWith('\n')) "$body$prefix"
                                else "$body\n$prefix"
                            } else {
                                "$body$prefix${suffix.orEmpty()}"
                            })
                        },
                    )
                }
                if (attachments.isNotEmpty()) {
                    AttachmentStrip(
                        attachments = attachments,
                        onOpen = { attachment ->
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    File(Uri.parse(attachment.uri).path!!),
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, attachment.mimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }
                        },
                        onDelete = { attachmentToDelete = it },
                    )
                }
                Text(
                    text = "已自动保存 · ${attachments.size} 个附件",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF676B66),
                )
            }
        }
    }

    if (folderDialog) {
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text("选择文件夹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            currentNote?.let {
                                scope.launch { repository.moveToFolder(it.id, null) }
                            }
                            folderDialog = false
                        },
                    ) { Text("无文件夹") }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                currentNote?.let {
                                    scope.launch { repository.moveToFolder(it.id, folder.id) }
                                }
                                folderDialog = false
                            },
                        ) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderDialog = false }) { Text("取消") }
            },
        )
    }
    if (tagDialog && currentNote != null) {
        var tags by remember(currentNote?.id) { mutableStateOf(currentNote?.tags.orEmpty()) }
        AlertDialog(
            onDismissRequest = { tagDialog = false },
            title = { Text("标签") },
            text = {
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("逗号分隔") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.setTags(noteId, tags) }
                        tagDialog = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { tagDialog = false }) { Text("取消") }
            },
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("移到回收站？") },
            text = { Text("你可以稍后从回收站恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.moveToTrash(noteId) }
                        deleteConfirm = false
                        onBack()
                    },
                ) { Text("移到回收站") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("取消") }
            },
        )
    }
    attachmentToDelete?.let { attachment ->
        AlertDialog(
            onDismissRequest = { attachmentToDelete = null },
            title = { Text("删除附件？") },
            text = { Text("确定删除“${attachment.name}”吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.removeAttachment(attachment) }
                        attachmentToDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { attachmentToDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FormattingBar(
    onInsert: (prefix: String, suffix: String?, multiline: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = { onInsert("# ", null, true) }) { Text("H1") }
        OutlinedButton(onClick = { onInsert("**", "**", false) }) { Text("B") }
        OutlinedButton(onClick = { onInsert("- ", null, true) }) { Text("• 列表") }
        OutlinedButton(onClick = { onInsert("- [ ] ", null, true) }) { Text("☐ 待办") }
        OutlinedButton(onClick = { onInsert("[链接文字](https://)", null, false) }) {
            Text("链接")
        }
        OutlinedButton(onClick = { onInsert("`代码`", null, false) }) { Text("代码") }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<AttachmentEntity>,
    onOpen: (AttachmentEntity) -> Unit,
    onDelete: (AttachmentEntity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        attachments.forEach { attachment ->
            Card {
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (attachment.mimeType?.startsWith("image/") == true) {
                        AsyncImage(
                            model = File(Uri.parse(attachment.uri).path ?: ""),
                            contentDescription = attachment.name,
                            modifier = Modifier
                                .size(104.dp)
                                .background(Color(0xFFE8E1D4)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier
                                .size(104.dp)
                                .padding(28.dp),
                        )
                    }
                    Text(
                        text = attachment.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row {
                        TextButton(onClick = { onOpen(attachment) }) { Text("打开") }
                        IconButton(onClick = { onDelete(attachment) }) {
                            Icon(Icons.Outlined.Close, "删除附件", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
