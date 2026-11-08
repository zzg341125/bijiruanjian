package io.github.latticenotes.data

import io.github.latticenotes.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object OnlineSyncManager {
    private val attemptedThisProcess = AtomicBoolean(false)

    suspend fun syncOnLaunch(
        repository: NotesRepository,
        settings: AppSettings,
        credentials: CredentialVault,
    ): String? = withContext(Dispatchers.IO) {
        if (!settings.webDavAutoSync || settings.webDavUrl.isBlank()) return@withContext null
        if (!attemptedThisProcess.compareAndSet(false, true)) return@withContext null

        runCatching {
            val client = WebDavClient(
                WebDavConfig(
                    fileUrl = settings.webDavUrl,
                    username = settings.webDavUsername,
                    password = credentials.loadWebDavPassword(),
                ),
            )
            client.downloadOrNull()?.takeIf { it.isNotBlank() }?.let {
                repository.mergeBackup(BackupManager.decode(it))
            }
            client.upload(BackupManager.encode(repository.getAllForBackup()))
            "WebDAV 自动同步完成"
        }.getOrElse { error ->
            "WebDAV 自动同步失败：${error.message ?: "网络错误"}"
        }
    }
}
