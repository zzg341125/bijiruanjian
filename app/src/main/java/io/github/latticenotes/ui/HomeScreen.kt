package io.github.latticenotes.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.latticenotes.data.FolderEntity
import io.github.latticenotes.data.NoteEntity
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.util.AttachmentReferences
import io.github.latticenotes.util.formatTimestamp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class NoteView {
    ALL,
    STARRED,
    TRASH,
    FOLDER,
}

private enum class NoteSort {
    UPDATED,
    CREATED,
    TITLE,
}

private val categoryTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: NotesRepository,
    initialSearch: Boolean,
    onSearchConsumed: () -> Unit,
    onCreateNote: (Long?) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenKnowledge: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes by repository.observeNotes().collectAsStateWithLifecycle(emptyList())
    val folders by repository.observeFolders().collectAsStateWithLifecycle(emptyList())
    val markdownPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            val count = uris.mapNotNull { repository.importMarkdown(it) }.size
            Toast.makeText(context, "已导入 $count 个 Markdown 文件", Toast.LENGTH_SHORT).show()
        }
    }

    var view by remember { mutableStateOf(NoteView.ALL) }
    var selectedFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var sort by remember { mutableStateOf(NoteSort.UPDATED) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var appMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var moveNote by remember { mutableStateOf<NoteEntity?>(null) }
    var tagNote by remember { mutableStateOf<NoteEntity?>(null) }
    var deleteNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(initialSearch) {
        if (initialSearch) {
            searchFocus.requestFocus()
            onSearchConsumed()
        }
    }

    val filtered = remember(notes, view, selectedFolder, query, sort) {
        notes.asSequence()
            .filter { note ->
                when (view) {
                    NoteView.ALL -> !note.trashed
                    NoteView.STARRED -> !note.trashed && note.starred
                    NoteView.TRASH -> note.trashed
                    NoteView.FOLDER -> !note.trashed && note.folderId == selectedFolder?.id
                }
            }
            .filter { note ->
                query.isBlank() ||
                    note.title.contains(query, ignoreCase = true) ||
                    note.body.contains(query, ignoreCase = true) ||
                    note.tags.contains(query, ignoreCase = true)
            }
            .let { sequence ->
                when (sort) {
                    NoteSort.UPDATED -> sequence.sortedWith(
                        compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt },
                    )
                    NoteSort.CREATED -> sequence.sortedWith(
                        compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.createdAt },
                    )
                    NoteSort.TITLE -> sequence.sortedWith(
                        compareByDescending<NoteEntity> { it.pinned }
                            .thenBy { it.title.lowercase().ifBlank { "untitled" } },
                    )
                }
            }
            .toList()
    }
    val allCount = remember(notes) { notes.count { !it.trashed } }
    val starredCount = remember(notes) { notes.count { !it.trashed && it.starred } }
    val trashCount = remember(notes) { notes.count { it.trashed } }
    val latestUpdate = remember(notes) { notes.maxOfOrNull { it.updatedAt } }
    val categoryTitle = when (view) {
        NoteView.ALL -> "全部便签"
        NoteView.STARRED -> "加星便签"
        NoteView.TRASH -> "回收站"
        NoteView.FOLDER -> selectedFolder?.name ?: "文件夹"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                title = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { categoryMenuOpen = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = categoryTitle,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (categoryMenuOpen) "▴" else "▾",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false },
                            modifier = Modifier.widthIn(min = 300.dp, max = 360.dp),
                            containerColor = Color(0xFFFFFBF5),
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides Color(0xFF2F2822),
                            ) {
                                CategoryMenuRow(
                                label = "全部便签",
                                count = allCount,
                                selected = view == NoteView.ALL,
                                icon = { Icon(Icons.Outlined.List, null) },
                                onClick = {
                                    view = NoteView.ALL
                                    selectedFolder = null
                                    categoryMenuOpen = false
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            CategoryMenuRow(
                                label = "加星便签",
                                count = starredCount,
                                selected = view == NoteView.STARRED,
                                icon = { Icon(Icons.Outlined.Star, null) },
                                onClick = {
                                    view = NoteView.STARRED
                                    selectedFolder = null
                                    categoryMenuOpen = false
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            CategoryMenuRow(
                                label = "回收站",
                                count = trashCount,
                                selected = view == NoteView.TRASH,
                                icon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    view = NoteView.TRASH
                                    selectedFolder = null
                                    categoryMenuOpen = false
                                },
                            )
                            folders.forEach { folder ->
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                                CategoryMenuRow(
                                    label = folder.name,
                                    count = notes.count { !it.trashed && it.folderId == folder.id },
                                    selected = view == NoteView.FOLDER && selectedFolder?.id == folder.id,
                                    icon = {
                                        Icon(
                                            Icons.Outlined.List,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {
                                        selectedFolder = folder
                                        view = NoteView.FOLDER
                                        categoryMenuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                                CategoryMenuFooter(
                                    lastUpdated = latestUpdate,
                                    onManageFolders = {
                                        categoryMenuOpen = false
                                        editFolder = null
                                        folderDialogOpen = true
                                    },
                                    onSort = {
                                        categoryMenuOpen = false
                                        sortMenuOpen = true
                                    },
                                    onAddFolder = {
                                        categoryMenuOpen = false
                                        editFolder = null
                                        folderDialogOpen = true
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenKnowledge) {
                        Icon(Icons.Outlined.Info, contentDescription = "本地知识检索")
                    }
                    Box {
                        IconButton(onClick = { appMenuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = appMenuOpen,
                            onDismissRequest = { appMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建文件夹") },
                                leadingIcon = { Icon(Icons.Outlined.List, null) },
                                onClick = {
                                    appMenuOpen = false
                                    editFolder = null
                                    folderDialogOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导入 Markdown") },
                                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                onClick = {
                                    appMenuOpen = false
                                    markdownPicker.launch(
                                        arrayOf(
                                            "text/markdown",
                                            "text/x-markdown",
                                            "text/plain",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("管理文件夹") },
                                leadingIcon = { Icon(Icons.Outlined.List, null) },
                                onClick = {
                                    appMenuOpen = false
                                    selectedFolder = null
                                    view = NoteView.ALL
                                    editFolder = null
                                    folderDialogOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("排序") },
                                onClick = {
                                    appMenuOpen = false
                                    sortMenuOpen = true
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
        floatingActionButton = {
            if (view != NoteView.TRASH) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onCreateNote(selectedFolder?.id.takeIf { view == NoteView.FOLDER })
                    },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("新建") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .focusRequester(searchFocus),
                placeholder = { Text("搜索标题、正文或标签") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                if (filtered.isEmpty()) {
                    EmptyNotes(
                        isSearching = query.isNotBlank(),
                        isTrash = view == NoteView.TRASH,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 12.dp,
                            bottom = 96.dp,
                        ),
                    ) {
                        items(filtered, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                trashed = view == NoteView.TRASH,
                                onClick = {
                                    if (view == NoteView.TRASH) {
                                        scope.launch { repository.restore(note.id) }
                                    } else {
                                        onOpenNote(note.id)
                                    }
                                },
                                onTogglePin = {
                                    scope.launch { repository.setPinned(note.id, !note.pinned) }
                                },
                                onToggleStar = {
                                    scope.launch { repository.setStarred(note.id, !note.starred) }
                                },
                                onDelete = {
                                    deleteNote = note
                                },
                                onRestore = { scope.launch { repository.restore(note.id) } },
                                onFolder = { moveNote = note },
                                onTags = { tagNote = note },
                                onDuplicate = {
                                    scope.launch {
                                        repository.duplicateNote(note.id)?.let(onOpenNote)
                                    }
                                },
                                onShare = {
                                    val text = note.title.ifBlank { "无标题" } +
                                        "\n\n" + AttachmentReferences.plainText(note.body)
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
                        }
                    }
                }
            }
        }
    }

    if (folderDialogOpen) {
        FolderDialog(
            folders = folders,
            onDismiss = { folderDialogOpen = false },
            onCreate = {
                scope.launch {
                    repository.createFolder(it, 0xFF305C4A)
                    folderDialogOpen = false
                }
            },
            onRename = { folder ->
                editFolder = folder
            },
            onDelete = { folder, deleteNotes ->
                scope.launch {
                    repository.deleteFolder(folder.id, deleteNotes)
                    if (selectedFolder?.id == folder.id) {
                        selectedFolder = null
                        view = NoteView.ALL
                    }
                }
            },
        )
    }
    editFolder?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { editFolder = null },
            onRename = { name ->
                scope.launch {
                    repository.updateFolder(folder.id, name, folder.color)
                    editFolder = null
                }
            },
        )
    }
    moveNote?.let { note ->
        AlertDialog(
            onDismissRequest = { moveNote = null },
            title = { Text("移动到文件夹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch { repository.moveToFolder(note.id, null) }
                            moveNote = null
                        },
                    ) { Text("无文件夹") }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                scope.launch { repository.moveToFolder(note.id, folder.id) }
                                moveNote = null
                            },
                        ) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveNote = null }) { Text("取消") }
            },
        )
    }
    tagNote?.let { note ->
        TagDialog(
            note = note,
            onDismiss = { tagNote = null },
            onSave = {
                scope.launch { repository.setTags(note.id, it) }
                tagNote = null
            },
        )
    }
    deleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteNote = null },
            title = { Text(if (note.trashed) "永久删除？" else "移到回收站？") },
            text = {
                Text(
                    if (note.trashed) {
                        "此操作会永久删除笔记及其附件索引，无法撤销。"
                    } else {
                        "笔记会移到回收站，你仍可以恢复。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (note.trashed) {
                                repository.permanentlyDelete(note.id)
                            } else {
                                repository.moveToTrash(note.id)
                            }
                        }
                        deleteNote = null
                    },
                ) { Text(if (note.trashed) "永久删除" else "移到回收站") }
            },
            dismissButton = {
                TextButton(onClick = { deleteNote = null }) { Text("取消") }
            },
        )
    }
    if (sortMenuOpen) {
        AlertDialog(
            onDismissRequest = { sortMenuOpen = false },
            containerColor = Color(0xFFFFFBF5),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("排序方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        NoteSort.UPDATED to "最近修改",
                        NoteSort.CREATED to "最近创建",
                        NoteSort.TITLE to "标题",
                    ).forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                sort = value
                                sortMenuOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (sort == value) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sortMenuOpen = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CategoryMenuRow(
    label: String,
    count: Int,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryMenuFooter(
    lastUpdated: Long?,
    onManageFolders: () -> Unit,
    onSort: () -> Unit,
    onAddFolder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryFooterButton(onClick = onManageFolders) {
            Icon(Icons.Outlined.List, contentDescription = "管理文件夹")
        }
        Text(
            text = if (lastUpdated == null) {
                "暂无更新"
            } else {
                "上次更新：${categoryTimestamp(lastUpdated)}"
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CategoryFooterButton(onClick = onSort) {
            Text("A↕", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(8.dp))
        CategoryFooterButton(onClick = onAddFolder) {
            Icon(Icons.Outlined.Add, contentDescription = "新建文件夹")
        }
    }
}

@Composable
private fun CategoryFooterButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun categoryTimestamp(value: Long): String =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(categoryTimeFormatter)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteEntity,
    trashed: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onFolder: () -> Unit,
    onTags: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3E8D3),
            contentColor = Color(0xFF20231F),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .drawBehind {
                    val step = 32.dp.toPx()
                    var y = step
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFF8D806E).copy(alpha = 0.12f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                        y += step
                    }
                },
        ) {
            Text(
                text = formatTimestamp(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF676B66),
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "无标题" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.pinned) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "已置顶",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Outlined.MoreVert, "更多操作", Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (trashed) {
                            DropdownMenuItem(
                                text = { Text("恢复") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = {
                                    menuOpen = false
                                    onRestore()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("永久删除") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (note.pinned) "取消置顶" else "置顶") },
                                leadingIcon = { Icon(Icons.Outlined.Star, null) },
                                onClick = {
                                    menuOpen = false
                                    onTogglePin()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.starred) "取消加星" else "加星") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Star,
                                        null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onToggleStar()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("移动文件夹") },
                                leadingIcon = { Icon(Icons.Outlined.List, null) },
                                onClick = {
                                    menuOpen = false
                                    onFolder()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("标签") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = {
                                    menuOpen = false
                                    onTags()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("创建副本") },
                                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                onClick = {
                                    menuOpen = false
                                    onDuplicate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享文本") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    onShare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("移到回收站") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotes(isSearching: Boolean, isTrash: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isTrash) Icons.Outlined.Delete else Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    isSearching -> "没有匹配的笔记"
                    isTrash -> "回收站为空"
                    else -> "还没有笔记"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isSearching) "换个关键词试试" else "点击右下角新建笔记",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderDialog(
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (FolderEntity) -> Unit,
    onDelete: (FolderEntity, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<FolderEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("新文件夹名称") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            enabled = name.isNotBlank(),
                            onClick = { onCreate(name.trim()) },
                        ) { Icon(Icons.Outlined.Add, "创建") }
                    },
                )
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(folder.color)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(folder.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRename(folder) }) {
                            Icon(Icons.Outlined.Edit, "重命名")
                        }
                        IconButton(onClick = { deleting = folder }) {
                            Icon(Icons.Outlined.Delete, "删除")
                        }
                    }
                }
                if (folders.isEmpty()) {
                    Text("还没有文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除文件夹 ${folder.name}？") },
            text = { Text("可以选择保留其中的笔记。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(folder, true)
                        deleting = null
                    },
                ) { Text("同时删除笔记") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDelete(folder, false)
                            deleting = null
                        },
                    ) { Text("保留笔记") }
                    TextButton(onClick = { deleting = null }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun RenameFolderDialog(
    folder: FolderEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onRename(name.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TagDialog(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var tags by remember(note.id) { mutableStateOf(note.tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("使用逗号分隔") },
                supportingText = { Text("例如：工作, 灵感, 待读") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(tags) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
