package io.github.latticenotes.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp

@Composable
fun InlineDocumentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    controller: DocumentEditorController,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val textSizePx = with(density) { 20.sp.toPx() }
    val lineHeightPx = with(density) { 32.sp.toPx() }

    fun applyTypography(editor: EditText) {
        val oldSize = editor.textSize
        editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        if (oldSize == textSizePx && editor.tag == lineHeightPx) return
        val metrics = editor.paint.fontMetrics
        val fontHeight = metrics.descent - metrics.ascent
        editor.setLineSpacing((lineHeightPx - fontHeight).coerceAtLeast(0f), 1f)
        editor.tag = lineHeightPx
    }

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { context ->
            DocumentEditText(context).apply {
                controller.attach(this)
                background = null
                gravity = Gravity.TOP or Gravity.START
                includeFontPadding = false
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isSingleLine = false
                isNestedScrollingEnabled = false
                isScrollContainer = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setPadding(0, 0, 0, 0)
                setTextColor(textColor.toArgb())
                applyTypography(this)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        text: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) = Unit

                    override fun onTextChanged(
                        text: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) = Unit

                    override fun afterTextChanged(text: Editable?) {
                        if (!suppressCallback) {
                            currentOnValueChange(text?.toString().orEmpty())
                        }
                    }
                })
            }
        },
        update = { editor ->
            applyTypography(editor)
            val current = editor.text?.toString().orEmpty()
            val initialBinding = !editor.hasBoundInitialValue
            if (current != value) {
                val oldSelection = editor.selectionStart.coerceAtLeast(0)
                val selection = when {
                    initialBinding -> 0
                    value.startsWith(current) -> value.length
                    else -> oldSelection.coerceAtMost(value.length)
                }
                editor.suppressCallback = true
                editor.setText(value)
                editor.setSelection(selection)
                editor.suppressCallback = false
            }
            if (initialBinding) {
                editor.hasBoundInitialValue = true
                editor.setSelection(0)
                editor.post { editor.scrollTo(0, 0) }
            }
        },
    )
}

enum class DocumentFormatAction {
    HEADING,
    CENTER,
    BULLET,
    BOLD,
    QUOTE,
    TODO,
}

class DocumentEditorController {
    private var editor: EditText? = null

    fun attach(editor: EditText) {
        this.editor = editor
    }

    fun apply(action: DocumentFormatAction): Boolean {
        val editor = editor ?: return false
        val text = editor.text ?: return false
        val selectionStart = editor.selectionStart.coerceAtLeast(0)
        val selectionEnd = editor.selectionEnd.coerceAtLeast(selectionStart)
        editor.beginBatchEdit()
        when (action) {
            DocumentFormatAction.HEADING -> applyLinePrefix(editor, "# ")
            DocumentFormatAction.CENTER -> applyCenteredLines(editor)
            DocumentFormatAction.BULLET -> applyLinePrefix(editor, "- ")
            DocumentFormatAction.QUOTE -> applyLinePrefix(editor, "> ")
            DocumentFormatAction.TODO -> applyLinePrefix(editor, "- [ ] ")
            DocumentFormatAction.BOLD -> applyBold(editor, selectionStart, selectionEnd)
        }
        editor.endBatchEdit()
        editor.requestFocus()
        return true
    }

    private fun applyBold(editor: EditText, start: Int, end: Int) {
        val text = editor.text
        if (start == end) {
            editor.text.replace(start, end, "**粗体**")
            editor.setSelection(start + 2, start + 4)
            return
        }
        val selected = text.subSequence(start, end).toString()
        editor.text.replace(start, end, "**$selected**")
        editor.setSelection(start + 2, start + 2 + selected.length)
    }

    private fun applyLinePrefix(editor: EditText, prefix: String) {
        val text = editor.text
        val (start, end) = selectedLineRange(
            text,
            editor.selectionStart.coerceAtLeast(0),
            editor.selectionEnd.coerceAtLeast(editor.selectionStart.coerceAtLeast(0)),
        )
        val lines = text.subSequence(start, end).toString().split('\n')
        val removePrefix = lines.isNotEmpty() && lines.all {
            it.isBlank() || it.startsWith(prefix)
        }
        val replacement = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                removePrefix -> line.removePrefix(prefix)
                else -> prefix + line
            }
        }
        editor.text.replace(start, end, replacement)
        editor.setSelection(start, start + replacement.length)
    }

    private fun applyCenteredLines(editor: EditText) {
        val text = editor.text
        val (start, end) = selectedLineRange(
            text,
            editor.selectionStart.coerceAtLeast(0),
            editor.selectionEnd.coerceAtLeast(editor.selectionStart.coerceAtLeast(0)),
        )
        val lines = text.subSequence(start, end).toString().split('\n')
        val removeCenter = lines.isNotEmpty() && lines.all {
            it.isBlank() || (it.startsWith("<center>") && it.endsWith("</center>"))
        }
        val replacement = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                removeCenter -> line.removePrefix("<center>").removeSuffix("</center>")
                else -> "<center>$line</center>"
            }
        }
        editor.text.replace(start, end, replacement)
        editor.setSelection(start, start + replacement.length)
    }

    private fun selectedLineRange(text: CharSequence, start: Int, end: Int): Pair<Int, Int> {
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        val lineStart = if (safeStart == 0) {
            0
        } else {
            text.lastIndexOf('\n', safeStart - 1).let { if (it < 0) 0 else it + 1 }
        }
        val nextBreak = text.indexOf('\n', safeEnd)
        val lineEnd = if (nextBreak < 0) text.length else nextBreak
        return lineStart to lineEnd
    }
}

private class DocumentEditText(context: Context) : EditText(context) {
    var suppressCallback: Boolean = false
    var hasBoundInitialValue: Boolean = false
}
