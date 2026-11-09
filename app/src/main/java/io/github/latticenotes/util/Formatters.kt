package io.github.latticenotes.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

fun formatTimestamp(value: Long): String =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)

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
