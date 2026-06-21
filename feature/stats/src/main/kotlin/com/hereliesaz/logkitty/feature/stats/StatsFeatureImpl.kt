package com.hereliesaz.logkitty.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.hereliesaz.logkitty.core.feature.StatsFeature
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // often, and keep a rolling window of recent snapshots so the UI can draw trend sparklines.
        // produceState ties the loop to composition: it restarts when the target/root flag changes
        // and is cancelled when the Stats view leaves the screen.
        val history by produceState<List<AppStats>>(initialValue = emptyList(), packageName, useRoot, label) {
            // produceState runs on the composition's (main) dispatcher; the collector does blocking
            // binder calls (NetworkStatsManager) and root shell I/O, so run the loop off the main
            // thread. delay() is the cancellation point that breaks the loop.
            withContext(Dispatchers.IO) {
                val collector = AppStatsCollector(appContext)
                val buffer = ArrayDeque<AppStats>()
                // collectSlow fires several heavy dumpsys/logcat shells under an 8s timeout. Running
                // it inline would stall the 2s fast loop (live CPU/mem/GPU) and block the first frame
                // until it finished, so run it in its own coroutine and just read the latest result.
                // AtomicReference gives a clean happens-before across the two IO-pool threads.
                val cachedSlow = AtomicReference<SlowStats?>(null)
                val slowJob = launch {
                    while (true) {
                        cachedSlow.set(collector.collectSlow(packageName, useRoot))
                        delay(FAST_INTERVAL_MS * SLOW_REFRESH_EVERY)
                    }
                }
                try {
                    while (true) {
                        val snapshot = collector.collect(packageName, label, useRoot)
                        val slow = cachedSlow.get()
                        buffer.addLast(
                            snapshot.copy(
                                power = slow?.power,
                                sensors = slow?.sensors,
                                crashes = slow?.crashes,
                            ),
                        )
                        while (buffer.size > HISTORY_MAX) buffer.removeFirst()
                        value = buffer.toList()
                        delay(FAST_INTERVAL_MS)
                    }
                } finally {
                    slowJob.cancel()
                }
            }
        }

        StatsView(history = history, fontFamily = fontFamily, fontSize = fontSize, modifier = modifier)
    }

    private companion object {
        const val FAST_INTERVAL_MS = 2000L
        const val SLOW_REFRESH_EVERY = 5
        const val HISTORY_MAX = 45 // ~90s of trend at the 2s cadence
    }
}
