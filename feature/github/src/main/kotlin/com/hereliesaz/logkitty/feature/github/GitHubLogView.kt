package com.hereliesaz.logkitty.feature.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a GitHub Actions job log with ANSI/annotation coloring and collapsible `##[group]` blocks.
 *
 * [logLines] is already capped by the API layer (streamed, last N lines). Parsing runs off the main
 * thread, and the list is virtualized via [LazyColumn]; text is wrapped in a [SelectionContainer] so
 * it can be selected/copied.
 */
@Composable
fun GitHubLogView(logLines: List<String>, fontFamily: FontFamily?, fontSize: Int, modifier: Modifier = Modifier) {
    val parsed by produceState<List<ParsedLogLine>?>(null, logLines) {
        value = withContext(Dispatchers.Default) { LogAnsiClassifier.parse(logLines) }
    }

    val lines = parsed
    if (lines == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // groupId -> collapsed. Reading it during the filter below subscribes to toggles.
    val collapsed = remember(logLines) { mutableStateMapOf<Int, Boolean>() }
    val visible = lines.filter { line ->
        val g = line.groupId
        g == null || line.isGroupHeader || collapsed[g] != true
    }

    val listState = rememberLazyListState()
    SelectionContainer {
        LazyColumn(state = listState, modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(visible.size) { idx ->
                val line = visible[idx]
                if (line.isGroupHeader && line.groupId != null) {
                    val isCollapsed = collapsed[line.groupId] == true
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF4DD0E1), fontWeight = FontWeight.Bold)) {
                                append(if (isCollapsed) "▸ " else "▾ ")
                                append(line.plain)
                            }
                        },
                        fontSize = (fontSize - 1).sp,
                        fontFamily = fontFamily,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { collapsed[line.groupId] = !isCollapsed }
                            .padding(vertical = 1.dp),
                    )
                } else {
                    Text(
                        text = line.toAnnotated(),
                        fontSize = (fontSize - 1).sp,
                        fontFamily = fontFamily,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun ParsedLogLine.toAnnotated() = buildAnnotatedString {
    for (span in spans) {
        withStyle(
            SpanStyle(
                color = span.color ?: DEFAULT_TEXT,
                fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
            ),
        ) { append(span.text) }
    }
}

private val DEFAULT_TEXT = Color.White.copy(alpha = 0.92f)
