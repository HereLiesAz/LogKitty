package com.hereliesaz.logkitty.core.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs short, one-shot shell scripts and returns their combined output.
 *
 * Mirrors the privilege model the logcat reader already uses: when [useRoot] is true the script is
 * run through `su -c`, capturing data from any process on the device; otherwise it runs in the app's
 * own unprivileged shell, where most `/proc/<other-pid>` reads and `dumpsys` calls are denied.
 * Callers are expected to treat a `null` result (or empty sections) as "unavailable" and degrade
 * gracefully.
 *
 * Lives in `:core` so the base app (root probe, foreground-app monitor) and the on-demand stats
 * feature can share one bounded, watchdog-protected process runner.
 */
object RootShell {

    /**
     * Executes [script] (a `sh` snippet, may span multiple lines / use globs and pipes) and returns
     * its combined stdout+stderr, or `null` if the process could not be started or timed out before
     * producing output.
     */
    suspend fun run(script: String, useRoot: Boolean, timeoutMs: Long = 4000, maxChars: Int = MAX_OUTPUT_CHARS): String? =
        withContext(Dispatchers.IO) {
            val cmd = if (useRoot) listOf("su", "-c", script) else listOf("sh", "-c", script)
            var process: Process? = null
            try {
                val started = ProcessBuilder(cmd).redirectErrorStream(true).start()
                process = started
                // Reading blocks until EOF, which only happens when the process exits. If `su` hangs
                // (e.g. waiting on a root-permission prompt) that would block forever, so a watchdog
                // force-kills the process after the timeout to unblock the read.
                val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
                val watchdog = launch {
                    delay(timeoutMs)
                    timedOut.set(true)
                    started.destroyForcibly()
                }
                // Bounded read: verbose dumpsys output must not balloon memory. Past the cap the rest
                // is drained and discarded so the process can still exit.
                val out = StringBuilder()
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        val room = maxChars - out.length
                        if (room > 0) out.append(buf, 0, minOf(n, room))
                    }
                }
                watchdog.cancel()
                // A killed run is incomplete; don't hand partial output to parsers as if it were whole.
                if (timedOut.get()) null else out.toString().ifBlank { null }
            } catch (e: Exception) {
                null
            } finally {
                process?.destroyForcibly()
            }
        }

    /** True if a root shell is reachable (used to decide between full and best-effort collection). */
    suspend fun isRootAvailable(): Boolean =
        run("id -u", useRoot = true, timeoutMs = 3000)?.trim() == "0"

    private const val MAX_OUTPUT_CHARS = 2_000_000
}
