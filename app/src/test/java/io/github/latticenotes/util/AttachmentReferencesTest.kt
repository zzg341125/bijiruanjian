package io.github.latticenotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentReferencesTest {
    @Test
    fun plainTextDropsCustomColorMarkup() {
        val body = "今天<color:#C34C4C>完成小组</color>工作"

        assertEquals("今天完成小组工作", AttachmentReferences.plainText(body))
    }

    @Test
    fun appendsAndReadsImageReference() {
        val reference = AttachmentReferences.imageMarkdown(42, "图片.png")
        val body = AttachmentReferences.appendToBody("正文", reference)

        assertTrue(body.contains(reference))
        assertEquals(setOf(42L), AttachmentReferences.referencedImageIds(body))
        assertEquals("正文", AttachmentReferences.plainText(body))
    }

    @Test
    fun removesOnlyTheRequestedImageAndKeepsFollowingText() {
        val first = AttachmentReferences.imageMarkdown(1, "第一张.png")
        val second = AttachmentReferences.imageMarkdown(2, "第二张.png")
        val body = AttachmentReferences.appendAllToBody("正文", listOf(first, second)) + "\n结尾"

        val updated = AttachmentReferences.removeReference(body, 1)

        assertEquals(setOf(2L), AttachmentReferences.referencedImageIds(updated))
        assertTrue(updated.contains(second))
        assertTrue(updated.contains("正文"))
        assertTrue(updated.trimEnd().endsWith("结尾"))
    }

    @Test
    fun reordersImageReferencesWithoutMovingSurroundingText() {
        val first = AttachmentReferences.imageMarkdown(1, "第一张.png")
        val second = AttachmentReferences.imageMarkdown(2, "第二张.png")
        val body = "开头\n$first\n中间\n$second\n结尾"

        val updated = AttachmentReferences.reorderReferences(body, listOf(2, 1))

        assertEquals(
            "开头\n$second\n中间\n$first\n结尾",
            updated,
        )
    }

    @Test
    fun insertsImagesAtTheCurrentBodyCursor() {
        val reference = AttachmentReferences.imageMarkdown(7, "光标图片.png")

        val updated = AttachmentReferences.insertAllAt("开头结尾", 2, listOf(reference))

        assertEquals("开头\n$reference\n结尾", updated)
    }

    @Test
    fun splitsTextAndImagesIntoDocumentOrderBlocks() {
        val first = AttachmentReferences.imageMarkdown(1, "第一张.png")
        val second = AttachmentReferences.imageMarkdown(2, "第二张.png")
        val body = "开头\n$first\n中间\n$second\n结尾"

        val blocks = AttachmentReferences.documentBlocks(body)

        assertEquals(
            listOf("text-start", "image-1", "text-after-1", "image-2", "text-after-2"),
            blocks.map(DocumentBodyBlock::key),
        )
        assertEquals(listOf("开头", "中间", "结尾"), blocks.filterNot { it.isImage }.map { it.text })
        assertEquals(listOf(1L, 2L), blocks.mapNotNull { it.imageId })
    }

    @Test
    fun editingABlockPreservesImageSeparationNewlines() {
        val reference = AttachmentReferences.imageMarkdown(1, "图片.png")
        val body = "开头\n$reference\n结尾"
        val before = AttachmentReferences.documentBlocks(body)
            .first { it.key == "text-start" }

        val firstEdit = body.replaceRange(
            before.start,
            before.end,
            before.replacementText("修改后"),
        )
        val after = AttachmentReferences.documentBlocks(firstEdit)
            .first { it.key == "text-after-1" }
        val edited = firstEdit.replaceRange(
            after.start,
            after.end,
            after.replacementText("新结尾"),
        )

        assertTrue(edited.startsWith("修改后\n"))
        assertTrue(edited.contains(reference))
        assertTrue(edited.endsWith("\n新结尾"))
    }

    @Test
    fun staleBlockBoundsDoNotBreakEditingAfterImageContentChanges() {
        val reference = AttachmentReferences.imageMarkdown(1, "图片.png")
        val body = "新开头\n$reference\n新结尾"

        val edited = AttachmentReferences.replaceDocumentBlockText(
            body = body,
            blockKey = "text-after-1",
            editedText = "更新后的结尾",
        )

        assertTrue(edited.startsWith("新开头\n"))
        assertTrue(edited.contains(reference))
        assertTrue(edited.endsWith("\n更新后的结尾"))
    }
}
