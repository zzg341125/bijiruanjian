package io.github.latticenotes.data

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.latticenotes.backup.DecodedMarkdownNote
import io.github.latticenotes.backup.MarkdownNoteCodec
import io.github.latticenotes.util.AttachmentReferences
import io.github.latticenotes.util.ImageEditing
import io.github.latticenotes.util.isImageAttachment
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.Base64

class NotesRepository(
    private val context: Context,
    private val dao: NotesDao,
) {
    fun observeNotes(): Flow<List<NoteEntity>> = dao.observeNotes()

    fun observeNote(id: Long): Flow<NoteEntity?> = dao.observeNote(id)

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> =
        dao.observeAttachments(noteId)

    suspend fun createNote(
        title: String = "",
        body: String = "",
        folderId: Long? = null,
        format: NoteFormat = NoteFormat.TEXT,
    ): Long {
        val id = dao.insert(
            NoteEntity(
                title = title,
                body = body,
                folderId = folderId,
                format = format.name,
            ),
        )
        writeMarkdownMirror(id)
        return id
    }

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun save(note: NoteEntity) {
        val previous = dao.getNote(note.id)
        val updated = note.copy(updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        if (
            !updated.syncAsBinary &&
            previous != null &&
            previous.title != updated.title
        ) {
            renameMarkdownMirror(updated)
        } else {
            writeMarkdownMirror(updated.id)
        }
    }

    suspend fun duplicateNote(id: Long): Long? {
        val source = dao.getNote(id) ?: return null
        val now = System.currentTimeMillis()
        val duplicateId = dao.insert(
            source.copy(
                id = 0,
                title = source.title.ifBlank { "无标题" } + " 副本",
                pinned = false,
                createdAt = now,
                updatedAt = now,
                trashed = false,
                deletedAt = null,
                reminderAt = null,
            ),
        )
        writeMarkdownMirror(duplicateId)
        return duplicateId
    }

    suspend fun moveToTrash(id: Long) {
        dao.moveToTrash(id)
        deleteMarkdownMirror(id)
    }

    suspend fun restore(id: Long) {
        dao.restore(id)
        writeMarkdownMirror(id)
    }

    suspend fun permanentlyDelete(id: Long) {
        dao.getAttachments(id).forEach { deleteAttachmentFile(it.uri) }
        dao.delete(id)
        deleteMarkdownMirror(id)
    }

    suspend fun setPinned(id: Long, value: Boolean) {
        dao.setPinned(id, value)
        writeMarkdownMirror(id)
    }

    suspend fun setStarred(id: Long, value: Boolean) {
        dao.setStarred(id, value)
        writeMarkdownMirror(id)
    }

    suspend fun moveToFolder(id: Long, folderId: Long?) {
        dao.moveToFolder(id, folderId)
        writeMarkdownMirror(id)
    }

    suspend fun setTags(id: Long, tags: String) {
        dao.setTags(id, tags)
        writeMarkdownMirror(id)
    }

    suspend fun setReminder(id: Long, timeMillis: Long?) {
        dao.setReminder(id, timeMillis)
        writeMarkdownMirror(id)
    }

    suspend fun createFolder(name: String, color: Long): Long =
        dao.insertFolder(FolderEntity(name = name, color = color))

    suspend fun updateFolder(id: Long, name: String, color: Long) =
        run {
            dao.updateFolder(id, name, color)
            dao.getAllForBackup().filter { it.folderId == id }.forEach {
                writeMarkdownMirror(it.id)
            }
        }

    suspend fun deleteFolder(id: Long, deleteNotes: Boolean) {
        val affectedNoteIds = dao.getAllForBackup()
            .filter { it.folderId == id }
            .map { it.id }
        if (deleteNotes) {
            affectedNoteIds.forEach { permanentlyDelete(it) }
        } else {
            dao.detachNotesFromFolder(id)
            affectedNoteIds.forEach { writeMarkdownMirror(it) }
        }
        dao.deleteFolder(id)
    }

    suspend fun addAttachment(noteId: Long, source: Uri): AttachmentEntity? {
        val resolver = context.contentResolver
        val mime = resolver.getType(source)
        val originalName = resolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "attachment"

        val suffix = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        val destinationName = buildString {
            append(UUID.randomUUID())
            if (suffix != null) append('.').append(suffix)
        }
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val destination = File(directory, destinationName)

        val copied = runCatching {
            resolver.openInputStream(source)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) return null

        val attachment = AttachmentEntity(
            noteId = noteId,
            uri = Uri.fromFile(destination).toString(),
            name = originalName,
            mimeType = mime,
        )
        val inserted = attachment.copy(id = dao.insertAttachment(attachment))
        writeMarkdownMirror(noteId)
        return inserted
    }

    suspend fun removeAttachment(attachment: AttachmentEntity) {
        val note = dao.getNote(attachment.noteId)
        if (note != null && attachment.isImageAttachment()) {
            val updatedBody = AttachmentReferences.removeReference(
                note.body,
                attachment.id,
            )
            if (updatedBody != note.body) {
                dao.upsert(
                    note.copy(
                        body = updatedBody,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        deleteAttachmentFile(attachment.uri)
        dao.deleteAttachment(attachment.id)
        writeMarkdownMirror(attachment.noteId)
    }

    suspend fun updateAttachmentCaption(
        attachmentId: Long,
        noteId: Long,
        caption: String,
    ) {
        dao.updateAttachmentCaption(attachmentId, caption.trim())
        writeMarkdownMirror(noteId)
    }

    suspend fun reorderAttachments(noteId: Long, orderedAttachmentIds: List<Long>) {
        if (orderedAttachmentIds.isEmpty()) return
        val base = System.currentTimeMillis() - orderedAttachmentIds.size
        orderedAttachmentIds.forEachIndexed { index, attachmentId ->
            dao.updateAttachmentCreatedAt(attachmentId, base + index)
        }
        val note = dao.getNote(noteId)
        if (note != null) {
            val reorderedBody = AttachmentReferences.reorderReferences(
                note.body,
                orderedAttachmentIds,
            )
            if (reorderedBody != note.body) {
                dao.upsert(
                    note.copy(
                        body = reorderedBody,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        writeMarkdownMirror(noteId)
    }

    suspend fun cropImageAttachment(
        attachment: AttachmentEntity,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): AttachmentEntity? = withContext(Dispatchers.IO) {
        runCatching {
            if (!ImageEditing.isCropSupported(attachment)) return@runCatching null
            val uri = Uri.parse(attachment.uri)
            if (uri.scheme != "file") return@runCatching null
            val file = File(uri.path ?: return@runCatching null)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@runCatching null
            val bounds = ImageEditing.cropBounds(
                width = bitmap.width,
                height = bitmap.height,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            ) ?: run {
                bitmap.recycle()
                return@runCatching null
            }
            val cropped = Bitmap.createBitmap(
                bitmap,
                bounds.left,
                bounds.top,
                bounds.right - bounds.left,
                bounds.bottom - bounds.top,
            )
            file.outputStream().use { output ->
                val extension = file.extension.lowercase()
                val mime = attachment.mimeType?.lowercase().orEmpty()
                val format = when {
                    mime == "image/png" || extension == "png" -> Bitmap.CompressFormat.PNG
                    mime == "image/webp" || extension == "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                cropped.compress(format, 92, output)
            }
            cropped.recycle()
            if (cropped !== bitmap) bitmap.recycle()
            writeMarkdownMirror(attachment.noteId)
            attachment
        }.getOrNull()
    }

    suspend fun saveImageToGallery(attachment: AttachmentEntity): Boolean =
        withContext(Dispatchers.IO) {
            val sourceUri = Uri.parse(attachment.uri)
            if (sourceUri.scheme != "file") return@withContext false
            val sourceFile = File(sourceUri.path ?: return@withContext false)
            if (!sourceFile.isFile) return@withContext false
            val extension = attachment.name.substringAfterLast('.', "")
            val mime = attachment.mimeType
                ?.takeIf { it.startsWith("image/") }
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.lowercase())
                ?: "image/jpeg"
            val displayName = ImageEditing.galleryFileName(
                attachment,
                System.currentTimeMillis(),
            )
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/曲阳便签")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext false
            runCatching {
                context.contentResolver.openOutputStream(target)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: error("无法写入相册")
                context.contentResolver.update(
                    target,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
                true
            }.getOrElse {
                context.contentResolver.delete(target, null, null)
                false
            }
        }

    suspend fun search(query: String): List<NoteEntity> =
        dao.search(query.trim(), limit = 50)

    suspend fun importMarkdown(uri: Uri): Long? {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported note.md"
        val content = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val buffer = CharArray(8192)
            val output = StringBuilder()
            while (output.length < MAX_IMPORT_CHARS) {
                val count = reader.read(buffer)
                if (count < 0) break
                output.append(buffer, 0, count)
            }
            output.toString().take(MAX_IMPORT_CHARS)
        } ?: return null
        val title = displayName
            .substringBeforeLast('.', displayName)
            .ifBlank { "Markdown 导入" }
        return createNote(
            title = title,
            body = content,
            format = NoteFormat.MARKDOWN,
        )
    }

    suspend fun getAllForBackup(): BackupPayload = BackupPayload(
        notes = dao.getAllForBackup(),
        folders = dao.getFoldersForBackup(),
        attachments = dao.getAllAttachmentsForBackup().map(::withEmbeddedData),
    )

    suspend fun restoreBackup(payload: BackupPayload) {
        payload.folders.forEach { dao.insertFolder(it.copy(id = it.id)) }
        payload.notes.forEach { dao.upsert(it) }
        payload.attachments.forEach {
            dao.insertAttachment(materializeAttachment(it).copy(id = 0))
        }
    }

    suspend fun mergeBackup(payload: BackupPayload) {
        val localNotes = dao.getAllForBackup().associateBy { it.id }
        payload.notes.forEach { remote ->
            val local = localNotes[remote.id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                dao.upsert(remote)
            }
        }

        val localFolderIds = dao.getFoldersForBackup().map { it.id }.toSet()
        payload.folders.filter { it.id !in localFolderIds }.forEach {
            dao.insertFolder(it)
        }

        val existingAttachments = dao.getAllAttachmentsForBackup()
            .map { "${it.noteId}:${it.name}" }
            .toSet()
        val mergedNoteIds = dao.getAllForBackup().map { it.id }.toSet()
        payload.attachments
            .filter { it.noteId in mergedNoteIds }
            .filter { "${it.noteId}:${it.name}" !in existingAttachments }
            .forEach {
                dao.insertAttachment(materializeAttachment(it).copy(id = 0))
            }
    }

    suspend fun getAttachmentsForExport(noteId: Long): List<AttachmentEntity> =
        dao.getAttachments(noteId).map(::withEmbeddedData)

    suspend fun getAllMarkdownFiles(): Map<String, String> = withContext(Dispatchers.IO) {
        val notes = dao.getAllForBackup()
        notes.filter { it.trashed }.forEach { deleteMarkdownMirror(it.id) }
        notes.filterNot { it.trashed || it.syncAsBinary }
            .forEach { writeMarkdownMirror(it.id) }
        val directory = markdownDirectory()
        directory
            .walkTopDown()
            .filter { file ->
                file.isFile && file.extension.lowercase() in NOTE_FILE_EXTENSIONS
            }
            .associate { file ->
                file.relativeTo(directory).invariantSeparatorsPath to file.readText()
            }
    }

    suspend fun getMarkdownSyncStates(): Map<String, MarkdownSyncState> =
        withContext(Dispatchers.IO) {
            val file = File(markdownDirectory(), SYNC_STATE_FILE)
            if (!file.isFile) return@withContext emptyMap()
            file.readLines().mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size != 3) return@mapNotNull null
                runCatching {
                    val name = decodeStateValue(parts[0])
                    val remoteFingerprint = decodeStateValue(parts[1])
                    val localHash = decodeStateValue(parts[2])
                    name to MarkdownSyncState(remoteFingerprint, localHash)
                }.getOrNull()
            }.toMap()
        }

    suspend fun saveMarkdownSyncStates(states: Map<String, MarkdownSyncState>) =
        withContext(Dispatchers.IO) {
            val directory = markdownDirectory().apply { mkdirs() }
            File(directory, SYNC_STATE_FILE).writeText(
                states.entries.joinToString("\n") { (name, state) ->
                    listOf(name, state.remoteFingerprint, state.localHash)
                        .joinToString("|", transform = ::encodeStateValue)
                },
            )
        }

    suspend fun importMarkdownDocuments(
        documents: List<MarkdownNoteImport>,
        forceOverwrite: Boolean = false,
    ) {
        documents.forEach { imported ->
            var document = imported.document
            val local = dao.getNote(document.note.id)
            if (
                local != null &&
                !forceOverwrite &&
                local.updatedAt >= document.note.updatedAt
            ) {
                recordRemoteFileName(document.note.id, imported.fileName)
                return@forEach
            }

            if (document.note.id <= 0) {
                val insertedId = dao.insert(document.note.copy(id = 0))
                document = document.copy(note = document.note.copy(id = insertedId))
            }
            dao.upsert(document.note)

            val folderId = document.note.folderId
            if (
                folderId != null &&
                document.folderName != null &&
                dao.getFoldersForBackup().none { it.id == folderId }
            ) {
                dao.insertFolder(
                    FolderEntity(
                        id = folderId,
                        name = document.folderName,
                        color = document.folderColor,
                    ),
                )
            }

            val existing = dao.getAttachments(document.note.id).toMutableList()
            var body = document.note.body
            document.embeddedAttachments.sortedByDescending { it.range.first }.forEach { image ->
                val attachment = existing.firstOrNull {
                    it.name == image.name && it.mimeType == image.mimeType
                } ?: run {
                    val materialized = materializeAttachment(
                        AttachmentEntity(
                            noteId = document.note.id,
                            uri = "",
                            name = image.name,
                            mimeType = image.mimeType,
                        ).apply {
                            embeddedData = image.encodedData
                        },
                    )
                    if (!materialized.uri.startsWith("file:")) return@forEach
                    materialized.copy(
                        id = dao.insertAttachment(
                            materialized.copy(id = 0),
                        ),
                    ).also(existing::add)
                }
                val replacement = if (image.isImage) {
                    AttachmentReferences.imageMarkdown(attachment.id, attachment.name)
                } else {
                    "[${attachment.name}](quyang-attachment://${attachment.id})"
                }
                if (
                    image.range.first <= image.range.last &&
                    image.range.last < body.length
                ) {
                    body = body.replaceRange(image.range, replacement)
                }
            }
            dao.upsert(document.note.copy(body = body))
            recordRemoteFileName(document.note.id, imported.fileName)
            writeMarkdownMirror(document.note.id)
        }
    }

    suspend fun moveToTrashFromRemote(noteId: Long) {
        dao.moveToTrash(noteId)
        deleteMarkdownMirror(noteId)
    }

    suspend fun getBinarySyncStates(): Map<String, BinarySyncState> =
        withContext(Dispatchers.IO) {
            val file = File(markdownDirectory(), BINARY_SYNC_STATE_FILE)
            if (!file.isFile) return@withContext emptyMap()
            runCatching {
                val root = JSONObject(file.readText())
                buildMap {
                    root.keys().forEach { name ->
                        val value = root.getJSONObject(name)
                        put(
                            name,
                            BinarySyncState(
                                noteId = value.getLong("noteId"),
                                attachmentId = value.getLong("attachmentId"),
                                remoteFingerprint = value.optString("remoteFingerprint"),
                                localHash = value.optString("localHash"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyMap())
        }

    suspend fun saveBinarySyncStates(states: Map<String, BinarySyncState>) =
        withContext(Dispatchers.IO) {
            val directory = markdownDirectory().apply { mkdirs() }
            val root = JSONObject()
            states.forEach { (name, state) ->
                root.put(
                    name,
                    JSONObject().apply {
                        put("noteId", state.noteId)
                        put("attachmentId", state.attachmentId)
                        put("remoteFingerprint", state.remoteFingerprint)
                        put("localHash", state.localHash)
                    },
                )
            }
            File(directory, BINARY_SYNC_STATE_FILE).writeText(root.toString(2))
        }

    suspend fun readBinaryFile(state: BinarySyncState): ByteArray? =
        withContext(Dispatchers.IO) {
            val note = dao.getNote(state.noteId) ?: return@withContext null
            if (note.trashed || !note.syncAsBinary) return@withContext null
            val attachment = dao.getAttachments(state.noteId)
                .firstOrNull { it.id == state.attachmentId }
                ?: return@withContext null
            val uri = Uri.parse(attachment.uri)
            if (uri.scheme != "file") return@withContext null
            File(uri.path ?: return@withContext null)
                .takeIf(File::isFile)
                ?.readBytes()
        }

    suspend fun storeRemoteBinaryFile(
        remoteName: String,
        bytes: ByteArray,
        existing: BinarySyncState?,
        remoteFingerprint: String,
    ): BinarySyncState = withContext(Dispatchers.IO) {
        val localHash = bytes.sha256()
        val existingNote = existing?.let { dao.getNote(it.noteId) }
        val existingAttachment = existing?.let { state ->
            dao.getAttachments(state.noteId).firstOrNull { it.id == state.attachmentId }
        }
        val existingUri = existingAttachment?.uri?.let(Uri::parse)
        val existingFile = existingUri
            ?.takeIf { it.scheme == "file" }
            ?.path
            ?.let(::File)
        if (existingNote != null && existingFile != null) {
            existingFile.parentFile?.mkdirs()
            existingFile.writeBytes(bytes)
            dao.upsert(
                existingNote.copy(
                    title = binaryDisplayName(remoteName),
                    updatedAt = System.currentTimeMillis(),
                    syncAsBinary = true,
                ),
            )
            return@withContext BinarySyncState(
                noteId = existingNote.id,
                attachmentId = existingAttachment.id,
                remoteFingerprint = remoteFingerprint,
                localHash = localHash,
            )
        }

        val noteId = dao.insert(
            NoteEntity(
                title = binaryDisplayName(remoteName),
                format = NoteFormat.TEXT.name,
                syncAsBinary = true,
            ),
        )
        val extension = remoteName.substringAfterLast('.', "")
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(
            directory,
            buildString {
                append(UUID.randomUUID())
                if (extension.isNotBlank()) append('.').append(extension)
            },
        )
        file.writeBytes(bytes)
        val attachmentId = dao.insertAttachment(
            AttachmentEntity(
                noteId = noteId,
                uri = Uri.fromFile(file).toString(),
                name = remoteName,
                mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.lowercase()),
            ),
        )
        recordRemoteFileName(noteId, remoteName)
        BinarySyncState(
            noteId = noteId,
            attachmentId = attachmentId,
            remoteFingerprint = remoteFingerprint,
            localHash = localHash,
        )
    }

    suspend fun moveBinaryToTrash(noteId: Long) {
        dao.moveToTrash(noteId)
    }

    private fun binaryDisplayName(remoteName: String): String =
        remoteName.substringAfterLast('/').ifBlank { "同步文件" }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }

    private fun withEmbeddedData(attachment: AttachmentEntity): AttachmentEntity {
        if (attachment.embeddedData != null) return attachment
        return runCatching {
            val uri = Uri.parse(attachment.uri)
            if (uri.scheme != "file") return@runCatching attachment
            val file = File(uri.path ?: return@runCatching attachment)
            if (!file.isFile || file.length() > MAX_EMBEDDED_ATTACHMENT_BYTES) {
                return@runCatching attachment
            }
            attachment.copy().apply {
                embeddedData = Base64.getEncoder().encodeToString(file.readBytes())
            }
        }.getOrDefault(attachment)
    }

    private fun materializeAttachment(attachment: AttachmentEntity): AttachmentEntity {
        val encoded = attachment.embeddedData ?: return attachment
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size <= MAX_EMBEDDED_ATTACHMENT_BYTES)
            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
            val suffix = attachment.name.substringAfterLast('.', "")
            val file = File(
                directory,
                buildString {
                    append(UUID.randomUUID())
                    if (suffix.isNotBlank()) append('.').append(suffix)
                },
            )
            file.writeBytes(bytes)
            attachment.copy(uri = Uri.fromFile(file).toString()).apply {
                embeddedData = encoded
            }
        }.getOrDefault(attachment)
    }

    private suspend fun writeMarkdownMirror(noteId: Long) {
        withContext(Dispatchers.IO) {
            val note = dao.getNote(noteId) ?: return@withContext
            val mappedName = readRemoteFileNames()[note.id]
            val legacyFile = File(markdownDirectory(), MarkdownNoteCodec.fileName(note.id))
            if (note.title.isBlank() && mappedName == null && !legacyFile.isFile) {
                return@withContext
            }
            val attachments = dao.getAttachments(note.id).map(::withEmbeddedData)
            val folder = note.folderId?.let { id ->
                dao.getFoldersForBackup().firstOrNull { it.id == id }
            }
            val content = MarkdownNoteCodec.encode(note, attachments, folder)
            val directory = markdownDirectory().apply { mkdirs() }
            val file = File(directory, markdownFileName(note.id))
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    private suspend fun renameMarkdownMirror(note: NoteEntity) = withContext(Dispatchers.IO) {
        val directory = markdownDirectory().apply { mkdirs() }
        val names = readRemoteFileNames()
        val legacyName = MarkdownNoteCodec.fileName(note.id)
        val oldName = names[note.id]
            ?: legacyName.takeIf { File(directory, it).isFile }
            ?: return@withContext writeMarkdownMirror(note.id)
        val usedNames = buildSet {
            addAll(names.filterKeys { it != note.id }.values)
            addAll(
                directory.walkTopDown()
                    .filter(File::isFile)
                    .map { it.relativeTo(directory).invariantSeparatorsPath }
                    .filter { it != oldName },
            )
        }
        val newName = MarkdownNoteCodec.fileNameForTitle(
            title = note.title,
            noteId = note.id,
            usedNames = usedNames,
        )
        names[note.id] = newName
        writeRemoteFileNames(names)
        if (newName != oldName) {
            val oldFile = File(directory, oldName)
            val newFile = File(directory, newName)
            newFile.parentFile?.mkdirs()
            if (oldFile.isFile) {
                if (!oldFile.renameTo(newFile)) {
                    oldFile.copyTo(newFile, overwrite = true)
                    oldFile.delete()
                }
            }
        }
        writeMarkdownMirror(note.id)
    }

    private suspend fun deleteMarkdownMirror(noteId: Long) {
        withContext(Dispatchers.IO) {
            File(markdownDirectory(), markdownFileName(noteId)).delete()
            val names = readRemoteFileNames()
            if (names.remove(noteId) != null) {
                writeRemoteFileNames(names)
            }
        }
    }

    private fun markdownDirectory(): File =
        File(context.filesDir, "markdown-notes")

    private suspend fun markdownFileName(noteId: Long): String {
        val names = readRemoteFileNames()
        names[noteId]?.let { return it }

        val directory = markdownDirectory().apply { mkdirs() }
        val legacyName = MarkdownNoteCodec.fileName(noteId)
        val note = dao.getNote(noteId)
        val legacyFile = File(directory, legacyName)
        if (legacyFile.isFile && note?.title.isNullOrBlank()) {
            names[noteId] = legacyName
            writeRemoteFileNames(names)
            return legacyName
        }

        val usedNames = names.values.toMutableSet().apply {
            addAll(
                directory.walkTopDown()
                    .filter(File::isFile)
                    .map { it.relativeTo(directory).invariantSeparatorsPath },
            )
            remove(legacyName)
        }
        val title = note?.title.orEmpty()
        val generated = MarkdownNoteCodec.fileNameForTitle(title, noteId, usedNames)
        if (legacyFile.isFile && generated != legacyName) {
            val generatedFile = File(directory, generated)
            generatedFile.parentFile?.mkdirs()
            if (!legacyFile.renameTo(generatedFile)) {
                legacyFile.copyTo(generatedFile, overwrite = true)
                legacyFile.delete()
            }
        }
        names[noteId] = generated
        writeRemoteFileNames(names)
        return generated
    }

    private suspend fun recordRemoteFileName(noteId: Long, fileName: String) =
        withContext(Dispatchers.IO) {
            val names = readRemoteFileNames()
            names[noteId] = fileName
            writeRemoteFileNames(names)
        }

    private fun readRemoteFileNames(): MutableMap<Long, String> {
        val file = File(markdownDirectory(), REMOTE_FILE_NAMES)
        if (!file.isFile) return mutableMapOf()
        return file.readLines()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val id = line.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
                val name = runCatching {
                    String(
                        Base64.getUrlDecoder().decode(line.substring(separator + 1)),
                        Charsets.UTF_8,
                    )
                }.getOrNull() ?: return@mapNotNull null
                id to name
            }
            .toMap()
            .toMutableMap()
    }

    private fun writeRemoteFileNames(names: Map<Long, String>) {
        val directory = markdownDirectory().apply { mkdirs() }
        File(directory, REMOTE_FILE_NAMES).writeText(
            names.entries.joinToString("\n") { (id, name) ->
                val encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(name.toByteArray(Charsets.UTF_8))
                "$id=$encoded"
            },
        )
    }

    private fun encodeStateValue(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeStateValue(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private fun deleteAttachmentFile(uriValue: String) {
        runCatching {
            val uri = Uri.parse(uriValue)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }

    companion object {
        private const val MAX_IMPORT_CHARS = 2_000_000
        private const val MAX_EMBEDDED_ATTACHMENT_BYTES = 30L * 1024 * 1024
        private val NOTE_FILE_EXTENSIONS = setOf("md", "markdown", "txt")
        private const val REMOTE_FILE_NAMES = ".remote-file-names"
        private const val SYNC_STATE_FILE = ".sync-state"
        private const val BINARY_SYNC_STATE_FILE = ".binary-sync-state"
    }
}

data class MarkdownNoteImport(
    val fileName: String,
    val document: DecodedMarkdownNote,
)

data class MarkdownSyncState(
    val remoteFingerprint: String,
    val localHash: String,
)

data class BinarySyncState(
    val noteId: Long,
    val attachmentId: Long,
    val remoteFingerprint: String,
    val localHash: String,
)
