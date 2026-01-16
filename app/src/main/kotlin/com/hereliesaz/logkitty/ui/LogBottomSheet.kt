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
    onSendPrompt: (String) -> Unit,
    onInteraction: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent
    val isExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent

    // Safely find the hidden detent from the state's own list to avoid identity issues
    val hiddenDetent = remember(sheetState.detents) { 
        sheetState.detents.find { it.toString().contains("hidden", ignoreCase = true) } ?: SheetDetent.Hidden 
    }

    val scope = rememberCoroutineScope()
    BackHandler(enabled = isExpanded) {
        scope.launch {
            sheetState.jumpTo(hiddenDetent)
        }
    }

    val systemLogMessages by viewModel.filteredSystemLog.collectAsState()
    val isContextModeEnabled by viewModel.isContextModeEnabled.collectAsState()
    val currentApp by viewModel.currentForegroundApp.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val logColors by viewModel.logColors.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- OPTIMIZED AUTO-SCROLL ---
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var selectedLogIndex by remember { mutableStateOf<Int?>(null) }

    // Detect if user scrolled up manually
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            autoScrollEnabled = lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(systemLogMessages.size) {
        if (autoScrollEnabled && systemLogMessages.isNotEmpty() && selectedLogIndex == null) {
            listState.scrollToItem(systemLogMessages.size - 1)
        }
    }

    val contentHeight = when (sheetState.currentDetent) {
        fullyExpandedDetent -> screenHeight * 0.9f
        halfwayDetent -> screenHeight * 0.6f
        peekDetent -> screenHeight * 0.35f
        hiddenDetent -> screenHeight * 0.12f
        else -> 0.dp
    }

    // Swiping side to side logic
    val swipeThreshold = 100f

    // Blocking connection for LazyColumn to prevent sheet drag on overscroll
    val blockingNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consume all available scroll to prevent parent (BottomSheet) from getting it
                return available
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // Consume all available fling
                return available
            }
        }
    }

    BottomSheet(
        state = sheetState,
        modifier = Modifier.fillMaxSize()
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

                if (contentHeight > 0.dp) {
                    Column(
                        modifier = Modifier.height(contentHeight)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { /* Handled by scrollable tab row or custom logic if needed */ }
                                    ) { change, dragAmount ->
                                        if (abs(dragAmount) > swipeThreshold) {
                                            val currentIndex = tabs.indexOf(selectedTab)
                                            if (dragAmount > 0) { // Swipe Right -> Previous Tab
                                                if (currentIndex > 0) {
                                                    viewModel.selectTab(tabs[currentIndex - 1])
                                                }
                                            } else { // Swipe Left -> Next Tab
                                                if (currentIndex < tabs.size - 1) {
                                                    viewModel.selectTab(tabs[currentIndex + 1])
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                        ) {
                            // Handle
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

                if (isHalfwayExpanded) {
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
