package com.hereliesaz.logkitty.nativecrash

import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine

/**
 * Collapses long stack traces in the log list: within each run of consecutive frame lines (Java
 * `at …(…)` or native `#NN pc …`), the first [KEEP] stay visible and the rest become one [Entry.Fold]
 * row until it is expanded. Pure, so it is cheap to recompute on every list change.
 */
object BacktraceFolding {

    const val KEEP = 4

    sealed interface Entry {
        val key: Any
        data class Line(val line: IndexedLogLine) : Entry { override val key: Any get() = line.id }
        /** [firstId] = id of the first hidden line, used as the expansion key. */
        data class Fold(val firstId: Long, val hidden: Int) : Entry { override val key: Any get() = "fold_$firstId" }
    }

    // `at pkg.Cls.method(…)`: at least one dot, so prose like "retry at connect()" isn't a frame.
    private val JAVA_FRAME = Regex("""(?:^|\s)at [\w$]+(?:\.[\w$<>-]+)+\([^)]*\)\s*$""")

    fun isFrame(text: String): Boolean = JAVA_FRAME.containsMatchIn(text) || NativeFrame.parse(text) != null

    /**
     * [frameCache] memoizes [isFrame] per line id (lines are immutable), so recomputing the fold on
     * each log batch only pays the regex cost for new lines.
     */
    fun fold(lines: List<IndexedLogLine>, expanded: Set<Long>, frameCache: MutableMap<Long, Boolean>? = null): List<Entry> {
        fun isFrame(line: IndexedLogLine): Boolean =
            frameCache?.getOrPut(line.id) { isFrame(line.text) } ?: isFrame(line.text)
        val out = ArrayList<Entry>(lines.size)
        var i = 0
        while (i < lines.size) {
            if (!isFrame(lines[i])) { out += Entry.Line(lines[i]); i++; continue }
            var end = i
            while (end < lines.size && isFrame(lines[end])) end++
            val run = lines.subList(i, end)
            if (run.size <= KEEP + 1 || run[KEEP].id in expanded) {
                run.forEach { out += Entry.Line(it) }
            } else {
                run.take(KEEP).forEach { out += Entry.Line(it) }
                out += Entry.Fold(run[KEEP].id, run.size - KEEP)
            }
            i = end
        }
        return out
    }
}
