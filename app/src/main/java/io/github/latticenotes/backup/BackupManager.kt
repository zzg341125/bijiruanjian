package io.github.latticenotes.backup

import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.data.BackupPayload
import io.github.latticenotes.data.FolderEntity
import io.github.latticenotes.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {
    fun encode(payload: BackupPayload): String {
        val root = JSONObject().apply {
            put("schema", 1)
            put("exportedAt", System.currentTimeMillis())
            put(
                "folders",
                JSONArray().apply {
                    payload.folders.forEach { folder ->
                        put(
                            JSONObject().apply {
                                put("id", folder.id)
                                put("name", folder.name)
                                put("color", folder.color)
                                put("createdAt", folder.createdAt)
                            },
                        )
                    }
                },
            )
            put(
                "notes",
                JSONArray().apply {
                    payload.notes.forEach { note ->
                        put(note.toJson())
                    }
                },
            )
            put(
                "attachments",
                JSONArray().apply {
                    payload.attachments.forEach { attachment ->
                        put(
                            JSONObject().apply {
                                put("noteId", attachment.noteId)
                                put("uri", attachment.uri)
                                put("name", attachment.name)
                                put("mimeType", attachment.mimeType)
                                put("createdAt", attachment.createdAt)
                                if (attachment.embeddedData != null) {
                                    put("embeddedData", attachment.embeddedData)
                                }
                            },
                        )
                    }
                },
            )
        }
        return root.toString(2)
    }

    fun decode(text: String): BackupPayload {
        val root = JSONObject(text)
        val folders = root.optJSONArray("folders").orEmptyObjects().map { item ->
            FolderEntity(
                id = item.getLong("id"),
                name = item.getString("name"),
                color = item.optLong("color", 0xFF305C4A),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        val notes = root.optJSONArray("notes").orEmptyObjects().map { item ->
            NoteEntity(
                id = item.getLong("id"),
                title = item.optString("title"),
                body = item.optString("body"),
                format = item.optString("format", "TEXT"),
                folderId = item.optLongOrNull("folderId"),
                tags = item.optString("tags"),
                color = item.optLong("color", 0xFFF8F4EA),
                pinned = item.optBoolean("pinned"),
                starred = item.optBoolean("starred"),
                trashed = item.optBoolean("trashed"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                deletedAt = item.optLongOrNull("deletedAt"),
                reminderAt = item.optLongOrNull("reminderAt"),
                syncAsBinary = item.optBoolean("syncAsBinary"),
            )
        }
        val noteIds = notes.map { it.id }.toSet()
        val attachments = root.optJSONArray("attachments").orEmptyObjects()
            .filter { it.getLong("noteId") in noteIds }
            .map { item ->
                AttachmentEntity(
                    noteId = item.getLong("noteId"),
                    uri = item.optString("uri"),
                    name = item.optString("name", "attachment"),
                    mimeType = item.optString("mimeType").ifBlank { null },
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                ).apply {
                    embeddedData = item.optString("embeddedData").ifBlank { null }
                }
            }
        return BackupPayload(notes, folders, attachments)
    }

    private fun NoteEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("format", format)
        if (folderId == null) put("folderId", JSONObject.NULL) else put("folderId", folderId)
        put("tags", tags)
        put("color", color)
        put("pinned", pinned)
        put("starred", starred)
        put("trashed", trashed)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (deletedAt == null) put("deletedAt", JSONObject.NULL) else put("deletedAt", deletedAt)
        if (reminderAt == null) put("reminderAt", JSONObject.NULL) else put("reminderAt", reminderAt)
        put("syncAsBinary", syncAsBinary)
    }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(getJSONObject(index))
        }
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null
}
