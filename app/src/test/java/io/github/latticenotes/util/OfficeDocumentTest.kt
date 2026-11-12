package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeDocumentTest {
    @Test
    fun recognizesWordAndExcelAttachmentsCaseInsensitively() {
        val word = attachment("report.DOCX")
        val excel = attachment("budget.xlsx")

        assertTrue(word.isEmbeddedOfficeDocument())
        assertTrue(excel.isEmbeddedOfficeDocument())
    }

    @Test
    fun selectsTheFirstEditableOfficeAttachment() {
        val attachments = listOf(
            attachment("photo.png"),
            attachment("report.docx"),
            attachment("budget.xlsx"),
        )

        assertEquals("report.docx", attachments.firstEmbeddedOfficeAttachment()?.name)
    }

    @Test
    fun ignoresDocumentsWithoutAnEmbeddedOfficeEditor() {
        val attachments = listOf(
            attachment("letter.pdf"),
            attachment("plain.txt"),
        )

        assertNull(attachments.firstEmbeddedOfficeAttachment())
    }

    private fun attachment(name: String) = AttachmentEntity(
        noteId = 1,
        uri = "file:///tmp/$name",
        name = name,
        mimeType = null,
    )
}
