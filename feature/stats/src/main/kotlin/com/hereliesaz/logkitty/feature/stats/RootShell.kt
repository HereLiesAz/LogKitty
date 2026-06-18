package com.hereliesaz.logkitty.feature.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs short, one-shot shell scripts and returns their combined output.
 *
 * Mirrors the privilege model [LogcatReader] already uses: when [useRoot] is true the script is run
 * through `su -c`, capturing data from any process on the device; otherwise it runs in the app's own
 * unprivileged shell, where most `/proc/<other-pid>` reads and `dumpsys` calls are denied. Callers
 * are expected to treat a `null` result (or empty sections) as "unavailable" and degrade gracefully.
 */
object RootShell {

    /**
     * Executes [script] (a `sh` snippet, may span multiple lines / use globs and pipes) and returns
     * its combined stdout+stderr, or `null` if the process could not be started or timed out before
     * producing output.
     */
    suspend fun run(script: String, useRoot: Boolean, timeoutMs: Long = 4000): String? =
        withContext(Dispatchers.IO) {
            val cmd = if (useRoot) listOf("su", "-c", script) else listOf("sh", "-c", script)
            var process: Process? = null
            try {
                val started = ProcessBuilder(cmd).redirectErrorStream(true).start()
                process = started
                // readText() blocks until the stream hits EOF, which only happens when the process
                // exits. If `su` hangs (e.g. waiting on a root-permission prompt) that would block
                // forever, so a watchdog force-kills the process after the timeout to unblock the read.
                val watchdog = launch {
                    delay(timeoutMs)
                    started.destroyForcibly()
                }
                val output = BufferedReader(InputStreamReader(started.inputStream)).use { it.readText() }
                watchdog.cancel()
                output.ifBlank { null }
            } catch (e: Exception) {
                null
            } finally {
                process?.destroyForcibly()
            }
        }

    /** True if a root shell is reachable (used to decide between full and best-effort collection). */
    suspend fun isRootAvailable(): Boolean =
        run("id -u", useRoot = true, timeoutMs = 3000)?.trim() == "0"
}
