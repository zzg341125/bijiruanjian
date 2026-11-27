package io.github.latticenotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineDocumentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    controller: DocumentEditorController,
    textColor: Color,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLineHeightChanged: (Float) -> Unit = {},
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnLineHeightChanged by rememberUpdatedState(onLineHeightChanged)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var focused by remember { mutableStateOf(false) }
    var cursorVisible by remember { mutableStateOf(true) }
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
    LaunchedEffect(focused, fieldValue.selection, fieldValue.text) {
        cursorVisible = true
        if (focused) {
            while (true) {
                delay(500)
                cursorVisible = !cursorVisible
            }
        }
    }
    controller.visualEditorColor = textColor
    SideEffect {
        controller.changeListener = currentOnValueChange
    }
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color(0xFFC47B28),
            backgroundColor = textColor.copy(alpha = 0.3f),
        ),
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                controller.update(next)
                currentOnValueChange(next.text)
            },
            modifier = modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            textStyle = TextStyle(
                color = textColor,
                fontSize = EditorTextMetrics.bodyFontSize,
                lineHeight = EditorTextMetrics.lineHeight,
                lineHeightStyle = EditorTextMetrics.lineHeightStyle,
            ),
            cursorBrush = SolidColor(Color.Transparent),
            interactionSource = interactionSource,
            visualTransformation = controller.visualTransformation,
            onTextLayout = { layout ->
                latestLayout = layout
                if (layout.lineCount > 0) {
                    currentOnLineHeightChanged(layout.getLineBottom(0) - layout.getLineTop(0))
                }
            },
            decorationBox = { innerTextField ->
                Box {
                    innerTextField()
                    val layout = latestLayout
                    if (focused && cursorVisible && layout != null && fieldValue.selection.collapsed) {
                        val transformed = controller.visualTransformation
                            .filter(AnnotatedString(fieldValue.text))
                        val cursorOffset = transformed.offsetMapping
                            .originalToTransformed(
                                fieldValue.selection.end.coerceIn(0, fieldValue.text.length),
                            )
                            .let {
                                EditorTextMetrics.clampCursorOffset(
                                    it,
                                    layout.layoutInput.text.length,
                                )
                            }
                        val line = layout.getLineForOffset(cursorOffset)
                        val lineTop = layout.getLineTop(line)
                        val lineBottom = layout.getLineBottom(line)
                        val cursorFontSize = transformed.text.spanStyles
                            .firstOrNull {
                                cursorOffset >= it.start &&
                                    cursorOffset <= it.end &&
                                    it.item.fontSize.value.isFinite()
                            }
                            ?.item
                            ?.fontSize
                            ?.value
                            ?: EditorTextMetrics.bodyFontSize.value
                        val cursorHeight = with(density) {
                            EditorTextMetrics.cursorHeightDp(
                                fontScale = fontScale,
                                fontSizeSp = cursorFontSize,
                            ).toPx()
                        }
                        Canvas(Modifier.matchParentSize()) {
                            val x = layout.getCursorRect(cursorOffset).left
                            val top = (lineTop + (lineBottom - lineTop - cursorHeight) / 2f)
                                .coerceIn(lineTop, (lineBottom - cursorHeight).coerceAtLeast(lineTop))
                            drawRoundRect(
                                color = textColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                                size = Size(1.5.dp.toPx(), cursorHeight),
                                cornerRadius = CornerRadius(0.75.dp.toPx()),
                            )
                        }
                    }
                }
            },
            maxLines = Int.MAX_VALUE,
        )
    }
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
    private var colorSelection: TextRange? = null
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
        if (value.text != textFieldValue.text) {
            colorSelection = null
        } else if (!value.selection.collapsed) {
            colorSelection = value.selection
        }
        textFieldValue = value
    }

    internal fun syncExternal(text: String) {
        val current = textFieldValue
        if (current.text == text) return
        colorSelection = null
        val cursor = current.selection.end.coerceIn(0, text.length)
        textFieldValue = TextFieldValue(
            text = text,
            selection = TextRange(cursor),
        )
    }

    internal fun prepareColorSelection() {
        if (!textFieldValue.selection.collapsed) {
            colorSelection = textFieldValue.selection
        }
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
        colorSelection = null
        textFieldValue = TextFieldValue(
            text = updated.text,
            selection = TextRange(updated.selectionStart, updated.selectionEnd),
        )
        changeListener?.invoke(updated.text)
        return true
    }

    fun applyColor(color: Color): Boolean {
        val current = textFieldValue
        val selection = current.selection
            .takeUnless { it.collapsed }
            ?: colorSelection
            ?: return false
        val colorHex = "#%06X".format(color.toArgb() and 0xFFFFFF)
        val updated = DocumentMarkupFormatter.applyColor(
            text = current.text,
            selectionStart = selection.start,
            selectionEnd = selection.end,
            colorHex = colorHex,
        )
        if (updated.text == current.text) return false
        textFieldValue = TextFieldValue(
            text = updated.text,
            selection = TextRange(updated.selectionStart, updated.selectionEnd),
        )
        colorSelection = TextRange(updated.selectionStart, updated.selectionEnd)
        changeListener?.invoke(updated.text)
        return true
    }

    companion object {
        private val FORMAT_ACCENT = Color(0xFFC47B28)
    }
}
