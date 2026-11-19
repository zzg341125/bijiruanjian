package io.github.latticenotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

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

    @Test
    fun listTimestampFollowsSmartisanRelativeDateStyle() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = timestamp(zone, "2026-11-19T12:00")

        assertEquals(
            "今天 11:20",
            formatListTimestamp(timestamp(zone, "2026-11-19T11:20"), now, zone),
        )
        assertEquals(
            "星期一 11/16 09:08",
            formatListTimestamp(timestamp(zone, "2026-11-16T09:08"), now, zone),
        )
        assertEquals(
            "7天前 11/12 08:00",
            formatListTimestamp(timestamp(zone, "2026-11-12T08:00"), now, zone),
        )
        assertEquals(
            "353天前 2025 12/01 07:45",
            formatListTimestamp(timestamp(zone, "2025-12-01T07:45"), now, zone),
        )
    }

    private fun timestamp(zone: ZoneId, value: String): Long =
        LocalDateTime.parse(value)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
