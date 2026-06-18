package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.core.feature.FeatureLoader
import com.hereliesaz.logkitty.core.feature.FeatureModules
import com.hereliesaz.logkitty.core.feature.StatsFeature
import com.hereliesaz.logkitty.feature.FeatureInstallStatus
import com.hereliesaz.logkitty.feature.rememberFeatureInstall

/**
 * Hosts the optional Developer Stats feature inside the bottom sheet.
 *
 * The stats code ships in the on-demand `:feature:stats` module, so this base composable can't
 * reference it directly. It checks install state via Play Feature Delivery: when the module is
 * present it loads the [StatsFeature] entry point reflectively and renders it; otherwise it shows a
 * lightweight prompt to download the add-on (which also defers the PACKAGE_USAGE_STATS permission
 * until that point).
 */
@Composable
fun StatsFeatureSlot(
    packageName: String?,
    label: String,
    useRoot: Boolean,
    fontFamily: FontFamily?,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    if (packageName.isNullOrBlank()) {
        Centered(modifier) { Text("Select an app tab to see its stats.", color = Color.Gray, fontSize = fontSize.sp) }
        return
    }

    val handle = rememberFeatureInstall(FeatureModules.STATS)
    when (val status = handle.status) {
        is FeatureInstallStatus.Installed -> {
            val feature = remember(packageName) { FeatureLoader.load<StatsFeature>(FeatureModules.STATS_IMPL) }
            if (feature != null) {
                feature.StatsContent(packageName, label, useRoot, fontFamily, fontSize, modifier)
            } else {
                // Installed per Play but the class isn't on the path yet (rare timing case) — prompt a retry.
                InstallPrompt(modifier, fontSize, message = "Finishing install — tap to load.", onInstall = handle.install)
            }
        }
        is FeatureInstallStatus.NotInstalled ->
            InstallPrompt(modifier, fontSize, onInstall = handle.install)
        is FeatureInstallStatus.Installing ->
            Centered(modifier) {
                if (status.progress >= 0f) {
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.width(180.dp))
                } else {
                    CircularProgressIndicator()
                }
                Text("Installing Developer Stats…", color = Color.White.copy(alpha = 0.8f), fontSize = fontSize.sp)
            }
        is FeatureInstallStatus.Failed ->
            InstallPrompt(modifier, fontSize, message = status.message, onInstall = handle.install)
    }
}

@Composable
private fun InstallPrompt(
    modifier: Modifier,
    fontSize: Int,
    message: String? = null,
    onInstall: () -> Unit,
) {
    Centered(modifier) {
        Text(
            message ?: "Developer Stats is an optional add-on (CPU, memory, GPU, network & power). " +
                "Download it to enable per-app metrics — this is also when the Usage Access permission is requested.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Button(onClick = onInstall) { Text("Get Developer Stats") }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
