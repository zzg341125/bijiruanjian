package io.github.latticenotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {
    @Test
    fun notePreview_removesMarkdownMarkersAndCollapsesWhitespace() {
        val result = notePreview(
            """
            # 标题
            - [ ] 第一项
            **重点**  和 `代码`
            """.trimIndent(),
        )
        assertEquals("标题 第一项 重点 和 代码", result)
    }

    @Test
    fun notePreview_truncatesWithoutGoingBeyondLimit() {
        val result = notePreview("a".repeat(400), limit = 20)
        assertEquals(21, result.length)
        assertTrue(result.endsWith("…"))
    }
}
