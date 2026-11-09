package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.util.AttachmentReferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFileExporterTest {
    @Test
    fun embedsReferencedImageAtItsDocumentPosition() {
        val attachment = AttachmentEntity(
            id = 7,
            noteId = 1,
            uri = "file:///image.png",
            name = "图片.png",
            mimeType = "image/png",
        ).apply {
            embeddedData = "aGVsbG8="
        }
        val body = AttachmentReferences.appendToBody(
            "照片如下",
            AttachmentReferences.imageMarkdown(attachment.id, attachment.name),
        )

        val html = SingleFileExporter.toHtml(
            title = "带图便签",
            body = body,
            attachments = listOf(attachment),
        )

        assertTrue(html.contains("照片如下"))
        assertTrue(html.contains("data:image/png;base64,aGVsbG8="))
        assertFalse(html.contains("quyang-attachment://7"))
    }
}
