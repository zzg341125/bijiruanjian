package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.BackupPayload
import io.github.latticenotes.data.FolderEntity
import io.github.latticenotes.data.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun backup_roundTripsNotesFoldersAndAttachmentIndex() {
        val payload = BackupPayload(
            notes = listOf(
                NoteEntity(
                    id = 7,
                    title = "Roadmap",
                    body = "# Next\n- [ ] Ship",
                    format = "MARKDOWN",
                    folderId = 3,
                    tags = "work,release",
                    starred = true,
                    syncAsBinary = true,
                ),
            ),
            folders = listOf(FolderEntity(id = 3, name = "Work")),
            attachments = listOf(
                AttachmentEntity(
                    noteId = 7,
                    uri = "file:///tmp/image.png",
                    name = "image.png",
                    mimeType = "image/png",
                ).apply { embeddedData = "aGVsbG8=" },
            ),
        )

        val decoded = BackupManager.decode(BackupManager.encode(payload))

        assertEquals("Roadmap", decoded.notes.single().title)
        assertEquals("work,release", decoded.notes.single().tags)
        assertEquals("Work", decoded.folders.single().name)
        assertEquals("image.png", decoded.attachments.single().name)
        assertEquals("aGVsbG8=", decoded.attachments.single().embeddedData)
        assertTrue(decoded.notes.single().starred)
        assertTrue(decoded.notes.single().syncAsBinary)
    }
}
