package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.FolderEntity
import io.github.latticenotes.data.NoteEntity
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.util.AttachmentReferences
import java.util.Base64

object MarkdownNoteCodec {
    private const val EXTERNAL_ID_BASE = 1L shl 62
    private val headerPattern = Regex(
        """\A<!-- quyang-note\n(.*?)\n-->\r?\n?""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val embeddedDataPattern = Regex(
        """(!)?\[([^\]]*)]\(data:([^;)]+);base64,([^)]+)\)""",
    )

    fun fileName(noteId: Long): String = "note-$noteId.md"

    fun encode(
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        folder: FolderEntity? = null,
    ): String {
        val attachmentsById = attachments.associateBy { it.id }
        val references = AttachmentReferences.references(note.body)
        val bodyWithImages = buildString {
            var cursor = 0
            references.forEach { reference ->
                if (reference.range.first > cursor) {
                    append(note.body.substring(cursor, reference.range.first))
                }
                val attachment = attachmentsById[reference.attachmentId]
                val original = note.body.substring(reference.range.first, reference.range.last + 1)
                if (attachment?.embeddedData != null) {
                    append(embeddedAttachmentMarkdown(attachment))
                } else {
                    append(original)
                }
                cursor = reference.range.last + 1
            }
            if (cursor < note.body.length) {
                append(note.body.substring(cursor))
            }
            val referencedIds = references.map { it.attachmentId }.toSet()
            attachments
                .filter { it.id !in referencedIds && it.embeddedData != null }
                .forEach { attachment ->
                    append("\n\n")
                    append(embeddedAttachmentMarkdown(attachment))
                }
        }.trimEnd()

        val safeTitle = note.title.replace('\n', ' ').replace('\r', ' ')
        val displayTitle = safeTitle.ifBlank { "无标题" }
        val metadata = buildList {
            add("version=1")
            add("id=${note.id}")
            add("title=${encodeValue(note.title)}")
            add("format=${note.format}")
            add("folder-id=${note.folderId ?: ""}")
            add("folder-name=${encodeValue(folder?.name.orEmpty())}")
            add("folder-color=${folder?.color ?: 0L}")
            add("tags=${encodeValue(note.tags)}")
            add("color=${note.color}")
            add("pinned=${note.pinned}")
            add("starred=${note.starred}")
            add("trashed=${note.trashed}")
            add("created-at=${note.createdAt}")
            add("updated-at=${note.updatedAt}")
            add("deleted-at=${note.deletedAt ?: ""}")
            add("reminder-at=${note.reminderAt ?: ""}")
        }.joinToString("\n")

        return buildString {
            append("<!-- quyang-note\n")
            append(metadata)
            append("\n-->\n\n")
            append("# ").append(displayTitle).append("\n\n")
            if (bodyWithImages.isNotBlank()) {
                append(bodyWithImages)
                append('\n')
            }
        }
    }

    fun decode(markdown: String, sourceFileName: String = ""): DecodedMarkdownNote? {
        val header = headerPattern.find(markdown)
        val metadata = header?.groupValues?.get(1)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else {
                    line.substring(0, separator) to line.substring(separator + 1)
                }
            }
            .toMap()
        val id = metadata["id"]?.toLongOrNull()
            ?: externalNoteId(sourceFileName)
            ?: return null
        val title = decodeValue(metadata["title"].orEmpty())
        var body = if (header != null) {
            markdown.substring(header.range.last + 1).trimStart('\r', '\n')
        } else {
            markdown.trimStart('\r', '\n')
        }
        if (body.startsWith("# ")) {
            body = body.substringAfter('\n', "").trimStart('\r', '\n')
        }
        val resolvedTitle = title.ifBlank {
            sourceFileName.substringBeforeLast('.').ifBlank { "Markdown 记事" }
        }
        val note = NoteEntity(
            id = id,
            title = resolvedTitle,
            body = body,
            format = runCatching {
                NoteFormat.valueOf(metadata["format"].orEmpty())
            }.getOrDefault(NoteFormat.MARKDOWN).name,
            folderId = metadata["folder-id"]?.toLongOrNull(),
            tags = decodeValue(metadata["tags"].orEmpty()),
            color = metadata["color"]?.toLongOrNull() ?: 0xFFF8F4EA,
            pinned = metadata["pinned"].toBoolean(),
            starred = metadata["starred"].toBoolean(),
            trashed = metadata["trashed"].toBoolean(),
            createdAt = metadata["created-at"]?.toLongOrNull() ?: System.currentTimeMillis(),
            updatedAt = metadata["updated-at"]?.toLongOrNull() ?: System.currentTimeMillis(),
            deletedAt = metadata["deleted-at"]?.toLongOrNull(),
            reminderAt = metadata["reminder-at"]?.toLongOrNull(),
        )
        return DecodedMarkdownNote(
            note = note,
            embeddedAttachments = embeddedAttachments(body),
            folderName = decodeValue(metadata["folder-name"].orEmpty()).ifBlank { null },
            folderColor = metadata["folder-color"]?.toLongOrNull() ?: 0xFF305C4A,
        )
    }

    private fun externalNoteId(sourceFileName: String): Long? {
        if (sourceFileName.isBlank()) return null
        val hash = sourceFileName.lowercase().hashCode().toLong()
        return EXTERNAL_ID_BASE or (hash and 0x3FFFFFFFFFFFFFFFL)
    }

    fun embeddedAttachments(markdown: String): List<MarkdownEmbeddedAttachment> =
        embeddedDataPattern.findAll(markdown).map { match ->
            MarkdownEmbeddedAttachment(
                name = match.groupValues[2].ifBlank { "附件" },
                mimeType = match.groupValues[3],
                encodedData = match.groupValues[4],
                isImage = match.groupValues[1].isNotEmpty(),
                range = match.range,
            )
        }.toList()

    private fun embeddedAttachmentMarkdown(attachment: AttachmentEntity): String {
        val encoded = attachment.embeddedData ?: return attachment.name
        val mime = attachment.mimeType ?: if (attachment.isImage()) {
            "image/*"
        } else {
            "application/octet-stream"
        }
        val name = attachment.name.replace("]", "\\]")
        val dataUrl = "data:$mime;base64,$encoded"
        return if (mime.startsWith("image/")) {
            "![$name]($dataUrl)"
        } else {
            "[$name]($dataUrl)"
        }
    }

    private fun AttachmentEntity.isImage(): Boolean =
        mimeType?.startsWith("image/") == true ||
            name.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    private fun encodeValue(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeValue(value: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrDefault(value)
}

data class DecodedMarkdownNote(
    val note: NoteEntity,
    val embeddedAttachments: List<MarkdownEmbeddedAttachment>,
    val folderName: String?,
    val folderColor: Long,
)

data class MarkdownEmbeddedAttachment(
    val name: String,
    val mimeType: String,
    val encodedData: String,
    val isImage: Boolean,
    val range: IntRange,
)
