package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.NoteEntity
import io.github.latticenotes.data.NoteFormat
import io.github.latticenotes.util.AttachmentReferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownNoteCodecTest {
    @Test
    fun plainMarkdownUsesFileAndHeadingAsTitle() {
        val markdown = "# RustDesk远程控制软件\n\n正文内容"

        val decoded = MarkdownNoteCodec.decode(
            markdown,
            "RustDesk远程控制软件.md",
        )

        assertNotNull(decoded)
        assertEquals("RustDesk远程控制软件", decoded!!.note.title)
        assertEquals("正文内容", decoded.note.body)
        assertEquals(
            decoded.note.id,
            MarkdownNoteCodec.decode(markdown, "RustDesk远程控制软件.md")!!.note.id,
        )
    }

    @Test
    fun markdownRoundTripPreservesNoteAndEmbeddedImage() {
        val attachment = AttachmentEntity(
            id = 9,
            noteId = 3,
            uri = "file:///image.png",
            name = "图片.png",
            mimeType = "image/png",
        ).apply {
            embeddedData = "aGVsbG8="
        }
        val note = NoteEntity(
            id = 3,
            title = "个人笔记",
            body = AttachmentReferences.appendToBody(
                "正文内容",
                AttachmentReferences.imageMarkdown(attachment.id, attachment.name),
            ),
            format = NoteFormat.MARKDOWN.name,
            tags = "自用",
            updatedAt = 123_456,
        )

        val markdown = MarkdownNoteCodec.encode(note, listOf(attachment))
        val decoded = MarkdownNoteCodec.decode(markdown)

        assertNotNull(decoded)
        assertEquals("个人笔记", decoded!!.note.title)
        assertEquals(123_456, decoded.note.updatedAt)
        assertEquals(1, decoded.embeddedAttachments.size)
        assertTrue(decoded.note.body.contains("data:image/png;base64,aGVsbG8="))
        assertFalse(markdown.contains("\"notes\""))
        assertEquals("note-3.md", MarkdownNoteCodec.fileName(3))
    }
}
