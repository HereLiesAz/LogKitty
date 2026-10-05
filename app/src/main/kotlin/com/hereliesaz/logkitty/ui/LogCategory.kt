package com.hereliesaz.logkitty.ui

/**
 * The app tab's quick-filter chips. Each category is a cheap text/level predicate over a log line;
 * [ALL] passes everything.
 */
enum class LogCategory(val label: String) {
    ALL("All Logs"),
    CRASHES("Crashes"),
    ERRORS("Errors"),
    WARNINGS("Warnings"),
    NETWORK("Network"),
    MEMORY("Memory"),
    ANRS("ANRs");

    fun matches(line: String): Boolean = when (this) {
        ALL -> true
        CRASHES -> CRASH_MARKERS.any { line.contains(it) }
        ERRORS -> LogLevel.fromLine(line).let { it == LogLevel.ERROR || it == LogLevel.ASSERT }
        WARNINGS -> LogLevel.fromLine(line) == LogLevel.WARNING
        NETWORK -> NETWORK_MARKERS.any { line.contains(it, ignoreCase = true) }
        MEMORY -> MEMORY_MARKERS.any { line.contains(it, ignoreCase = true) }
        ANRS -> ANR_MARKERS.any { line.contains(it) }
    }

    private companion object {
        val CRASH_MARKERS = listOf("FATAL EXCEPTION", "AndroidRuntime", "Fatal signal", "backtrace:", ">>> ")
        val NETWORK_MARKERS = listOf("http", "socket", "okhttp", "retrofit", "network", "dns", "ssl", "connect")
        val MEMORY_MARKERS = listOf("OutOfMemory", "GC freed", "lowmemory", "lowmemorykiller", "trimMemory", "onTrimMemory", "heap")
        val ANR_MARKERS = listOf("ANR in", "Application Not Responding", "Input dispatching timed out")
    }
}
