package io.github.latticenotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorMarkupTest {
    @Test
    fun lineActionCleansPreviouslyNestedMarkup() {
        val source =
            "# <center>- **- [ ] > **- <center>春节期间向局里协调一辆警车给我们用" +
                "</center>******</center></center>"

        val result = DocumentMarkupFormatter.apply(
            text = source,
            selectionStart = 0,
            selectionEnd = source.length,
            action = DocumentFormatAction.HEADING,
        )

        assertEquals("# 春节期间向局里协调一辆警车给我们用", result.text)
    }

    @Test
    fun lineFormatsReplaceEachOtherInsteadOfNesting() {
        val heading = DocumentMarkupFormatter.apply(
            text = "# 工作待办",
            selectionStart = 2,
            selectionEnd = 2,
            action = DocumentFormatAction.TODO,
        )
        val toggledOff = DocumentMarkupFormatter.apply(
            text = heading.text,
            selectionStart = heading.selectionStart,
            selectionEnd = heading.selectionEnd,
            action = DocumentFormatAction.TODO,
        )

        assertEquals("- [ ] 工作待办", heading.text)
        assertEquals("工作待办", toggledOff.text)
    }

    @Test
    fun boldSelectionCanBeAppliedAndToggledOff() {
        val bold = DocumentMarkupFormatter.apply(
            text = "今天完成小组件",
            selectionStart = 2,
            selectionEnd = 6,
            action = DocumentFormatAction.BOLD,
        )
        val plain = DocumentMarkupFormatter.apply(
            text = bold.text,
            selectionStart = bold.selectionStart,
            selectionEnd = bold.selectionEnd,
            action = DocumentFormatAction.BOLD,
        )

        assertEquals("今天**完成小组**件", bold.text)
        assertEquals("今天完成小组件", plain.text)
    }

    @Test
    fun visualEditorHidesFormattingMarkupAndKeepsStyles() {
        val source =
            "# 标题\n<center>- [ ] 居中待办</center>\n**重点**正文"

        val transformed = MarkdownVisualTransformation(
            textColor = Color.Black,
            accentColor = Color(0xFFC47B28),
        ).filter(androidx.compose.ui.text.AnnotatedString(source))
        val visible = transformed.text.text

        assertFalse(visible.contains("# "))
        assertFalse(visible.contains("<center>"))
        assertFalse(visible.contains("- [ ]"))
        assertFalse(visible.contains("**"))
        assertTrue(visible.contains("☐ 居中待办"))
        assertTrue(
            transformed.text.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold
            },
        )
        assertTrue(
            transformed.text.paragraphStyles.any {
                it.item.textAlign == TextAlign.Center
            },
        )
        assertEquals(
            transformed.text.length,
            transformed.offsetMapping.originalToTransformed(source.length),
        )
        assertEquals(
            source.length,
            transformed.offsetMapping.transformedToOriginal(transformed.text.length),
        )
    }
}
