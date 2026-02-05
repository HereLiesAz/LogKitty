package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.ui.theme.CodingFont

/**
 * [SettingsScreen] provides a full-screen configuration UI.
 *
 * It allows the user to tweak every aspect of the app:
 * - **Appearance:** Opacity, Background Color, Fonts, Timestamps.
 * - **Behavior:** Log Buffer Size, Active Log Levels, Filtering modes.
 * - **Advanced:** Root Access, Log Reversal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: MainViewModel) {
    val scrollState = rememberScrollState()

    // Collecting StateFlows from ViewModel
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val isContextMode by viewModel.isContextModeEnabled.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val showTimestamp by viewModel.showTimestamp.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val activeLevels by viewModel.activeLogLevels.collectAsState()

    // Local state for dialogs
    var showColorPicker by remember { mutableStateOf(false) }

    // Color Picker Dialog Overlay
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(backgroundColorInt),
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                viewModel.setBackgroundColor(it.toArgb())
                showColorPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {

            // --- SECTION: APPEARANCE ---
            SettingsSectionHeader("Appearance")

            // Background Color Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showColorPicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Color", style = MaterialTheme.typography.bodyLarge)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(backgroundColorInt))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
            HorizontalDivider()

            // Opacity Slider
            Text("Background Opacity: ${(overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = overlayOpacity,
                onValueChange = { viewModel.setOverlayOpacity(it) },
                valueRange = 0.1f..1.0f,
                steps = 9
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION: TYPOGRAPHY ---
            SettingsSectionHeader("Typography")

            // Font Size Slider
            Text("Font Size: ${fontSize}sp", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { viewModel.setFontSize(it.toInt()) },
                valueRange = 8f..24f,
                steps = 15
            )

            // Font Family Dropdown
            var fontExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { fontExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape
                ) {
                    Text("Font: $fontFamilyName")
                }
                DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                    CodingFont.values().forEach { font ->
                        DropdownMenuItem(
                            text = { Text(font.displayName) },
                            onClick = {
                                viewModel.setFontFamily(font)
                                fontExpanded = false
                            }
                        )
                    }
                }
            }

            // Timestamps Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Timestamps", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = showTimestamp, onCheckedChange = { viewModel.setShowTimestamp(it) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION: BEHAVIOR ---
            SettingsSectionHeader("Behavior")

            // Log Level Matrix
            Text("Active Log Levels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LogLevel.values().forEach { level ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkbox(
                            checked = activeLevels.contains(level.name),
                            onCheckedChange = { viewModel.toggleLogLevel(level, it) }
                        )
                        Text(level.name.first().toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Buffer Size Dropdown
            var bufferExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Buffer Size", style = MaterialTheme.typography.bodyLarge)
                Box {
                    OutlinedButton(onClick = { bufferExpanded = true }) { Text(bufferSize.toString()) }
                    DropdownMenu(expanded = bufferExpanded, onDismissRequest = { bufferExpanded = false }) {
                        listOf(1000, 2000, 5000, 10000).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size lines") },
                                onClick = {
                                    viewModel.setBufferSize(size)
                                    bufferExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Context Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Context Mode (Auto-Filter)", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isContextMode, onCheckedChange = { viewModel.toggleContextMode() })
            }

            // Root Access Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) { Text("Root Access", style = MaterialTheme.typography.bodyLarge) }
                Switch(checked = isRootEnabled, onCheckedChange = { viewModel.setRootEnabled(it) })
            }

            // Reverse Log Order Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reverse Log Order", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isLogReversed, onCheckedChange = { viewModel.setLogReversed(it) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- ACTIONS ---
            AzButton(
                onClick = { viewModel.clearLog() },
                text = "Clear Log",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            AzButton(
                onClick = { viewModel.resetLogColors() },
                text = "Reset Colors",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
