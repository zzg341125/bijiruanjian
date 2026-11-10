package io.github.latticenotes.data

import io.github.latticenotes.backup.DecodedMarkdownNote
import io.github.latticenotes.backup.MarkdownNoteCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.ceil

object OnlineSyncManager {
    const val FOREGROUND_SYNC_INTERVAL_MILLIS = 30_000L
    private val syncMutex = Mutex()
    private val cooldown = SyncCooldown()

    suspend fun sync(
        repository: NotesRepository,
        settings: AppSettings,
        credentials: CredentialVault,
        force: Boolean = false,
    ): String? = syncMutex.withLock {
        if ((!settings.webDavAutoSync && !force) || settings.webDavUrl.isBlank()) {
            return@withLock null
        }
        if (cooldown.isActive()) {
            return@withLock if (force) {
                val minutes = ceil(cooldown.remainingMillis() / 60_000.0).toInt()
                "同步冷却中：坚果云刚刚限流或服务不可用，请约 $minutes 分钟后再试。"
            } else {
                null
            }
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val client = WebDavClient(
                    WebDavConfig(
                        fileUrl = settings.webDavUrl,
                        username = settings.webDavUsername,
                        password = credentials.loadWebDavPasswordOrDefault(),
                    ),
                )
                val remoteFiles = client.listMarkdownFiles()
                val remoteByName = remoteFiles.associateBy { it.name }
                val localBefore = repository.getAllMarkdownFiles()
                val states = repository.getMarkdownSyncStates().toMutableMap()
                val allNames = buildSet {
                    addAll(remoteByName.keys)
                    addAll(localBefore.keys)
                    addAll(states.keys)
                }
                val actions = allNames.associateWith { name ->
                    ObsidianSyncPlanner.plan(
                        localHash = localBefore[name]?.sha256(),
                        remoteFingerprint = remoteByName[name]?.fingerprint,
                        previous = states[name],
                    )
                }
                val plannedDeletionCount = actions.values.count {
                    it == ObsidianSyncAction.DELETE_LOCAL ||
                        it == ObsidianSyncAction.DELETE_REMOTE
                }
                if (
                    ObsidianSyncSafety.shouldBlockDeletions(
                        plannedDeletionCount,
                        allNames.size,
                    )
                ) {
                    return@runCatching buildString {
                        append("同步已暂停：本轮计划删除 $plannedDeletionCount/")
                        append("${allNames.size} 个文件，超过删除保护阈值。")
                        append("请检查 WebDAV 目录是否为空或同步地址是否正确。")
                    }
                }
                val importedNames = mutableSetOf<String>()
                val failedRemoteNames = mutableSetOf<String>()
                val successfulDownloadNames = mutableSetOf<String>()
                val localConflictWinners = mutableSetOf<String>()
                var conflictCount = 0
                var conflictCopies = 0
                suspend fun importRemote(
                    name: String,
                    document: DecodedMarkdownNote,
                    forceOverwrite: Boolean,
                ): Boolean {
                    val result = runCatching {
                        repository.importMarkdownDocuments(
                            listOf(MarkdownNoteImport(name, document)),
                            forceOverwrite = forceOverwrite,
                        )
                    }
                    if (result.isFailure) failedRemoteNames += name
                    return result.isSuccess
                }

                actions.filterValues { it == ObsidianSyncAction.CLEAN_HISTORY }
                    .keys
                    .forEach(states::remove)

                val filesToDownload = actions
                    .filterValues {
                        it == ObsidianSyncAction.DOWNLOAD || it == ObsidianSyncAction.CONFLICT
                    }
                    .keys
                    .mapNotNull(remoteByName::get)
                val downloadResult = client.downloadMarkdownFiles(filesToDownload)
                failedRemoteNames += downloadResult.failedNames
                downloadResult.contents.forEach { (name, content) ->
                    val remote = remoteByName[name] ?: return@forEach
                    val contentHash = content.sha256()
                    val localContent = localBefore[name]
                    if (localContent != null && localContent.sha256() == contentHash) {
                        successfulDownloadNames += name
                        return@forEach
                    }

                    val remoteDocument = MarkdownNoteCodec.decode(content, remote.name)
                    when (actions[name]) {
                        ObsidianSyncAction.DOWNLOAD -> {
                            if (remoteDocument != null) {
                                if (importRemote(name, remoteDocument, forceOverwrite = true)) {
                                    importedNames += name
                                    successfulDownloadNames += name
                                }
                            }
                        }
                        ObsidianSyncAction.CONFLICT -> {
                            conflictCount++
                            val localDocument = localContent?.let {
                                MarkdownNoteCodec.decode(it, name)
                            }
                            if (remoteDocument == null) {
                                localConflictWinners += name
                            } else {
                                val localUpdatedAt =
                                    localDocument?.note?.updatedAt ?: Long.MIN_VALUE
                                val remoteUpdatedAt = remoteDocument.note.updatedAt
                                when (
                                    ObsidianSyncPlanner.resolveConflict(
                                        localUpdatedAt,
                                        remoteUpdatedAt,
                                    )
                                ) {
                                    ObsidianConflictResolution.KEEP_REMOTE -> {
                                        if (
                                            importRemote(
                                                name,
                                                remoteDocument,
                                                forceOverwrite = true,
                                            )
                                        ) {
                                            importedNames += name
                                            successfulDownloadNames += name
                                        }
                                    }
                                    ObsidianConflictResolution.KEEP_LOCAL -> {
                                        localConflictWinners += name
                                    }
                                    ObsidianConflictResolution.KEEP_BOTH -> {
                                        localDocument?.note?.id?.let {
                                            repository.duplicateNote(it)
                                        }
                                        if (
                                            importRemote(
                                                name,
                                                remoteDocument,
                                                forceOverwrite = true,
                                            )
                                        ) {
                                            importedNames += name
                                            successfulDownloadNames += name
                                            conflictCopies++
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                val deleteLocalNames = actions
                    .filterValues { it == ObsidianSyncAction.DELETE_LOCAL }
                    .keys
                var deletedLocalCount = 0
                deleteLocalNames.forEach { name ->
                    val noteId = localBefore[name]?.let {
                        MarkdownNoteCodec.decode(it, name)?.note?.id
                    } ?: return@forEach
                    repository.moveToTrashFromRemote(noteId)
                    states.remove(name)
                    deletedLocalCount++
                }

                var deletedRemoteCount = 0
                for (name in actions.filterValues {
                    it == ObsidianSyncAction.DELETE_REMOTE
                }.keys) {
                    val result = runCatching {
                        client.deleteFile(client.markdownFileUrl(name))
                    }
                    if (result.isSuccess) {
                        states.remove(name)
                        deletedRemoteCount++
                    } else {
                        failedRemoteNames += name
                        if ((result.exceptionOrNull() as? WebDavHttpException)?.code == 429) {
                            break
                        }
                    }
                }

                val localAfter = repository.getAllMarkdownFiles()
                successfulDownloadNames.forEach { name ->
                    val remote = remoteByName[name] ?: return@forEach
                    val localHash = localAfter[name]?.sha256()
                        ?: downloadResult.contents[name]?.sha256()
                        ?: return@forEach
                    states[name] = MarkdownSyncState(remote.fingerprint, localHash)
                }

                val localNamesToUpload = buildSet {
                    addAll(
                        actions.filterValues { it == ObsidianSyncAction.UPLOAD }.keys,
                    )
                    addAll(localConflictWinners)
                    addAll(
                        ObsidianSyncPlanner.newLocalUploadNames(
                            localBefore = localBefore.keys,
                            localAfter = localAfter.keys,
                            downloadedFromRemote = successfulDownloadNames,
                        ),
                    )
                }
                val uploadCandidates = linkedMapOf<String, String>()
                localNamesToUpload.forEach { name ->
                    val content = localAfter[name] ?: return@forEach
                    if (name !in failedRemoteNames) uploadCandidates[name] = content
                }
                val uploadResult = client.uploadMarkdownFiles(uploadCandidates)
                uploadCandidates.forEach { (name, content) ->
                    if (name !in uploadResult.failedNames) {
                        states[name] = MarkdownSyncState(
                            remoteFingerprint =
                                uploadResult.remoteFiles[name]?.fingerprint.orEmpty(),
                            localHash = content.sha256(),
                        )
                    }
                }
                val failedNames = (failedRemoteNames + uploadResult.failedNames).toSet()
                repository.saveMarkdownSyncStates(states)
                val binaryResult = syncBinaryFiles(client, repository)
                buildString {
                    append("Markdown 同步完成：远端 ${remoteFiles.size} 个")
                    append("，扫描 ${client.directoryPageCount} 页")
                    if (client.skippedNonNoteFileCount > 0) {
                        append("，附件文件 ${client.skippedNonNoteFileCount} 个")
                    }
                    append("，导入/更新 ${importedNames.size} 个")
                    append("，上传 ${uploadResult.uploadedCount} 个")
                    append("，附件导入/更新 ${binaryResult.importedCount} 个")
                    append("，冲突 $conflictCount 个")
                    if (conflictCopies > 0) append("（保留 $conflictCopies 个副本）")
                    append("，删除本地 $deletedLocalCount 个")
                    append("、远端 $deletedRemoteCount 个")
                    if (failedNames.isNotEmpty()) {
                        append("，失败 ${failedNames.size} 个，下轮自动重试")
                    }
                    if (binaryResult.failedCount > 0) {
                        append("，附件失败 ${binaryResult.failedCount} 个")
                    }
                }
            }.getOrElse { error ->
                if (
                    error is WebDavHttpException &&
                    error.code in setOf(429, 503)
                ) {
                    cooldown.block(error.retryAfterSeconds)
                    val minutes = ceil(cooldown.remainingMillis() / 60_000.0).toInt()
                    "WebDAV 暂时受限（HTTP ${error.code}）：已暂停自动同步约 $minutes 分钟，请稍后再试。"
                } else {
                    "WebDAV 自动同步失败：${error.message ?: "网络错误"}"
                }
            }
        }
    }

    private suspend fun syncBinaryFiles(
        client: WebDavClient,
        repository: NotesRepository,
    ): BinarySyncResult {
        val remoteByName = client.binaryFiles.associateBy { it.name }
        val states = repository.getBinarySyncStates().toMutableMap()
        val allNames = buildSet {
            addAll(remoteByName.keys)
            addAll(states.keys)
        }
        var importedCount = 0
        var failedCount = 0
        try {
            for (name in allNames) {
                val state = states[name]
                val localBytes = state?.let { repository.readBinaryFile(it) }
                val localHash = localBytes?.sha256()
                val remote = remoteByName[name]
                val action = ObsidianSyncPlanner.plan(
                    localHash = localHash,
                    remoteFingerprint = remote?.fingerprint,
                    previous = state?.let {
                        MarkdownSyncState(it.remoteFingerprint, it.localHash)
                    },
                )
                try {
                    when (action) {
                        ObsidianSyncAction.DOWNLOAD, ObsidianSyncAction.CONFLICT -> {
                            val remoteFile = remote ?: continue
                            val bytes = client.downloadBytes(remoteFile.url)
                            val hash = bytes.sha256()
                            if (state?.localHash == hash) {
                                states[name] = state.copy(
                                    remoteFingerprint = remoteFile.fingerprint,
                                )
                            } else {
                                states[name] = repository.storeRemoteBinaryFile(
                                    remoteName = name,
                                    bytes = bytes,
                                    existing = state,
                                    remoteFingerprint = remoteFile.fingerprint,
                                )
                                importedCount++
                            }
                        }
                        ObsidianSyncAction.UPLOAD -> {
                            if (state == null || localBytes == null) continue
                            client.uploadBytes(
                                client.markdownFileUrl(name),
                                localBytes,
                                mimeType = null,
                            )
                            states[name] = state.copy(
                                remoteFingerprint = "",
                                localHash = localHash ?: state.localHash,
                            )
                        }
                        ObsidianSyncAction.DELETE_LOCAL -> {
                            if (state != null) {
                                repository.moveBinaryToTrash(state.noteId)
                                states.remove(name)
                            }
                        }
                        ObsidianSyncAction.DELETE_REMOTE -> {
                            client.deleteFile(client.markdownFileUrl(name))
                            states.remove(name)
                        }
                        ObsidianSyncAction.CLEAN_HISTORY -> states.remove(name)
                        ObsidianSyncAction.NONE -> Unit
                    }
                } catch (error: Throwable) {
                    if (error is WebDavHttpException && error.code in setOf(429, 503)) {
                        throw error
                    }
                    failedCount++
                }
            }
        } finally {
            repository.saveBinarySyncStates(states)
        }
        return BinarySyncResult(importedCount, failedCount)
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
}

private data class BinarySyncResult(
    val importedCount: Int,
    val failedCount: Int,
)
