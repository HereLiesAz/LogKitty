package com.hereliesaz.logkitty.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

/**
 * [LogcatReader] is the core data ingestion engine for LogKitty.
 *
 * It is responsible for spawning and managing the system process that reads logs (via `logcat`).
 * This object is designed to be resilient: it expects the underlying shell process to die or
 * be killed by the system, and it includes logic to automatically restart the stream without
 * crashing the application.
 */
object LogcatReader {

    /** Matches a standard logcat timestamp (`MM-DD HH:MM:SS.mmm`); used to detect real log output. */
    private val TIMESTAMP_PATTERN = Regex("""\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}""")

    /**
     * Starts observing the logcat stream.
     *
     * This function returns a cold [Flow] that emits log lines as strings.
     * The flow runs on [Dispatchers.IO] to prevent blocking the main thread.
     *
     * @param useRoot If true, attempts to run `logcat` via `su` to capture logs from all apps.
     *                If false, runs standard `logcat` (which on modern Android is often restricted
     *                to the app's own logs unless READ_LOGS is granted via ADB).
     */
    fun observe(useRoot: Boolean): Flow<String> = flow {
        // Prefer the uid-annotated format ("-v uid -v time") so per-app tabs can filter reliably by
        // UID. Some devices' logcat reject the `uid` modifier and emit nothing; if the uid attempt
        // exits without ever producing a real (timestamped) log line, permanently downgrade to the
        // plain "-v time" format so logs always appear.
        var useUidFormat = true

        // Endless loop: The "Resurrection Loop".
        // If the process dies (e.g., log buffer cleared, system kills it), we want to restart it
        // automatically so the user doesn't have to toggle the service.
        while (currentCoroutineContext().isActive) {
            val formatArgs = if (useUidFormat) listOf("-v", "uid", "-v", "time") else listOf("-v", "time")
            val cmd = if (useRoot) {
                listOf("su", "-c", "logcat " + formatArgs.joinToString(" "))
            } else {
                listOf("logcat") + formatArgs
            }

            var process: Process? = null
            var sawValidLog = false
            try {
                // Use ProcessBuilder for better control over streams than Runtime.exec()
                val pb = ProcessBuilder(cmd)

                // Redirect stderr to stdout so we catch error messages from logcat itself (e.g. "Permission Denied")
                // and display them in the log stream.
                pb.redirectErrorStream(true)

                process = pb.start()

                // Read from the process's combined output stream.
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    // Read line by line. This blocks until a line is available.
                    var line: String? = reader.readLine()

                    // Inner loop: Stream data while the process is alive.
                    while (currentCoroutineContext().isActive && line != null) {
                        if (!sawValidLog && TIMESTAMP_PATTERN.containsMatchIn(line)) sawValidLog = true
                        emit(line)
                        line = reader.readLine()
                    }
                }
            } catch (e: IOException) {
                // IO Exceptions usually mean the stream broke (EPIPE) or the process crashed.
                emit("Logcat reader warning: ${e.message}. Retrying...")
                // Short delay to avoid CPU spinning if the error is persistent.
                delay(2000)
            } catch (e: Exception) {
                // Catch-all for unexpected errors (SecurityException, etc).
                emit("Logcat reader failed: ${e.message}")
                delay(5000)
            } finally {
                // Ensure the process and any root children are cleaned up before we loop around.
                try {
                    process?.destroyForcibly()
                    if (useRoot) {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f logcat"))
                    }
                } catch (e: Exception) {
                    // Ignore cleanup exceptions
                }
            }

            // The uid format exited without ever emitting a real log line — this device likely
            // rejects `-v uid`. Downgrade to plain `-v time` and retry immediately so logs appear.
            if (useUidFormat && !sawValidLog) {
                useUidFormat = false
                continue
            }

            // If the coroutine is still active but the loop exited, wait a bit before restarting.
            if (currentCoroutineContext().isActive) {
                delay(1000)
            }
        }
    }.flowOn(Dispatchers.IO)
}
