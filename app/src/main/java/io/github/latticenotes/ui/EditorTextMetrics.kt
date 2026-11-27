package io.github.latticenotes.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle

internal object EditorTextMetrics {
    val titleFontSize = 18.sp
    val bodyFontSize = 16.sp
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
    fun clampCursorOffset(offset: Int, layoutTextLength: Int): Int =
        offset.coerceIn(0, layoutTextLength.coerceAtLeast(0))
}
