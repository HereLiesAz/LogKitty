package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dokar.sheets.BottomSheetState
import com.dokar.sheets.BottomSheetValue
import com.dokar.sheets.PeekHeight
import com.dokar.sheets.m3.BottomSheetLayout
import kotlinx.coroutines.launch

@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    screenHeight: Dp,
    navBarHeight: Dp,
    currentPeekFraction: Float,
    onPeekFractionChange: (Float) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isHidden = sheetState.value == BottomSheetValue.Collapsed

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

    // Peek Logic (Simplified)
    LaunchedEffect(sheetState.dragProgress) {
        if (sheetState.dragProgress > 0.45f && currentPeekFraction != 0.50f) {
            onPeekFractionChange(0.50f)
        } else if (sheetState.dragProgress <= 0.45f && currentPeekFraction != 0.25f) {
            onPeekFractionChange(0.25f)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Collapsed "Handle" area (Visible when collapsed)
        if (isHidden) {
            val latestLog = systemLogMessages.lastOrNull() ?: "LogKitty Ready"
            
            // This Box draws BEHIND the navbar because the window is NO_LIMITS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Height includes navbar to ensure we cover the bottom edge
                    .height((screenHeight * 0.05f) + navBarHeight) 
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
                    .clickable { scope.launch { sheetState.peek() } }
            ) {
                // Content sits ABOVE navbar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = navBarHeight) // Respect navbar for text
                        .height(screenHeight * 0.05f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = latestLog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Drag Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(40.dp)
                            .height(4.dp)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // The Sheet
        BottomSheetLayout(
            state = sheetState,
            peekHeight = PeekHeight.dp((screenHeight * currentPeekFraction + navBarHeight).value),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
            ) {
                // Expanded Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    )
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (tabs.isNotEmpty()) {
                            val index = tabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[index]))
                        }
                    }
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.title) }
                        )
                    }
                }

                // Log List
                if (systemLogMessages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No logs", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = isLogReversed,
                        contentPadding = PaddingValues(bottom = navBarHeight + 16.dp), // Add extra padding
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        itemsIndexed(systemLogMessages) { index, message ->
                            // Simplified for brevity - reuse your item composable
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 1.dp),
                                color = logColors[LogLevel.fromLine(message)] ?: Color.White
                            )
                        }
                    }
                }
            }
        }
        
        // Header Actions (Settings, Clear, etc) - Copied from previous logic
        if (!isHidden) {
             Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            ) {
                // ... (Keep existing buttons: Settings, Save, Copy, Clear)
                IconButton(onClick = onSettingsClick) {
                     Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { viewModel.clearLog() }) {
                    Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
