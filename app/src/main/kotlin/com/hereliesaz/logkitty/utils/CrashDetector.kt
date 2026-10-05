package com.hereliesaz.logkitty.utils

/**
 * Pure (Android-free) recognizer for app-crash log lines.
 *
 * Signatures (each names the package or is attributed by UID):
 *  - Java crash: `AndroidRuntime: FATAL EXCEPTION: main` (emitted from the crashing app's UID),
 *    followed by `AndroidRuntime: Process: com.example, PID: 1234` (names the package).
 *  - Native crash: `DEBUG: pid: 1234, tid: 1234, name: … >>> com.example <<<` (tombstone header).
 *
 * Ordinary process deaths (`Process … has died`, low-memory kills) are deliberately ignored.
 */
object CrashDetector {
    private val PROCESS_LINE = Regex("""AndroidRuntime.*Process: ([\w.]+)[:,]""")
    private val NATIVE_LINE = Regex(""">>> ([\w.]+)(?::[\w.]+)? <<<""")

    /**
     * Returns the package from [watched] (package → UID, UID may be null) that [text] / [uid] report
     * as crashing, or `null`.
     */
    fun crashedPackage(text: String, uid: Int?, watched: Map<String, Int?>): String? {
        if (watched.isEmpty()) return null
        // Only the runtime's own header counts — an app logging the phrase itself is not a crash.
        if (uid != null && text.contains("FATAL EXCEPTION") && text.contains("AndroidRuntime")) {
            watched.entries.firstOrNull { it.value == uid }?.let { return it.key }
        }
        val named = PROCESS_LINE.find(text)?.groupValues?.get(1)
            ?: NATIVE_LINE.find(text)?.groupValues?.get(1)
            ?: return null
        return named.takeIf { it in watched }
    }
}
