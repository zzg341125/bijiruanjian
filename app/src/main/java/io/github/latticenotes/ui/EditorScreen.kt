package io.github.latticenotes.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
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
import io.github.latticenotes.util.AttachmentReferences
import io.github.latticenotes.util.isImageAttachment
import io.github.frozencaviar.weaveoffice.WeaveOfficeActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val EditorToolbar = Color(0xFF58483C)
private val EditorPaper = Color(0xFFFDF8EC)
private val EditorInk = Color(0xFF563C30)
private val EditorMutedInk = Color(0xFF87796B)
private val EditorRule = Color(0xFFD8C9B1)
private val EditorFormatBar = Color(0xFFFFF1D3)
private val EditorAccent = Color(0xFFC47B28)
private val EditorShareSheet = Color(0xFFF4F2EF)
private val editorClockFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val editorDateFormatter = DateTimeFormatter.ofPattern("M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long,
    repository: NotesRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val note by repository.observeNote(noteId).collectAsStateWithLifecycle(null)
    val attachments by repository.observeAttachments(noteId).collectAsStateWithLifecycle(emptyList())
    val folders by repository.observeFolders().collectAsStateWithLifecycle(emptyList())

    var loaded by remember(noteId) { mutableStateOf(false) }
    var title by remember(noteId) { mutableStateOf("") }
    var body by remember(noteId) { mutableStateOf("") }
    var format by remember(noteId) { mutableStateOf(NoteFormat.TEXT) }
    var preview by remember { mutableStateOf(false) }
    var shareSheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var formatMenuOpen by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var attachmentToDelete by remember { mutableStateOf<AttachmentEntity?>(null) }
    val history = remember(noteId) { EditorHistory() }
    val documentEditor = remember(noteId) { DocumentEditorController() }
    val documentScroll = remember(noteId) { ScrollState(0) }

    val activity = context.findActivity()
    DisposableEffect(activity) {
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = lightStatusBars ?: true
            controller?.isAppearanceLightNavigationBars = lightNavigationBars ?: true
        }
    }
    LaunchedEffect(shareSheetOpen, activity) {
        if (shareSheetOpen) {
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .isAppearanceLightStatusBars = false
            }
        }
    }

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
        val mergeKey = if (value.length < title.length) "title-delete" else "title-insert"
        history.record(EditorTextSnapshot(title, body), mergeKey)
        title = value
    }

    fun setBodyWithHistory(value: String) {
        if (value == body) return
        val mergeKey = if (value.length < body.length) "body-delete" else "body-insert"
        history.record(EditorTextSnapshot(title, body), mergeKey)
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
            var insertedImages = 0
            uris.forEach { uri ->
                val attachment = repository.addAttachment(noteId, uri) ?: return@forEach
                if (attachment.isImageAttachment()) {
                    insertedImages++
                }
            }
            if (insertedImages > 0) {
                Toast.makeText(
                    context,
                    "已插入 $insertedImages 张图片到便签正文",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val singleFileExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val exportedAttachments = repository.getAttachmentsForExport(noteId)
                    val html = SingleFileExporter.toHtml(
                        title = title,
                        body = body,
                        attachments = exportedAttachments,
                        iconDataUri = SingleFileExporter.appIconDataUri(context),
                    )
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
    val currentFolder = folders.firstOrNull { it.id == currentNote?.folderId }
    val characterCount = AttachmentReferences.plainText(body).count { !it.isWhitespace() }
    val imageAttachments = attachments.filter(AttachmentEntity::isImageAttachment)
    val fileAttachments = attachments.filterNot(AttachmentEntity::isImageAttachment)
    val editorBody = AttachmentReferences.plainText(body)


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            Surface(
                color = EditorToolbar,
                contentColor = Color(0xFFFFF8EA),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = ::goBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = ::undo,
                            enabled = history.canUndo,
                            modifier = Modifier
                                .width(58.dp)
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text(
                                text = "撤销",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = if (history.canUndo) {
                                    Color(0xFFFFF8EA)
                                } else {
                                    Color(0x66FFF8EA)
                                },
                            )
                        }
                        TextButton(
                            onClick = ::redo,
                            enabled = history.canRedo,
                            modifier = Modifier
                                .width(58.dp)
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text(
                                text = "重做",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = if (history.canRedo) {
                                    Color(0xFFFFF8EA)
                                } else {
                                    Color(0x66FFF8EA)
                                },
                            )
                        }
                        IconButton(
                            onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加图片或附件")
                        }
                        IconButton(
                            onClick = ::goBack,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                shareSheetOpen = true
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            SendPlaneGlyph(
                                modifier = Modifier
                                    .size(23.dp)
                                    .semantics { contentDescription = "分享" },
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (preview) "返回编辑" else "预览便签") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = {
                                        menuOpen = false
                                        preview = !preview
                                    },
                                )
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
                                    leadingIcon = { Icon(Icons.Outlined.Star, null) },
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
                                        val base = currentNote?.reminderAt
                                            ?: (System.currentTimeMillis() + 3_600_000)
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
                    }
                }
            }
        },
        bottomBar = {
            if (!preview) {
                EditorFormatToolbar(
                    modifier = Modifier.navigationBarsPadding(),
                    onFormat = { action ->
                        format = NoteFormat.MARKDOWN
                        documentEditor.apply(action)
                    },
                )
            }
        },
    ) { padding ->
        RuledPaper(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            lineSpacing = 32.dp,
            paperColor = EditorPaper,
            lineColor = EditorRule.copy(alpha = 0.7f),
            marginColor = EditorRule.copy(alpha = 0.78f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(documentScroll)
                    .padding(start = 54.dp, end = 18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .border(
                                width = 1.dp,
                                color = EditorRule,
                                shape = RoundedCornerShape(3.dp),
                            )
                            .clickable { folderDialog = true }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▤", color = EditorMutedInk, fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = currentFolder?.name ?: "全部便签",
                            modifier = Modifier.weight(1f),
                            color = EditorMutedInk,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("▾", color = EditorMutedInk, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = editorTimestamp(
                            currentNote?.updatedAt ?: System.currentTimeMillis(),
                        ),
                        color = EditorMutedInk,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = " | $characterCount",
                        color = EditorMutedInk,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    IconButton(
                        onClick = {
                            currentNote?.let {
                                scope.launch { repository.setStarred(it.id, !it.starred) }
                            }
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = if (currentNote?.starred == true) {
                                "取消加星"
                            } else {
                                "加星"
                            },
                            tint = if (currentNote?.starred == true) {
                                Color(0xFFD19B3D)
                            } else {
                                EditorRule
                            },
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .height(24.dp)
                                .background(
                                    color = Color(0xFFFFE8B2),
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFE2BC62),
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .clickable { formatMenuOpen = true }
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (format) {
                                    NoteFormat.TEXT -> "TXT"
                                    NoteFormat.MARKDOWN -> "MD"
                                    NoteFormat.CHECKLIST -> "待办"
                                },
                                color = Color(0xFFB06B1C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DropdownMenu(
                            expanded = formatMenuOpen,
                            onDismissRequest = { formatMenuOpen = false },
                        ) {
                            NoteFormat.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                NoteFormat.TEXT -> "纯文本"
                                                NoteFormat.MARKDOWN -> "Markdown"
                                                NoteFormat.CHECKLIST -> "待办清单"
                                            },
                                        )
                                    },
                                    onClick = {
                                        format = mode
                                        preview = false
                                        formatMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Text(" ▾", color = EditorRule, fontSize = 11.sp)
                }

                if (!preview) {
                    BasicTextField(
                        value = title,
                        onValueChange = ::setTitleWithHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clipToBounds(),
                        textStyle = TextStyle(
                            color = EditorInk,
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(EditorInk),
                        decorationBox = { innerTextField ->
                            if (title.isBlank()) {
                                Text(
                                    text = "标题",
                                    color = EditorMutedInk.copy(alpha = 0.55f),
                                    fontSize = 24.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            innerTextField()
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                    ) {
                        if (editorBody.isBlank()) {
                            Text(
                                text = "开始记录…",
                                color = EditorMutedInk.copy(alpha = 0.52f),
                                fontSize = 20.sp,
                                lineHeight = 32.sp,
                            )
                        }
                        InlineDocumentEditor(
                            value = editorBody,
                            onValueChange = ::setBodyWithHistory,
                            controller = documentEditor,
                            textColor = EditorInk,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EmbeddedDocumentImages(
                        attachments = imageAttachments,
                        onOpen = { openAttachment(context, it) },
                        onDelete = { attachmentToDelete = it },
                    )
                } else {
                    Text(
                        text = title.ifBlank { "无标题" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        color = EditorInk,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MarkdownPreview(
                        markdown = editorBody,
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                    EmbeddedDocumentImages(
                        attachments = imageAttachments,
                        onOpen = { openAttachment(context, it) },
                        onDelete = { attachmentToDelete = it },
                    )
                }

                if (fileAttachments.isNotEmpty()) {
                    AttachmentStrip(
                        attachments = fileAttachments,
                        onOpen = { openAttachment(context, it) },
                        onDelete = { attachmentToDelete = it },
                    )
                }
            }
        }
    }

    if (shareSheetOpen) {
        val shareText = noteShareText(title, body)
        ModalBottomSheet(
            onDismissRequest = { shareSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = EditorShareSheet,
            contentColor = Color(0xFF4D4B48),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            dragHandle = null,
        ) {
            ShareActionSheet(
                onClose = { shareSheetOpen = false },
                onCopyText = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("曲阳便签", shareText))
                    shareSheetOpen = false
                    Toast.makeText(context, "文字已复制", Toast.LENGTH_SHORT).show()
                },
                onShareText = {
                    shareSheetOpen = false
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            "以文字形式分享",
                        ),
                    )
                },
                onShareImage = {
                    shareSheetOpen = false
                    ShareImage.share(
                        context,
                        title,
                        AttachmentReferences.plainText(body),
                    )
                },
                onSendEmail = {
                    shareSheetOpen = false
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        title.ifBlank { "曲阳便签" },
                                    )
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                },
                                "发送邮件",
                            ),
                        )
                    }.onFailure {
                        Toast.makeText(context, "未找到可用的邮件应用", Toast.LENGTH_SHORT).show()
                    }
                },
            )
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
private fun ShareActionSheet(
    onClose: () -> Unit,
    onCopyText: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onSendEmail: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "请选择操作",
                color = Color(0xFF4F4D4B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color(0xFF7A7875),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ShareActionButton("复制文字", onCopyText)
        Spacer(Modifier.height(14.dp))
        ShareActionButton("以文字形式分享", onShareText)
        Spacer(Modifier.height(14.dp))
        ShareActionButton("以图片形式分享", onShareImage)
        Spacer(Modifier.height(14.dp))
        ShareActionButton("发送邮件", onSendEmail)
    }
}

@Composable
private fun ShareActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick),
        color = Color(0xFFFFFEFC),
        contentColor = Color(0xFF625F5C),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SendPlaneGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color(0xFFFFF8EA)
        val stroke = 1.8.dp.toPx()
        val plane = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            lineTo(size.width * 0.92f, size.height * 0.08f)
            lineTo(size.width * 0.68f, size.height * 0.9f)
            lineTo(size.width * 0.47f, size.height * 0.61f)
            close()
        }
        drawPath(
            path = plane,
            color = color,
            style = Stroke(
                width = stroke,
                join = StrokeJoin.Round,
            ),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.47f, size.height * 0.61f),
            end = Offset(size.width * 0.92f, size.height * 0.08f),
            strokeWidth = stroke,
        )
    }
}

private fun noteShareText(title: String, body: String): String =
    title.ifBlank { "无标题" } + "\n\n" + AttachmentReferences.plainText(body)

@Composable
private fun EditorFormatToolbar(
    modifier: Modifier = Modifier,
    onFormat: (DocumentFormatAction) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EditorFormatBar,
        contentColor = EditorAccent,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorFormatButton(
                label = "标题",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.HEADING) },
            ) {
                Text(
                    text = "H",
                    color = EditorAccent,
                    fontSize = 25.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            EditorToolbarDivider()
            EditorFormatButton(
                label = "居中",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.CENTER) },
            ) {
                CenterLinesGlyph()
            }
            EditorToolbarDivider()
            EditorFormatButton(
                label = "列表",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.BULLET) },
            ) {
                ListGlyph()
            }
            EditorToolbarDivider()
            EditorFormatButton(
                label = "粗体",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.BOLD) },
            ) {
                Text(
                    text = "B",
                    color = EditorAccent,
                    fontSize = 25.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            EditorToolbarDivider()
            EditorFormatButton(
                label = "引用",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.QUOTE) },
            ) {
                Text(
                    text = "❝",
                    color = EditorAccent,
                    fontSize = 25.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            EditorToolbarDivider()
            EditorFormatButton(
                label = "待办事项",
                modifier = Modifier.weight(1.25f),
                onClick = { onFormat(DocumentFormatAction.TODO) },
            ) {
                Text(
                    text = "☑",
                    color = EditorAccent,
                    fontSize = 24.sp,
                    lineHeight = 25.sp,
                )
            }
        }
    }
}

