package io.github.latticenotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
    fun selectedTextCanUseOneOfTheCustomColors() {
        val result = DocumentMarkupFormatter.applyColor(
            text = "今天完成小组件",
            selectionStart = 2,
            selectionEnd = 6,
            colorHex = "#C34C4C",
        )
        val visible = MarkdownVisualTransformation(
            textColor = Color.Black,
            accentColor = Color(0xFFC47B28),
        ).filter(androidx.compose.ui.text.AnnotatedString(result.text))

        assertEquals(
            "完成小组",
            result.text.substring(result.selectionStart, result.selectionEnd),
        )
        assertFalse(visible.text.text.contains("<color:"))
        assertTrue(
            visible.text.spanStyles.any {
                it.item.color == Color(0xFFC34C4C)
            },
        )
    }

    @Test
    fun centerFormatsOnlyTheCurrentLineWhenSeveralLinesAreSelected() {
        val source = "第一行\n第二行\n第三行"
        val result = DocumentMarkupFormatter.apply(
            text = source,
            selectionStart = 0,
            selectionEnd = source.length,
            action = DocumentFormatAction.CENTER,
        )

        assertEquals("<center>第一行</center>\n第二行\n第三行", result.text)
    }

    @Test
    fun colorKeepsTheSelectionCapturedBeforeOpeningTheMenu() {
        val controller = DocumentEditorController()
        controller.update(
            TextFieldValue(
                text = "今天完成小组件",
                selection = TextRange(2, 6),
            ),
        )
        controller.prepareColorSelection()
        controller.update(
            TextFieldValue(
                text = "今天完成小组件",
                selection = TextRange(2),
            ),
        )

        assertTrue(controller.applyColor(Color(0xFFC34C4C)))
        assertEquals("今天<color:#C34C4C>完成小组</color>件", controller.textFieldValue.text)
    }

    @Test
    fun selectedTextCanChangeThroughAllSevenColorsWithoutNesting() {
        val controller = DocumentEditorController()
        val colors = listOf(
            Color(0xFF4D4A46),
            Color(0xFFC34C4C),
            Color(0xFFC47B28),
            Color(0xFF9A7A20),
            Color(0xFF3E7456),
            Color(0xFF3F65A8),
            Color(0xFF7A5798),
        )
        controller.update(
            TextFieldValue(
                text = "今天完成小组件",
                selection = TextRange(2, 6),
            ),
        )
        controller.prepareColorSelection()
        colors.forEach { color ->
            assertTrue(controller.applyColor(color))
            controller.update(
                TextFieldValue(
                    text = controller.textFieldValue.text,
                    selection = TextRange(controller.textFieldValue.text.length),
                ),
            )
            controller.prepareColorSelection()
        }

        assertEquals("今天<color:#7A5798>完成小组</color>件", controller.textFieldValue.text)
    }

    @Test
    fun colorCanChangeInsideAnExistingColoredSpan() {
        val opening = "<color:#C34C4C>"
        val closing = "</color>"
        val source = "今天${opening}完成小组${closing}件"
        val selectedStart = "今天".length + opening.length + "完成".length
        val selectedEnd = selectedStart + "小组".length

        val result = DocumentMarkupFormatter.applyColor(
            text = source,
            selectionStart = selectedStart,
            selectionEnd = selectedEnd,
            colorHex = "#C47B28",
        )

        assertEquals(
            "今天<color:#C34C4C>完成</color><color:#C47B28>小组</color>件",
            result.text,
        )
    }

    @Test
    fun coloringCenteredTextKeepsCenterMarkupAroundTheColorSpan() {
        val source = "<center>居中文字</center>"

        val result = DocumentMarkupFormatter.applyColor(
            text = source,
            selectionStart = 0,
            selectionEnd = source.length,
            colorHex = "#C47B28",
        )

        assertEquals(
            "<center><color:#C47B28>居中文字</color></center>",
            result.text,
        )
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
            transformed.text.spanStyles.any {
                it.item.fontSize == 24.sp
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

    @Test
    fun visualEditorRendersHeadingOnANonFirstLine() {
        val source = "第一段\n# 第二行标题\n正文"

        val transformed = MarkdownVisualTransformation(
            textColor = Color.Black,
            accentColor = Color(0xFFC47B28),
        ).filter(androidx.compose.ui.text.AnnotatedString(source))

        assertEquals("第一段\n第二行标题\n正文", transformed.text.text)
        assertTrue(
            transformed.text.spanStyles.any {
                it.item.fontSize == 24.sp &&
                    it.start == "第一段\n".length &&
                    it.end == "第一段\n第二行标题".length
            },
        )
    }
}
