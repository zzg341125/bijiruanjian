package io.github.latticenotes.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianSyncSafetyTest {
    @Test
    fun blocksDeletionWhenMoreThanHalfOfLargeVaultWouldDisappear() {
        assertTrue(ObsidianSyncSafety.shouldBlockDeletions(deletionCount = 4, totalCount = 6))
    }

    @Test
    fun allowsDeletionAtHalfOfLargeVault() {
        assertFalse(ObsidianSyncSafety.shouldBlockDeletions(deletionCount = 3, totalCount = 6))
    }

    @Test
    fun allowsSmallDeleteBatchesForNormalNoteCleanup() {
        assertFalse(ObsidianSyncSafety.shouldBlockDeletions(deletionCount = 4, totalCount = 4))
    }
}
