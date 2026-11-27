package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

object AttachmentReferences {
    private const val URI_PREFIX = "quyang-attachment://"
    private val referencePattern = Regex("""!\[[^\]]*]\(quyang-attachment://(\d+)\)""")
    private val colorTagPattern = Regex("""<color:#[0-9A-Fa-f]{6}>|</color>""")
    private val excessBlankLines = Regex("""\n{3,}""")

    fun imageMarkdown(attachmentId: Long, name: String): String {
        val alt = name
            .replace("\\", "\\\\")
            .replace("]", "\\]")
            .ifBlank { "图片" }
        return "![$alt]($URI_PREFIX$attachmentId)"
    }

    fun appendToBody(body: String, reference: String): String {
        val text = body.trimEnd()
        return if (text.isBlank()) {
            "$reference\n"
        } else {
            "$text\n\n$reference\n"
        }
    }

    fun appendAllToBody(body: String, references: List<String>): String =
        references.fold(body) { result, reference ->
            appendToBody(result, reference)
        }

    fun insertAllAt(
        body: String,
        position: Int,
        references: List<String>,
    ): String {
        if (references.isEmpty()) return body
        val offset = position.coerceIn(0, body.length)
        val before = body.substring(0, offset)
        val after = body.substring(offset)
        val leadingBreak = if (before.isEmpty() || before.endsWith('\n')) "" else "\n"
        val trailingBreak = if (after.isEmpty() || after.startsWith('\n')) "" else "\n"
        return before +
            leadingBreak +
            references.joinToString("\n") +
            trailingBreak +
            after
    }

    fun documentBlocks(body: String): List<DocumentBodyBlock> {
        val references = references(body)
        val blocks = mutableListOf<DocumentBodyBlock>()
        var cursor = 0
        var textKey = "text-start"
        var centerBlockCount = 0

        fun appendTextBlocks(start: Int, end: Int, key: String) {
            if (start == end) {
                blocks += textBlock(body, start, end, key)
                return
            }
            var groupStart = start
            var normalGroupCount = 0
            var lineStart = start
            while (lineStart < end) {
                val lineBreak = body.indexOf('\n', lineStart)
                val lineEnd = if (lineBreak < 0 || lineBreak >= end) end else lineBreak
                val blockEnd = if (lineBreak >= 0 && lineBreak < end) lineBreak + 1 else end
                val line = body.substring(lineStart, lineEnd)
                if (line.trim().startsWith("<center>", ignoreCase = true) &&
                    line.trim().endsWith("</center>", ignoreCase = true)
                ) {
                    if (groupStart < lineStart) {
                        val groupKey = if (normalGroupCount == 0) {
                            key
                        } else {
                            "$key-normal-$normalGroupCount"
                        }
                        blocks += textBlock(body, groupStart, lineStart, groupKey)
                        normalGroupCount++
                    }
                    centerBlockCount++
                    blocks += textBlock(
                        body = body,
                        start = lineStart,
                        end = blockEnd,
                        key = "center-$centerBlockCount",
                        centered = true,
                    )
                    groupStart = blockEnd
                }
                lineStart = blockEnd
            }
            if (groupStart < end) {
                val groupKey = if (normalGroupCount == 0) {
                    key
                } else {
                    "$key-normal-$normalGroupCount"
                }
                blocks += textBlock(body, groupStart, end, groupKey)
            }
        }

        if (references.isEmpty()) {
            appendTextBlocks(0, body.length, "text-start")
            return blocks
        }

        references.forEach { reference ->
            appendTextBlocks(cursor, reference.range.first, textKey)
            blocks += DocumentBodyBlock(
                key = "image-${reference.attachmentId}",
                start = reference.range.first,
                end = reference.range.last + 1,
                imageId = reference.attachmentId,
            )
            cursor = reference.range.last + 1
            textKey = "text-after-${reference.attachmentId}"
        }
        appendTextBlocks(cursor, body.length, textKey)
        return blocks
    }

