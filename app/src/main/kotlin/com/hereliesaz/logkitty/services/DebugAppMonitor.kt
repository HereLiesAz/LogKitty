package com.hereliesaz.logkitty.services

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.hereliesaz.logkitty.core.shell.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Watches the app chosen in **Developer options → Select debug app** (`Settings.Global.debug_app`)
 * so LogKitty can follow it automatically.
 *
 * - **Non-root**: reads the setting directly. `debug_app` is a hidden key, so Android 12+ may refuse
 *   it to apps targeting S+. Then, if `DUMP` has been granted (ADB, Shizuku or root self-grant), it
 *   falls back to `dumpsys activity processes`, which prints `mDebugApp=`.
 * - **Root**: `settings get global debug_app`, which always works.
 *
 * Polls rather than registering a ContentObserver: an observer on a hidden key is subject to the
 * same read restriction, and the root path has no observer at all.
 */
class DebugAppMonitor(context: Context) {

    private val appContext = context.applicationContext

    /** Calls [onChange] with each new non-blank debug app until the coroutine is cancelled. */
    suspend fun observe(useRoot: Boolean, onChange: (String) -> Unit) = withContext(Dispatchers.IO) {
        var last: String? = null
        while (currentCoroutineContext().isActive) {
            val pkg = (if (useRoot) rootDebugApp() else directDebugApp())
                ?.trim()?.takeUnless { it.isEmpty() || it == "null" }
            if (pkg != null && pkg != last) {
                last = pkg
                withContext(Dispatchers.Main) { onChange(pkg) }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun directDebugApp(): String? =
        runCatching { Settings.Global.getString(appContext.contentResolver, KEY_DEBUG_APP) }.getOrNull()
            ?: dumpsysDebugApp()

    private suspend fun dumpsysDebugApp(): String? {
        if (appContext.checkSelfPermission(android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) return null
        val output = RootShell.run("dumpsys activity processes | grep mDebugApp", useRoot = false, timeoutMs = 3000)
            ?: return null
        return DEBUG_APP_LINE.find(output)?.groupValues?.get(1)
    }

    private suspend fun rootDebugApp(): String? =
        RootShell.run("settings get global $KEY_DEBUG_APP", useRoot = true, timeoutMs = 3000)
            ?.lineSequence()?.firstOrNull()

    private companion object {
        const val POLL_INTERVAL_MS = 2000L
        const val KEY_DEBUG_APP = "debug_app"
        // Line looks like: `  mDebugApp=com.example mDebugTransient=false mWaitForDebugger=false`
        val DEBUG_APP_LINE = Regex("""mDebugApp=([a-zA-Z][a-zA-Z0-9_.]*)""")
    }
}
