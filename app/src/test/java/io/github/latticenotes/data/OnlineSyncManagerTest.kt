package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineSyncManagerTest {
    @Test
    fun foregroundSyncIntervalIsThirtySeconds() {
        assertEquals(30_000L, OnlineSyncManager.FOREGROUND_SYNC_INTERVAL_MILLIS)
    }

    @Test
    fun onlyCompletedSyncMessagesAreReportedAsSuccessful() {
        assertEquals(
            true,
            OnlineSyncManager.isSuccessfulSyncMessage(
                "Markdown 同步完成：远端 5 个，导入/更新 1 个",
            ),
        )
        assertEquals(
            false,
            OnlineSyncManager.isSuccessfulSyncMessage(
                "WebDAV 自动同步失败：连接超时",
            ),
        )
    }
}
