package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.sheets.BottomSheetState
import com.dokar.sheets.BottomSheetValue
import com.dokar.sheets.PeekHeight
import com.dokar.sheets.m3.BottomSheetLayout
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.ui.theme.getGoogleFontFamily
import kotlinx.coroutines.launch

/**
 * [LogBottomSheet] is the primary UI of the application.
 *
 * It acts as the "Face" of LogKitty, rendering the floating window content.
 *
 * **Interaction Model:**
 * - **Collapsed:** Shows a tiny "Peek" strip at the bottom (anchored above the nav bar).
 *   Displays the single most recent log line.
 * - **Peek (Half-Expanded):** Shows about 35% of the screen. Good for casual monitoring.
 * - **Expanded:** Shows nearly full screen (limited by system bars) for deep investigation.
 *
 * **Performance:**
 * Uses [LazyColumn] to efficiently render the high-frequency log stream.
 */
@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    screenHeight: Dp,
    navBarHeight: Dp,
    collapsedHeightDp: Dp,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Check if we are in the smallest state to switch between "Peek View" and "List View".
    val isHidden = sheetState.value == BottomSheetValue.Collapsed

    // Collect all necessary state from the ViewModel.
    val systemLogMessages by viewModel.filteredSystemLog.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val showTimestamp by viewModel.showTimestamp.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val logColors by viewModel.logColors.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val isContextMode by viewModel.isContextModeEnabled.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    
    // Apply user-defined opacity to the background color.
    val sheetBackgroundColor = Color(backgroundColorInt).copy(alpha = overlayOpacity)
    
    // Resolve the selected Google Font family.
    val currentFontFamily = remember(fontFamilyName) {
        val enumVal = try { CodingFont.valueOf(fontFamilyName) } catch (e: Exception) { CodingFont.SYSTEM }
        getGoogleFontFamily(enumVal.fontName)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- 1. COLLAPSED VIEW (The "Peek" Strip) ---
        // This is a custom Box rendered manually when the sheet is collapsed.
        // We do this because the actual BottomSheet library might hide completely or behave differently
        // at 0 height. We want a persistent "ticker" at the bottom.
        if (isHidden) {
            val rawLatest = systemLogMessages.lastOrNull() ?: "LogKitty Ready"
            // Strip timestamp if user setting requests it, to save horizontal space in the small strip.
            val latestLog = if (showTimestamp) rawLatest else rawLatest.replace(Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), "")
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeightDp)
                    .align(Alignment.BottomCenter)
                    .background(sheetBackgroundColor)
                    // Tapping the strip expands it to the "Peek" (35%) state.
                    .clickable { scope.launch { sheetState.peek() } }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Ensure we respect the system navigation bar so we don't draw under the gesture area/buttons.
                        .padding(bottom = navBarHeight)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Visual Drag Handle indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(40.dp)
                            .height(4.dp)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )
                    // The single line of log text.
                    Text(
                        text = latestLog,
                        fontFamily = currentFontFamily,
                        fontSize = fontSize.sp,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
            }
        }

        // --- 2. EXPANDED VIEW (The Actual Bottom Sheet) ---
        BottomSheetLayout(
            state = sheetState,
            // We define the "Peek" height as 35% of the screen.
            peekHeight = PeekHeight.dp((screenHeight * 0.35f).value),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetBackgroundColor)
            ) {
                // Drag Handle area
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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

                // Tab Row (System, Errors, App-specific tabs)
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
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tab.title)
                                    // Allow closing app-specific tabs
                                    if (tab.type == TabType.APP) {
                                        IconButton(onClick = { viewModel.closeTab(tab) }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Close, "Close")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Main Content Area
                if (systemLogMessages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No logs", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = isLogReversed,
                        // Add bottom padding to account for the navbar + some breathing room
                        contentPadding = PaddingValues(bottom = navBarHeight + 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        itemsIndexed(systemLogMessages) { index, message ->
                            val displayText = if (showTimestamp) message else message.replace(Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), "")

                            // Determine color based on log level
                            val level = LogLevel.fromLine(message)
                            val color = logColors[level] ?: Color.White

                            Text(
                                text = displayText,
                                fontFamily = currentFontFamily,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4).sp,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 0.5.dp),
                                color = color,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        
        // --- 3. HEADER CONTROLS (Floating Icons) ---
        // Visible only when expanded/peeking.
        if (!isHidden) {
             Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            ) {
                // Toggle Context Mode (Eye Icon)
                IconButton(onClick = { viewModel.toggleContextMode() }) {
                     Icon(
                         if (isContextMode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                         "Context Mode",
                         tint = MaterialTheme.colorScheme.onSurface
                     )
                }
                // Save to File
                IconButton(onClick = onSaveClick) {
                     Icon(Icons.Default.Save, "Save", tint = MaterialTheme.colorScheme.onSurface)
                }
                // Copy All to Clipboard
                IconButton(onClick = {
                    scope.launch { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(systemLogMessages.joinToString("\n"))) }
                }) {
                     Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurface)
                }
                // Open Settings
                IconButton(onClick = onSettingsClick) {
                     Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                }
                // Clear Logs
                IconButton(onClick = { viewModel.clearLog() }) {
                    Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
