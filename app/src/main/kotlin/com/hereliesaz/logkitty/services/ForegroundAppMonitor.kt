package com.hereliesaz.logkitty.services

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hereliesaz.logkitty.core.AccessibilityActions
import com.hereliesaz.logkitty.core.shell.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Detects the foreground app for Context Mode **without an accessibility service**.
 *
 * - **Non-root**: polls [UsageStatsManager] for the most recent foreground event (needs the
 *   "Usage access" special-access grant — see Settings).
 * - **Root**: polls `dumpsys activity activities` for the resumed activity (no permission needed),
 *   so rooted users keep Context Mode without granting usage access.
 *
 * Each change is announced via the [AccessibilityActions.ACTION_FOREGROUND_APP_CHANGED] broadcast —
 * the same signal the ViewModel and overlay already consume — so nothing downstream changed.
 */
class ForegroundAppMonitor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Polls until the coroutine is cancelled, broadcasting each foreground-app change. Runs entirely
     * on [Dispatchers.IO]: every step here is blocking (UsageStatsManager IPC, `su` process exec,
     * PackageManager IPC), so it must stay off the main thread to avoid ANRs.
     */
    suspend fun observe(useRoot: Boolean) = withContext(Dispatchers.IO) {
        resolveLauncherOnce()
        var last: String? = null
        while (currentCoroutineContext().isActive) {
            val pkg = if (useRoot) rootForeground() else usageForeground()
            if (!pkg.isNullOrBlank() && pkg != last) {
                last = pkg
                appContext.sendBroadcast(
                    Intent(AccessibilityActions.ACTION_FOREGROUND_APP_CHANGED).apply {
                        putExtra(AccessibilityActions.EXTRA_PACKAGE_NAME, pkg)
                        setPackage(appContext.packageName)
                    }
                )
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Resolve the launcher once (HOME resolveActivity needs no package-visibility permission). */
    private fun resolveLauncherOnce() {
        if (AccessibilityActions.resolvedLauncherPackage != null) return
        AccessibilityActions.resolvedLauncherPackage = runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            appContext.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
    }

    private fun usageForeground(): String? {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        return try {
            val events = usm.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var pkg: String? = null
            var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp >= lastTime) {
                    lastTime = event.timeStamp
                    pkg = event.packageName
                }
            }
            pkg
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun rootForeground(): String? {
        // RootShell enforces the 3s budget with a watchdog that force-kills a hung `su` before the
        // blocking read, so a stalled root prompt can't drag the poll past its interval.
        val output = RootShell.run(
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'",
            useRoot = true,
            timeoutMs = 3000,
        ) ?: return null
        // Lines look like: topResumedActivity=ActivityRecord{hash u0 com.example/.MainActivity t123}
        return RESUMED_PACKAGE.find(output)?.groupValues?.get(1)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1500L
        const val LOOKBACK_MS = 10_000L
        // Captures the package in `<pkg>/<activity>` from a dumpsys resumed-activity line.
        val RESUMED_PACKAGE = Regex("""\s([a-zA-Z][a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]+""")
    }
}
