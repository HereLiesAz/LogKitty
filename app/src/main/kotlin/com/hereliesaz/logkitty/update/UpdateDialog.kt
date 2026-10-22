package com.hereliesaz.logkitty.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.logkitty.R

/** "Update available" prompt naming where the update lives; [onOpen] goes there. */
@Composable
fun UpdateDialog(update: UpdateChecker.Result.Available, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val where = stringResource(if (update.source == UpdateChecker.Source.PLAY) R.string.update_source_play else R.string.update_source_github)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                if (update.version != null) stringResource(R.string.update_available_version, update.version, where)
                else stringResource(R.string.update_available_no_version, where)
            )
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.update_open, where)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}
