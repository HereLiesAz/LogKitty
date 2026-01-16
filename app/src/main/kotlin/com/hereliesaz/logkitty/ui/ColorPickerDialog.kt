package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismissRequest: () -> Unit
) {
    var hexText by remember { mutableStateOf(String.format("#%08X", initialColor.toArgb())) }
    var currentColor by remember { mutableStateOf(initialColor) }

    val presets = listOf(
        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
        Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B), Color.White
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Color", style = MaterialTheme.typography.titleLarge)

                // Preview
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                )

                // Hex Input
                AzTextBox(
                    value = hexText,
                    onValueChange = {
                        hexText = it
                        try {
                            currentColor = Color(android.graphics.Color.parseColor(it))
                        } catch (e: Exception) {
                            // Invalid hex
                        }
                    },
                    hint = "Hex Code",
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = {}
                )

                // Presets
                Text("Presets", style = MaterialTheme.typography.titleSmall)

                // Simple Grid manually
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.chunked(5).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            currentColor = color
                                            hexText = String.format("#%08X", color.toArgb())
                                        }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AzButton(
                        onClick = onDismissRequest,
                        text = "Cancel",
                        shape = AzButtonShape.RECTANGLE,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    AzButton(
                        onClick = { onColorSelected(currentColor) },
                        text = "Select",
                        shape = AzButtonShape.RECTANGLE
                    )
                }
            }
        }
    }
}
