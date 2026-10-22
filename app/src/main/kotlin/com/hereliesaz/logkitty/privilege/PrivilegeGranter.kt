package com.hereliesaz.logkitty.privilege

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.hereliesaz.logkitty.core.shell.RootShell

/**
 * Turns root or Shizuku into permanent grants, so LogKitty runs without either afterwards:
 *
 * - `READ_LOGS` — logcat for every app without `su` (takes effect after a process restart, because
 *   the `log` group is only attached when the process is forked).
 * - `DUMP` — `dumpsys` for non-root code paths.
 * - Usage access (`GET_USAGE_STATS` app-op) — Context Mode and per-app stats.
 *
 * Every command is separated by `;` so one refusal (e.g. an OEM blocking an app-op) can't stop the
 * rest. Success is judged by re-checking the grants, never by parsing `pm` output.
 */
object PrivilegeGranter {

    enum class Backend { ROOT, SHIZUKU }

    data class Result(val readLogs: Boolean, val dump: Boolean, val usage: Boolean, val needsRestart: Boolean) {
        val anyMissing get() = !readLogs || !dump || !usage
    }

    private fun script(pkg: String) = listOf(
        "pm grant $pkg ${Manifest.permission.READ_LOGS}",
        "pm grant $pkg ${Manifest.permission.DUMP}",
        "appops set $pkg GET_USAGE_STATS allow",
    ).joinToString("; ")

    suspend fun grant(context: Context, backend: Backend): Result {
        val app = context.applicationContext
        val hadReadLogs = hasReadLogs(app)
        val script = script(app.packageName)
        when (backend) {
            Backend.ROOT -> RootShell.run(script, useRoot = true, timeoutMs = 10_000)
            Backend.SHIZUKU -> if (ShizukuShell.requestPermission()) ShizukuShell.run(script, timeoutMs = 10_000)
        }
        val readLogs = hasReadLogs(app)
        return Result(
            readLogs = readLogs,
            dump = app.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED,
            usage = hasUsageAccess(app),
            // Root keeps reading logcat through `su`, so only the non-root path needs the new gid now.
            needsRestart = backend == Backend.SHIZUKU && readLogs && !hadReadLogs,
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
