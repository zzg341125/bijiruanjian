package io.github.latticenotes.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import io.github.latticenotes.data.AttachmentEntity
import java.io.File
import java.util.UUID

data class ImageCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object ImageEditing {
    private const val WHITE_CHANNEL_THRESHOLD = 242
    private const val BORDER_SAMPLE_LIMIT = 1024

    fun trimWhiteBorders(file: File, mimeType: String?, name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        val normalizedMime = mimeType?.lowercase().orEmpty()
        if (
            normalizedMime !in setOf("image/jpeg", "image/png", "image/webp") &&
            extension !in setOf("jpg", "jpeg", "png", "webp")
        ) {
            return false
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sample = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: return false
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        val sampleBounds = whiteBorderCropBounds(
            width = sample.width,
            height = sample.height,
        ) { x, y ->
            isWhiteBorderPixel(pixels[y * sample.width + x])
        }
        val sampleWidth = sample.width
        val sampleHeight = sample.height
        sample.recycle()
        if (sampleBounds == null) return false

        val left = sampleBounds.left * bounds.outWidth / sampleWidth
        val top = sampleBounds.top * bounds.outHeight / sampleHeight
        val right = sampleBounds.right * bounds.outWidth / sampleWidth
        val bottom = sampleBounds.bottom * bounds.outHeight / sampleHeight
        if (right <= left || bottom <= top) return false

        val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false)
            ?: return false
        val cropped = runCatching {
            decoder.decodeRegion(
                Rect(left, top, right, bottom),
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }.getOrNull()
        decoder.recycle()
        if (cropped == null) return false

        val format = when {
            mimeType.equals("image/png", ignoreCase = true) ||
                name.endsWith(".png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
            mimeType.equals("image/webp", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true) -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val temporary = File(file.parentFile, ".trim-${UUID.randomUUID()}.${file.extension}")
        val saved = runCatching {
            temporary.outputStream().use { output ->
                check(cropped.compress(format, 92, output)) { "无法保存图片" }
            }
            temporary.inputStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
        cropped.recycle()
        temporary.delete()
        return saved
    }

    fun whiteBorderCropBounds(
        width: Int,
        height: Int,
        isBackground: (Int, Int) -> Boolean,
    ): ImageCropBounds? {
        if (width <= 0 || height <= 0) return null
        fun columnIsBackground(x: Int): Boolean =
            (0 until height).all { y -> isBackground(x, y) }
        fun rowIsBackground(y: Int): Boolean =
            (0 until width).all { x -> isBackground(x, y) }

        var left = 0
        while (left < width && columnIsBackground(left)) left++
        var right = width
        while (right > left && columnIsBackground(right - 1)) right--
        var top = 0
        while (top < height && rowIsBackground(top)) top++
        var bottom = height
        while (bottom > top && rowIsBackground(bottom - 1)) bottom--

        if (left == 0 && top == 0 && right == width && bottom == height) return null
        if (right <= left || bottom <= top) return null
        if ((right - left) * 4 < width || (bottom - top) * 4 < height) return null
        return ImageCropBounds(left, top, right, bottom)
    }

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

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (
            width / sampleSize > BORDER_SAMPLE_LIMIT ||
            height / sampleSize > BORDER_SAMPLE_LIMIT
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun isWhiteBorderPixel(color: Int): Boolean {
        val alpha = Color.alpha(color)
        return alpha <= 8 || (
            alpha >= 245 &&
                Color.red(color) >= WHITE_CHANNEL_THRESHOLD &&
                Color.green(color) >= WHITE_CHANNEL_THRESHOLD &&
                Color.blue(color) >= WHITE_CHANNEL_THRESHOLD
            )
    }
}

fun AttachmentEntity.displayCaption(): String = caption.ifBlank { name }
