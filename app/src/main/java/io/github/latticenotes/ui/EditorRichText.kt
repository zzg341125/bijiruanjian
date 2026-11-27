package io.github.latticenotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

internal data class FormattedDocument(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal object DocumentMarkupFormatter {
    private val headingPrefix = Regex("^#{1,6}\\s+")
    private val bulletPrefix = Regex("^[-*]\\s+")
    private val todoPrefix = Regex("^-\\s+\\[[ xX]]\\s+")
    private val quotePrefix = Regex("^>\\s+")
    private val colorOpen = Regex("<color:#[0-9A-Fa-f]{6}>")
    private val colorClose = Regex("</color>")
    private val colorTag = Regex("<color:#[0-9A-Fa-f]{6}>|</color>")

    fun apply(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        action: DocumentFormatAction,
    ): FormattedDocument {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        return if (action == DocumentFormatAction.BOLD) {
            toggleBold(text, start, end)
        } else {
            toggleLineFormat(text, start, end, action)
        }
    }

    fun applyColor(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        colorHex: String,
    ): FormattedDocument {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        if (start == end) return FormattedDocument(text, start, end)
        val enclosingColor = enclosingColorSpan(text, start, end)
        val replaceStart = enclosingColor?.first ?: start
        val replaceEnd = (enclosingColor?.last ?: (end - 1)) + 1
        val selected = text.substring(replaceStart, replaceEnd)
            .replace(colorTag, "")
        if (selected.isBlank()) return FormattedDocument(text, start, end)
        val opening = "<color:${colorHex.uppercase()}>"
        val replacement = "$opening$selected</color>"
        return FormattedDocument(
            text = text.replaceRange(replaceStart, replaceEnd, replacement),
            selectionStart = replaceStart + opening.length,
            selectionEnd = replaceStart + opening.length + selected.length,
        )
    }

    private fun enclosingColorSpan(text: String, start: Int, end: Int): IntRange? {
        colorOpen.findAll(text, 0)
            .takeWhile { it.range.first < start }
            .toList()
            .asReversed()
            .forEach { opening ->
                val contentStart = opening.range.last + 1
                if (start < contentStart) return@forEach
                val closing = matchingColorClose(text, contentStart) ?: return@forEach
                if (end <= closing.first) {
                    return opening.range.first..closing.last
                }
            }
        return null
    }

    private fun matchingColorClose(text: String, from: Int): IntRange? {
        var depth = 1
        val tags = sequence {
            yieldAll(colorOpen.findAll(text, from))
            yieldAll(colorClose.findAll(text, from))
        }.sortedBy { it.range.first }
        tags.forEach { tag ->
            if (tag.value.startsWith("<color:")) {
                depth++
            } else {
                depth--
                if (depth == 0) return tag.range
            }
        }
        return null
    }

    private fun toggleLineFormat(
        text: String,
        start: Int,
        end: Int,
        action: DocumentFormatAction,
    ): FormattedDocument {
        val range = if (action == DocumentFormatAction.CENTER) {
            lineRange(text, start, start)
        } else {
            lineRange(text, start, end)
        }
        val originalLines = text.substring(range.first, range.second).split('\n')
        val nonBlank = originalLines.filter { stripLineMarkup(it).isNotBlank() }
        if (nonBlank.isEmpty()) return FormattedDocument(text, start, end)

        val remove = nonBlank.all { hasFormat(it, action) }
        val replacement = originalLines.joinToString("\n") { line ->
            val content = stripLineMarkup(line)
            if (content.isBlank()) {
                ""
            } else if (remove) {
                content
            } else {
                when (action) {
                    DocumentFormatAction.CENTER -> "<center>$content</center>"
                    else -> linePrefix(action) + content
                }
            }
        }
        val updated = text.replaceRange(range.first, range.second, replacement)
        return FormattedDocument(
            text = updated,
            selectionStart = range.first,
            selectionEnd = range.first + replacement.length,
        )
    }

    private fun toggleBold(text: String, start: Int, end: Int): FormattedDocument {
        val range = if (start == end) wordRange(text, start) else start to end
        if (range.first == range.second) {
            val placeholder = "粗体"
            val updated = text.replaceRange(start, start, "**$placeholder**")
            return FormattedDocument(
                text = updated,
                selectionStart = start + 2,
                selectionEnd = start + 2 + placeholder.length,
            )
        }

        val selected = text.substring(range.first, range.second)
        val wrapped = selected.length >= 4 &&
            selected.startsWith("**") &&
            selected.endsWith("**")
        val surrounded = range.first >= 2 &&
            range.second + 2 <= text.length &&
            text.substring(range.first - 2, range.first) == "**" &&
            text.substring(range.second, range.second + 2) == "**"
        return when {
            surrounded -> {
                val updated = text.removeRange(range.second, range.second + 2)
                    .removeRange(range.first - 2, range.first)
                FormattedDocument(
                    text = updated,
                    selectionStart = range.first - 2,
                    selectionEnd = range.second - 2,
                )
            }
            wrapped -> {
                val updated = text.removeRange(range.second - 2, range.second)
                    .removeRange(range.first, range.first + 2)
                FormattedDocument(
                    text = updated,
                    selectionStart = range.first,
                    selectionEnd = range.second - 4,
                )
            }
            else -> {
                val updated = text.replaceRange(range.first, range.second, "**$selected**")
                FormattedDocument(
                    text = updated,
                    selectionStart = range.first + 2,
                    selectionEnd = range.second + 2,
                )
            }
        }
    }

    private fun stripLineMarkup(input: String): String {
        var value = input.trimStart()
        while (true) {
            val before = value
            value = when {
                headingPrefix.containsMatchIn(value) ->
                    value.replaceFirst(headingPrefix, "")
                todoPrefix.containsMatchIn(value) ->
                    value.replaceFirst(todoPrefix, "")
                bulletPrefix.containsMatchIn(value) ->
                    value.replaceFirst(bulletPrefix, "")
                quotePrefix.containsMatchIn(value) ->
                    value.replaceFirst(quotePrefix, "")
                value.startsWith("<center>", ignoreCase = true) ->
                    value.substring("<center>".length)
                value.startsWith("</center>", ignoreCase = true) ->
                    value.substring("</center>".length)
                value.startsWith("**") -> value.substring(2)
                else -> value
            }
            value = value
                .replace("<center>", "", ignoreCase = true)
                .replace("</center>", "", ignoreCase = true)
                .replace("**", "")
            if (value == before) break
        }
        return value.trim()
    }

    private fun hasFormat(line: String, action: DocumentFormatAction): Boolean {
        val content = stripLineMarkup(line)
        if (content.isBlank()) return false
        val canonical = when (action) {
            DocumentFormatAction.CENTER -> "<center>$content</center>"
            DocumentFormatAction.BOLD -> content
            else -> linePrefix(action) + content
        }
        return line.trimStart() == canonical
    }

    private fun linePrefix(action: DocumentFormatAction): String = when (action) {
        DocumentFormatAction.HEADING -> "# "
        DocumentFormatAction.CENTER -> ""
        DocumentFormatAction.BULLET -> "- "
        DocumentFormatAction.BOLD -> ""
        DocumentFormatAction.QUOTE -> "> "
        DocumentFormatAction.TODO -> "- [ ] "
    }

    private fun lineRange(text: String, start: Int, end: Int): Pair<Int, Int> {
        val lineStart = if (start == 0) {
            0
        } else {
            text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
        }
        val nextBreak = text.indexOf('\n', end)
        return lineStart to if (nextBreak < 0) text.length else nextBreak
    }

    private fun wordRange(text: String, cursor: Int): Pair<Int, Int> {
        if (text.isEmpty()) return cursor to cursor
        var start = cursor.coerceIn(0, text.length)
        var end = start
        while (start > 0 && isWordCharacter(text[start - 1])) start--
        while (end < text.length && isWordCharacter(text[end])) end++
        return start to end
    }

    private fun isWordCharacter(character: Char): Boolean =
        character.isLetterOrDigit() && character !in "，。！？、；：“”‘’（）《》【】"
}

internal class MarkdownVisualTransformation(
    private val textColor: Color,
    private val accentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val output = StringBuilder()
        val originalToVisual = IntArray(source.length + 1) { -1 }
        val visualToOriginal = mutableListOf<Int>()
        val spanStyles = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        val paragraphStyles = mutableListOf<AnnotatedString.Range<ParagraphStyle>>()

        fun mapVisual(position: Int, original: Int) {
            while (visualToOriginal.size <= position) visualToOriginal += -1
            visualToOriginal[position] = original
        }

        fun hide(from: Int, to: Int) {
            val visual = output.length
            for (index in from..to) originalToVisual[index] = visual
        }

        fun appendOriginal(index: Int) {
            val before = output.length
            originalToVisual[index] = before
            mapVisual(before, index)
            output.append(source[index])
            originalToVisual[index + 1] = output.length
            mapVisual(output.length, index + 1)
        }

        fun appendGenerated(value: String, original: Int) {
            val start = output.length
            output.append(value)
            for (position in start..output.length) mapVisual(position, original)
        }

        var activeColor: Color? = null
        var activeColorStart = 0
        var lineStart = 0
        while (lineStart <= source.length) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) source.length else newline
            val analysis = analyzeLine(source, lineStart, lineEnd)
            val visualLineStart = output.length
            analysis.generatedPrefix?.let {
                appendGenerated(it, analysis.contentStart)
                spanStyles += AnnotatedString.Range(
                    SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                    output.length - it.length,
                    output.length,
                )
            }

            var cursor = analysis.contentStart
            var boldStart: Int? = null
            while (cursor < lineEnd) {
                when {
                    source.startsWith("<color:", cursor, ignoreCase = true) -> {
                        val colorTag = colorOpen.matchAt(source, cursor)
                        if (colorTag != null && colorTag.range.last < lineEnd) {
                            hide(cursor, colorTag.range.last)
                            if (activeColor == null) {
                                activeColor = parseColor(colorTag.groupValues[1])
                                activeColorStart = output.length
                            }
                            cursor = colorTag.range.last + 1
                        } else {
                            appendOriginal(cursor)
                            cursor++
                        }
                    }
                    source.startsWith("</color>", cursor, ignoreCase = true) -> {
                        hide(cursor, cursor + "</color>".length - 1)
                        if (activeColor != null && activeColorStart < output.length) {
                            spanStyles += AnnotatedString.Range(
                                SpanStyle(color = activeColor!!),
                                activeColorStart,
                                output.length,
                            )
                        }
                        activeColor = null
                        cursor += "</color>".length
                    }
                    source.startsWith("**", cursor) -> {
                        hide(cursor, cursor + 1)
                        if (boldStart == null) {
                            boldStart = output.length
                        } else {
                            if (boldStart < output.length) {
                                spanStyles += AnnotatedString.Range(
                                    SpanStyle(fontWeight = FontWeight.Bold),
                                    boldStart,
                                    output.length,
                                )
                            }
                            boldStart = null
                        }
                        cursor += 2
                    }
                    source.startsWith("<center>", cursor, ignoreCase = true) -> {
                        hide(cursor, cursor + "<center>".length - 1)
                        analysis.centered = true
                        cursor += "<center>".length
                    }
                    source.startsWith("</center>", cursor, ignoreCase = true) -> {
                        hide(cursor, cursor + "</center>".length - 1)
                        cursor += "</center>".length
                    }
                    else -> {
                        appendOriginal(cursor)
                        cursor++
                    }
                }
            }
            if (boldStart != null && boldStart < output.length) {
                spanStyles += AnnotatedString.Range(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    boldStart,
                    output.length,
                )
            }

            val visualLineEnd = output.length
            if (visualLineStart < visualLineEnd) {
                if (analysis.heading) {
                    spanStyles += AnnotatedString.Range(
                        SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                        visualLineStart,
                        visualLineEnd,
                    )
                }
                if (analysis.quote) {
                    spanStyles += AnnotatedString.Range(
                        SpanStyle(
                            color = textColor.copy(alpha = 0.78f),
                            fontStyle = FontStyle.Italic,
                        ),
                        visualLineStart,
                        visualLineEnd,
                    )
                }
                if (analysis.centered) {
                    paragraphStyles += AnnotatedString.Range(
                        ParagraphStyle(textAlign = TextAlign.Center),
                        visualLineStart,
                        visualLineEnd,
                    )
                }
            }

            if (newline >= 0) {
                appendOriginal(newline)
                lineStart = newline + 1
            } else {
                break
            }
        }

        if (activeColor != null && activeColorStart < output.length) {
            spanStyles += AnnotatedString.Range(
                SpanStyle(color = activeColor!!),
                activeColorStart,
                output.length,
            )
        }

        originalToVisual[source.length] = output.length
        mapVisual(output.length, source.length)
        var lastOriginal = 0
        visualToOriginal.indices.forEach { index ->
            if (visualToOriginal[index] >= 0) {
                lastOriginal = visualToOriginal[index]
            } else {
                visualToOriginal[index] = lastOriginal
            }
        }
        var lastVisual = 0
        originalToVisual.indices.forEach { index ->
            if (originalToVisual[index] >= 0) {
                lastVisual = originalToVisual[index]
            } else {
                originalToVisual[index] = lastVisual
            }
        }

        val annotated = AnnotatedString.Builder(output.toString()).apply {
            spanStyles.forEach { addStyle(it.item, it.start, it.end) }
            paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        }.toAnnotatedString()
        val reverse = visualToOriginal.toIntArray()
        return TransformedText(
            text = annotated,
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    originalToVisual[offset.coerceIn(0, source.length)]

                override fun transformedToOriginal(offset: Int): Int =
                    reverse[offset.coerceIn(0, reverse.lastIndex)]
            },
        )
    }

    private fun analyzeLine(source: String, start: Int, end: Int): LineAnalysis {
        val analysis = LineAnalysis(contentStart = start)
        var cursor = start
        var changed: Boolean
        do {
            changed = false
            if (source.startsWith("<center>", cursor, ignoreCase = true)) {
                analysis.centered = true
                cursor += "<center>".length
                changed = true
            } else if (source.startsWith("</center>", cursor, ignoreCase = true)) {
                cursor += "</center>".length
                changed = true
            } else if (source.startsWith("**", cursor)) {
                cursor += 2
                changed = true
            } else if (source.startsWith("- [ ] ", cursor)) {
                analysis.todo = true
                analysis.checked = false
                cursor += "- [ ] ".length
                changed = true
            } else if (source.startsWith("- [x] ", cursor, ignoreCase = true)) {
                analysis.todo = true
                analysis.checked = true
                cursor += "- [x] ".length
                changed = true
            } else if (source.startsWith("- ", cursor)) {
                analysis.bullet = true
                cursor += 2
                changed = true
            } else if (source.startsWith("* ", cursor)) {
                analysis.bullet = true
                cursor += 2
                changed = true
            } else if (source.startsWith("> ", cursor)) {
                analysis.quote = true
                cursor += 2
                changed = true
            } else {
                val headingStart = cursor
                while (
                    cursor < end &&
                    cursor - headingStart < HEADING_MAX_DEPTH &&
                    source[cursor] == '#'
                ) {
                    cursor++
                }
                val headingDepth = cursor - headingStart
                if (
                    headingDepth in 1..HEADING_MAX_DEPTH &&
                    cursor < end &&
                    source[cursor].isWhitespace()
                ) {
                    while (cursor < end && source[cursor].isWhitespace()) cursor++
                    analysis.heading = true
                    changed = true
                } else {
                    cursor = headingStart
                }
            }
        } while (changed)
        analysis.contentStart = cursor.coerceAtMost(end)
        analysis.generatedPrefix = when {
            analysis.todo -> if (analysis.checked) "☑ " else "☐ "
            analysis.bullet -> "• "
            analysis.quote -> "▍ "
            else -> null
        }
        return analysis
    }

    private data class LineAnalysis(
        var contentStart: Int,
        var heading: Boolean = false,
        var centered: Boolean = false,
        var bullet: Boolean = false,
        var todo: Boolean = false,
        var checked: Boolean = false,
        var quote: Boolean = false,
        var generatedPrefix: String? = null,
    )

    companion object {
        private const val HEADING_MAX_DEPTH = 6
        private val colorOpen = Regex("<color:(#[0-9A-Fa-f]{6})>")

        private fun parseColor(hex: String): Color? =
            hex.removePrefix("#")
                .toLongOrNull(16)
                ?.let { Color(0xFF000000L or it) }
    }
}
