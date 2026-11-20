package io.github.latticenotes.ui

import org.junit.Assert.assertEquals
import androidx.compose.ui.text.style.LineHeightStyle
import org.junit.Test

class EditorTextMetricsTest {
    @Test
    fun ruledPaperLineHeightFollowsConfiguredFontScale() {
        assertEquals(
            42f,
            EditorTextMetrics.lineHeightDp(1f).value,
            0.001f,
        )
        assertEquals(
            33.6f,
            EditorTextMetrics.lineHeightDp(0.8f).value,
            0.001f,
        )
        assertEquals(
            63f,
            EditorTextMetrics.lineHeightDp(1.5f).value,
            0.001f,
        )
    }

    @Test
    fun editorTextIsCenteredInsideEachRuledLine() {
        assertEquals(
            LineHeightStyle.Alignment.Center,
            EditorTextMetrics.lineHeightStyle.alignment,
        )
        assertEquals(
            LineHeightStyle.Trim.None,
            EditorTextMetrics.lineHeightStyle.trim,
        )
    }
}
