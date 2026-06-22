package com.hereliesaz.logkitty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.ui.theme.getGoogleFontFamily
import kotlin.math.abs

/** Convenience for callers that used to call `SheetController.hide()`. */
fun AzSheetController.hide() { snapTo(AzSheetDetent.HIDDEN) }

/**
 * The log-viewing content slot for `AzBottomSheetWindowHost`.
 *
 * The host owns the four-detent state machine, the scrim above HALF/FULL, the hidden swipe-up
 * strip, and the accumulated-delta vertical drag — so this composable only renders what fills
 * each detent's body:
 *
 *   HIDDEN — a one-line strip showing the latest log entry; tapping it steps up.
 *   PEEK   — the last four log lines (sized so at least three are visible above the nav bar).
 *   HALF / FULL — tabs, optional selection action bar, and the log list.
 *
 * Horizontal-drag tab switching is implemented here because the system-overlay flavor of the host
 * doesn't expose `onSwipeLeft/Right` callbacks. Vertical drag is handled by the host.
 *
 * **Selection model**
 *   Tap a log line → an action toolbar (copy / search / prohibit) appears above the log area.
 *   Long-press a log line → enter multi-select mode with that line in the set. Subsequent taps
 *     toggle lines in/out of the set; the toolbar shows the count and a batch Copy. Search and
 *     Prohibit are only shown when exactly one line is selected. Clearing the last line (or
 *     pressing X) exits multi-select mode.
 */
@Composable
fun LogBottomSheet(
    controller: AzSheetController,
    viewModel: MainViewModel,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val indexedLog by viewModel.filteredIndexedLog.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val showTimestamp by viewModel.showTimestamp.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val logColors by viewModel.logColors.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val tagColoringEnabled by viewModel.tagColoringEnabled.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val githubOwner by viewModel.githubOwner.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()

    val currentFontFamily = remember(fontFamilyName) {
        val enumVal = try { CodingFont.valueOf(fontFamilyName) } catch (e: Exception) { CodingFont.SYSTEM }
        getGoogleFontFamily(enumVal.fontName)
    }

    var selectedLineIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedLines = remember(selectedLineIds, indexedLog) {
        if (selectedLineIds.isEmpty()) emptyList()
        else indexedLog.filter { it.id in selectedLineIds }.sortedBy { it.id }
    }

    // Which app tabs are currently flipped from Logs to the developer-stats view. Tracked per tab id
    // so each monitored app remembers its own Logs/Stats choice while the sheet is open.
    var statsModeTabs by remember { mutableStateOf<Set<String>>(emptySet()) }
    val statsActive = selectedTab.type == TabType.APP && selectedTab.id in statsModeTabs
    // The Stats view's content (and its polling) lives entirely in the on-demand :feature:stats
    // module via StatsFeatureSlot, which only collects while it's on screen — so there's nothing to
    // start/stop from here.

    when (controller.detent) {
        AzSheetDetent.HIDDEN -> PeekStrip(
            modifier = Modifier.fillMaxSize(),
            // One line: the latest entry, sitting just above the nav bar.
            lines = if (indexedLog.isEmpty()) listOf(stringResource(R.string.sheet_ready))
                    else indexedLog.takeLast(1).map { it.text },
            showTimestamp = showTimestamp,
            fontFamily = currentFontFamily,
            fontSize = fontSize,
            onTap = { controller.stepUp() },
            onSwipeLeft = { viewModel.selectNextTab() },
            onSwipeRight = { viewModel.selectPreviousTab() },
        )
        AzSheetDetent.PEEK -> PeekStrip(
            modifier = Modifier.fillMaxSize(),
            // The last three entries, oldest-to-newest, pinned above the nav bar.
            lines = if (indexedLog.isEmpty()) listOf(stringResource(R.string.sheet_ready))
                    else indexedLog.takeLast(3).map { it.text },
            showTimestamp = showTimestamp,
            fontFamily = currentFontFamily,
            fontSize = fontSize,
            onTap = { controller.stepUp() },
            onSwipeLeft = { viewModel.selectNextTab() },
            onSwipeRight = { viewModel.selectPreviousTab() },
        )
        AzSheetDetent.HALF, AzSheetDetent.FULL -> ExpandedView(
            tabs = tabs,
            selectedTab = selectedTab,
            indexedLog = indexedLog,
            logColors = logColors,
            tagColoringEnabled = tagColoringEnabled,
            fontFamily = currentFontFamily,
            fontSize = fontSize,
            showTimestamp = showTimestamp,
            isLogReversed = isLogReversed,
            isPaused = isPaused,
            showStatsToggle = selectedTab.type == TabType.APP,
            statsActive = statsActive,
            useRoot = isRootEnabled,
            onToggleStats = {
                statsModeTabs = if (selectedTab.id in statsModeTabs) statsModeTabs - selectedTab.id
                                else statsModeTabs + selectedTab.id
            },
            selectedLineIds = selectedLineIds,
            selectedLines = selectedLines,
            onTapLine = { id ->
                if (isMultiSelectMode) {
                    val next = if (id in selectedLineIds) selectedLineIds - id else selectedLineIds + id
                    selectedLineIds = next
                    if (next.isEmpty()) isMultiSelectMode = false
                } else {
                    selectedLineIds = if (selectedLineIds == setOf(id)) emptySet() else setOf(id)
                }
            },
            onLongPressLine = { id ->
                isMultiSelectMode = true
                selectedLineIds = selectedLineIds + id
            },
            onClearSelection = {
                selectedLineIds = emptySet()
                isMultiSelectMode = false
            },
            onTabSelected = { viewModel.selectTab(it) },
            onCloseAppTab = { viewModel.closeTab(it) },
            onSwipeLeft = { viewModel.selectNextTab() },
            onSwipeRight = { viewModel.selectPreviousTab() },
            onSaveClick = {
                controller.hide()
                onSaveClick()
            },
            onSettingsClick = {
                controller.hide()
                onSettingsClick()
            },
            onTogglePause = { viewModel.togglePause() },
            onClearClick = { viewModel.clearActiveTab() },
            onCloseClick = { controller.hide() },
            onCopySelected = {
                val joined = selectedLines.joinToString("\n") { it.text }
                clipboardManager.setText(AnnotatedString(joined))
                selectedLineIds = emptySet()
                isMultiSelectMode = false
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
                selectedLineIds = emptySet()
                isMultiSelectMode = false
            },
            onProhibitLine = { line ->
                viewModel.prohibitLog(line.text)
                selectedLineIds = emptySet()
                isMultiSelectMode = false
            },
            githubOwner = githubOwner,
            githubRepo = githubRepo,
            githubTokenProvider = { viewModel.readGithubToken() },
        )
    }
}

