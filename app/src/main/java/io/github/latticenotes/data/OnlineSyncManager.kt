package io.github.latticenotes.data

import io.github.latticenotes.backup.MarkdownNoteCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object OnlineSyncManager {
    const val FOREGROUND_SYNC_INTERVAL_MILLIS = 30_000L
    private const val REQUEST_SPACING_MILLIS = 150L
    private val syncMutex = Mutex()

    suspend fun sync(
        repository: NotesRepository,
        settings: AppSettings,
        credentials: CredentialVault,
        force: Boolean = false,
    ): String? = syncMutex.withLock {
        if ((!settings.webDavAutoSync && !force) || settings.webDavUrl.isBlank()) {
            return@withLock null
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
                val importedNames = mutableSetOf<String>()
                val failedRemoteNames = mutableSetOf<String>()

                remoteFiles.forEach { remote ->
                    val localContent = localBefore[remote.name]
                    val state = states[remote.name]
                    val remoteChanged = state?.remoteFingerprint != remote.fingerprint
                    if (!remoteChanged && localContent != null) return@forEach

                    val result = runCatching {
                        client.downloadFrom(remote.url)
                    }
                    result.onSuccess { content ->
                        val contentHash = content.sha256()
                        if (state?.localHash == contentHash) {
                            states[remote.name] = state.copy(remoteFingerprint = remote.fingerprint)
                            return@onSuccess
                        }
                        MarkdownNoteCodec.decode(content, remote.name)?.let { document ->
                            repository.importMarkdownDocuments(
                                listOf(MarkdownNoteImport(remote.name, document)),
                            )
                            importedNames += remote.name
                        }
                    }.onFailure {
                        failedRemoteNames += remote.name
                    }
                    delay(REQUEST_SPACING_MILLIS)
                }

                val localAfter = repository.getAllMarkdownFiles()
                var uploaded = 0
                localAfter.forEach { (name, content) ->
                    val remote = remoteByName[name]
                    val state = states[name]
                    val localHash = content.sha256()
                    val localChanged = state?.localHash != localHash
                    val remoteChanged = state?.remoteFingerprint != remote?.fingerprint
                    val shouldUpload = remote == null ||
                        localChanged ||
                        remoteChanged ||
                        name in importedNames
                    if (shouldUpload && name !in failedRemoteNames) {
                        client.uploadTo(client.markdownFileUrl(name), content)
                        states[name] = MarkdownSyncState(
                            remoteFingerprint = remote?.fingerprint.orEmpty(),
                            localHash = localHash,
                        )
                        uploaded++
                        delay(REQUEST_SPACING_MILLIS)
                    }
                }
                repository.saveMarkdownSyncStates(states)
                buildString {
                    append("Markdown 同步完成：远端 ${remoteFiles.size} 个")
                    append("，导入/更新 ${importedNames.size} 个")
                    append("，上传 $uploaded 个")
                    if (failedRemoteNames.isNotEmpty()) {
                        append("，失败 ${failedRemoteNames.size} 个，下轮自动重试")
                    }
                }
            }.getOrElse { error ->
                "WebDAV 自动同步失败：${error.message ?: "网络错误"}"
            }
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
