package com.hereliesaz.logkitty.ui

import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine

/**
 * One stretch of the log recorded while [pkg] was the Context Mode app, starting at log id [startId].
 * A segment ends where the next one starts; switching apps never clears earlier segments.
 */
data class ContextSegment(val pkg: String, val startId: Long)

/** Pure helpers for mapping log lines onto Context Mode segments (Android-free, unit-tested). */
object ContextSegments {

    /**
     * The segment [id] belongs to: the last one starting at or before it. Lines older than the first
     * segment belong to the first one (they were captured before any app switch was seen).
     */
    fun segmentFor(segments: List<ContextSegment>, id: Long): ContextSegment? {
        if (segments.isEmpty()) return null
        var lo = 0
        var hi = segments.size - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].startId <= id) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return segments[found]
    }

    /** Splits [lines] (in id order) into consecutive runs, one per segment, dropping empty segments. */
    fun group(lines: List<IndexedLogLine>, segments: List<ContextSegment>): List<Pair<ContextSegment, List<IndexedLogLine>>> {
        if (segments.isEmpty() || lines.isEmpty()) return emptyList()
        val out = ArrayList<Pair<ContextSegment, MutableList<IndexedLogLine>>>()
        for (line in lines) {
            val seg = segmentFor(segments, line.id) ?: continue
            if (out.isEmpty() || out.last().first != seg) out.add(seg to mutableListOf())
            out.last().second.add(line)
        }
        return out
    }

    /** Appends a segment for [pkg] unless it is already the current one; drops segments older than [oldestId]. */
    fun append(segments: List<ContextSegment>, pkg: String, startId: Long, oldestId: Long?): List<ContextSegment> {
        if (segments.lastOrNull()?.pkg == pkg) return segments
        val next = segments + ContextSegment(pkg, startId)
        if (oldestId == null) return next
        // A segment is fully evicted once the following one starts at or before the oldest buffered line.
        val firstLive = next.indices.firstOrNull { i -> i == next.lastIndex || next[i + 1].startId > oldestId } ?: 0
        return next.drop(firstLive)
    }
}
