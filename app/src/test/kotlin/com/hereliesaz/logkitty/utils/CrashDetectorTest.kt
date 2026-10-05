package com.hereliesaz.logkitty.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashDetectorTest {
    private val watched = mapOf<String, Int?>("com.example.app" to 10123, "com.other" to null)

    @Test fun fatalExceptionByUid() = assertEquals("com.example.app",
        CrashDetector.crashedPackage("10-05 09:00:00.000 E/AndroidRuntime( 1234): FATAL EXCEPTION: main", 10123, watched))

    @Test fun processLineByName() = assertEquals("com.other",
        CrashDetector.crashedPackage("E/AndroidRuntime( 99): Process: com.other, PID: 99", null, watched))

    @Test fun processLineWithSubprocess() = assertEquals("com.example.app",
        CrashDetector.crashedPackage("E/AndroidRuntime( 99): Process: com.example.app:remote, PID: 99", null, watched))

    @Test fun nativeTombstone() = assertEquals("com.example.app",
        CrashDetector.crashedPackage("F/DEBUG ( 77): pid: 1, tid: 1, name: main  >>> com.example.app <<<", null, watched))

    @Test fun unwatchedIgnored() = assertNull(
        CrashDetector.crashedPackage("E/AndroidRuntime( 99): Process: com.unwatched, PID: 99", null, watched))

    @Test fun normalDeathIgnored() = assertNull(
        CrashDetector.crashedPackage("I/ActivityManager( 1): Process com.example.app (pid 5) has died", 1000, watched))

    @Test fun appLoggingPhraseIgnored() = assertNull(
        CrashDetector.crashedPackage("I/MyApp( 1): user typed FATAL EXCEPTION", 10123, watched))
}
