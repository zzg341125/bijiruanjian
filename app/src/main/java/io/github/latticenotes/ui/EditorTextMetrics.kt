package io.github.latticenotes.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object EditorTextMetrics {
    val titleFontSize = 24.sp
    val bodyFontSize = 20.sp
    val lineHeight = 32.sp

    fun lineHeightDp(fontScale: Float) = (lineHeight.value * fontScale).dp
}
