package com.hereliesaz.logkitty.feature.github

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.core.feature.GitHubFeature

/**
 * Entry point the base app loads reflectively (see `FeatureModules.GITHUB_IMPL`) once this module is
 * installed. Fully self-contained: it will own the GitHub REST client, the ANSI/annotation log
 * parser, and the runs → jobs → job-log drill-down panel.
 *
 * Phase 0: a placeholder confirming the dynamic-feature graph builds and the slot wires up. The real
 * panel (runs list, job log, live polling, coloring) lands in later phases.
 */
class GitHubFeatureImpl : GitHubFeature {

    @Composable
    override fun GitHubPanel(
        owner: String,
        repo: String,
        tokenProvider: () -> String?,
        fontFamily: FontFamily?,
        fontSize: Int,
        onWatchRun: (owner: String, repo: String, runId: Long, runName: String) -> Unit,
        onConfigure: () -> Unit,
        modifier: Modifier,
    ) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.github_coming_soon),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = fontSize.sp,
                fontFamily = fontFamily,
            )
        }
    }
}
