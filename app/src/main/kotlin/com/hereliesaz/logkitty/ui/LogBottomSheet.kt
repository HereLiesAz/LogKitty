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
import androidx.compose.material.icons.filled.Settings
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

@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    screenHeight: Dp,
    navBarHeight: Dp,
    collapsedHeightDp: Dp,
    currentPeekFraction: Float,
    onPeekFractionChange: (Float) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isHidden = sheetState.value == BottomSheetValue.Collapsed

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

    val listState = rememberLazyListState()
    
    val sheetBackgroundColor = Color(backgroundColorInt).copy(alpha = overlayOpacity)
    
    val currentFontFamily = remember(fontFamilyName) {
        val enumVal = try { CodingFont.valueOf(fontFamilyName) } catch (e: Exception) { CodingFont.SYSTEM }
        getGoogleFontFamily(enumVal.fontName)
    }

    LaunchedEffect(sheetState.dragProgress) {
        if (sheetState.dragProgress > 0.45f && currentPeekFraction != 0.50f) {
            onPeekFractionChange(0.50f)
        } else if (sheetState.dragProgress <= 0.45f && currentPeekFraction != 0.25f) {
            onPeekFractionChange(0.25f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isHidden) {
            val rawLatest = systemLogMessages.lastOrNull() ?: "LogKitty Ready"
            // Strip timestamp if setting is off
            val latestLog = if (showTimestamp) rawLatest else rawLatest.replace(Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), "")
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeightDp)
                    .align(Alignment.BottomCenter)
                    .background(sheetBackgroundColor)
                    .clickable { scope.launch { sheetState.peek() } }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = navBarHeight)
                        .height(collapsedHeightDp - navBarHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = latestLog,
                        fontFamily = currentFontFamily,
                        fontSize = fontSize.sp,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(40.dp)
                            .height(4.dp)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.8f))
                    )
                }
            }
        }

        BottomSheetLayout(
            state = sheetState,
            peekHeight = PeekHeight.dp((screenHeight * currentPeekFraction + navBarHeight).value),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetBackgroundColor)
            ) {
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

                if (systemLogMessages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No logs", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = isLogReversed,
                        contentPadding = PaddingValues(bottom = navBarHeight + 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        itemsIndexed(systemLogMessages) { index, message ->
                            val displayText = if (showTimestamp) message else message.replace(Regex("^\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"), "")
                            Text(
                                text = displayText,
                                fontFamily = currentFontFamily,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4).sp,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 0.5.dp),
                                color = logColors[LogLevel.fromLine(message)] ?: Color.White
                            )
                        }
                    }
                }
            }
        }
        
        if (!isHidden) {
             Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            ) {
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
