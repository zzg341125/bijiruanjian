package io.github.latticenotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RuledPaper(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 34.dp,
    paperColor: Color? = null,
    lineColor: Color? = null,
    marginColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val paper = paperColor ?: MaterialTheme.colorScheme.surface
    val line = lineColor ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val margin = marginColor ?: MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f)
    Box(
        modifier = modifier.drawBehind {
            drawRect(paper)
            val step = lineSpacing.toPx()
            var y = step
            while (y < size.height) {
                drawLine(
                    color = line,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.1f,
                )
                y += step
            }
            val marginX = 34.dp.toPx()
            drawLine(
                color = margin,
                start = Offset(marginX, 0f),
                end = Offset(marginX, size.height),
                strokeWidth = 1.5f,
            )
        },
        content = content,
    )
}
