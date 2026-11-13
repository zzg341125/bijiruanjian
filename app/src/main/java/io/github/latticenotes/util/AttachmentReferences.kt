package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

object AttachmentReferences {
    private const val URI_PREFIX = "quyang-attachment://"
    private val referencePattern = Regex("""!\[[^\]]*]\(quyang-attachment://(\d+)\)""")
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
