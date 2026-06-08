package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hereliesaz.logkitty.R

/**
 * A simple RGB color picker dialog.
 * Used in [SettingsScreen] to allow users to customize the overlay background color.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableStateOf(initialColor.red) }
    var green by remember { mutableStateOf(initialColor.green) }
    var blue by remember { mutableStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Preview Box
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // RGB Sliders
                ColorSlider(value = red, onValueChange = { red = it }, label = stringResource(R.string.color_picker_channel_red), color = Color.Red)
                ColorSlider(value = green, onValueChange = { green = it }, label = stringResource(R.string.color_picker_channel_green), color = Color.Green)
                ColorSlider(value = blue, onValueChange = { blue = it }, label = stringResource(R.string.color_picker_channel_blue), color = Color.Blue)

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(currentColor) }) {
                        Text(stringResource(R.string.select))
                    }
                }
            }
        }
    }
}

/**
 * Helper composable for a single color channel slider.
 */
@Composable
private fun ColorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(24.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.3f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 255).toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
