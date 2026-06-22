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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.core.feature.FeatureLoader
import com.hereliesaz.logkitty.core.feature.FeatureModules
import com.hereliesaz.logkitty.core.feature.GitHubFeature
import com.hereliesaz.logkitty.feature.FeatureInstallStatus
import com.hereliesaz.logkitty.feature.rememberFeatureInstall

/**
 * Hosts the optional GitHub Actions feature inside the bottom sheet (and, later, the full screen).
 *
 * The GitHub code ships in the on-demand `:feature:github` module, so this base composable can't
 * reference it directly. It checks install state via Play Feature Delivery: when the module is
 * present it loads the [GitHubFeature] entry point reflectively and renders its panel; otherwise it
 * shows a lightweight prompt to download the add-on.
 *
 * Phase 0: repo config and the secure token store don't exist yet, so the panel is invoked with an
 * empty repo and no-op callbacks — enough to verify the module graph and the slot wiring. Phase 1+
 * passes the real owner/repo + `tokenProvider` from the base credential store, and wires
 * `onWatchRun` / `onConfigure`.
 */
@Composable
fun GitHubFeatureSlot(
    fontFamily: FontFamily?,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val handle = rememberFeatureInstall(FeatureModules.GITHUB)
    when (val status = handle.status) {
        is FeatureInstallStatus.Installed -> {
            val context = LocalContext.current
            var retryTrigger by remember { mutableStateOf(0) }
            val feature = remember(retryTrigger) {
                FeatureLoader.load<GitHubFeature>(FeatureModules.GITHUB_IMPL, context)
            }
            if (feature != null) {
                feature.GitHubPanel(
                    owner = "",
                    repo = "",
                    tokenProvider = { null },
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    onWatchRun = { _, _, _, _ -> },
                    onConfigure = {},
                    modifier = modifier,
                )
            } else {
                InstallPrompt(
                    modifier, fontSize,
                    message = stringResource(R.string.github_finishing_install),
                    onInstall = { retryTrigger++ },
                )
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
                Text(stringResource(R.string.github_installing), color = Color.White.copy(alpha = 0.8f), fontSize = fontSize.sp)
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
            message ?: stringResource(R.string.github_install_prompt),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Button(onClick = onInstall) { Text(stringResource(R.string.github_get)) }
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
