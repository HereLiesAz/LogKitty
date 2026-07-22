package com.hereliesaz.logkitty.core.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * Contracts the base `:app` module calls into, implemented inside dynamic feature modules.
 *
 * The base cannot reference a dynamic module's classes at compile time (the dependency points the
 * other way), so each optional feature exposes a small interface here in `:core`. The base loads the
 * concrete implementation reflectively via [FeatureLoader] once the module has been installed, and
 * talks to it only through these interfaces. Method signatures therefore use only types visible to
 * every module (framework + Compose types).
 */

/** Developer-stats feature (`:feature:stats`). Renders the live per-app metrics dashboard. */
interface StatsFeature {
    @Composable
    fun StatsContent(
        packageName: String,
        label: String,
        useRoot: Boolean,
        fontFamily: FontFamily?,
        fontSize: Int,
        modifier: Modifier,
    )
}



/**
 * GitHub Actions feature (`:feature:github`). Renders the workflow-runs → jobs → job-log drill-down;
 * owns the GitHub REST networking and the ANSI/annotation log parser.
 *
 * Signatures use only types visible to every module (framework + Compose + function types). The PAT
 * is read lazily through [tokenProvider] (never passed as a snapshot String) so it isn't captured in
 * a recomposition-stable param and edits in Settings are picked up on the next read.
 */
interface GitHubFeature {
    @Composable
    fun GitHubPanel(
        owner: String,
        repo: String,
        tokenProvider: () -> String?,
        fontFamily: FontFamily?,
        fontSize: Int,
        onWatchRun: (owner: String, repo: String, runId: Long, runName: String) -> Unit,
        onConfigure: () -> Unit,
        modifier: Modifier,
    )
}