@Composable
private fun PeekStrip(
    modifier: Modifier,
    lines: List<String>,
    showTimestamp: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontSize: Int,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val timestampRegex = remember { Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+") }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .pointerInputHorizontalDrag(threshold = 48f, onLeft = onSwipeLeft, onRight = onSwipeRight)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() }
    ) {
        // Force fontScale = 1 so the rendered line height always equals the height the detent was
        // sized for (sheetConfig in LogKittyOverlayService) — otherwise a user with a large system
        // font scale would overflow the strip and only one line would fit.
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                // Bottom-align so the line(s) hug the bottom edge (just above the nav bar); when
                // the log is shorter than the strip, the slack appears above the text, not below.
                verticalArrangement = Arrangement.Bottom
            ) {
                lines.forEach { line ->
                    val displayText = if (showTimestamp) line else line.replace(timestampRegex, "")
                    Text(
                        text = displayText,
                        fontFamily = fontFamily,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.35f).sp,
                        // Trim the leading above the first line and below the last so the text
                        // block hugs the bottom edge tightly.
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Proportional,
                                trim = LineHeightStyle.Trim.Both
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
            }
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
    isPaused: Boolean,
    showStatsToggle: Boolean,
    statsActive: Boolean,
    useRoot: Boolean,
    onToggleStats: () -> Unit,
    selectedLineIds: Set<Long>,
    selectedLines: List<IndexedLogLine>,
    onTapLine: (Long) -> Unit,
    onLongPressLine: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onTabSelected: (LogTab) -> Unit,
    onCloseAppTab: (LogTab) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTogglePause: () -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
    onCopySelected: () -> Unit,
    onSearchLine: (IndexedLogLine) -> Unit,
    onProhibitLine: (IndexedLogLine) -> Unit,
    githubOwner: String,
    githubRepo: String,
    githubTokenProvider: () -> String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // --- Header zone: tabs + icons (horizontal drag = tab swap; host handles vertical) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                                                contentDescription = stringResource(R.string.cd_close_tab),
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
                    if (showStatsToggle) {
                        LogsStatsToggle(statsActive = statsActive, onToggle = onToggleStats)
                    }
                    IconButton(onClick = onSaveClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Save, stringResource(R.string.cd_save), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onTogglePause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.cd_resume_logging) else stringResource(R.string.cd_pause_logging),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onClearClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteSweep, stringResource(R.string.cd_clear_logs), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onCloseClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }

        // --- Selection action bar (Copy / Search / Prohibit) — logs only ---
        AnimatedVisibility(visible = !statsActive && selectedLineIds.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            val count = selectedLines.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (count <= 1) stringResource(R.string.sheet_selected_entry) else stringResource(R.string.sheet_count_selected, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopySelected, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.cd_copy_selected), tint = MaterialTheme.colorScheme.onSurface)
                }
                if (count == 1) {
                    val only = selectedLines.first()
                    IconButton(onClick = { onSearchLine(only) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, stringResource(R.string.cd_search_google), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { onProhibitLine(only) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Block, stringResource(R.string.cd_prohibit_tag), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.cd_deselect), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // --- Log area (horizontal = tab swap; host handles vertical) ---
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInputHorizontalDrag(threshold = 64f, onLeft = onSwipeLeft, onRight = onSwipeRight)
        ) {
            if (selectedTab.type == TabType.GITHUB) {
                // The GitHub tab's body is the on-demand :feature:github panel, not the log stream.
                GitHubFeatureSlot(
                    owner = githubOwner,
                    repo = githubRepo,
                    tokenProvider = githubTokenProvider,
                    onConfigure = onSettingsClick,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (statsActive) {
                StatsFeatureSlot(
                    packageName = selectedTab.filterValue,
                    label = selectedTab.title,
                    useRoot = useRoot,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (indexedLog.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.sheet_no_logs), color = Color.Gray)
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
                            isSelected = line.id in selectedLineIds,
                            showTimestamp = showTimestamp,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            colors = logColors,
                            tagColoringEnabled = tagColoringEnabled,
                            onClick = { onTapLine(line.id) },
                            onLongClick = { onLongPressLine(line.id) }
                        )
                    }
                }
            }
        }

        // --- Banner ad, pinned to the bottom of the expanded sheet (HALF / FULL only). ---
        // Takes no space (and shows no divider) until the on-demand :feature:ads module is installed
        // and the ad loads, so it never leaves an empty gap below the log. Shown on every tab,
        // including GitHub, so the banner stays consistently anchored at the bottom.
        AdBannerSlot(modifier = Modifier.fillMaxWidth(), showTopDivider = true)
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
    onLongClick: () -> Unit,
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
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        // Force fontScale = 1 so log lines always render at the user's chosen size, independent of
        // the device's system font-size setting.
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
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
}

/**
 * Compact two-segment pill that flips an app tab between its Logs and developer-Stats views.
 * Shown only when an app-specific tab is selected.
 */
@Composable
private fun LogsStatsToggle(statsActive: Boolean, onToggle: () -> Unit) {
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() }
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Segment(stringResource(R.string.stats_toggle_logs), active = !statsActive, activeBg = activeBg)
        Segment(stringResource(R.string.stats_toggle_stats), active = statsActive, activeBg = activeBg)
    }
}

@Composable
private fun Segment(label: String, active: Boolean, activeBg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) activeBg else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
}

/**
 * Modifier that fires [onLeft] / [onRight] exactly once per gesture when the *accumulated*
 * horizontal displacement crosses [threshold]. Used for tab switching inside the sheet body —
 * the host owns vertical drag and detent transitions.
 */
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
