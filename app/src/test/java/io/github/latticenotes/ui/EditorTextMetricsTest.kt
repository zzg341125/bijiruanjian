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

    @Test
    fun cursorUsesBodyTextHeightAtEveryFontScale() {
        assertEquals(16f, EditorTextMetrics.cursorHeightDp(1f).value, 0.001f)
        assertEquals(12.8f, EditorTextMetrics.cursorHeightDp(0.8f).value, 0.001f)
        assertEquals(24f, EditorTextMetrics.cursorHeightDp(1.5f).value, 0.001f)
    }

    @Test
    fun headingCursorUsesHeadingTextHeight() {
        assertEquals(
            24f,
            EditorTextMetrics.cursorHeightDp(
                fontScale = 1f,
                fontSizeSp = 24f,
            ).value,
            0.001f,
        )
    }

    @Test
    fun cursorOffsetStaysInsideTheLatestTextLayout() {
        assertEquals(1, EditorTextMetrics.clampCursorOffset(2, 1))
        assertEquals(0, EditorTextMetrics.clampCursorOffset(-1, 3))
        assertEquals(3, EditorTextMetrics.clampCursorOffset(3, 3))
    }
}
