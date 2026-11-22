package io.github.latticenotes.ui

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.util.AttachmentReferences
import io.github.latticenotes.util.ImageEditing
import io.github.latticenotes.util.displayCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditingTest {
    @Test
    fun imagesMoveByOneAndStayAtBoundaries() {
        val images = listOf(image(1), image(2), image(3))

        assertEquals(
            listOf(2L, 1L, 3L),
            ImageEditing.move(images, direction = -1, attachmentId = 2).map { it.id },
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            ImageEditing.move(images, direction = -1, attachmentId = 3).map { it.id },
        )
        assertEquals(images, ImageEditing.move(images, direction = -1, attachmentId = 1))
        assertEquals(images, ImageEditing.move(images, direction = 1, attachmentId = 3))
    }

    @Test
    fun cropBoundsClampAndRequirePositiveArea() {
        val bounds = ImageEditing.cropBounds(
            width = 1000,
            height = 500,
            left = 0.1f,
            top = 0.2f,
            right = 0.8f,
            bottom = 0.9f,
        )

        assertEquals(100, bounds?.left)
        assertEquals(100, bounds?.top)
        assertEquals(800, bounds?.right)
        assertEquals(450, bounds?.bottom)
        assertNull(
            ImageEditing.cropBounds(
                width = 100,
                height = 100,
                left = 0.8f,
                top = 0.1f,
                right = 0.2f,
                bottom = 0.9f,
            ),
        )
    }

    @Test
    fun captionFallsBackToAttachmentName() {
        assertEquals("旅行", image(1, caption = "旅行").displayCaption())
        assertEquals("photo.jpg", image(1).displayCaption())
    }

    @Test
    fun imagesFollowTheOrderOfTheirDocumentReferences() {
        val images = listOf(image(1), image(2), image(3))
        val body = AttachmentReferences.appendAllToBody(
            "",
            listOf(
                AttachmentReferences.imageMarkdown(3, "3.jpg"),
                AttachmentReferences.imageMarkdown(1, "1.jpg"),
            ),
        )

        assertEquals(
            listOf(3L, 1L, 2L),
            ImageEditing.orderedImages(body, images).map { it.id },
        )
    }

    @Test
    fun gallerySavePreservesTheOriginalImageFormat() {
        assertEquals("曲阳便签-42.png", ImageEditing.galleryFileName(image(1, name = "cover.PNG"), 42))
        assertFalse(ImageEditing.isCropSupported(image(1, name = "motion.gif")))
        assertTrue(ImageEditing.isCropSupported(image(1, name = "photo.jpg")))
    }

    private fun image(
        id: Long,
        caption: String = "",
        name: String = "photo.jpg",
    ) = AttachmentEntity(
        id = id,
        noteId = 1,
        uri = "file:///image-$id.jpg",
        name = name,
        mimeType = when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        },
        caption = caption,
    )
}
