package io.github.latticenotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun keepsTheMostRecentTwentyUndoSteps() {
        val history = EditorHistory()

        repeat(21) { index ->
            history.record(
                EditorTextSnapshot("", index.toString()),
                mergeKey = null,
                timeMillis = index.toLong(),
            )
        }

        var current = EditorTextSnapshot("", "20")
        repeat(20) {
            current = history.undo(current) ?: error("撤销步数不足 20")
        }

        assertEquals(EditorTextSnapshot("", "1"), current)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun consecutiveDeletionIsUndoneInOneStep() {
        val history = EditorHistory()
        val original = EditorTextSnapshot("标题", "准备删除的文字")

        history.record(original, mergeKey = "body-delete", timeMillis = 1_000)
        history.record(
            original.copy(body = "准备删除的文"),
            mergeKey = "body-delete",
            timeMillis = 1_200,
        )
        history.record(
            original.copy(body = "准备删除的"),
            mergeKey = "body-delete",
            timeMillis = 1_400,
        )

        val restored = history.undo(original.copy(body = "准备删除"))

        assertEquals(original, restored)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun pauseStartsNewUndoStep() {
        val history = EditorHistory()
        val original = EditorTextSnapshot("", "abcdef")

        history.record(original, mergeKey = "body-delete", timeMillis = 1_000)
        history.record(
            original.copy(body = "abcde"),
            mergeKey = "body-delete",
            timeMillis = 4_000,
        )
        val restored = history.undo(original.copy(body = "abcd"))

        assertEquals(original.copy(body = "abcde"), restored)
        assertTrue(history.canUndo)
    }
}
