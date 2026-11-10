package io.github.latticenotes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore

enum class NoteFormat {
    TEXT,
    MARKDOWN,
    CHECKLIST,
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val format: String = NoteFormat.TEXT.name,
    val folderId: Long? = null,
    val tags: String = "",
    val color: Long = 0xFFF8F4EA,
    val pinned: Boolean = false,
    val starred: Boolean = false,
    val trashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val reminderAt: Long? = null,
    val syncAsBinary: Boolean = false,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF305C4A,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val createdAt: Long = System.currentTimeMillis(),
) {
    @Ignore
    var embeddedData: String? = null
}

data class BackupPayload(
    val notes: List<NoteEntity>,
    val folders: List<FolderEntity>,
    val attachments: List<AttachmentEntity>,
)