@Composable
private fun EditorFormatButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(width = 28.dp, height = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        Text(
            text = label,
            color = EditorAccent,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditorToolbarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(42.dp)
            .background(Color(0xFFD7B976).copy(alpha = 0.7f)),
    )
}

@Composable
private fun CenterLinesGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val color = EditorAccent
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.2f), Offset(size.width * 0.84f, size.height * 0.2f), stroke)
        drawLine(color, Offset(size.width * 0.3f, size.height * 0.52f), Offset(size.width * 0.7f, size.height * 0.52f), stroke)
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.84f), stroke)
    }
}

@Composable
private fun ListGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val color = EditorAccent
        val stroke = 2.dp.toPx()
        listOf(0.2f, 0.5f, 0.8f).forEach { y ->
            drawCircle(color, radius = 1.7.dp.toPx(), center = Offset(size.width * 0.18f, size.height * y))
            drawLine(
                color = color,
                start = Offset(size.width * 0.34f, size.height * y),
                end = Offset(size.width * 0.84f, size.height * y),
                strokeWidth = stroke,
            )
        }
    }
}

private fun editorTimestamp(value: Long): String {
    val dateTime = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    val day = if (dateTime.toLocalDate() == LocalDate.now()) {
        "今天"
    } else {
        dateTime.format(editorDateFormatter)
    }
    return "$day ${dateTime.format(editorClockFormatter)}"
}

