package com.hereliesaz.logkitty.privilege

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.core.shell.RootShell

/**
 * Turns root or Shizuku into permanent grants, so LogKitty runs without either afterwards:
 *
 * - `DUMP` — `dumpsys` for non-root code paths.
 * - Usage access (`GET_USAGE_STATS` app-op) — Context Mode and per-app stats.
 * - `READ_LOGS` — logcat for every app without `su`.
 *
 * READ_LOGS carries the `log` gid, which is only attached when a process is forked, and granting it
 * may itself make the system kill LogKitty. So when it is still missing, the privileged shell runs
 * the whole script **detached** (`setsid`, survives our death) and finishes with
 * `am force-stop` + `am start`: the shell user relaunches the app into a fresh process, with no
 * background-activity-start rules in the way. Commands are `;`-separated so one refusal (e.g. an OEM
 * blocking an app-op) can't stop the rest; success is judged by re-checking the grants.
 */
object PrivilegeGranter {

    enum class Backend { ROOT, SHIZUKU }

    /** [restarting] = a relaunch was scheduled; the current process is about to be replaced. */
    data class Result(val readLogs: Boolean, val dump: Boolean, val usage: Boolean, val restarting: Boolean)

    private const val TAG = "PrivilegeGranter"

    private const val PREFS = "privilege"
    private const val KEY_AUTO_RESTART_TRIED = "auto_restart_tried"

    /**
     * [userInitiated] = a button press. Automatic grants (root being enabled at launch) relaunch the
     * app at most once per install, so a device that refuses READ_LOGS can't restart-loop.
     */
    suspend fun grant(context: Context, backend: Backend, userInitiated: Boolean = true): Result {
        val app = context.applicationContext
        val pkg = app.packageName
        val base = "appops set $pkg GET_USAGE_STATS allow; pm grant $pkg ${Manifest.permission.DUMP}"
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val restarting = !hasReadLogs(app) && (userInitiated || !prefs.getBoolean(KEY_AUTO_RESTART_TRIED, false))
        if (restarting && !userInitiated) prefs.edit().putBoolean(KEY_AUTO_RESTART_TRIED, true).commit()
        val script = if (restarting) {
            val main = ComponentName(app, MainActivity::class.java).flattenToShortString()
            val inner = "$base; pm grant $pkg ${Manifest.permission.READ_LOGS}; sleep 1; am force-stop $pkg; am start -n $main"
            "setsid sh -c '$inner' </dev/null >/dev/null 2>&1 & echo started"
        } else {
            "$base; echo done"
        }
        val output = when (backend) {
            Backend.ROOT -> RootShell.run(script, useRoot = true, timeoutMs = 10_000)
            Backend.SHIZUKU -> if (ShizukuShell.requestPermission()) ShizukuShell.run(script, timeoutMs = 10_000) else null
        }
        if (output == null) Log.w(TAG, "Grant via $backend did not run")
        return Result(
            readLogs = hasReadLogs(app),
            dump = app.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED,
            usage = hasUsageAccess(app),
            restarting = restarting && output != null,
        )
    }

    fun hasReadLogs(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    private fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}
