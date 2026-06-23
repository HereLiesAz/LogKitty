package com.hereliesaz.logkitty.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * UMP consent: ads are gated on [AdsFeature.canRequestAds]. When this slot is hosted by an Activity
 * (e.g. the launcher), it runs [AdsFeature.gatherConsent] to request consent info and show the form
 * if required. When hosted by the overlay service (no Activity), it relies on the consent state UMP
 * persisted during an earlier Activity session. The banner — and thus any ad request — appears only
 * once consent permits it.
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
            val activity = remember(context) { context.findActivity() }
            // Seed from UMP's persisted state (true on the overlay path after a prior Activity
            // session, or when consent isn't required); the Activity path may flip it after the form.
            var consentReady by remember { mutableStateOf(ads.canRequestAds(context)) }
            LaunchedEffect(ads, activity) {
                if (!consentReady && activity != null) {
                    ads.gatherConsent(activity) { consentReady = ads.canRequestAds(activity) }
                }
            }
            if (consentReady) {
                // Initialize the SDK only once consent allows requesting ads.
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
}

/** Unwraps a [Context] to its hosting [Activity], or `null` (e.g. the overlay service's context). */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
