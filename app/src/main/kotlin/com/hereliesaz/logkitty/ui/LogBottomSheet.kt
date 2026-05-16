package com.hereliesaz.logkitty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.ui.theme.getGoogleFontFamily
import kotlin.math.abs

/**
 * The four-detent overlay rewritten for the new gesture and tab model.
 *
 * **Detents**
 *   HIDDEN  – an invisible swipe-up zone at the bottom edge; the window shrinks so the
 *             underlying app receives every touch outside that strip. The launcher disables
 *             the overlay entirely so the swipe-up-for-app-drawer gesture is untouched.
 *   PEEK    – a one-line ticker showing the latest log entry.
 *   HALF    – roughly 50% of the screen, with a transparent scrim above.
 *   FULL    – the log fills everything except the bottom 10% of the screen, scrim above.
 *
 * **Gestures**
 *   Swipe up from the HIDDEN strip → step to PEEK (then HALF, then FULL on subsequent swipes).
 *   Tap anywhere on the scrim above an expanded sheet → step down by one detent.
 *   Header row vertical drag       → step the detent up or down.
 *   Header / row horizontal drag   → switch to the previous/next tab.
 *
 * **Selection model**
 *   Tap a log line → an action toolbar (copy / search / prohibit) appears at the top of the log area.
 *   Tap the same line again or use the X in the toolbar to deselect.
 */
@Composable
fun LogBottomSheet(
    controller: SheetController,
    viewModel: MainViewModel,
    screenHeight: Dp,
    navBarHeight: Dp,
    collapsedHeightDp: Dp,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val indexedLog by viewModel.filteredIndexedLog.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val showTimestamp by viewModel.showTimestamp.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val logColors by viewModel.logColors.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val tagColoringEnabled by viewModel.tagColoringEnabled.collectAsState()

    val sheetBackgroundColor = Color(backgroundColorInt).copy(alpha = overlayOpacity)

    val currentFontFamily = remember(fontFamilyName) {
        val enumVal = try { CodingFont.valueOf(fontFamilyName) } catch (e: Exception) { CodingFont.SYSTEM }
        getGoogleFontFamily(enumVal.fontName)
    }

    // Bottom-sheet content always fills the host window — the WindowManager (via [LogKittyOverlayService])
    // is the source of truth for size, sized exactly to the current detent.

    // X-button double-press tracking (clear → hide).
    var lastClearAt by remember { mutableLongStateOf(0L) }

    var selectedLineId by remember { mutableStateOf<Long?>(null) }
    val selectedLine = remember(selectedLineId, indexedLog) {
        selectedLineId?.let { id -> indexedLog.firstOrNull { it.id == id } }
    }

    val current = controller.detent
    Box(modifier = Modifier.fillMaxSize()) {
        when (current) {
            SheetDetent.HIDDEN -> HiddenSwipeZone(
                modifier = Modifier.fillMaxSize(),
                onSwipeUp = { controller.stepUp() }
            )
            SheetDetent.PEEK -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetBackgroundColor)
            ) {
                PeekStrip(
                    modifier = Modifier.fillMaxSize(),
                    latest = indexedLog.lastOrNull()?.text ?: "LogKitty Ready",
                    showTimestamp = showTimestamp,
                    fontFamily = currentFontFamily,
                    fontSize = fontSize,
                    navBarHeight = navBarHeight,
                    onTap = { controller.stepUp() },
                    onSwipeUp = { controller.stepUp() },
                    onSwipeDown = { controller.hide() },
                    onSwipeLeft = { viewModel.selectNextTab() },
                    onSwipeRight = { viewModel.selectPreviousTab() }
                )
            }
            SheetDetent.HALF, SheetDetent.FULL -> {
                // Split the window into scrim (top) and sheet (bottom) by weight so the
                // sheet always uses its full share of the *actually measured* window —
                // independent of any insets the system applies to the configured height.
                val scrimWeight: Float
                val sheetWeight: Float
                if (current == SheetDetent.HALF) {
                    scrimWeight = 1f; sheetWeight = 1f      // 50 / 50
                } else {
                    scrimWeight = 1f; sheetWeight = 9f      // 10 / 90
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    // Transparent scrim — tap anywhere above the sheet steps the detent
                    // down by one.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(scrimWeight)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { controller.stepDown() }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(sheetWeight)
                            .background(sheetBackgroundColor)
                    ) {
                        ExpandedView(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        indexedLog = indexedLog,
                        logColors = logColors,
                        tagColoringEnabled = tagColoringEnabled,
                        fontFamily = currentFontFamily,
                        fontSize = fontSize,
                        showTimestamp = showTimestamp,
                        isLogReversed = isLogReversed,
                        navBarHeight = navBarHeight,
                        selectedLine = selectedLine,
                        onSelectLine = { id -> selectedLineId = if (selectedLineId == id) null else id },
                        onClearSelection = { selectedLineId = null },
                        onTabSelected = { viewModel.selectTab(it) },
                        onCloseAppTab = { viewModel.closeTab(it) },
                        onSwipeLeft = { viewModel.selectNextTab() },
                        onSwipeRight = { viewModel.selectPreviousTab() },
                        onHeaderDragUp = { controller.stepUp() },
                        onHeaderDragDown = { controller.stepDown() },
                        onSaveClick = {
                            controller.hide()
                            onSaveClick()
                        },
                        onSettingsClick = {
                            controller.hide()
                            onSettingsClick()
                        },
                        onClearClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastClearAt < 3000L) {
                                controller.hide()
                                lastClearAt = 0L
                            } else {
                                viewModel.clearActiveTab()
                                lastClearAt = now
                            }
                        },
                        onCopyLine = { line ->
                            clipboardManager.setText(AnnotatedString(line.text))
                            selectedLineId = null
                        },
                        onSearchLine = { line ->
                            // Cap the query — full log lines (stack traces) can blow past the URL/intent
                            // size limits and silently fail to launch the browser.
                            val query = java.net.URLEncoder.encode(line.text.take(500), "UTF-8")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.google.com/search?q=$query")).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                            selectedLineId = null
                        },
                        onProhibitLine = { line ->
                            viewModel.prohibitLog(line.text)
                            selectedLineId = null
                        },
                    )
                    }
                }
            }
        }
    }
}

