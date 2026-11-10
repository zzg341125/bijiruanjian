package io.github.latticenotes.data

object ObsidianSyncSafety {
    private const val MIN_FILES_FOR_DELETE_PROTECTION = 5

    fun shouldBlockDeletions(deletionCount: Int, totalCount: Int): Boolean =
        totalCount >= MIN_FILES_FOR_DELETE_PROTECTION &&
            deletionCount * 2 > totalCount
}
