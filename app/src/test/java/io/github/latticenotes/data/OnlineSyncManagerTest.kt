package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineSyncManagerTest {
    @Test
    fun foregroundSyncIntervalIsThirtySeconds() {
        assertEquals(30_000L, OnlineSyncManager.FOREGROUND_SYNC_INTERVAL_MILLIS)
    }
}
