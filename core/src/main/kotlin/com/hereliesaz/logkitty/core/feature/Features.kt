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

/** Ads feature (`:feature:ads`). Renders the AdMob banner; owns play-services-ads + AD_ID. */
interface AdsFeature {
    fun initialize(appContext: android.content.Context)

    @Composable
    fun BannerAd(adUnitId: String, modifier: Modifier)
}
