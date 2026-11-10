package io.github.latticenotes.data

enum class ObsidianSyncAction {
    NONE,
    UPLOAD,
    DOWNLOAD,
    CONFLICT,
    DELETE_REMOTE,
    DELETE_LOCAL,
    CLEAN_HISTORY,
}

enum class ObsidianConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH,
}

object ObsidianSyncPlanner {
    fun plan(
        localHash: String?,
        remoteFingerprint: String?,
        previous: MarkdownSyncState?,
    ): ObsidianSyncAction {
        if (localHash == null && remoteFingerprint == null) {
            return ObsidianSyncAction.CLEAN_HISTORY
        }
        if (localHash == null) {
            val localDeleted = previous != null &&
                previous.remoteFingerprint.isNotBlank() &&
                previous.remoteFingerprint == remoteFingerprint
            return if (localDeleted) {
                ObsidianSyncAction.DELETE_REMOTE
            } else {
                ObsidianSyncAction.DOWNLOAD
            }
        }
        if (remoteFingerprint == null) {
            val remoteDeleted = previous != null &&
                previous.localHash.isNotBlank() &&
                previous.localHash == localHash
            return if (remoteDeleted) {
                ObsidianSyncAction.DELETE_LOCAL
            } else {
                ObsidianSyncAction.UPLOAD
            }
        }
        if (previous == null) return ObsidianSyncAction.CONFLICT

        val localChanged = previous.localHash != localHash
        val remoteChanged = previous.remoteFingerprint != remoteFingerprint
        return when {
            !localChanged && !remoteChanged -> ObsidianSyncAction.NONE
            localChanged && !remoteChanged -> ObsidianSyncAction.UPLOAD
            !localChanged && remoteChanged -> ObsidianSyncAction.DOWNLOAD
            else -> ObsidianSyncAction.CONFLICT
        }
    }

    fun resolveConflict(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
    ): ObsidianConflictResolution = when {
        remoteUpdatedAt > localUpdatedAt -> ObsidianConflictResolution.KEEP_REMOTE
        localUpdatedAt > remoteUpdatedAt -> ObsidianConflictResolution.KEEP_LOCAL
        else -> ObsidianConflictResolution.KEEP_BOTH
    }

    fun newLocalUploadNames(
        localBefore: Set<String>,
        localAfter: Set<String>,
        downloadedFromRemote: Set<String>,
    ): Set<String> = (localAfter - localBefore) - downloadedFromRemote
}
