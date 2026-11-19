package io.github.latticenotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileImportModelTest {
    @Test
    fun classifiesTextMarkdownAndDocuments() {
        assertEquals(
            ImportedNoteKind.MARKDOWN,
            FileImportModel.kindFor("meeting.notes.md", "application/octet-stream"),
        )
        assertEquals(
            ImportedNoteKind.TEXT,
            FileImportModel.kindFor("readme.txt", "text/plain"),
        )
        assertEquals(
            ImportedNoteKind.ATTACHMENT,
            FileImportModel.kindFor("report.docx", "application/octet-stream"),
        )
        assertEquals(
            ImportedNoteKind.ATTACHMENT,
            FileImportModel.kindFor("scan", "application/pdf"),
        )
    }

    @Test
    fun stripsOnlyTheLastExtensionForTheDisplayTitle() {
        assertEquals("release.notes", FileImportModel.titleFor("release.notes.md"))
        assertEquals("季度总结", FileImportModel.titleFor("C:\\docs\\季度总结.docx"))
        assertEquals("导入文件", FileImportModel.titleFor(".pdf"))
    }

    @Test
    fun prefersKnownExtensionMimeOverGenericProviderMime() {
        assertEquals(
            "application/pdf",
            FileImportModel.mimeTypeFor("scan.PDF", "application/octet-stream"),
        )
        assertEquals(
            "application/custom",
            FileImportModel.mimeTypeFor("scan.pdf", "application/custom; charset=binary"),
        )
    }

    @Test
    fun remoteNamesRemainUniqueWithoutLosingTheExtension() {
        val first = FileImportModel.uniqueRemoteName("季度总结.docx", emptySet())
        val second = FileImportModel.uniqueRemoteName(
            "季度总结.docx",
            setOf("季度总结.docx"),
        )

        assertEquals("季度总结.docx", first)
        assertEquals("季度总结 (2).docx", second)
    }

    @Test
    fun batchSummarySeparatesNotesFromAttachments() {
        val summary = LocalImportBatchResult(
            listOf(
                LocalImportResult("one.md", 1, ImportedNoteKind.MARKDOWN),
                LocalImportResult("two.txt", 2, ImportedNoteKind.TEXT),
                LocalImportResult("three.pdf", 3, ImportedNoteKind.ATTACHMENT),
            ),
        ).summaryMessage()

        assertEquals("已导入 3 个文件（记事 2 个，附件 1 个）", summary)
        assertTrue(LocalImportResult("x.pdf", 9, ImportedNoteKind.ATTACHMENT).succeeded)
        assertFalse(LocalImportResult("x.pdf", errorMessage = "无法复制文件").succeeded)
    }
}
