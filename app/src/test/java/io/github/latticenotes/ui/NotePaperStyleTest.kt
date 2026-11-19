package io.github.latticenotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotePaperStyleTest {
    @Test
    fun unknownStoredValueFallsBackToDefault() {
        assertEquals(NotePaperStyle.DEFAULT, NotePaperStyle.fromStorage("unknown"))
        assertEquals(NotePaperStyle.DEFAULT, NotePaperStyle.fromStorage(null))
    }

    @Test
    fun fourPaperStylesHaveDistinctPaperColors() {
        val colors = NotePaperStyle.entries.map { notePaperPalette(it).paper }
        assertEquals(4, colors.toSet().size)
        assertNotEquals(colors[0], colors[3])
    }
}
