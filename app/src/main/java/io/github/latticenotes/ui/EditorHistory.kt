package io.github.latticenotes.ui

import androidx.compose.runtime.mutableStateListOf

data class EditorTextSnapshot(
    val title: String,
    val body: String,
)

class EditorHistory(private val maximumEntries: Int = 100) {
    private val undoStack = mutableStateListOf<EditorTextSnapshot>()
    private val redoStack = mutableStateListOf<EditorTextSnapshot>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun record(current: EditorTextSnapshot) {
        if (undoStack.lastOrNull() == current) return
        undoStack.add(current)
        if (undoStack.size > maximumEntries) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo(current: EditorTextSnapshot): EditorTextSnapshot? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(current)
        return previous
    }

    fun redo(current: EditorTextSnapshot): EditorTextSnapshot? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(current)
        return next
    }
}
