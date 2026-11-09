package io.github.latticenotes.ui

import androidx.compose.runtime.mutableStateListOf

data class EditorTextSnapshot(
    val title: String,
    val body: String,
)

class EditorHistory(private val maximumEntries: Int = 20) {
    private val undoStack = mutableStateListOf<EditorTextSnapshot>()
    private val redoStack = mutableStateListOf<EditorTextSnapshot>()
    private var lastMergeKey: String? = null
    private var lastRecordedAt: Long = Long.MIN_VALUE

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun record(
        current: EditorTextSnapshot,
        mergeKey: String? = null,
        timeMillis: Long = System.currentTimeMillis(),
    ) {
        if (undoStack.lastOrNull() == current) return
        val canMerge = mergeKey != null &&
            mergeKey == lastMergeKey &&
            timeMillis - lastRecordedAt <= 2_500
        if (!canMerge) {
            undoStack.add(current)
            if (undoStack.size > maximumEntries) undoStack.removeAt(0)
        }
        lastMergeKey = mergeKey
        lastRecordedAt = timeMillis
        redoStack.clear()
    }

    fun undo(current: EditorTextSnapshot): EditorTextSnapshot? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(current)
        clearMerge()
        return previous
    }

    fun redo(current: EditorTextSnapshot): EditorTextSnapshot? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(current)
        clearMerge()
        return next
    }

    private fun clearMerge() {
        lastMergeKey = null
        lastRecordedAt = Long.MIN_VALUE
    }
}
