package io.github.latticenotes.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

object ShareImage {
    fun share(context: Context, title: String, body: String) {
        val file = createBitmap(context, title, body)
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

    private fun createBitmap(context: Context, title: String, body: String): File {
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
        val safeBody = body.take(60_000)
        val titleLayout = StaticLayout.Builder.obtain(
            safeTitle,
            0,
            safeTitle.length,
            titlePaint,
            contentWidth,
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .build()
        val bodyLayout = StaticLayout.Builder.obtain(
            safeBody,
            0,
            safeBody.length,
            bodyPaint,
            contentWidth,
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.08f)
            .build()

        val footerHeight = 110
        val height = (horizontal + titleLayout.height + 52 + bodyLayout.height + footerHeight)
            .coerceIn(480, 18_000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 247, 240))

        canvas.save()
        canvas.translate(horizontal.toFloat(), horizontal.toFloat())
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 52f)
        bodyLayout.draw(canvas)
        canvas.restore()

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(69, 92, 80)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "Lattice Notes",
            horizontal.toFloat(),
            (height - 44).toFloat(),
            footerPaint,
        )

        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val output = File(directory, "note-${System.currentTimeMillis()}.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return output
    }
}
