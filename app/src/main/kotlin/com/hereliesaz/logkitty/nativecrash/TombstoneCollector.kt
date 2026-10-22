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

    // tombstoned writes the file a moment after logcat's excerpt; give it time to finish.
    private const val SETTLE_MS = 3000L
    private const val MARKER = "@@TOMBSTONE@@"

    suspend fun collect(context: Context, pkg: String, symbols: NativeSymbolStore): File? = withContext(Dispatchers.IO) {
        delay(SETTLE_MS)
        // Newest text tombstones first (Android 12+ also writes a .pb twin); first one naming the app wins.
        val script = """
            cd /data/tombstones 2>/dev/null || exit 0
            for f in $(ls -t | grep '^tombstone_' | grep -v '\.pb$' | head -n 5); do
              if grep -qF '>>> $pkg' "${'$'}f"; then echo '$MARKER'; cat "${'$'}f"; break; fi
            done
        """.trimIndent()
        val output = RootShell.run(script, useRoot = true, timeoutMs = 10_000) ?: return@withContext null
        val body = output.substringAfter(MARKER, "").trimStart('\n')
        if (body.isBlank()) return@withContext null
        val lines = body.lines().map { line -> symbolicate(line, symbols) }
        SessionLogFileWriter.writeStandalone(context, "${pkg}_tombstone", lines)
    }

    private fun symbolicate(line: String, symbols: NativeSymbolStore): String {
        val frame = NativeFrame.parse(line) ?: return line
        val symbol = symbols.lookup(frame) ?: return line
        return line.substring(0, frame.end) + " [" + symbol + "]" + line.substring(frame.end)
    }
}
