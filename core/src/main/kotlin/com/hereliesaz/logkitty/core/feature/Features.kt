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
 * Ads feature (`:feature:ads`). Renders the AdMob banner; owns play-services-ads, the AD_ID
 * permission, and the UMP (User Messaging Platform) consent flow.
 *
 * Consent must be gathered before ads are requested. The UMP form needs an [android.app.Activity],
 * but the banner is also hosted by the overlay foreground service (no Activity) — so the contract is
 * split: [gatherConsent] runs the form from an Activity host (e.g. the launcher Activity), while the
 * service-hosted banner relies on the consent state UMP persists across the process, queried via
 * [canRequestAds]. [BannerAd] only loads an ad once consent allows it.
 */
interface AdsFeature {
    fun initialize(appContext: android.content.Context)

    /**
     * Runs the UMP consent flow (requests the latest consent info and shows the form if required),
     * then invokes [onComplete] regardless of outcome. Must be called with an [android.app.Activity]
     * because the form needs one. Idempotent and safe to call repeatedly.
     */
    fun gatherConsent(activity: android.app.Activity, onComplete: () -> Unit)

    /** Whether ads may be requested yet — consent obtained or not required. Reads UMP's persisted state. */
    fun canRequestAds(context: android.content.Context): Boolean

    /**
     * Whether a "privacy options" entry must be offered so the user can change consent later — i.e.
     * UMP reports `privacyOptionsRequirementStatus == REQUIRED` (consent applies to this user). Used
     * to decide whether to show the Settings entry at all.
     */
    fun isPrivacyOptionsRequired(context: android.content.Context): Boolean

    /**
     * Shows the UMP privacy-options form so the user can review/change consent, then invokes
     * [onComplete]. Needs an [android.app.Activity]; safe to call when one is available (Settings).
     */
    fun showPrivacyOptions(activity: android.app.Activity, onComplete: () -> Unit)

    @Composable
    fun BannerAd(adUnitId: String, modifier: Modifier)
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