    fun replaceDocumentBlockText(
        body: String,
        blockKey: String,
        editedText: String,
    ): String {
        val currentBlock = documentBlocks(body)
            .firstOrNull { it.key == blockKey && !it.isImage }
            ?: return body
        if (currentBlock.text == editedText) return body
        return body.replaceRange(
            currentBlock.start,
            currentBlock.end,
            currentBlock.replacementText(editedText),
        )
    }

    fun referencedImageIds(body: String): Set<Long> =
        references(body)
            .map { it.attachmentId }
            .toSet()

    fun references(body: String): List<AttachmentReference> =
        referencePattern.findAll(body).mapNotNull { match ->
            match.groupValues[1].toLongOrNull()?.let { id ->
                AttachmentReference(
                    attachmentId = id,
                    range = match.range,
                )
            }
        }.toList()

    fun plainText(body: String): String =
        body.replace(referencePattern, "")
            .replace(colorTagPattern, "")
            .replace(excessBlankLines, "\n\n")
            .trim()

    fun removeReference(body: String, attachmentId: Long): String =
        body.replace(
            Regex("""!\[[^\]]*]\($URI_PREFIX${Regex.escape(attachmentId.toString())}\)"""),
            "",
        )
            .replace(excessBlankLines, "\n\n")
            .trim()

    fun reorderReferences(body: String, orderedAttachmentIds: List<Long>): String {
        val references = references(body)
        if (references.size < 2 || orderedAttachmentIds.isEmpty()) return body
        val referencesById = references.associateBy { it.attachmentId }
        val requested = orderedAttachmentIds
            .distinct()
            .mapNotNull(referencesById::get)
        if (requested.isEmpty()) return body
        val ordered = (requested + references.filterNot { it.attachmentId in orderedAttachmentIds })
            .take(references.size)
        return buildString {
            var cursor = 0
            references.forEachIndexed { index, original ->
                append(body.substring(cursor, original.range.first))
                val replacement = ordered[index]
                append(
                    body.substring(
                        replacement.range.first,
                        replacement.range.last + 1,
                    ),
                )
                cursor = original.range.last + 1
            }
            append(body.substring(cursor))
        }
    }

    fun imageAttachments(
        body: String,
        attachments: List<AttachmentEntity>,
    ): Map<Long, AttachmentEntity> {
        val ids = referencedImageIds(body)
        return attachments
            .asSequence()
            .filter { it.id in ids && it.mimeType?.startsWith("image/") == true }
            .associateBy { it.id }
    }

    private fun textBlock(
        body: String,
        start: Int,
        end: Int,
        key: String,
        centered: Boolean = false,
    ): DocumentBodyBlock {
        val raw = body.substring(start, end)
        val leading = raw.takeWhile { it == '\n' }
        val withoutLeading = raw.drop(leading.length)
        val trailing = withoutLeading.takeLastWhile { it == '\n' }
        val content = withoutLeading.dropLast(trailing.length)
        return DocumentBodyBlock(
            key = key,
            start = start,
            end = end,
            text = content,
            leadingNewlines = leading,
            trailingNewlines = trailing,
            centered = centered,
        )
    }
}

fun AttachmentEntity.isImageAttachment(): Boolean {
    if (mimeType?.startsWith("image/") == true) return true
    return name.substringAfterLast('.', "").lowercase() in setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif",
        "bmp",
        "heic",
        "heif",
    )
}

data class AttachmentReference(
    val attachmentId: Long,
    val range: IntRange,
)

data class DocumentBodyBlock(
    val key: String,
    val start: Int,
    val end: Int,
    val text: String = "",
    val leadingNewlines: String = "",
    val trailingNewlines: String = "",
    val imageId: Long? = null,
    val centered: Boolean = false,
) {
    val isImage: Boolean
        get() = imageId != null

    fun replacementText(editedText: String): String =
        leadingNewlines + editedText + trailingNewlines
}
