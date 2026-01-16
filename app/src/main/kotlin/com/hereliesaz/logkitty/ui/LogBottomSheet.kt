package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LogBottomSheet(
    sheetState: BottomSheetState,
    viewModel: MainViewModel,
    peekDetent: SheetDetent,
    halfwayDetent: SheetDetent,
    fullyExpandedDetent: SheetDetent,
    screenHeight: Dp,
    onSendPrompt: (String) -> Unit,
    onSaveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isHalfwayExpanded = sheetState.currentDetent == halfwayDetent || sheetState.currentDetent == fullyExpandedDetent

    val systemLogMessages by viewModel.filteredSystemLog.collectAsState()
    val isContextModeEnabled by viewModel.isContextModeEnabled.collectAsState()
    val currentApp by viewModel.currentForegroundApp.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- OPTIMIZED AUTO-SCROLL ---
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Detect if user scrolled up manually
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            autoScrollEnabled = lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(systemLogMessages.size) {
        if (autoScrollEnabled && systemLogMessages.isNotEmpty()) {
            listState.scrollToItem(systemLogMessages.size - 1)
        }
    }

    val contentHeight = when (sheetState.currentDetent) {
        fullyExpandedDetent -> screenHeight * 0.8f
        halfwayDetent -> screenHeight * 0.5f
        peekDetent -> screenHeight * 0.25f
        else -> 0.dp
    }

    val bottomBufferHeight = screenHeight * 0.075f

    // Theme is provided by parent (LogKittyTheme)
    BottomSheet(
        state = sheetState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = overlayOpacity))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

                if (contentHeight > 0.dp) {
                    Column(modifier = Modifier.height(contentHeight)) {

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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                itemsIndexed(
                                    items = systemLogMessages,
                                    key = { index, _ -> index }
                                ) { _, message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        ContextlessChatInput(
                            modifier = Modifier.fillMaxWidth(),
                            onSend = onSendPrompt
                        )

                        Spacer(modifier = Modifier.height(bottomBufferHeight))
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
                        IconButton(onClick = onSaveClick) {
                             Icon(
                                 imageVector = Icons.Default.Save,
                                 contentDescription = "Save Log",
                                 tint = MaterialTheme.colorScheme.onSurface
                             )
                        }
                        IconButton(onClick = onSettingsClick) {
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
                        IconButton(onClick = { viewModel.clearLog() }) {
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
