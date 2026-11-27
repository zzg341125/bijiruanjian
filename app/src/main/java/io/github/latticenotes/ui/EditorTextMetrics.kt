package io.github.latticenotes.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle

internal object EditorTextMetrics {
    val titleFontSize = 18.sp
    val bodyFontSize = 16.sp
    val headingFontSize = 24.sp
    val lineHeight = 42.sp
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun lineHeightDp(fontScale: Float) = (lineHeight.value * fontScale).dp
    fun cursorHeightDp(
        fontScale: Float,
        fontSizeSp: Float = bodyFontSize.value,
    ) = (fontSizeSp * fontScale).dp

    fun cursorFontSizeSp(
        visibleText: AnnotatedString,
        originalText: String,
        visibleOffset: Int,
        originalOffset: Int,
    ): Float = visibleText.spanStyles
        .firstOrNull {
            visibleOffset.coerceIn(0, visibleText.length) in it.start..it.end &&
                it.item.fontSize.value.isFinite()
        }
        ?.item
        ?.fontSize
        ?.value
        ?: if (originalLineIsHeading(originalText, originalOffset)) {
            headingFontSize.value
        } else {
            bodyFontSize.value
        }

    private fun originalLineIsHeading(text: String, offset: Int): Boolean {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = if (safeOffset == 0) {
            0
        } else {
            text.lastIndexOf('\n', safeOffset - 1).let { if (it < 0) 0 else it + 1 }
        }
        val lineEnd = text.indexOf('\n', safeOffset).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
            .replace(Regex("<color:#[0-9A-Fa-f]{6}>|</color>"), "")
            .removePrefix("<center>")
            .removeSuffix("</center>")
            .trimStart()
        return Regex("^#{1,6}\\s+").containsMatchIn(line)
    }

    fun clampCursorOffset(offset: Int, layoutTextLength: Int): Int =
        offset.coerceIn(0, layoutTextLength.coerceAtLeast(0))
}
