package com.hereliesaz.logkitty.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCategoryTest {
    @Test fun crashes() = assertTrue(LogCategory.CRASHES.matches("10-05 09:00:00.000 E/AndroidRuntime( 1): FATAL EXCEPTION: main"))
    @Test fun errorsByLevel() {
        assertTrue(LogCategory.ERRORS.matches("10-05 09:00:00.000 E/Tag( 1): boom"))
        assertFalse(LogCategory.ERRORS.matches("10-05 09:00:00.000 I/Tag( 1): fine"))
    }
    @Test fun warnings() = assertTrue(LogCategory.WARNINGS.matches("10-05 09:00:00.000 W/Tag( 1): hmm"))
    @Test fun network() = assertTrue(LogCategory.NETWORK.matches("I/OkHttp( 1): --> GET https://x"))
    @Test fun memory() = assertTrue(LogCategory.MEMORY.matches("I/art( 1): Background concurrent copying GC freed 1MB"))
    @Test fun anrs() = assertTrue(LogCategory.ANRS.matches("E/ActivityManager( 1): ANR in com.example"))
    @Test fun allPassesEverything() = assertTrue(LogCategory.ALL.matches("anything"))
}
