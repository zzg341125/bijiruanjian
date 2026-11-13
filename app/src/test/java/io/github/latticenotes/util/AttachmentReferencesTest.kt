package io.github.latticenotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentReferencesTest {
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
}
