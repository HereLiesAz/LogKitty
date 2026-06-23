package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hereliesaz.logkitty.BuildConfig
import com.hereliesaz.logkitty.core.feature.AdsFeature
import com.hereliesaz.logkitty.core.feature.FeatureLoader
import com.hereliesaz.logkitty.core.feature.FeatureModules
import com.hereliesaz.logkitty.feature.FeatureInstallStatus
import com.hereliesaz.logkitty.feature.rememberFeatureInstall

/**
 * Reusable AdMob banner slot.
 *
 * The AdMob SDK and the [com.google.android.gms.ads.AdView] live in the on-demand `:feature:ads`
 * module (so `play-services-ads` and the `AD_ID` permission ship only with it). This composable
 * installs that module in the background on first composition, loads the [AdsFeature] entry point
 * reflectively, and renders the banner once present.
 *
 * It occupies **no layout space until the ad is ready**, so callers can drop it at the bottom of a
 * layout (Settings, the log sheet) without reserving an empty gap when ads aren't available yet.
 *
 * The unit ID comes from [BuildConfig.ADMOB_BANNER_UNIT_ID]: Google's test unit for debug, the real
 * unit for release when configured in local.properties/env. The app ID lives in AndroidManifest;
 * SDK init happens here via [AdsFeature.initialize].
 *
 * Known limitation — no UMP (User Messaging Platform) consent flow yet. A compliant GDPR/UMP gate
 * can't live here as-is: `UserMessagingPlatform.loadAndShowConsentFormIfRequired` needs an *Activity*,
 * but this banner is also hosted by the overlay foreground service, which has none. Adding it
 * properly means a new `user-messaging-platform` dependency in `:feature:ads`, an Activity-only
 * consent entry point on [AdsFeature], and gating ad init on the returned consent state. Tracked as a
 * deliberate follow-up rather than shipped half-implemented (a wrong consent gate is a policy risk).
 *
 * @param showTopDivider draws a hairline divider above the banner — but only once the banner is
 *   actually rendered, so no stray line is left behind when ads aren't available.
 */
@Composable
fun AdBannerSlot(modifier: Modifier = Modifier, showTopDivider: Boolean = false) {
    val context = LocalContext.current
    val handle = rememberFeatureInstall(FeatureModules.ADS)
    LaunchedEffect(Unit) { handle.install() }
    if (handle.status is FeatureInstallStatus.Installed) {
        val ads = remember { FeatureLoader.load<AdsFeature>(FeatureModules.ADS_IMPL, context) }
        if (ads != null) {
            LaunchedEffect(ads) { ads.initialize(context.applicationContext) }
            if (showTopDivider) {
                Column(modifier = modifier) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    ads.BannerAd(BuildConfig.ADMOB_BANNER_UNIT_ID, Modifier.fillMaxWidth())
                }
            } else {
                ads.BannerAd(BuildConfig.ADMOB_BANNER_UNIT_ID, modifier)
            }
        }
    }
}