private fun openAttachment(context: Context, attachment: AttachmentEntity) {
    try {
        val path = Uri.parse(attachment.uri).path ?: error("附件路径无效")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            File(path),
        )
        val extension = attachment.name.substringAfterLast('.', "").lowercase()
        val mimeType = attachment.mimeType
            ?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        if (
            (extension == "docx" || extension == "xlsx") &&
            Build.SUPPORTED_ABIS.contains("arm64-v8a")
        ) {
            try {
                File(context.filesDir, "update-check-off").writeText("1")
                context.startActivity(
                    Intent(context, WeaveOfficeActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    },
                )
                return
            } catch (_: Throwable) {
                // Fall through to an installed Office app if the embedded engine cannot start.
            }
        }
        val mimeCandidates = listOf(mimeType, "application/octet-stream", "*/*").distinct()
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            for (candidateType in mimeCandidates) {
                val intent = Intent(action).apply {
                    setDataAndType(uri, candidateType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // Try the next MIME or action.
                }
            }
        }
        throw ActivityNotFoundException()
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "没有安装可打开 ${attachment.name} 的应用。请安装 WPS Office 或 Microsoft 365。",
            Toast.LENGTH_LONG,
        ).show()
    } catch (it: Throwable) {
        Toast.makeText(context, "无法打开附件：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EmbeddedDocumentImages(
    attachments: List<AttachmentEntity>,
    onOpen: (AttachmentEntity) -> Unit,
    onDelete: (AttachmentEntity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        attachments.forEach { attachment ->
            AsyncImage(
                model = File(Uri.parse(attachment.uri).path.orEmpty()),
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(min = 120.dp, max = 720.dp)
                    .padding(vertical = 8.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = EditorMutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onOpen(attachment) }) { Text("打开") }
                IconButton(
                    onClick = { onDelete(attachment) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "删除图片",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
