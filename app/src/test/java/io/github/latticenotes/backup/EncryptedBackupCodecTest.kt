package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.BackupPayload
import io.github.latticenotes.data.FolderEntity
import io.github.latticenotes.data.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupCodecTest {
    @Test
    fun encrypted_backup_roundTrips_note_folder_and_attachment() {
        val payload = BackupPayload(
            notes = listOf(
                NoteEntity(
                    id = 7,
                    title = "曲阳周记",
                    body = "把今天写下来。",
                    folderId = 3,
                    tags = "daily",
                ),
            ),
            folders = listOf(FolderEntity(id = 3, name = "日常")),
            attachments = listOf(
                AttachmentEntity(
                    noteId = 7,
                    uri = "file:///tmp/walk.png",
                    name = "walk.png",
                    mimeType = "image/png",
                ).apply { embeddedData = "aGVsbG8=" },
            ),
        )

        val encoded = EncryptedBackupCodec.encode(payload, "correct horse battery")
        val decoded = EncryptedBackupCodec.decode(encoded, "correct horse battery")

        assertEquals("曲阳周记", decoded.notes.single().title)
        assertEquals("日常", decoded.folders.single().name)
        assertEquals("walk.png", decoded.attachments.single().name)
        assertEquals("aGVsbG8=", decoded.attachments.single().embeddedData)
    }

    @Test
    fun decode_rejects_wrong_password_with_actionable_message() {
        val encoded = EncryptedBackupCodec.encode(
            BackupPayload(notes = emptyList(), folders = emptyList(), attachments = emptyList()),
            "correct horse battery",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.decode(encoded, "wrong password")
        }

        assertEquals("备份密码不正确或文件已损坏", error.message)
    }
}
