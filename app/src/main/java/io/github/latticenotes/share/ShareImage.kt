package io.github.latticenotes.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.util.AttachmentReferences
import java.io.File

object ShareImage {
    fun share(
        context: Context,
        title: String,
        body: String,
        attachments: List<AttachmentEntity> = emptyList(),
    ) {
        val file = createBitmap(context, title, body, attachments)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享长图"))
    }

    private fun createBitmap(
        context: Context,
        title: String,
        body: String,
        attachments: List<AttachmentEntity>,
    ): File {
        val width = 1080
        val horizontal = 68
        val contentWidth = width - horizontal * 2
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 38, 33)
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(48, 54, 50)
            textSize = 36f
            typeface = Typeface.DEFAULT
        }
        val safeTitle = title.ifBlank { "无标题" }
        val titleLayout = StaticLayout.Builder.obtain(
            safeTitle,
            0,
            safeTitle.length,
            titlePaint,
            contentWidth,
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .build()
        val items = renderItems(
            body = body.take(60_000),
            attachments = attachments,
            contentWidth = contentWidth,
            bodyPaint = bodyPaint,
        )

        val footerHeight = 110
        val itemHeights = items.sumOf { it.height }
        val itemGaps = (items.size - 1).coerceAtLeast(0) * 18
        val height = (horizontal + titleLayout.height + 52 + itemHeights + itemGaps + footerHeight)
            .coerceIn(480, 18_000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(249, 242, 227))
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 105, 91, 74)
            strokeWidth = 2f
        }
        var lineY = 96f
        while (lineY < height) {
            canvas.drawLine(0f, lineY, width.toFloat(), lineY, linePaint)
            lineY += 82f
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 171, 104, 54)
            strokeWidth = 3f
        }
        canvas.drawLine(horizontal - 24f, 0f, horizontal - 24f, height.toFloat(), marginPaint)

        canvas.save()
        canvas.translate(horizontal.toFloat(), horizontal.toFloat())
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 52f)
        items.forEachIndexed { index, item ->
            when (item) {
                is ShareItem.Text -> item.layout.draw(canvas)
                is ShareItem.Image -> {
                    val destination = RectF(
                        item.left,
                        0f,
                        item.left + item.bitmap.width,
                        item.bitmap.height.toFloat(),
                    )
                    canvas.drawBitmap(item.bitmap, null, destination, null)
                    item.captionLayout?.let { caption ->
                        canvas.save()
                        canvas.translate(0f, item.bitmap.height + 20f)
                        caption.draw(canvas)
                        canvas.restore()
                    }
                }
            }
            if (index < items.lastIndex) {
                canvas.translate(0f, item.height + 18f)
            } else {
                canvas.translate(0f, item.height.toFloat())
            }
        }
        canvas.restore()

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(69, 92, 80)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "曲阳便签",
            horizontal.toFloat(),
            (height - 44).toFloat(),
            footerPaint,
        )

        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val output = File(directory, "note-${System.currentTimeMillis()}.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        items.filterIsInstance<ShareItem.Image>().forEach { it.bitmap.recycle() }
        return output
    }

    private fun renderItems(
        body: String,
        attachments: List<AttachmentEntity>,
        contentWidth: Int,
        bodyPaint: TextPaint,
    ): List<ShareItem> {
        val byId = attachments.associateBy { it.id }
        val references = AttachmentReferences.references(body)
        val items = mutableListOf<ShareItem>()
        var cursor = 0
        references.forEach { reference ->
            if (reference.range.first > cursor) {
                textItem(
                    body.substring(cursor, reference.range.first),
                    contentWidth,
                    bodyPaint,
                )?.let(items::add)
            }
            loadImage(byId[reference.attachmentId], contentWidth, bodyPaint)?.let(items::add)
            cursor = reference.range.last + 1
        }
        if (cursor < body.length) {
            textItem(body.substring(cursor), contentWidth, bodyPaint)?.let(items::add)
        }
        return items.ifEmpty {
            listOf(ShareItem.Text(layout("", contentWidth, bodyPaint)))
        }
    }

    private fun textItem(
        text: String,
        contentWidth: Int,
        bodyPaint: TextPaint,
    ): ShareItem.Text? {
        val cleaned = AttachmentReferences.plainText(text)
        if (cleaned.isBlank()) return null
        return ShareItem.Text(layout(cleaned, contentWidth, bodyPaint))
    }

    private fun layout(
        text: String,
        contentWidth: Int,
        paint: TextPaint,
    ): StaticLayout = StaticLayout.Builder.obtain(
        text,
        0,
        text.length,
        paint,
        contentWidth,
    ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(10f, 1.08f)
        .build()

    private fun loadImage(
        attachment: AttachmentEntity?,
        contentWidth: Int,
        bodyPaint: TextPaint,
    ): ShareItem.Image? {
        if (attachment == null) return null
        val uri = Uri.parse(attachment.uri)
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > contentWidth * 2) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        var targetWidth = contentWidth
        var targetHeight = (bitmap.height * targetWidth.toFloat() / bitmap.width).toInt()
        if (targetHeight > 1_400) {
            targetHeight = 1_400
            targetWidth = (bitmap.width * targetHeight.toFloat() / bitmap.height).toInt()
        }
        val scaled = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
        val captionLayout = attachment.caption.trim()
            .takeIf { it.isNotBlank() }
            ?.let {
                layout(
                    it,
                    contentWidth,
                    TextPaint(bodyPaint).apply {
                        textSize = 28f
                        color = Color.rgb(112, 99, 84)
                    },
                )
            }
        return ShareItem.Image(
            bitmap = scaled,
            captionLayout = captionLayout,
            left = (contentWidth - scaled.width) / 2f,
            height = scaled.height + (captionLayout?.let { it.height + 20 } ?: 0),
        )
    }

    private sealed interface ShareItem {
        val height: Int

        data class Text(val layout: StaticLayout) : ShareItem {
            override val height: Int = layout.height
        }

        data class Image(
            val bitmap: Bitmap,
            val captionLayout: StaticLayout?,
            val left: Float,
            override val height: Int,
        ) : ShareItem
    }
}
