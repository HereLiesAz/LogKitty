package com.hereliesaz.logkitty.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexTest {

    private val timestampPattern = LogcatReader.TIMESTAMP_PATTERN

    @Test
    fun `test timestamp pattern matches valid logcat timestamps`() {
        // Valid logcat strings
        assertTrue(timestampPattern.containsMatchIn("12-25 10:30:45.123 D/Tag: Message"))
        assertTrue(timestampPattern.containsMatchIn("01-01 00:00:00.000 V/App: Hello"))
        assertTrue(timestampPattern.containsMatchIn("08-15 23:59:59.999 E/Error: Crash"))
        // `-v year` output is still real log output; the unanchored pattern finds `12-25 10:30:45.123` in it.
        assertTrue(timestampPattern.containsMatchIn("2023-12-25 10:30:45.123 D/Tag: Message"))
    }

    @Test
    fun `test timestamp pattern rejects invalid formats`() {
        // Invalid or different formats
        assertFalse(timestampPattern.containsMatchIn("12-25 10:30:45 D/Tag: Message")) // Missing ms
        assertFalse(timestampPattern.containsMatchIn("10:30:45.123 D/Tag: Message")) // Missing date
        assertFalse(timestampPattern.containsMatchIn("Just a normal log line without timestamp"))
    }
}