/**
 * Invisible swipe-up zone. A vertical drag fires [onSwipeUp] once per gesture, advancing
 * the sheet by one detent. No visible handle — the user finds it via the same edge-swipe
 * affordance the system uses for the app drawer (the launcher itself disables this zone
 * via [SheetController.isEnabled], so the gestures don't conflict there).
 */
@Composable
private fun HiddenSwipeZone(
    modifier: Modifier,
    onSwipeUp: () -> Unit,
) {
    Box(
        modifier = modifier.pointerInputVerticalDrag(
            threshold = 16f,
            onUp = onSwipeUp,
            onDown = { }
        )
    )
}

@Composable
private fun PeekStrip(
    modifier: Modifier,
    latest: String,
    showTimestamp: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontSize: Int,
    navBarHeight: Dp,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val displayText = if (showTimestamp) latest else latest.replace(
        Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), ""
    )

    Box(
        modifier = modifier
            .pointerInputVerticalDrag(threshold = 24f, onUp = onSwipeUp, onDown = onSwipeDown)
            .pointerInputHorizontalDrag(threshold = 48f, onLeft = onSwipeLeft, onRight = onSwipeRight)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBarHeight)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = displayText,
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ExpandedView(
    tabs: List<LogTab>,
    selectedTab: LogTab,
    indexedLog: List<IndexedLogLine>,
    logColors: Map<LogLevel, Color>,
    tagColoringEnabled: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontSize: Int,
    showTimestamp: Boolean,
    isLogReversed: Boolean,
    navBarHeight: Dp,
    selectedLine: IndexedLogLine?,
    onSelectLine: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onTabSelected: (LogTab) -> Unit,
    onCloseAppTab: (LogTab) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onHeaderDragUp: () -> Unit,
    onHeaderDragDown: () -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onCopyLine: (IndexedLogLine) -> Unit,
    onSearchLine: (IndexedLogLine) -> Unit,
    onProhibitLine: (IndexedLogLine) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // --- Header zone: tabs + icons (vertical drag = detent, horizontal = tab swap) ---
        // No visible handle — the header itself is the drag target.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInputVerticalDrag(threshold = 18f, onUp = onHeaderDragUp, onDown = onHeaderDragDown)
                .pointerInputHorizontalDrag(threshold = 48f, onLeft = onSwipeLeft, onRight = onSwipeRight)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    modifier = Modifier.weight(1f),
                    indicator = { tabPositions ->
                        if (tabs.isNotEmpty()) {
                            val idx = tabs.indexOf(selectedTab).coerceAtLeast(0)
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[idx]))
                        }
                    }
                ) {
                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tab.title, maxLines = 1)
                                    if (isSelected && tab.type == TabType.APP) {
                                        IconButton(
                                            onClick = { onCloseAppTab(tab) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close tab",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSaveClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Save, "Save", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onClearClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Clear, "Clear / Hide", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }

        // --- Selection action bar (Copy / Search / Prohibit) ---
        AnimatedVisibility(visible = selectedLine != null, enter = fadeIn(), exit = fadeOut()) {
            val line = selectedLine ?: return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Selected entry",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopyLine(line) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy this line", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { onSearchLine(line) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Search, "Search Google", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { onProhibitLine(line) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Block, "Prohibit this tag", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Deselect", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // --- Log area (vertical = scroll, horizontal = tab swap) ---
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInputHorizontalDrag(threshold = 64f, onLeft = onSwipeLeft, onRight = onSwipeRight)
        ) {
            if (indexedLog.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = isLogReversed,
                    // No nav-bar bottom inset — the log is allowed to draw behind the system
                    // navigation bar so as many lines as possible are visible.
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(indexedLog, key = { it.id }) { line ->
                        LogRow(
                            line = line,
                            isSelected = selectedLine?.id == line.id,
                            showTimestamp = showTimestamp,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            colors = logColors,
                            tagColoringEnabled = tagColoringEnabled,
                            onClick = { onSelectLine(line.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    line: IndexedLogLine,
    isSelected: Boolean,
    showTimestamp: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontSize: Int,
    colors: Map<LogLevel, Color>,
    tagColoringEnabled: Boolean,
    onClick: () -> Unit,
) {
    val text = if (showTimestamp) line.text else line.text.replace(
        Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), ""
    )
    val level = LogLevel.fromLine(line.text)
    val baseColor = colors[level] ?: Color.White
    val tagColor = if (tagColoringEnabled) TagColors.colorFor(LogLevel.tagFromLine(line.text)) else null

    val annotated = buildAnnotatedString {
        if (tagColor != null) {
            val tag = LogLevel.tagFromLine(line.text)
            if (!tag.isNullOrBlank() && text.contains(tag)) {
                val idx = text.indexOf(tag)
                withStyle(SpanStyle(color = baseColor)) { append(text.substring(0, idx)) }
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) { append(tag) }
                withStyle(SpanStyle(color = baseColor)) { append(text.substring(idx + tag.length)) }
                return@buildAnnotatedString
            }
        }
        withStyle(SpanStyle(color = baseColor)) { append(text) }
    }

    val bg = if (isSelected) Color.White.copy(alpha = 0.10f) else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = annotated,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.35f).sp,
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * Modifier that fires [onUp] / [onDown] exactly once per gesture when the *accumulated*
 * vertical displacement crosses [threshold]. The per-frame `dragAmount` reported by
 * Compose is a delta, not a total — accumulating into `totalDrag` is required, otherwise
 * fast flicks fire repeatedly and slow drags never fire at all.
 */
private fun Modifier.pointerInputVerticalDrag(
    threshold: Float,
    onUp: () -> Unit,
    onDown: () -> Unit,
): Modifier = this.pointerInput(threshold) {
    var totalDrag = 0f
    var fired = false
    detectVerticalDragGestures(
        onDragStart = { totalDrag = 0f; fired = false },
        onDragEnd = { totalDrag = 0f; fired = false },
        onDragCancel = { totalDrag = 0f; fired = false },
        onVerticalDrag = { change, dragAmount ->
            if (fired) return@detectVerticalDragGestures
            totalDrag += dragAmount
            if (abs(totalDrag) > threshold) {
                fired = true
                change.consume()
                if (totalDrag < 0) onUp() else onDown()
            }
        }
    )
}

private fun Modifier.pointerInputHorizontalDrag(
    threshold: Float,
    onLeft: () -> Unit,
    onRight: () -> Unit,
): Modifier = this.pointerInput(threshold) {
    var totalDrag = 0f
    var fired = false
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f; fired = false },
        onDragEnd = { totalDrag = 0f; fired = false },
        onDragCancel = { totalDrag = 0f; fired = false },
        onHorizontalDrag = { change, dragAmount ->
            if (fired) return@detectHorizontalDragGestures
            totalDrag += dragAmount
            if (abs(totalDrag) > threshold) {
                fired = true
                change.consume()
                if (totalDrag < 0) onLeft() else onRight()
            }
        }
    )
}
