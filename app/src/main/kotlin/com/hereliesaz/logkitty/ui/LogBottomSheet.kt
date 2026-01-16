package com.hereliesaz.logkitty.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    fullyExpandedDetent: SheetDetent,
    screenHeight: Dp,
    bottomPadding: Dp, // Added
    onSendPrompt: (String) -> Unit,
    onInteraction: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent
    val isExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent || sheetState.currentDetent == peekDetent

    // Safely find the hidden detent from the state's own list to avoid identity issues
    val hiddenDetent = remember(sheetState.detents) { 
        sheetState.detents.find { it.toString().contains("hidden", ignoreCase = true) } ?: SheetDetent.Hidden 
    }

    val isHidden = sheetState.currentDetent == hiddenDetent

    val scope = rememberCoroutineScope()
    BackHandler(enabled = isExpanded && !isHidden) {
        scope.launch {
            sheetState.jumpTo(hiddenDetent)
        }
    }

    val systemLogMessages by viewModel.filteredSystemLog.collectAsState()
    val isContextModeEnabled by viewModel.isContextModeEnabled.collectAsState()
    val currentApp by viewModel.currentForegroundApp.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val logColors by viewModel.logColors.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- OPTIMIZED AUTO-SCROLL ---
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var selectedLogIndex by remember { mutableStateOf<Int?>(null) }

    // Detect if user scrolled manually
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            // In reversed mode (Newest at Top), auto-scroll means staying at index 0?
            // Wait, reverseLayout = true. Index 0 is at Bottom. Index N is at Top.
            // When reversed, items are laid out from bottom to top? No.
            // reverseLayout=true in Column: items are laid out from bottom to top.
            // The list starts at the bottom of the visible area.
            // If I have [A, B, C].
            // reverseLayout=false: Top [A, B, C] Bottom.
            // reverseLayout=true: Top [C, B, A] Bottom.
            // So if new item D arrives. [D, C, B, A].
            // To see D (newest), we need to be at the "start" (which is top).
            // Default scroll position for reverseLayout is 0?
            // Actually, usually reverseLayout starts scrolled to the bottom (index 0).
            // If list is [0..N], and reversed.
            // 0 is bottom. N is top.
            // If I want to see N (newest), I need to scroll to end?
            // Let's rely on standard chat behavior: reverseLayout=true, items=reversedList?
            // No, just reverseLayout=true.
            // If I want "Newest at Top", I want visual order: Newest, Older, Oldest.
            // If `systemLogMessages` is [Oldest, ..., Newest].
            // Standard: Oldest -> Newest.
            // I want: Newest -> Oldest.
            // So I should just reverse the list data OR use reverseLayout?
            // If I use reverseLayout=true on [Old, New]. It renders New (Top), Old (Bottom).
            // This is what we want.
            // Auto-scroll: Ensure we are seeing the Top (Newest).
            // Index N (Newest) is at the Top.
            // If reverseLayout=true, item 0 is at bottom. Item N is at top.
            // We need to scroll to item N?

            // Simpler approach: Just reverse the list in UI if needed.
            // messages = if (isLogReversed) systemLogMessages.reversed() else systemLogMessages
            // And keep reverseLayout = false.
            // Then "Newest" is at index 0. "Oldest" is at index N.
            // Auto-scroll: If reversed, scroll to 0. Else scroll to N.
            // This is easier to reason about.

            // But wait, `reversed()` on a large list is expensive.
            // `LazyColumn(reverseLayout = true)` is better for performance.

            // Let's stick to `reverseLayout = true`.
            // If `reverseLayout = true` on `[Old...New]`.
            // Visual:
            // New (Index N)
            // ...
            // Old (Index 0)

            // If I add N+1.
            // New+1 (Index N+1)
            // New (Index N)
            // ...

            // To keep "Newest" visible, I need to be scrolled to the top?
            // In `reverseLayout`, the "end" of the list (index N) is at the top?
            // Yes.
            // So I need to scroll to index N.

            if (!isLogReversed) {
                // Normal: Newest at bottom (Index N). Scroll to N.
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleItem != null) {
                    autoScrollEnabled = lastVisibleItem.index >= totalItems - 2
                }
            } else {
                // Reversed: Newest at top (Index N). Scroll to N?
                // Visual Top is Index N.
                // Scroll Offset 0 is usually "start" (Bottom for reverseLayout?? No).
                // Let's assume scrollToItem(N) brings N into view.
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleItem != null) {
                    // This logic detects if we are near the "end" (visually bottom).
                    // But we care about the "visual top" (Index N).
                    // Actually, let's just use `scrollToItem(systemLogMessages.size - 1)` for both cases?
                    // Normal: N is at bottom. ScrollTo(N) goes to bottom.
                    // Reversed: N is at top. ScrollTo(N) goes to top.
                    // So logic is the same!

                    // We just need to check if we are "at the end".
                    // lastVisibleItem.index is the index in data.
                    autoScrollEnabled = lastVisibleItem.index >= totalItems - 2
                }
            }
        }
    }

    LaunchedEffect(systemLogMessages.size, isLogReversed) {
        if (autoScrollEnabled && systemLogMessages.isNotEmpty() && selectedLogIndex == null) {
             listState.scrollToItem(systemLogMessages.size - 1)
        }
    }

    // Detent Heights are handled by Service, but we need to supply content height for the sheet itself
    // to layout properly within the window.
    // However, BottomSheet composable handles the height via detents.
    // We just need to ensure our content *can* fill it.

    // We also need to apply bottomPadding to the content to avoid the 10% gap if expanded.

    val contentHeight = when (sheetState.currentDetent) {
        fullyExpandedDetent -> (screenHeight * 0.80f)
        halfwayDetent -> (screenHeight * 0.50f)
        peekDetent -> (screenHeight * 0.25f)
        hiddenDetent -> (screenHeight * 0.02f)
        else -> 0.dp
    }.coerceAtLeast(0.dp)

    // Swiping side to side logic
    val swipeThreshold = 100f

    // Blocking connection for LazyColumn
    val blockingNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var isInteracting = false
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            if (changes.isNotEmpty()) {
                                val pressed = changes.any { it.pressed }
                                if (pressed != isInteracting) {
                                    isInteracting = pressed
                                    onInteraction(isInteracting)
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                             if (abs(totalDrag) > swipeThreshold && !isHidden) {
                                 val currentIndex = tabs.indexOf(selectedTab)
                                 if (totalDrag > 0) { // Swipe Right -> Previous Tab
                                     if (currentIndex > 0) {
                                         viewModel.selectTab(tabs[currentIndex - 1])
                                     }
                                 } else { // Swipe Left -> Next Tab
                                     if (currentIndex < tabs.size - 1) {
                                         viewModel.selectTab(tabs[currentIndex + 1])
                                     }
                                 }
                             }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                }
        ) {

            if (contentHeight > 0.dp) {
                Column(
                    modifier = Modifier
                        .height(contentHeight)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
                ) {
                    // Handle (Only visible if not hidden)
                    if (!isHidden) {
                         Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            )
                        }
                    }

                    if (isHidden) {
                        // HIDDEN DETENT CONTENT: Single latest log line
                        val latestLog = systemLogMessages.lastOrNull() ?: "No logs"
                        val level = LogLevel.fromLine(latestLog)
                        val color = logColors[level] ?: level.defaultColor

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = latestLog,
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                    } else {
                        // EXPANDED CONTENT
                        // Tab Row
                        ScrollableTabRow(
                            selectedTabIndex = tabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0,
                            edgePadding = 16.dp,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {},
                            indicator = { tabPositions ->
                                if (tabs.isNotEmpty()) {
                                    val index = tabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[index])
                                    )
                                }
                            }
                        ) {
                            tabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tab.title)
                                            if (tab.type == TabType.APP) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close Tab",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { viewModel.closeTab(tab) },
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (systemLogMessages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, _ -> /* Consume to block sheet drag */ }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "No log messages yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                reverseLayout = isLogReversed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .nestedScroll(blockingNestedScrollConnection)
                            ) {
                                itemsIndexed(
                                    items = systemLogMessages,
                                    key = { index, _ -> index }
                                ) { index, message ->
                                    val level = LogLevel.fromLine(message)
                                    val color = logColors[level] ?: level.defaultColor
                                    val isSelected = selectedLogIndex == index

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                            .clickable {
                                                selectedLogIndex = if (isSelected) null else index
                                                if (!isSelected) autoScrollEnabled = false
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = color
                                        )
                                        if (isSelected) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                                            ) {
                                                // Copy
                                                IconButton(onClick = {
                                                    clipboardManager.setText(AnnotatedString(message))
                                                    selectedLogIndex = null
                                                }) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                // Search
                                                IconButton(onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(message)}"))
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                    selectedLogIndex = null
                                                }) {
                                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                // Prohibit
                                                IconButton(onClick = {
                                                    viewModel.prohibitLog(message)
                                                    selectedLogIndex = null
                                                }) {
                                                    Icon(Icons.Default.Block, contentDescription = "Prohibit", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isHalfwayExpanded && !isHidden) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                ) {
                    IconButton(onClick = { viewModel.toggleContextMode() }) {
                         Icon(
                             imageVector = if (isContextModeEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                             contentDescription = if (isContextModeEnabled) "Context Mode On ($currentApp)" else "Context Mode Off",
                             tint = if (isContextModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                         )
                    }
                    IconButton(onClick = {
                        onSaveClick()
                        coroutineScope.launch {
                            sheetState.jumpTo(hiddenDetent)
                        }
                    }) {
                         Icon(
                             imageVector = Icons.Default.Save,
                             contentDescription = "Save Log",
                             tint = MaterialTheme.colorScheme.onSurface
                         )
                    }
                    IconButton(onClick = {
                        onSettingsClick()
                        coroutineScope.launch {
                            sheetState.jumpTo(hiddenDetent)
                        }
                    }) {
                         Icon(
                             imageVector = Icons.Default.Settings,
                             contentDescription = "Settings",
                             tint = MaterialTheme.colorScheme.onSurface
                         )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            clipboardManager.setText(AnnotatedString(systemLogMessages.joinToString("\n")))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        if (systemLogMessages.isNotEmpty()) {
                            viewModel.clearLog()
                        } else {
                            coroutineScope.launch {
                                sheetState.jumpTo(hiddenDetent)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
