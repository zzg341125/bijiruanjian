package io.github.latticenotes.backup

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.latticenotes.R
import io.github.latticenotes.data.AttachmentEntity
import java.io.ByteArrayOutputStream
import java.util.Base64

object SingleFileExporter {
    fun appIconDataUri(context: Context): String? = runCatching {
        val bitmap = context.getDrawable(R.drawable.ic_launcher_foreground)
            ?.toBitmap(width = 256, height = 256)
            ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        "data:image/png;base64," +
            Base64.getEncoder().encodeToString(output.toByteArray())
    }.getOrNull()

    fun toHtml(
        title: String,
        body: String,
        attachments: List<AttachmentEntity>,
        iconDataUri: String? = null,
    ): String {
        val safeTitle = escape(title.ifBlank { "无标题" })
        val sections = attachments.joinToString("\n") { attachment ->
            val encoded = attachment.embeddedData
            if (encoded == null) {
                "<p class=\"missing\">附件未嵌入：${escape(attachment.name)}</p>"
            } else if (attachment.mimeType?.startsWith("image/") == true) {
                """
                <figure>
                  <img src="data:${escapeAttribute(attachment.mimeType)};base64,$encoded"
                       alt="${escapeAttribute(attachment.name)}">
                  <figcaption>${escape(attachment.name)}</figcaption>
                </figure>
                """.trimIndent()
            } else {
                val mime = attachment.mimeType ?: "application/octet-stream"
                """
                <p class="file"><a download="${escapeAttribute(attachment.name)}"
                  href="data:${escapeAttribute(mime)};base64,$encoded">${escape(attachment.name)}</a></p>
                """.trimIndent()
            }
        }
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>$safeTitle</title>
              <style>
                :root { color-scheme: light; }
                body {
                  margin: 0; background: #e9e1d2; color: #3b3128;
                  font-family: "Noto Serif SC", "Songti SC", serif;
                }
                main {
                  max-width: 860px; min-height: 100vh; margin: 0 auto; box-sizing: border-box;
                  padding: 48px 60px 80px 92px;
                  background-color: #fbf6e9;
                  background-image: linear-gradient(to bottom, transparent 31px, #d0c4b0 32px);
                  background-size: 100% 32px;
                  border-left: 46px solid #e7dcc8;
                  box-shadow: 0 0 24px rgba(65,48,30,.2);
                }
                .brand { display:flex; align-items:center; gap:14px; color:#625545; margin-bottom:32px; }
                .brand-icon {
                  width: 52px;
                  height: 52px;
                  flex: none;
                  object-fit: contain;
                }
                h1 { font-size: 34px; line-height: 1.35; margin: 0 0 30px; }
                .body { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 19px; line-height: 32px; }
                figure { margin: 32px 0; }
                img { display:block; max-width:100%; height:auto; border-radius:8px; box-shadow:0 4px 18px rgba(54,42,30,.22); }
                figcaption, .missing { color:#796e60; font-size:14px; }
                .file a { color:#9b592d; }
                footer { margin-top:56px; color:#8a7b67; font-size:13px; }
                @media (max-width:600px) {
                  main { padding:32px 24px 56px 42px; border-left-width:24px; }
                  h1 { font-size:28px; }
                }
              </style>
            </head>
            <body>
              <main>
                <div class="brand">
                  ${if (iconDataUri != null) {
                      "<img class=\"brand-icon\" src=\"$iconDataUri\" alt=\"曲阳便签\">"
                  } else ""}
                  <strong>曲阳便签 · 单文件</strong>
                </div>
                <h1>$safeTitle</h1>
                <div class="body">${escape(body)}</div>
                $sections
                <footer>由曲阳便签导出 · 图片和附件已嵌入此 HTML 文件</footer>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String =
        escape(value).replace("\"", "&quot;")
}
