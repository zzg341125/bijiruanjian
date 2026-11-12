package io.github.latticenotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextMetricsTest {
    @Test
    fun ruledPaperLineHeightFollowsConfiguredFontScale() {
        assertEquals(
            32f,
            EditorTextMetrics.lineHeightDp(1f).value,
            0.001f,
        )
        assertEquals(
            25.6f,
            EditorTextMetrics.lineHeightDp(0.8f).value,
            0.001f,
        )
        assertEquals(
            48f,
            EditorTextMetrics.lineHeightDp(1.5f).value,
            0.001f,
        )
    }
}
