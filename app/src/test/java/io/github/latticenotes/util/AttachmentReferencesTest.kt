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
}
