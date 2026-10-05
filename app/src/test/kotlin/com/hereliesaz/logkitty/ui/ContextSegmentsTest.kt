package com.hereliesaz.logkitty.ui

import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextSegmentsTest {
    private val a = ContextSegment("com.a", 1)
    private val b = ContextSegment("com.b", 10)
    private val segs = listOf(a, b)
    private fun l(id: Long) = IndexedLogLine(id, "line $id")

    @Test fun segmentForPicksLastStartingAtOrBefore() {
        assertEquals(a, ContextSegments.segmentFor(segs, 5))
        assertEquals(b, ContextSegments.segmentFor(segs, 10))
        assertEquals(b, ContextSegments.segmentFor(segs, 99))
        assertEquals(a, ContextSegments.segmentFor(segs, 0)) // older than first → first
    }

    @Test fun groupSplitsBySegment() {
        val g = ContextSegments.group(listOf(l(2), l(3), l(11)), segs)
        assertEquals(listOf(a to listOf(l(2), l(3)), b to listOf(l(11))), g)
    }

    @Test fun appendSkipsSameAppAndTrimsEvicted() {
        assertEquals(segs, ContextSegments.append(segs, "com.b", 20, null))
        val c = ContextSegments.append(segs, "com.c", 20, oldestId = 12)
        assertEquals(listOf(b, ContextSegment("com.c", 20)), c)
    }
}
