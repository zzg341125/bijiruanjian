package io.github.latticenotes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow
import java.io.File
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
    ): Long = dao.insert(
        NoteEntity(
            title = title,
            body = body,
            folderId = folderId,
            format = format.name,
        ),
    )

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun save(note: NoteEntity) {
        dao.upsert(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateNote(id: Long): Long? {
        val source = dao.getNote(id) ?: return null
        val now = System.currentTimeMillis()
        return dao.insert(
            source.copy(
                id = 0,
                title = source.title.ifBlank { "Untitled" } + " copy",
                pinned = false,
                createdAt = now,
                updatedAt = now,
                trashed = false,
                deletedAt = null,
                reminderAt = null,
            ),
        )
    }

    suspend fun moveToTrash(id: Long) = dao.moveToTrash(id)

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun permanentlyDelete(id: Long) {
        dao.getAttachments(id).forEach { deleteAttachmentFile(it.uri) }
        dao.delete(id)
    }

    suspend fun setPinned(id: Long, value: Boolean) = dao.setPinned(id, value)

    suspend fun setStarred(id: Long, value: Boolean) = dao.setStarred(id, value)

    suspend fun moveToFolder(id: Long, folderId: Long?) = dao.moveToFolder(id, folderId)

    suspend fun setTags(id: Long, tags: String) = dao.setTags(id, tags)

    suspend fun setReminder(id: Long, timeMillis: Long?) = dao.setReminder(id, timeMillis)

    suspend fun createFolder(name: String, color: Long): Long =
        dao.insertFolder(FolderEntity(name = name, color = color))

    suspend fun updateFolder(id: Long, name: String, color: Long) =
        dao.updateFolder(id, name, color)

    suspend fun deleteFolder(id: Long, deleteNotes: Boolean) {
        if (deleteNotes) {
            dao.getAllForBackup()
                .filter { it.folderId == id }
                .forEach { permanentlyDelete(it.id) }
        } else {
            dao.detachNotesFromFolder(id)
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
        return attachment.copy(id = dao.insertAttachment(attachment))
    }

    suspend fun removeAttachment(attachment: AttachmentEntity) {
        deleteAttachmentFile(attachment.uri)
        dao.deleteAttachment(attachment.id)
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

    private fun deleteAttachmentFile(uriValue: String) {
        runCatching {
            val uri = Uri.parse(uriValue)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }

    companion object {
        private const val MAX_IMPORT_CHARS = 2_000_000
        private const val MAX_EMBEDDED_ATTACHMENT_BYTES = 30L * 1024 * 1024
    }
}
