package com.hereliesaz.logkitty.feature.github

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.hereliesaz.logkitty.core.feature.GitHubFeature

/**
 * Entry point the base app loads reflectively (see `FeatureModules.GITHUB_IMPL`) once this module is
 * installed. Fully self-contained: it owns the GitHub REST client ([GitHubApi]), the models, and the
 * runs → jobs → job-log drill-down ([GitHubPanel]). The base passes in repo coordinates, a lazy PAT
 * provider, and the watch/configure callbacks.
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
        GitHubActionsPanel(
            owner = owner,
            repo = repo,
            tokenProvider = tokenProvider,
            fontFamily = fontFamily,
            fontSize = fontSize,
            onWatchRun = onWatchRun,
            onConfigure = onConfigure,
            modifier = modifier,
        )
    }
}
