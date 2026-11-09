package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

object AttachmentReferences {
    private const val URI_PREFIX = "quyang-attachment://"
    private val referencePattern = Regex("""!\[[^\]]*]\(quyang-attachment://(\d+)\)""")

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
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

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
