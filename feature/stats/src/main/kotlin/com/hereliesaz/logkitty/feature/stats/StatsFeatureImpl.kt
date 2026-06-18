package com.hereliesaz.logkitty.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.hereliesaz.logkitty.core.feature.StatsFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Entry point the base app loads reflectively (see `FeatureModules.STATS_IMPL`) once this module is
 * installed. It is fully self-contained: it owns the [AppStatsCollector] and the polling loop, so
 * the base never has to compile against any stats type.
 */
class StatsFeatureImpl : StatsFeature {

    @Composable
    override fun StatsContent(
        packageName: String,
        label: String,
        useRoot: Boolean,
        fontFamily: FontFamily?,
        fontSize: Int,
        modifier: Modifier,
    ) {
        val appContext = LocalContext.current.applicationContext

        // Poll on a fast cadence for live metrics, refreshing the heavy power/scheduling stats less
        // often. produceState ties the loop to composition: it restarts when the target/root flag
        // changes and is cancelled when the Stats view leaves the screen.
        val stats by produceState<AppStats?>(initialValue = null, packageName, useRoot, label) {
            // produceState runs on the composition's (main) dispatcher; the collector does blocking
            // binder calls (NetworkStatsManager) and root shell I/O, so run the loop off the main
            // thread. produceState cancels this coroutine when the keys change or it leaves
            // composition; delay() is the cancellation point that breaks the loop.
            withContext(Dispatchers.IO) {
                val collector = AppStatsCollector(appContext)
                var cachedPower = collector.collectPower(packageName, useRoot)
                var iteration = 0
                while (true) {
                    val snapshot = collector.collect(packageName, label, useRoot)
                    if (iteration % POWER_REFRESH_EVERY == 0 && iteration != 0) {
                        cachedPower = collector.collectPower(packageName, useRoot)
                    }
                    value = snapshot.copy(power = cachedPower)
                    iteration++
                    delay(FAST_INTERVAL_MS)
                }
            }
        }

        StatsView(stats = stats, fontFamily = fontFamily, fontSize = fontSize, modifier = modifier)
    }

    private companion object {
        const val FAST_INTERVAL_MS = 2000L
        const val POWER_REFRESH_EVERY = 5
    }
}
