package com.hereliesaz.logkitty.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.dokar.sheets.BottomSheetState
import com.dokar.sheets.BottomSheetValue
import com.dokar.sheets.PeekHeight
import com.dokar.sheets.m3.BottomSheetLayout
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    screenHeight: Dp,
    isWindowExpanded: Boolean,
    bottomPadding: Dp,
    onSendPrompt: (String) -> Unit,
    onInteraction: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Determine visibility states
    // Collapsed = Hidden (0.02f line only)
    // Peeked = Peek (0.25f)
    // Expanded = Full (0.8f)

    val isHidden = sheetState.value == BottomSheetValue.Collapsed
    val isExpanded = sheetState.value == BottomSheetValue.Expanded
    val isPeeked = sheetState.value == BottomSheetValue.Peeked

    BackHandler(enabled = !isHidden) {
        scope.launch {
            sheetState.collapse()
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
    val listState = rememberLazyListState()

    // --- OPTIMIZED AUTO-SCROLL ---
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var selectedLogIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems > 0 && lastVisibleItem != null) {
                autoScrollEnabled = lastVisibleItem.index >= totalItems - 2
            }
        }
    }

    LaunchedEffect(systemLogMessages.size, isLogReversed) {
        if (autoScrollEnabled && systemLogMessages.isNotEmpty() && selectedLogIndex == null) {
             listState.scrollToItem(systemLogMessages.size - 1)
        }
    }

    // Swipe Threshold
    val swipeThreshold = 100f

    // Blocking connection for LazyColumn
    val blockingNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    // Container
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
    ) {
        // Hidden State Content (Always present, but visible when collapsed)
        // We render it if state is Collapsed
        if (isHidden) {
            val latestLog = systemLogMessages.lastOrNull() ?: "No logs"
            val level = LogLevel.fromLine(latestLog)
            val color = logColors[level] ?: level.defaultColor

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.02f)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
                    .clickable {
                        scope.launch { sheetState.peek() }
                    }
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
        }

        // Bottom Sheet
        BottomSheetLayout(
            state = sheetState,
            peekHeight = PeekHeight.dp((screenHeight * 0.25f).value),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
            skipPeeked = false,
        ) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                 if (abs(totalDrag) > swipeThreshold) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
                ) {
                    // Drag Handle
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

                    // Content
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
                                .padding(horizontal = 16.dp),
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
                                            IconButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(message))
                                                selectedLogIndex = null
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(message)}"))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                                selectedLogIndex = null
                                            }) {
                                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                                            }
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

        // Context Actions (Visible when expanded or peeked)
        if (!isHidden) {
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
                    scope.launch {
                        sheetState.collapse()
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
                    scope.launch {
                        sheetState.collapse()
                    }
                }) {
                     Icon(
                         imageVector = Icons.Default.Settings,
                         contentDescription = "Settings",
                         tint = MaterialTheme.colorScheme.onSurface
                     )
                }
                IconButton(onClick = {
                    scope.launch {
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
                        scope.launch {
                            sheetState.collapse()
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
