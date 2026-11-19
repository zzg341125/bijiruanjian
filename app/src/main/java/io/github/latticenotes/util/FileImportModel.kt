package io.github.latticenotes.util

enum class ImportedNoteKind {
    MARKDOWN,
    TEXT,
    ATTACHMENT,
}

data class LocalImportResult(
    val fileName: String,
    val noteId: Long? = null,
    val kind: ImportedNoteKind? = null,
    val errorMessage: String? = null,
) {
    val succeeded: Boolean
        get() = noteId != null && errorMessage == null
}

data class LocalImportBatchResult(
    val results: List<LocalImportResult>,
) {
    val importedCount: Int
        get() = results.count(LocalImportResult::succeeded)

    val failedCount: Int
        get() = results.count { !it.succeeded }

    fun summaryMessage(): String {
        if (results.isEmpty()) return "未选择文件"
        if (importedCount == 0) {
            return "导入失败：${results.firstNotNullOfOrNull { it.errorMessage } ?: "无法读取文件"}"
        }
        val notes = results.count {
            it.succeeded &&
                (it.kind == ImportedNoteKind.MARKDOWN || it.kind == ImportedNoteKind.TEXT)
        }
        val attachments = results.count {
            it.succeeded && it.kind == ImportedNoteKind.ATTACHMENT
        }
        return buildString {
            append("已导入 ").append(importedCount).append(" 个文件（记事 ")
            append(notes).append(" 个，附件 ").append(attachments).append(" 个）")
            if (failedCount > 0) {
                append("；").append(failedCount).append(" 个失败：")
                append(
                    results.firstOrNull { !it.succeeded }?.errorMessage
                        ?: "无法读取文件",
                )
            }
        }
    }
}

object FileImportModel {
    private val markdownExtensions = setOf("md", "markdown")
    private val textExtensions = setOf(
        "txt",
        "text",
        "csv",
        "json",
        "xml",
        "html",
        "htm",
    )
    private val knownMimeTypes = mapOf(
        "md" to "text/markdown",
        "markdown" to "text/markdown",
        "txt" to "text/plain",
        "csv" to "text/csv",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "htm" to "text/html",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "zip" to "application/zip",
    )

    fun kindFor(fileName: String, mimeType: String?): ImportedNoteKind {
        val extension = extensionOf(fileName)
        val normalizedMime = normalizedMime(mimeType)
        return when {
            extension in markdownExtensions -> ImportedNoteKind.MARKDOWN
            extension in textExtensions -> ImportedNoteKind.TEXT
            normalizedMime == "text/markdown" ||
                normalizedMime == "text/x-markdown" -> ImportedNoteKind.MARKDOWN
            normalizedMime.startsWith("text/") -> ImportedNoteKind.TEXT
            normalizedMime == "application/json" ||
                normalizedMime == "application/xml" -> ImportedNoteKind.TEXT
            else -> ImportedNoteKind.ATTACHMENT
        }
    }

    fun titleFor(fileName: String): String {
        val leaf = leafName(fileName)
        val title = if (leaf.contains('.')) {
            leaf.substringBeforeLast('.')
        } else {
            leaf
        }
        return title.trim().ifBlank { "导入文件" }
    }

    fun mimeTypeFor(fileName: String, reportedMimeType: String?): String? {
        val reported = normalizedMime(reportedMimeType).ifBlank { null }
        val known = knownMimeTypes[extensionOf(fileName)]
        return if (reported == null || reported == "application/octet-stream") {
            known ?: reported
        } else {
            reported
        }
    }

    fun uniqueRemoteName(preferredName: String, usedNames: Set<String>): String {
        val leaf = leafName(preferredName)
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim(' ', '.')
            .take(180)
            .ifBlank { "attachment" }
        if (leaf !in usedNames) return leaf

        val separator = leaf.lastIndexOf('.').takeIf { it > 0 }
        val base = separator?.let { leaf.substring(0, it) } ?: leaf
        val extension = separator?.let { leaf.substring(it) }.orEmpty()
        var index = 2
        var candidate = "$base ($index)$extension"
        while (candidate in usedNames) {
            index++
            candidate = "$base ($index)$extension"
        }
        return candidate
    }

    private fun normalizedMime(mimeType: String?): String =
        mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

    private fun extensionOf(fileName: String): String =
        leafName(fileName).substringAfterLast('.', "").lowercase()

    private fun leafName(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').trim()
}
