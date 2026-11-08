package io.github.latticenotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            markdown.lines().forEach { rawLine ->
                val line = rawLine.trimEnd()
                when {
                    line.startsWith("# ") -> Text(
                        text = inlineMarkdown(line.removePrefix("# ")),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    line.startsWith("## ") -> Text(
                        text = inlineMarkdown(line.removePrefix("## ")),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    line.startsWith("### ") -> Text(
                        text = inlineMarkdown(line.removePrefix("### ")),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Regex("^- \\[[ xX]]\\s*").containsMatchIn(line) -> {
                        val checked = line.getOrNull(3) == 'x' || line.getOrNull(3) == 'X'
                        Text(
                            text = (if (checked) "☑ " else "☐ ") +
                                inlineMarkdown(line.substring(5)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> Text(
                        text = buildAnnotatedString {
                            append("• ")
                            append(inlineMarkdown(line.drop(2)))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    line.matches(Regex("^\\d+\\.\\s+.*")) -> Text(
                        text = inlineMarkdown(line),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    line.startsWith("> ") -> Text(
                        text = inlineMarkdown(line.removePrefix("> ")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    line == "---" -> Text(
                        text = "────────────────",
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    line.isBlank() -> Text("")
                    else -> Text(
                        text = inlineMarkdown(line),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun inlineMarkdown(input: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    val bold = Regex("\\*\\*(.+?)\\*\\*")
    val italic = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    val code = Regex("`([^`]+)`")
    val link = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    val markers = mutableListOf<Triple<Int, Int, SpanStyle>>()

    bold.findAll(input).forEach { match ->
        markers += Triple(match.range.first, match.range.last + 1, SpanStyle(fontWeight = FontWeight.Bold))
    }
    italic.findAll(input).forEach { match ->
        markers += Triple(match.range.first, match.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic))
    }
    code.findAll(input).forEach { match ->
        markers += Triple(
            match.range.first,
            match.range.last + 1,
            SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1A305C4A)),
        )
    }
    link.findAll(input).forEach { match ->
        markers += Triple(
            match.range.first,
            match.range.last + 1,
            SpanStyle(color = Color(0xFF305C4A), textDecoration = TextDecoration.Underline),
        )
    }

    while (index < input.length) {
        val next = markers.filter { it.first >= index }.minByOrNull { it.first }
        if (next == null) {
            append(input.substring(index).replace("**", "").replace("`", ""))
            break
        }
        if (next.first > index) {
            append(input.substring(index, next.first).replace("**", "").replace("`", ""))
        }
        val rawEnd = minOf(next.second, input.length)
        var visible = input.substring(next.first, rawEnd)
            .replace("**", "")
            .replace("`", "")
        val linkMatch = Regex("\\[([^]]+)]\\(([^)]+)\\)").matchEntire(visible)
        if (linkMatch != null) {
            visible = linkMatch.groupValues[1]
        }
        pushStyle(next.third)
        append(visible)
        pop()
        index = rawEnd
    }
}
