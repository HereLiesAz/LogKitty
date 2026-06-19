package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.core.feature.AppPickerFeature
import com.hereliesaz.logkitty.core.feature.FeatureLoader
import com.hereliesaz.logkitty.core.feature.FeatureModules
import com.hereliesaz.logkitty.feature.FeatureInstallStatus
import com.hereliesaz.logkitty.feature.rememberFeatureInstall

/**
 * Hosts the app picker, which lives in the on-demand `:feature:appmonitor` module (alongside the
 * accessibility service). When the user opens it, the module is fetched in the background; once
 * present the picker dialog is loaded reflectively and shown. Until then a small "preparing" dialog
 * stands in so the action still feels responsive.
 */
@Composable
fun AppPickerSlot(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val handle = rememberFeatureInstall(FeatureModules.APPMONITOR)
    LaunchedEffect(Unit) { handle.install() }

    when (val status = handle.status) {
        is FeatureInstallStatus.Installed -> {
            var retry by remember { mutableStateOf(0) }
            val picker = remember(retry) {
                FeatureLoader.load<AppPickerFeature>(FeatureModules.APP_PICKER_IMPL, context)
            }
            if (picker != null) {
                picker.AppPicker(onAppSelected = onAppSelected, onDismiss = onDismiss)
            } else {
                PreparingDialog(stringResource(R.string.app_picker_finishing), onDismiss, onRetry = { retry++ })
            }
        }
        is FeatureInstallStatus.Failed ->
            PreparingDialog(status.message, onDismiss, onRetry = { handle.install() })
        else ->
            PreparingDialog(stringResource(R.string.app_picker_preparing), onDismiss, onRetry = null)
    }
}

@Composable
private fun PreparingDialog(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)?) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(message, color = MaterialTheme.colorScheme.onSurface)
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.app_picker_retry)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
