package io.github.latticenotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineDocumentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    controller: DocumentEditorController,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var focused by remember { mutableStateOf(false) }
    var latestLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fieldValue = controller.textFieldValue
    LaunchedEffect(value) {
        controller.syncExternal(value)
    }
    LaunchedEffect(fieldValue.selection, fieldValue.text, focused, imeBottom, latestLayout) {
        val layout = latestLayout ?: return@LaunchedEffect
        if (!focused) return@LaunchedEffect
        delay(40)
        val transformedCursor = controller.visualTransformation
            .filter(AnnotatedString(fieldValue.text))
            .offsetMapping
            .originalToTransformed(fieldValue.selection.end.coerceIn(0, fieldValue.text.length))
        val line = layout.getLineForOffset(transformedCursor.coerceIn(0, layout.layoutInput.text.length))
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = 0f,
                top = layout.getLineTop(line),
                right = layout.size.width.toFloat(),
                bottom = layout.getLineBottom(line) + 8f,
            ),
        )
    }
    controller.visualEditorColor = textColor
    SideEffect {
        controller.changeListener = currentOnValueChange
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = { next ->
            controller.update(next)
            currentOnValueChange(next.text)
        },
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(
            color = textColor,
            fontSize = EditorTextMetrics.bodyFontSize,
            lineHeight = EditorTextMetrics.lineHeight,
        ),
        cursorBrush = SolidColor(textColor),
        interactionSource = interactionSource,
        visualTransformation = controller.visualTransformation,
        onTextLayout = { latestLayout = it },
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
