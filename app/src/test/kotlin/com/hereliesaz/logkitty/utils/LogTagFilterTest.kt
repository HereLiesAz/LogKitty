package com.hereliesaz.logkitty.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTagFilterTest {

    // Standard `-v time` logcat shape: "MM-DD HH:MM:SS.mmm L/Tag( pid): message".
    private val line = "01-01 12:00:00.123 D/ActivityManager( 1234): started something"

    @Test
    fun tagOf_extractsTag() {
        assertEquals("ActivityManager", LogTagFilter.tagOf(line))
    }

    @Test
    fun tagOf_extractsTagWhenLevelStartsLine() {
        // `-v brief` style (or stripped timestamp/pid): line begins directly with the level.
        assertEquals("MyTag", LogTagFilter.tagOf("D/MyTag( 1234): hello"))
    }

    @Test
    fun tagOf_returnsNullWhenNoTag() {
        assertNull(LogTagFilter.tagOf("Logcat reader warning: stream closed. Retrying..."))
    }

    @Test
    fun isProhibited_matchesTagExactly_caseInsensitive() {
        assertTrue(LogTagFilter.isProhibited(line, setOf("activitymanager")))
    }

    @Test
    fun isProhibited_doesNotMatchTagSubstring() {
        // The bug being fixed: prohibiting "A" must NOT hide a line whose tag is "ActivityManager".
        assertFalse(LogTagFilter.isProhibited(line, setOf("A")))
        assertFalse(LogTagFilter.isProhibited(line, setOf("Activity")))
    }

    @Test
    fun isProhibited_substringFallbackForUntaggedLines() {
        val warning = "Logcat reader failed: permission denied"
        assertTrue(LogTagFilter.isProhibited(warning, setOf("permission denied")))
        assertFalse(LogTagFilter.isProhibited(warning, setOf("ActivityManager")))
    }

    @Test
    fun isProhibited_emptySetNeverHides() {
        assertFalse(LogTagFilter.isProhibited(line, emptySet()))
    }
}
