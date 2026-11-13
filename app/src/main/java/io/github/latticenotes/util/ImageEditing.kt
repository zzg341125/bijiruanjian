package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

data class ImageCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object ImageEditing {
    fun move(
        attachments: List<AttachmentEntity>,
        direction: Int,
        attachmentId: Long,
    ): List<AttachmentEntity> {
        val index = attachments.indexOfFirst { it.id == attachmentId }
        if (index < 0) return attachments
        val destination = (index + direction).coerceIn(0, attachments.lastIndex)
        if (destination == index) return attachments
        return attachments.toMutableList().apply {
            add(destination, removeAt(index))
        }
    }

    fun cropBounds(
        width: Int,
        height: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): ImageCropBounds? {
        if (width <= 0 || height <= 0) return null
        val cropLeft = (width * left.coerceIn(0f, 1f)).toInt().coerceIn(0, width)
        val cropTop = (height * top.coerceIn(0f, 1f)).toInt().coerceIn(0, height)
        val cropRight = (width * right.coerceIn(0f, 1f)).toInt().coerceIn(0, width)
        val cropBottom = (height * bottom.coerceIn(0f, 1f)).toInt().coerceIn(0, height)
        if (cropRight <= cropLeft || cropBottom <= cropTop) return null
        return ImageCropBounds(cropLeft, cropTop, cropRight, cropBottom)
    }

    fun orderedImages(
        body: String,
        attachments: List<AttachmentEntity>,
    ): List<AttachmentEntity> {
        val byId = attachments.associateBy { it.id }
        val referenced = AttachmentReferences.references(body)
            .mapNotNull { byId[it.attachmentId] }
            .distinctBy { it.id }
        return referenced + attachments
            .asSequence()
            .filter { attachment -> referenced.none { it.id == attachment.id } }
            .sortedBy { it.createdAt }
            .toList()
    }

    fun isCropSupported(attachment: AttachmentEntity): Boolean {
        val extension = attachment.name.substringAfterLast('.', "").lowercase()
        val mime = attachment.mimeType?.lowercase().orEmpty()
        return mime in setOf("image/jpeg", "image/png", "image/webp") ||
            extension in setOf("jpg", "jpeg", "png", "webp")
    }

    fun galleryFileName(attachment: AttachmentEntity, now: Long): String {
        val extension = attachment.name
            .substringAfterLast('.', "")
            .lowercase()
            .ifBlank {
                when (attachment.mimeType?.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/heic", "image/heif" -> "heic"
                    "image/bmp" -> "bmp"
                    else -> "jpg"
                }
            }
        return "曲阳便签-$now.$extension"
    }
}

fun AttachmentEntity.displayCaption(): String = caption.ifBlank { name }
