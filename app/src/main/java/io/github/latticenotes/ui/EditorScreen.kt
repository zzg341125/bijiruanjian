package io.github.latticenotes.ui

import android.Manifest
import androidx.annotation.DrawableRes
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
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.latticenotes.R
import io.github.latticenotes.backup.SingleFileExporter
import io.github.latticenotes.data.AppSettings
import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.reminder.ReminderScheduler
import io.github.latticenotes.share.ShareImage
import io.github.latticenotes.util.AttachmentReferences
import io.github.latticenotes.util.DocumentBodyBlock
import io.github.latticenotes.util.ImageEditing
import io.github.latticenotes.util.displayCaption
import io.github.latticenotes.util.isImageAttachment
import io.github.latticenotes.util.firstEmbeddedDocumentAttachment
import io.github.latticenotes.util.isEmbeddedDocument
import com.prempoddar.oaf.MainActivity as OafMainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.abs

private val EditorToolbarInk = Color(0xFFFFFCF7)
private val EditorAccent = Color(0xFFC47B28)
private val EditorShareSheet = Color(0xFFF4F2EF)
private val EditorMutedInk = Color(0xFF897966)
private val EditorRule = Color(0xFFDED4C5)
private val EditorFormatBar = Color(0xFFFBF7ED)
private val EditorFormatInk = Color(0xFF4D4A46)
private val editorClockFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val editorDateFormatter = DateTimeFormatter.ofPattern("M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long,
    repository: NotesRepository,
    settings: AppSettings,
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
    var insertImageMenuOpen by remember { mutableStateOf(false) }
    var pendingImageInsertOffset by remember(noteId) { mutableStateOf<Int?>(null) }
    var imagePreview by remember { mutableStateOf<AttachmentEntity?>(null) }
    var selectedImageId by remember(noteId) { mutableStateOf<Long?>(null) }
    var captionTarget by remember { mutableStateOf<AttachmentEntity?>(null) }
    var cropTarget by remember { mutableStateOf<AttachmentEntity?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var importingImages by remember { mutableStateOf(false) }
    var galleryPickerOpen by remember { mutableStateOf(false) }
    var autoOpenedOffice by remember(noteId) { mutableStateOf(false) }
    val history = remember(noteId) { EditorHistory() }
    var activeDocumentBlockKey by remember(noteId) { mutableStateOf<String?>(null) }
    val documentControllers = remember(noteId) {
        mutableMapOf<String, DocumentEditorController>()
    }
    val bodyFocusRequester = remember(noteId) { FocusRequester() }
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

    fun activeDocumentController(): DocumentEditorController? =
        activeDocumentBlockKey?.let(documentControllers::get)
            ?: documentControllers["text-start"]

    fun currentImageInsertionOffset(): Int {
        val block = activeDocumentBlockKey?.let { key ->
            AttachmentReferences.documentBlocks(body).firstOrNull {
                it.key == key && !it.isImage
            }
        } ?: return body.length
        val selection = documentControllers[block.key]
            ?.textFieldValue
            ?.selection
            ?.end
            ?: block.text.length
        return block.start + block.leadingNewlines.length +
            selection.coerceIn(0, block.text.length)
    }

    fun replaceDocumentBlock(block: DocumentBodyBlock, editedText: String) {
        if (block.isImage || block.text == editedText) return
        setBodyWithHistory(
            body.replaceRange(
                block.start,
                block.end,
                block.replacementText(editedText),
            ),
        )
    }

    fun prepareImageInsertion() {
        pendingImageInsertOffset = currentImageInsertionOffset()
        insertImageMenuOpen = true
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

    val imageAttachments = ImageEditing.orderedImages(
        body,
        attachments.filter(AttachmentEntity::isImageAttachment),
    )

    fun importImages(uris: List<Uri>) {
        if (importingImages) return
        if (uris.isEmpty()) {
            pendingImageInsertOffset = null
            return
        }
        val remaining = (9 - imageAttachments.size).coerceAtLeast(0)
        if (remaining == 0) {
            pendingImageInsertOffset = null
            Toast.makeText(context, "每条便签最多插入 9 张图片", Toast.LENGTH_SHORT).show()
            return
        }
        val insertionOffset = pendingImageInsertOffset ?: body.length
        importingImages = true
        scope.launch {
            try {
                val references = mutableListOf<String>()
                uris.take(remaining).forEach { uri ->
                    val attachment = repository.addAttachment(noteId, uri) ?: return@forEach
                    if (attachment.isImageAttachment()) {
                        references += AttachmentReferences.imageMarkdown(
                            attachment.id,
                            attachment.displayCaption(),
                        )
                    }
                }
                if (references.isNotEmpty()) {
                    setBodyWithHistory(
                        AttachmentReferences.insertAllAt(
                            body,
                            insertionOffset,
                            references,
                        ),
                    )
                    Toast.makeText(
                        context,
                        "已插入 ${references.size} 张图片到便签",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                if (uris.size > remaining) {
                    Toast.makeText(
                        context,
                        "每条便签最多插入 9 张图片",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                importingImages = false
                pendingImageInsertOffset = null
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            uris.forEach { uri ->
                repository.addAttachment(noteId, uri)
            }
        }
    }

    LaunchedEffect(
        loaded,
        importingImages,
        attachments.map { it.id },
    ) {
        if (!loaded || importingImages || imageAttachments.isEmpty()) return@LaunchedEffect
        val referenced = AttachmentReferences.referencedImageIds(body)
        val missing = imageAttachments.filterNot { it.id in referenced }
        if (missing.isNotEmpty()) {
            setBodyWithHistory(
                AttachmentReferences.appendAllToBody(
                    body,
                    missing.map {
                        AttachmentReferences.imageMarkdown(it.id, it.displayCaption())
                    },
                ),
            )
        }
    }

    val systemImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris ->
        importImages(uris)
    }
    val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            galleryPickerOpen = true
        } else {
            systemImagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    fun openGalleryPicker() {
        val remaining = (9 - imageAttachments.size).coerceAtLeast(0)
        if (remaining == 0) {
            pendingImageInsertOffset = null
            Toast.makeText(context, "每条便签最多插入 9 张图片", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(context, galleryPermission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            galleryPickerOpen = true
        } else {
            galleryPermissionLauncher.launch(galleryPermission)
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        importFiles(uris)
    }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            importImages(listOf(uri))
        } else {
            pendingImageInsertOffset = null
        }
    }
    fun launchCamera() {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "camera-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        pendingCameraUri = uri
        cameraPicker.launch(uri)
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
    val fileAttachments = attachments.filterNot(AttachmentEntity::isImageAttachment)
    val officeAttachment = fileAttachments.firstEmbeddedDocumentAttachment()
    val documentBlocks = remember(body) {
        AttachmentReferences.documentBlocks(body)
    }
    val editorLineSpacing = EditorTextMetrics.lineHeightDp(LocalDensity.current.fontScale)
    val paperStyle = NotePaperStyle.fromStorage(settings.notePaperStyle)
    val paper = notePaperPalette(paperStyle)

    LaunchedEffect(noteId, officeAttachment?.id) {
        if (!autoOpenedOffice && officeAttachment != null) {
            autoOpenedOffice = true
            currentNote?.let {
                repository.save(
                    it.copy(
                        title = title,
                        body = body,
                        format = format.name,
                    ),
                )
            }
            openAttachment(context, officeAttachment)
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            Surface(
                color = Color.Transparent,
                contentColor = EditorToolbarInk,
                shadowElevation = 0.dp,
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.smartisan_title_bar),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                    Column(Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EditorTitleButton(
                                    contentDescription = "返回",
                                    onClick = ::goBack,
                                ) {
                                    Icon(
                                        Icons.Outlined.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(25.dp),
                                    )
                                }
                                EditorHistoryButton(
                                    label = "撤销",
                                    enabled = history.canUndo,
                                    onClick = ::undo,
                                )
                                EditorHistoryButton(
                                    label = "重做",
                                    enabled = history.canRedo,
                                    onClick = ::redo,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { folderDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = currentFolder?.name ?: "全部便签",
                                        color = EditorToolbarInk,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = " ▾",
                                        color = EditorToolbarInk.copy(alpha = 0.75f),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EditorTitleButton(
                                    contentDescription = "插入图片",
                                    onClick = ::prepareImageInsertion,
                                ) {
                                    ImageToolGlyph(
                                        glyph = ImageGlyph.GALLERY,
                                        color = EditorToolbarInk,
                                        modifier = Modifier.size(24.dp),
                                        contentDescription = "插入图片",
                                    )
                                }
                                EditorTitleButton(
                                    contentDescription = "完成",
                                    onClick = ::goBack,
                                ) {
                                    Text(
                                        text = "✓",
                                        color = EditorToolbarInk,
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Light,
                                    )
                                }
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "更多",
                                    tint = EditorToolbarInk,
                                )
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
                                    text = { Text("分享") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = {
                                        menuOpen = false
                                        keyboardController?.hide()
                                        shareSheetOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("添加附件") },
                                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                    onClick = {
                                        menuOpen = false
                                        filePicker.launch(arrayOf("*/*"))
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
                                        val text = noteShareText(title, body)
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
                                        ShareImage.share(
                                            context,
                                            title,
                                            body,
                                            imageAttachments,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("导出单文件 HTML") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = {
                                        menuOpen = false
                                        singleFileExporter.launch(
                                            "${title.ifBlank { "无标题" }}.html",
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
                    Surface(color = paper.paper, shadowElevation = 1.dp) {
                        EditorInfoBar(
                            folderName = currentFolder?.name ?: "全部便签",
                            updatedAt = currentNote?.updatedAt ?: System.currentTimeMillis(),
                            characterCount = characterCount,
                            starred = currentNote?.starred == true,
                            format = format,
                            menuExpanded = formatMenuOpen,
                            lineHeight = editorLineSpacing,
                            onFolderClick = { folderDialog = true },
                            onStarClick = {
                                currentNote?.let {
                                    scope.launch { repository.setStarred(it.id, !it.starred) }
                                }
                            },
                            onMenuExpandedChange = { formatMenuOpen = it },
                            onSelectFormat = {
                                format = it
                                preview = false
                                formatMenuOpen = false
                            },
                        )
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
                        if (activeDocumentController()?.apply(action) == true) {
                            format = NoteFormat.MARKDOWN
                        }
                    },
                )
            }
        },
    ) { padding ->
        RuledPaper(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .padding(padding),
            paperColor = paper.paper,
            marginColor = paper.rule.copy(alpha = 0.92f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(documentScroll),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = pageHeight)
                            .drawBehind {
                                val step = editorLineSpacing.toPx()
                                if (step <= 0f) return@drawBehind
                                var y = step
                                while (y < size.height) {
                                    drawLine(
                                        color = paper.rule.copy(alpha = 0.94f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.35f,
                                    )
                                    y += step
                                }
                            }
                            .padding(start = 39.dp, end = 18.dp),
                    ) {
                if (!preview) {
                    BasicTextField(
                        value = title,
                        onValueChange = {
                            setTitleWithHistory(it.replace('\n', ' ').replace('\r', ' '))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = editorLineSpacing)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.key == Key.Enter &&
                                    event.type == KeyEventType.KeyDown
                                ) {
                                    bodyFocusRequester.requestFocus()
                                    keyboardController?.show()
                                    true
                                } else {
                                    false
                                }
                            },
                        textStyle = TextStyle(
                            color = paper.ink,
                            fontSize = EditorTextMetrics.titleFontSize,
                            lineHeight = EditorTextMetrics.lineHeight,
                            lineHeightStyle = EditorTextMetrics.lineHeightStyle,
                            fontWeight = FontWeight.Bold,
                        ),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                bodyFocusRequester.requestFocus()
                                keyboardController?.show()
                            },
                        ),
                        cursorBrush = SolidColor(paper.ink),
                        decorationBox = { innerTextField ->
                            if (title.isBlank()) {
                                Text(
                                    text = "标题",
                                    style = TextStyle(
                                        color = paper.mutedInk.copy(alpha = 0.62f),
                                        fontSize = EditorTextMetrics.titleFontSize,
                                        lineHeight = EditorTextMetrics.lineHeight,
                                        lineHeightStyle = EditorTextMetrics.lineHeightStyle,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                            innerTextField()
                        },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                    ) {
                        val imagesById = imageAttachments.associateBy { it.id }
                        val bodyIsBlank = AttachmentReferences.plainText(body).isBlank()
                        documentBlocks.forEachIndexed { blockIndex, block ->
                            key(block.key) {
                                val imageId = block.imageId
                                if (imageId != null) {
                                    val attachment = imagesById[imageId]
                                    if (attachment != null) {
                                        val imageIndex = documentBlocks
                                            .take(blockIndex)
                                            .count { it.isImage }
                                        EmbeddedDocumentImage(
                                            attachment = attachment,
                                            editable = true,
                                            selected = selectedImageId == attachment.id,
                                            canMoveUp = imageIndex > 0,
                                            canMoveDown = imageIndex < imageAttachments.lastIndex,
                                            onSelect = {
                                                selectedImageId =
                                                    if (selectedImageId == attachment.id) {
                                                        null
                                                    } else {
                                                        attachment.id
                                                    }
                                            },
                                            onPreview = { imagePreview = attachment },
                                            onCaption = { captionTarget = attachment },
                                            onCrop = { cropTarget = attachment },
                                            onSave = {
                                                scope.launch {
                                                    val saved = repository
                                                        .saveImageToGallery(attachment)
                                                    Toast.makeText(
                                                        context,
                                                        if (saved) {
                                                            "图片已保存到相册"
                                                        } else {
                                                            "保存图片失败"
                                                        },
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            },
                                            onMove = { direction ->
                                                scope.launch {
                                                    val reordered = ImageEditing.move(
                                                        imageAttachments,
                                                        direction,
                                                        attachment.id,
                                                    )
                                                    setBodyWithHistory(
                                                        AttachmentReferences.reorderReferences(
                                                            body,
                                                            reordered.map { it.id },
                                                        ),
                                                    )
                                                    repository.reorderAttachments(
                                                        noteId,
                                                        reordered.map { it.id },
                                                    )
                                                }
                                            },
                                            onDelete = { attachmentToDelete = attachment },
                                        )
                                    }
                                } else {
                                    val controller = documentControllers.getOrPut(block.key) {
                                        DocumentEditorController()
                                    }
                                    val firstTextBlock = documentBlocks
                                        .take(blockIndex)
                                        .none { !it.isImage }
                                    val editorModifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (firstTextBlock) {
                                                Modifier.focusRequester(bodyFocusRequester)
                                            } else {
                                                Modifier
                                            },
                                        )
                                    Box {
                                        if (bodyIsBlank && firstTextBlock) {
                                            Text(
                                                text = "开始记录…",
                                                style = TextStyle(
                                                    color = paper.mutedInk.copy(alpha = 0.58f),
                                                    fontSize = EditorTextMetrics.bodyFontSize,
                                                    lineHeight = EditorTextMetrics.lineHeight,
                                                    lineHeightStyle =
                                                        EditorTextMetrics.lineHeightStyle,
                                                ),
                                            )
                                        }
                                        InlineDocumentEditor(
                                            value = block.text,
                                            onValueChange = {
                                                replaceDocumentBlock(block, it)
                                            },
                                            controller = controller,
                                            textColor = paper.ink,
                                            modifier = editorModifier,
                                            onFocused = {
                                                activeDocumentBlockKey = block.key
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = title.ifBlank { "无标题" },
                        modifier = Modifier
                            .fillMaxWidth(),
                        color = paper.ink,
                        fontSize = EditorTextMetrics.titleFontSize,
                        lineHeight = EditorTextMetrics.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val imagesById = imageAttachments.associateBy { it.id }
                    documentBlocks.forEach { block ->
                        val imageId = block.imageId
                        if (imageId != null) {
                            val attachment = imagesById[imageId]
                            if (attachment != null) {
                                EmbeddedDocumentImage(
                                    attachment = attachment,
                                    editable = false,
                                    selected = false,
                                    canMoveUp = false,
                                    canMoveDown = false,
                                    onSelect = { imagePreview = attachment },
                                    onPreview = { imagePreview = attachment },
                                    onCaption = {},
                                    onCrop = {},
                                    onSave = {},
                                    onMove = {},
                                    onDelete = { attachmentToDelete = attachment },
                                )
                            }
                        } else if (block.text.isNotBlank()) {
                            MarkdownPreview(
                                markdown = block.text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (fileAttachments.isNotEmpty()) {
                    AttachmentStrip(
                        attachments = fileAttachments,
                        onOpen = { attachment ->
                            if (attachment.isEmbeddedDocument()) {
                                scope.launch {
                                    currentNote?.let {
                                        repository.save(
                                            it.copy(
                                                title = title,
                                                body = body,
                                                format = format.name,
                                            ),
                                        )
                                    }
                                    openAttachment(context, attachment)
                                    onBack()
                                }
                            } else {
                                openAttachment(context, attachment)
                            }
                        },
                        onDelete = { attachmentToDelete = it },
                    )
                }
                    }
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
                        .setPrimaryClip(
                            ClipData.newPlainText(
                                title.ifBlank { "无标题" },
                                shareText,
                            ),
                        )
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
                        body,
                        imageAttachments,
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
                                        title.ifBlank { "无标题" },
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
                        scope.launch {
                            if (attachment.isImageAttachment()) {
                                val updatedBody = AttachmentReferences.removeReference(
                                    body,
                                    attachment.id,
                                )
                                if (updatedBody != body) {
                                    setBodyWithHistory(updatedBody)
                                }
                                if (selectedImageId == attachment.id) {
                                    selectedImageId = null
                                }
                            }
                            repository.removeAttachment(attachment)
                        }
                        attachmentToDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { attachmentToDelete = null }) { Text("取消") }
            },
        )
    }
    captionTarget?.let { attachment ->
        ImageCaptionDialog(
            attachment = attachment,
            onDismiss = { captionTarget = null },
            onSave = { caption ->
                scope.launch {
                    repository.updateAttachmentCaption(attachment.id, noteId, caption)
                    captionTarget = null
                }
            },
        )
    }
    cropTarget?.let { attachment ->
        ImageCropDialog(
            attachment = attachment,
            onDismiss = { cropTarget = null },
            onCrop = { left, top, right, bottom ->
                scope.launch {
                    val result = repository.cropImageAttachment(
                        attachment,
                        left,
                        top,
                        right,
                        bottom,
                    )
                    Toast.makeText(
                        context,
                        if (result == null) "裁剪失败" else "图片已裁剪",
                        Toast.LENGTH_SHORT,
                    ).show()
                    cropTarget = null
                }
            },
        )
    }
    imagePreview?.let { attachment ->
        ImageGalleryDialog(
            attachments = imageAttachments,
            initialAttachmentId = attachment.id,
            onDismiss = { imagePreview = null },
            onSave = { target ->
                scope.launch {
                    val saved = repository.saveImageToGallery(target)
                    Toast.makeText(
                        context,
                        if (saved) "图片已保存到相册" else "保存图片失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    if (insertImageMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { insertImageMenuOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFFF7F7F7),
            contentColor = Color(0xFF333333),
            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "请选择操作",
                        color = Color(0xFF333333),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { insertImageMenuOpen = false },
                        modifier = Modifier.width(72.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("取消", color = Color(0xFF333333), fontSize = 15.sp)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE2E2E2)),
                )
                Spacer(Modifier.height(18.dp))
                InsertImageAction(
                    label = "从相册选取",
                    modifier = Modifier.padding(horizontal = 18.dp),
                ) {
                    insertImageMenuOpen = false
                    openGalleryPicker()
                }
                Spacer(Modifier.height(18.dp))
                InsertImageAction(
                    label = "拍摄照片",
                    modifier = Modifier.padding(horizontal = 18.dp),
                ) {
                    insertImageMenuOpen = false
                    launchCamera()
                }
            }
        }
    }

    if (galleryPickerOpen) {
        SmartisanImagePicker(
            maxSelection = (9 - imageAttachments.size).coerceAtLeast(1),
            onDismiss = { galleryPickerOpen = false },
            onConfirm = { uris ->
                galleryPickerOpen = false
                importImages(uris)
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
private fun EditorTitleButton(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun EditorHistoryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(48.dp)
            .height(38.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            color = EditorToolbarInk.copy(alpha = if (enabled) 1f else 0.48f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditorInfoBar(
    folderName: String,
    updatedAt: Long,
    characterCount: Int,
    starred: Boolean,
    format: NoteFormat,
    menuExpanded: Boolean,
    lineHeight: Dp,
    onFolderClick: () -> Unit,
    onStarClick: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSelectFormat: (NoteFormat) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeight)
            .padding(start = 39.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(96.dp)
                .height(24.dp)
                .clickable(onClick = onFolderClick)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folderName,
                modifier = Modifier.weight(1f),
                color = EditorMutedInk,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("▾", color = EditorMutedInk, fontSize = 11.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = editorTimestamp(updatedAt),
            color = EditorMutedInk,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Text(
            text = " | $characterCount",
            color = EditorMutedInk,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onStarClick,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = if (starred) "取消加星" else "加星",
                tint = if (starred) Color(0xFFD19B3D) else EditorRule,
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
                    .clickable { onMenuExpandedChange(true) }
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
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
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
                        onClick = { onSelectFormat(mode) },
                    )
                }
            }
        }
        Text(" ▾", color = EditorRule, fontSize = 11.sp)
    }
}

@Composable
private fun EditorFormatToolbar(
    modifier: Modifier = Modifier,
    onFormat: (DocumentFormatAction) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EditorFormatBar,
        contentColor = EditorFormatInk,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(43.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorTextFormatButton(
                label = "标题",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.HEADING) },
            )
            EditorToolbarDivider()
            EditorTextFormatButton(
                label = "居中",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.CENTER) },
            )
            EditorToolbarDivider()
            EditorTextFormatButton(
                label = "列表",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.BULLET) },
            )
            EditorToolbarDivider()
            EditorTextFormatButton(
                label = "粗体",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.BOLD) },
            )
            EditorToolbarDivider()
            EditorTextFormatButton(
                label = "引用",
                modifier = Modifier.weight(1f),
                onClick = { onFormat(DocumentFormatAction.QUOTE) },
            )
            EditorToolbarDivider()
            EditorTextFormatButton(
                label = "待办事项",
                modifier = Modifier.weight(1.25f),
                onClick = { onFormat(DocumentFormatAction.TODO) },
            )
        }
    }
}

@Composable
private fun EditorTextFormatButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = EditorFormatInk,
            fontSize = 13.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditorToolbarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(23.dp)
            .background(Color(0xFFE1D9CE)),
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
        if (attachment.isEmbeddedDocument()) {
            try {
                context.startActivity(
                    Intent(context, OafMainActivity::class.java).apply {
                        action = Intent.ACTION_EDIT
                        setDataAndType(Uri.fromFile(File(path)), mimeType)
                        putExtra(OafMainActivity.EXTRA_DISPLAY_NAME, attachment.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    },
                )
                return
            } catch (it: Throwable) {
                Toast.makeText(
                    context,
                    "内置 Office 编辑器无法打开：${it.message}",
                    Toast.LENGTH_LONG,
                ).show()
                return
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
private fun ImageCaptionDialog(
    attachment: AttachmentEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var caption by remember(attachment.id) { mutableStateOf(attachment.caption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片说明") },
        text = {
            TextField(
                value = caption,
                onValueChange = {
                    caption = it.replace('\n', ' ').take(200)
                },
                label = { Text("说明文字") },
                placeholder = { Text(attachment.name) },
                maxLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(caption.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ImageGalleryDialog(
    attachments: List<AttachmentEntity>,
    initialAttachmentId: Long,
    onDismiss: () -> Unit,
    onSave: (AttachmentEntity) -> Unit,
) {
    if (attachments.isEmpty()) return
    val initialPage = attachments.indexOfFirst { it.id == initialAttachmentId }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { attachments.size },
    )
    val current = attachments[pagerState.currentPage.coerceIn(attachments.indices)]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090807),
        ) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val attachment = attachments[page]
                    AsyncImage(
                        model = File(Uri.parse(attachment.uri).path.orEmpty()),
                        contentDescription = attachment.displayCaption(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 76.dp, bottom = 126.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "关闭预览",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${pagerState.currentPage + 1} / ${attachments.size}",
                        color = Color(0xFFD9D4CC),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC090807))
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = current.displayCaption(),
                        color = Color(0xFFF5EEE3),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onSave(current) }) {
                            Text("保存图片", color = Color(0xFFFFE0AA))
                        }
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = Color(0xFFE9E3D9))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCropDialog(
    attachment: AttachmentEntity,
    onDismiss: () -> Unit,
    onCrop: (Float, Float, Float, Float) -> Unit,
) {
    var left by remember(attachment.id) { mutableStateOf(0.05f) }
    var top by remember(attachment.id) { mutableStateOf(0.05f) }
    var right by remember(attachment.id) { mutableStateOf(0.95f) }
    var bottom by remember(attachment.id) { mutableStateOf(0.95f) }
    var imageSize by remember(attachment.uri) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    LaunchedEffect(attachment.uri) {
        val measuredSize = withContext(Dispatchers.IO) {
            runCatching {
                val path = Uri.parse(attachment.uri).path ?: return@runCatching null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.outWidth to options.outHeight
            }.getOrNull()
        }
        imageSize = measuredSize
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("裁剪图片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF171310)),
                ) {
                    AsyncImage(
                        model = File(Uri.parse(attachment.uri).path.orEmpty()),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val dimensions = imageSize
                        val imageWidth: Float
                        val imageHeight: Float
                        if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
                            val imageRatio = dimensions.first.toFloat() / dimensions.second
                            val boxRatio = size.width / size.height
                            if (boxRatio > imageRatio) {
                                imageHeight = size.height
                                imageWidth = imageHeight * imageRatio
                            } else {
                                imageWidth = size.width
                                imageHeight = imageWidth / imageRatio
                            }
                        } else {
                            imageWidth = size.width
                            imageHeight = size.height
                        }
                        val imageLeft = (size.width - imageWidth) / 2f
                        val imageTop = (size.height - imageHeight) / 2f
                        val cropLeft = imageLeft + imageWidth * left
                        val cropTop = imageTop + imageHeight * top
                        val cropRight = imageLeft + imageWidth * right
                        val cropBottom = imageTop + imageHeight * bottom
                        val shade = Color.Black.copy(alpha = 0.5f)
                        drawRect(shade, Offset(imageLeft, imageTop), Size(imageWidth, cropTop - imageTop))
                        drawRect(
                            shade,
                            Offset(imageLeft, cropBottom),
                            Size(imageWidth, imageTop + imageHeight - cropBottom),
                        )
                        drawRect(
                            shade,
                            Offset(imageLeft, cropTop),
                            Size(cropLeft - imageLeft, cropBottom - cropTop),
                        )
                        drawRect(
                            shade,
                            Offset(cropRight, cropTop),
                            Size(imageLeft + imageWidth - cropRight, cropBottom - cropTop),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropLeft, cropTop),
                            size = Size(cropRight - cropLeft, cropBottom - cropTop),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                CropSlider("左", left) { left = it.coerceAtMost(right - 0.05f) }
                CropSlider("上", top) { top = it.coerceAtMost(bottom - 0.05f) }
                CropSlider("右", right) { right = it.coerceAtLeast(left + 0.05f) }
                CropSlider("下", bottom) { bottom = it.coerceAtLeast(top + 0.05f) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCrop(left, top, right, bottom) }) { Text("裁剪") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(22.dp), color = EditorMutedInk)
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun InsertImageAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        color = Color(0xFFBABBBF),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmbeddedDocumentImage(
    attachment: AttachmentEntity,
    editable: Boolean,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: (AttachmentEntity) -> Unit,
    onPreview: (AttachmentEntity) -> Unit,
    onCaption: (AttachmentEntity) -> Unit,
    onCrop: (AttachmentEntity) -> Unit,
    onSave: (AttachmentEntity) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: (AttachmentEntity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 10.dp),
            shape = RoundedCornerShape(7.dp),
            color = Color(0xFFFFFEFB),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp)
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    AsyncImage(
                        model = File(Uri.parse(attachment.uri).path.orEmpty()),
                        contentDescription = attachment.displayCaption(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 380.dp)
                            .clickable {
                                if (editable) onSelect(attachment) else onPreview(attachment)
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
                if (attachment.caption.isNotBlank()) {
                    Text(
                        text = attachment.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorMutedInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (editable && selected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageActionButton(
                    icon = R.drawable.smartisan_image_delete,
                    label = "删除图片",
                    onClick = { onDelete(attachment) },
                )
                ImageActionButton(
                    icon = R.drawable.smartisan_image_caption,
                    label = "图片描述",
                    onClick = { onCaption(attachment) },
                )
                ImageActionButton(
                    icon = R.drawable.smartisan_image_save,
                    label = "保存图片",
                    onClick = { onSave(attachment) },
                )
                ImageActionButton(
                    icon = R.drawable.smartisan_image_preview,
                    label = "预览图片",
                    onClick = { onPreview(attachment) },
                )
                ImageActionButton(
                    icon = R.drawable.smartisan_image_crop,
                    label = "裁剪图片",
                    enabled = ImageEditing.isCropSupported(attachment),
                    onClick = { onCrop(attachment) },
                )
                Spacer(Modifier.weight(1f))
                ImageDragHandle(
                    attachment = attachment,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMove = { _, direction -> onMove(direction) },
                )
            }
        }
    }
}

@Composable
private fun ImageActionButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = label,
        modifier = Modifier
            .size(46.dp)
            .alpha(if (enabled) 1f else 0.36f)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ImageDragHandle(
    attachment: AttachmentEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier,
    onMove: (AttachmentEntity, Int) -> Unit,
) {
    var accumulated by remember(attachment.id) { mutableStateOf(0f) }
    Image(
        painter = painterResource(R.drawable.smartisan_image_drag),
        contentDescription = "拖动调整图片顺序",
        modifier = modifier
            .size(46.dp)
            .pointerInput(attachment.id, canMoveUp, canMoveDown) {
                detectDragGestures(
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount.y
                    if (abs(accumulated) >= 34.dp.toPx()) {
                        val direction = if (accumulated > 0f) 1 else -1
                        if (
                            (direction < 0 && canMoveUp) ||
                            (direction > 0 && canMoveDown)
                        ) {
                            onMove(attachment, direction)
                        }
                        accumulated = 0f
                    }
                }
            }
            .semantics { contentDescription = "拖动调整图片顺序" },
    )
}

private enum class ImageGlyph {
    GALLERY,
    CAMERA,
    DELETE,
    DESCRIPTION,
    SAVE,
    PREVIEW,
    CROP,
    DRAG,
}

@Composable
private fun ImageToolGlyph(
    glyph: ImageGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        },
    ) {
        val stroke = 2.1.dp.toPx()
        val left = size.width * 0.1f
        val top = size.height * 0.1f
        val right = size.width * 0.9f
        val bottom = size.height * 0.9f
        when (glyph) {
            ImageGlyph.GALLERY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top + size.height * 0.08f),
                    size = Size(right - left, bottom - top - size.height * 0.08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        3.dp.toPx(),
                        3.dp.toPx(),
                    ),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.07f,
                    center = Offset(size.width * 0.69f, size.height * 0.35f),
                    style = Stroke(width = stroke),
                )
                val mountain = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.73f)
                    lineTo(size.width * 0.4f, size.height * 0.48f)
                    lineTo(size.width * 0.57f, size.height * 0.69f)
                    lineTo(size.width * 0.7f, size.height * 0.55f)
                    lineTo(size.width * 0.84f, size.height * 0.73f)
                }
                drawPath(mountain, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ImageGlyph.CAMERA -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height * 0.27f),
                    size = Size(right - left, size.height * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.32f, size.height * 0.17f),
                    size = Size(size.width * 0.28f, size.height * 0.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.15f,
                    center = center,
                    style = Stroke(width = stroke),
                )
            }
            ImageGlyph.DELETE -> drawLine(
                color,
                Offset(left + stroke, center.y),
                Offset(right - stroke, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            ImageGlyph.DESCRIPTION -> {
                val pencil = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.78f)
                    lineTo(size.width * 0.3f, size.height * 0.52f)
                    lineTo(size.width * 0.65f, size.height * 0.17f)
                    lineTo(size.width * 0.82f, size.height * 0.34f)
                    lineTo(size.width * 0.47f, size.height * 0.7f)
                    close()
                }
                drawPath(pencil, color, style = Stroke(stroke, join = StrokeJoin.Round))
            }
            ImageGlyph.SAVE -> {
                drawLine(
                    color,
                    Offset(center.x, top),
                    Offset(center.x, size.height * 0.63f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.34f, size.height * 0.48f),
                    Offset(center.x, size.height * 0.64f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(center.x, size.height * 0.64f),
                    Offset(size.width * 0.66f, size.height * 0.48f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.18f, size.height * 0.87f),
                    Offset(size.width * 0.82f, size.height * 0.87f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            ImageGlyph.PREVIEW -> {
                drawLine(
                    color,
                    Offset(size.width * 0.2f, size.height * 0.2f),
                    Offset(size.width * 0.43f, size.height * 0.43f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.17f, size.height * 0.45f),
                    Offset(size.width * 0.17f, size.height * 0.17f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.17f, size.height * 0.17f),
                    Offset(size.width * 0.45f, size.height * 0.17f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.8f, size.height * 0.8f),
                    Offset(size.width * 0.57f, size.height * 0.57f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.83f, size.height * 0.55f),
                    Offset(size.width * 0.83f, size.height * 0.83f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.83f, size.height * 0.83f),
                    Offset(size.width * 0.55f, size.height * 0.83f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            ImageGlyph.CROP -> {
                drawLine(
                    color,
                    Offset(size.width * 0.22f, size.height * 0.12f),
                    Offset(size.width * 0.22f, size.height * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.12f, size.height * 0.22f),
                    Offset(size.width * 0.86f, size.height * 0.22f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.78f, size.height * 0.22f),
                    Offset(size.width * 0.78f, size.height * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.22f, size.height * 0.78f),
                    Offset(size.width * 0.88f, size.height * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            ImageGlyph.DRAG -> {
                listOf(0.28f, 0.5f, 0.72f).forEach { y ->
                    drawLine(
                        color,
                        Offset(left, size.height * y),
                        Offset(right, size.height * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
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
