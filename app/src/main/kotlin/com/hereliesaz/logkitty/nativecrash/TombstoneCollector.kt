package com.hereliesaz.logkitty.nativecrash

import android.content.Context
import com.hereliesaz.logkitty.core.shell.RootShell
import com.hereliesaz.logkitty.data.SessionLogFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Root only: after a monitored app's native crash, pulls the full tombstone from `/data/tombstones`
 * (registers, memory maps, every thread — far more than logcat's excerpt), symbolicates its
 * backtrace frames with [NativeSymbolStore], and saves it next to the session logs.
 */
object TombstoneCollector {

    // tombstoned writes the file a moment after logcat's excerpt; poll briefly for it.
    private const val FIRST_WAIT_MS = 2000L
    private const val RETRY_WAIT_MS = 2000L
    private const val ATTEMPTS = 3
    private const val MARKER = "@@TOMBSTONE@@"
    private val SAFE_PACKAGE = Regex("""^[A-Za-z0-9_.]+$""")

    /**
     * [crashedAtMs] = when the crash was seen; only tombstones written since then count, so an older
     * crash of the same app is never saved as this one.
     */
    suspend fun collect(context: Context, pkg: String, crashedAtMs: Long, symbols: NativeSymbolStore): File? = withContext(Dispatchers.IO) {
        // The package goes into a root shell script: accept only real package-name characters.
        if (!SAFE_PACKAGE.matches(pkg)) return@withContext null
        val since = crashedAtMs / 1000 - 5 // small allowance for clock rounding
        // Newest text tombstones first (Android 12+ also writes a .pb twin); the header must name
        // exactly this app (`>>> pkg <<<`, or `>>> pkg:process <<<`).
        val script = """
            cd /data/tombstones 2>/dev/null || exit 0
            for f in $(ls -t | grep '^tombstone_' | grep -v '\.pb$' | head -n 5); do
              [ "$(stat -c %Y "${'$'}f")" -ge $since ] || continue
              if grep -qF -e '>>> $pkg <<<' -e '>>> $pkg:' "${'$'}f"; then echo '$MARKER'; cat "${'$'}f"; break; fi
            done
        """.trimIndent()
        delay(FIRST_WAIT_MS)
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_WAIT_MS)
            val output = RootShell.run(script, useRoot = true, timeoutMs = 10_000) ?: return@repeat
            val body = output.substringAfter(MARKER, "").trimStart('\n')
            if (body.isNotBlank()) {
                val lines = body.lines().map { line -> symbolicate(line, symbols) }
                return@withContext SessionLogFileWriter.writeStandalone(context, "${pkg}_tombstone", lines)
            }
        }
        null
    }

    private fun symbolicate(line: String, symbols: NativeSymbolStore): String {
        val frame = NativeFrame.parse(line) ?: return line
        val symbol = symbols.lookup(frame) ?: return line
        return line.substring(0, frame.end) + " [" + symbol + "]" + line.substring(frame.end)
    }
}
