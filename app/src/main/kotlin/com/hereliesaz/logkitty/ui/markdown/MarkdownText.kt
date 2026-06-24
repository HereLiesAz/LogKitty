package com.hereliesaz.logkitty.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders a parsed Markdown document (see [parseMarkdownBlocks]) as Compose UI. The caller supplies
 * an [onLinkClick] so link taps route through the app's standard "open URL" path (an ACTION_VIEW
 * intent with a graceful toast on failure), rather than Compose's default URI handler.
 */
@Composable
fun MarkdownText(
    blocks: List<MdBlock>,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineText(block.spans, onLinkClick),
                    style = headingStyle(block.level),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (block.level <= 2) 16.dp else 12.dp, bottom = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = inlineText(block.spans, onLinkClick),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                is MdBlock.BulletItem -> ListItemRow(
                    indent = block.indent,
                    marker = "•",
                    spans = block.spans,
                    onLinkClick = onLinkClick,
                )

                is MdBlock.NumberedItem -> ListItemRow(
                    indent = block.indent,
                    marker = "${block.number}.",
                    spans = block.spans,
                    onLinkClick = onLinkClick,
                )

                is MdBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                ) {
                    Text(
                        text = inlineText(block.spans, onLinkClick),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MdBlock.CodeBlock -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MdBlock.Table -> MarkdownTable(block, onLinkClick)

                MdBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun ListItemRow(indent: Int, marker: String, spans: List<MdSpan>, onLinkClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = "$marker ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = inlineText(spans, onLinkClick),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, onLinkClick: (String) -> Unit) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            table.headers.forEach { cell ->
                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                    Text(
                        text = inlineText(cell, onLinkClick),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(color = border)
        table.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                val cols = table.headers.size.coerceAtLeast(1)
                for (c in 0 until cols) {
                    val cell = row.getOrNull(c) ?: emptyList()
                    Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                        Text(
                            text = inlineText(cell, onLinkClick),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = border)
        }
    }
}

@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

/** Builds a styled [AnnotatedString] from inline spans, wiring links through [onLinkClick]. */
@Composable
private fun inlineText(spans: List<MdSpan>, onLinkClick: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is MdSpan.Text -> append(span.text)
                is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                is MdSpan.BoldItalic ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(span.text) }
                is MdSpan.Code ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(span.text) }
                is MdSpan.Link -> withLink(
                    LinkAnnotation.Url(
                        url = span.url,
                        styles = TextLinkStyles(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                    ) { onLinkClick(span.url) },
                ) { append(span.text) }
            }
        }
    }
}
