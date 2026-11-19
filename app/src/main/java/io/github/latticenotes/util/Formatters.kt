package io.github.latticenotes.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
private val listTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val listMonthDayTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
private val listYearTimeFormatter = DateTimeFormatter.ofPattern("yyyy MM/dd HH:mm")

fun formatTimestamp(value: Long): String =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)

fun formatListTimestamp(
    value: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val target = Instant.ofEpochMilli(value).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(target.toLocalDate(), today)
    return when {
        target.toLocalDate() == today -> "今天 ${target.format(listTimeFormatter)}"
        days in 1..6L -> {
            val weekday = target.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
            "$weekday ${target.format(listMonthDayTimeFormatter)}"
        }
        days >= 7 && target.year == today.year ->
            "${days}天前 ${target.format(listMonthDayTimeFormatter)}"
        days >= 7 ->
            "${days}天前 ${target.format(listYearTimeFormatter)}"
        else -> target.format(listMonthDayTimeFormatter)
    }
}

fun notePreview(body: String, limit: Int = 180): String =
    body
        .replace(Regex("""!\[[^\]]*]\(quyang-attachment://\d+\)"""), "")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+\\[[ xX]]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("[*_`]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= limit) it else it.take(limit).trimEnd() + "…" }
