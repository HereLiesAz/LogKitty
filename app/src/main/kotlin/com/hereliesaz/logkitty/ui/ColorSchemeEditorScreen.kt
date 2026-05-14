package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Per-level color customization. Selecting a swatch opens the [ColorPickerDialog] for that
 * level and persists the choice via [MainViewModel.setLogColor], which also switches the
 * active scheme to CUSTOM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSchemeEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val logColors by viewModel.logColors.collectAsState()
    val scheme by viewModel.colorScheme.collectAsState()

    var editingLevel by remember { mutableStateOf<LogLevel?>(null) }
    editingLevel?.let { level ->
        ColorPickerDialog(
            initialColor = logColors[level] ?: level.defaultColor,
            onDismiss = { editingLevel = null },
            onColorSelected = {
                viewModel.setLogColor(level, it)
                editingLevel = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Colors — ${scheme.displayName}") },
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
                .padding(16.dp)
        ) {
            Text(
                "Tap a swatch to customize. Editing any color switches the active scheme to Custom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            LogLevel.values().forEach { level ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingLevel = level }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(level.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Letter: ${level.letter}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(logColors[level] ?: level.defaultColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
