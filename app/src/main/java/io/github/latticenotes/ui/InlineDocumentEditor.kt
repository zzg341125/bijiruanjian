package io.github.latticenotes.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun InlineDocumentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    controller: DocumentEditorController,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        controller.syncExternal(value)
    }
    controller.visualEditorColor = textColor
    SideEffect {
        controller.changeListener = currentOnValueChange
    }
    BasicTextField(
        value = controller.textFieldValue,
        onValueChange = { next ->
            controller.update(next)
            currentOnValueChange(next.text)
        },
        modifier = modifier,
        textStyle = TextStyle(
            color = textColor,
            fontSize = EditorTextMetrics.bodyFontSize,
            lineHeight = EditorTextMetrics.lineHeight,
        ),
        cursorBrush = SolidColor(textColor),
        visualTransformation = controller.visualTransformation,
        maxLines = Int.MAX_VALUE,
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
    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    private var cachedColor: Color? = null
    private var cachedTransformation: MarkdownVisualTransformation? = null
    internal var changeListener: ((String) -> Unit)? = null
    internal val visualTransformation: MarkdownVisualTransformation
        get() = cachedTransformation ?: MarkdownVisualTransformation(
            textColor = cachedColor ?: Color.Black,
            accentColor = FORMAT_ACCENT,
        ).also { cachedTransformation = it }

    internal var visualEditorColor: Color
        get() = cachedColor ?: Color.Black
        set(value) {
            if (cachedColor == value) return
            cachedColor = value
            cachedTransformation = null
        }

    internal fun update(value: TextFieldValue) {
        textFieldValue = value
    }

    internal fun syncExternal(text: String) {
        val current = textFieldValue
        if (current.text == text) return
        val cursor = current.selection.end.coerceIn(0, text.length)
        textFieldValue = TextFieldValue(
            text = text,
            selection = TextRange(cursor),
        )
    }

    fun apply(action: DocumentFormatAction): Boolean {
        val current = textFieldValue
        val updated = DocumentMarkupFormatter.apply(
            text = current.text,
            selectionStart = current.selection.start,
            selectionEnd = current.selection.end,
            action = action,
        )
        if (updated.text == current.text) return false
        textFieldValue = TextFieldValue(
            text = updated.text,
            selection = TextRange(updated.selectionStart, updated.selectionEnd),
        )
        changeListener?.invoke(updated.text)
        return true
    }

    companion object {
        private val FORMAT_ACCENT = Color(0xFFC47B28)
    }
}
