package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObsidianSyncPlannerTest {
    private val previous = MarkdownSyncState(
        remoteFingerprint = "\"remote-old\"|100",
        localHash = "local-old",
    )

    @Test
    fun bothSidesUnchanged_doesNothing() {
        assertEquals(
            ObsidianSyncAction.NONE,
            ObsidianSyncPlanner.plan("local-old", "\"remote-old\"|100", previous),
        )
    }

    @Test
    fun remoteCreated_downloads() {
        assertEquals(
            ObsidianSyncAction.DOWNLOAD,
            ObsidianSyncPlanner.plan(null, "\"remote-new\"|100", null),
        )
    }

    @Test
    fun localCreated_uploads() {
        assertEquals(
            ObsidianSyncAction.UPLOAD,
            ObsidianSyncPlanner.plan("local-new", null, null),
        )
    }

    @Test
    fun localModifiedAndRemoteUnchanged_uploads() {
        assertEquals(
            ObsidianSyncAction.UPLOAD,
            ObsidianSyncPlanner.plan("local-new", "\"remote-old\"|100", previous),
        )
    }

    @Test
    fun remoteModifiedAndLocalUnchanged_downloads() {
        assertEquals(
            ObsidianSyncAction.DOWNLOAD,
            ObsidianSyncPlanner.plan("local-old", "\"remote-new\"|100", previous),
        )
    }

    @Test
    fun bothSidesModified_requiresConflictResolution() {
        assertEquals(
            ObsidianSyncAction.CONFLICT,
            ObsidianSyncPlanner.plan("local-new", "\"remote-new\"|100", previous),
        )
    }

    @Test
    fun localDeletedAndRemoteUnchanged_deletesRemote() {
        assertEquals(
            ObsidianSyncAction.DELETE_REMOTE,
            ObsidianSyncPlanner.plan(null, "\"remote-old\"|100", previous),
        )
    }

    @Test
    fun remoteDeletedAndLocalUnchanged_deletesLocal() {
        assertEquals(
            ObsidianSyncAction.DELETE_LOCAL,
            ObsidianSyncPlanner.plan("local-old", null, previous),
        )
    }

    @Test
    fun remoteDeletedButLocalModified_uploadsLocal() {
        assertEquals(
            ObsidianSyncAction.UPLOAD,
            ObsidianSyncPlanner.plan("local-new", null, previous),
        )
    }

    @Test
    fun localDeletedButRemoteModified_downloadsRemote() {
        assertEquals(
            ObsidianSyncAction.DOWNLOAD,
            ObsidianSyncPlanner.plan(null, "\"remote-new\"|100", previous),
        )
    }

    @Test
    fun bothSidesAbsent_cleansSyncHistory() {
        assertEquals(
            ObsidianSyncAction.CLEAN_HISTORY,
            ObsidianSyncPlanner.plan(null, null, previous),
        )
    }

    @Test
    fun conflictKeepsRemoteWhenRemoteIsNewer() {
        assertEquals(
            ObsidianConflictResolution.KEEP_REMOTE,
            ObsidianSyncPlanner.resolveConflict(100, 200),
        )
    }

    @Test
    fun conflictKeepsLocalWhenLocalIsNewer() {
        assertEquals(
            ObsidianConflictResolution.KEEP_LOCAL,
            ObsidianSyncPlanner.resolveConflict(200, 100),
        )
    }

    @Test
    fun conflictKeepsBothWhenTimestampsAreEqual() {
        assertEquals(
            ObsidianConflictResolution.KEEP_BOTH,
            ObsidianSyncPlanner.resolveConflict(200, 200),
        )
    }

    @Test
    fun newLocalUploadNames_excludeFilesJustImportedFromRemote() {
        val result = ObsidianSyncPlanner.newLocalUploadNames(
            localBefore = setOf("existing.md"),
            localAfter = setOf("existing.md", "note-1.md", "note-2.md"),
            downloadedFromRemote = setOf("note-1.md"),
        )

        assertEquals(setOf("note-2.md"), result)
    }
}
