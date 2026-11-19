package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeDocumentTest {
    @Test
    fun recognizesWordExcelAndPdfAttachmentsCaseInsensitively() {
        val word = attachment("report.DOCX")
        val excel = attachment("budget.xlsx")
        val pdf = attachment("letter.PDF")

        assertTrue(word.isEmbeddedDocument())
        assertTrue(excel.isEmbeddedDocument())
        assertTrue(pdf.isEmbeddedDocument())
    }

    @Test
    fun selectsTheFirstEmbeddedDocumentAttachment() {
        val attachments = listOf(
            attachment("photo.png"),
            attachment("report.docx"),
            attachment("budget.xlsx"),
        )

        assertEquals("report.docx", attachments.firstEmbeddedDocumentAttachment()?.name)
    }

    @Test
    fun ignoresDocumentsWithoutAnEmbeddedViewer() {
        val attachments = listOf(
            attachment("plain.txt"),
            attachment("archive.zip"),
        )

        assertNull(attachments.firstEmbeddedDocumentAttachment())
    }

    private fun attachment(name: String) = AttachmentEntity(
        noteId = 1,
        uri = "file:///tmp/$name",
        name = name,
        mimeType = null,
    )
}
