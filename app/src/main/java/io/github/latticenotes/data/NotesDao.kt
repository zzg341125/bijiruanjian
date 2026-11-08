package io.github.latticenotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query(
        """
        SELECT * FROM notes
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE trashed = 0
          AND (
              title LIKE '%' || :query || '%' COLLATE NOCASE
              OR body LIKE '%' || :query || '%' COLLATE NOCASE
              OR tags LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY pinned DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 40): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET trashed = 1, deletedAt = :time, updatedAt = :time WHERE id = :id")
    suspend fun moveToTrash(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET trashed = 0, deletedAt = NULL, updatedAt = :time WHERE id = :id")
    suspend fun restore(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :time WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET starred = :starred, updatedAt = :time WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :time WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET tags = :tags, updatedAt = :time WHERE id = :id")
    suspend fun setTags(id: Long, tags: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET reminderAt = :timeMillis WHERE id = :id")
    suspend fun setReminder(id: Long, timeMillis: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM notes")
    suspend fun getAllForBackup(): List<NoteEntity>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    suspend fun getFoldersForBackup(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name, color = :color WHERE id = :id")
    suspend fun updateFolder(id: Long, name: String, color: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachNotesFromFolder(folderId: Long)

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAttachments(noteId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun getAllAttachmentsForBackup(): List<AttachmentEntity>

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}
