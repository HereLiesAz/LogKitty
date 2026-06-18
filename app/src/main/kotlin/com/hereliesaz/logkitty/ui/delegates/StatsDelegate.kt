package com.hereliesaz.logkitty.ui.delegates

import android.content.Context
import com.hereliesaz.logkitty.model.AppStats
import com.hereliesaz.logkitty.utils.AppStatsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the lifecycle of the developer-stats polling loop.
 *
 * The UI declares which app it currently wants stats for via [setTarget]; the delegate then polls
 * [AppStatsCollector] on a fast cadence for the live metrics and a slower cadence for the heavy
 * power/scheduling stats, publishing merged snapshots on [stats]. Polling only runs while a target
 * is set, so it costs nothing when the Stats view is closed.
 */
class StatsDelegate(
    private val scope: CoroutineScope,
    context: Context,
) {
    private val appContext = context.applicationContext

    private val _stats = MutableStateFlow<AppStats?>(null)
    val stats: StateFlow<AppStats?> = _stats.asStateFlow()

    private var pollJob: Job? = null
    private var currentTarget: String? = null

    /**
     * Begins (or redirects) polling for [pkg]. Passing the same package again is a no-op so the loop
     * isn't restarted on every recomposition. Passing `null` stops polling and clears the snapshot.
     */
    fun setTarget(pkg: String?, label: String, useRoot: Boolean) {
        val key = pkg?.let { "$it|$useRoot" }
        if (key == currentTarget) return
        currentTarget = key
        pollJob?.cancel()
        _stats.value = null
        if (pkg == null) return

        pollJob = scope.launch(Dispatchers.IO) {
            val collector = AppStatsCollector(appContext)
            var cachedPower = collector.collectPower(pkg, useRoot)
            var iteration = 0
            while (isActive) {
                val snapshot = collector.collect(pkg, label, useRoot)
                if (iteration % POWER_REFRESH_EVERY == 0 && iteration != 0) {
                    cachedPower = collector.collectPower(pkg, useRoot)
                }
                _stats.value = snapshot.copy(power = cachedPower)
                iteration++
                delay(FAST_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        currentTarget = null
        _stats.value = null
    }

    companion object {
        private const val FAST_INTERVAL_MS = 2000L
        // Power/scheduling stats refresh every Nth fast poll (here ~10s).
        private const val POWER_REFRESH_EVERY = 5
    }
}
