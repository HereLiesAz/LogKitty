package com.hereliesaz.logkitty.feature.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hereliesaz.logkitty.core.feature.AdsFeature

/**
 * Entry point the base loads reflectively once this module is installed. Holds the AdMob SDK, the
 * banner view, and the UMP consent flow, so `play-services-ads`, the UMP library, and the AD_ID
 * permission ship only with this module.
 */
class AdsFeatureImpl : AdsFeature {

    @Volatile
    private var initialized = false

    /** Initializes the Mobile Ads SDK once. Call only after consent permits requesting ads. */
    override fun initialize(appContext: Context) {
        if (initialized) return
        initialized = true
        runCatching { MobileAds.initialize(appContext) }
    }

    override fun canRequestAds(context: Context): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }.getOrDefault(false)

    override fun gatherConsent(activity: Activity, onComplete: () -> Unit) {
        // requestConsentInfoUpdate is async; the Activity could go away before it returns, and
        // showing a form on a finishing/destroyed Activity would crash (bad window token).
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Info updated: show the consent form if one is required/available, but only while
                // the Activity is still valid. The dismissal callback fires whether the form was
                // shown, dismissed, or wasn't needed.
                if (activity.isFinishing || activity.isDestroyed) {
                    onComplete()
                } else {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { onComplete() }
                }
            },
            {
                // Update failed (e.g. offline). Proceed — canRequestAds() may still be true from a
                // prior session, and BannerAd re-checks it before loading anything.
                onComplete()
            },
        )
    }

    @Composable
    override fun BannerAd(adUnitId: String, modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val adView = remember(context, adUnitId, lifecycleOwner) {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }

        // Pause/resume/destroy with the host lifecycle (AdMob policy + battery/memory). Keyed on the
        // adView too so a recreated view's predecessor is destroyed via onDispose.
        DisposableEffect(adView, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_DESTROY -> adView.destroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        // Fixed banner height avoids a layout shift (and accidental taps) when the ad loads.
        // key(adView) recreates the AndroidView when the remembered adView instance changes
        // (AndroidView's factory only runs once otherwise, keeping a destroyed view).
        key(adView) {
            AndroidView(
                modifier = modifier.fillMaxWidth().height(50.dp),
                factory = { adView },
            )
        }
    }
}
