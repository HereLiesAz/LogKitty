package com.hereliesaz.logkitty.retrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetracerTest {

    private val mapping = MappingFile.parse(
        """
        # compiler: R8
        com.example.app.MainActivity -> a.a:
        # {"id":"sourceFile","fileName":"MainActivity.kt"}
            int counter -> a
            1:1:void <init>():12:12 -> <init>
            2:4:void onClick(android.view.View):40:42 -> b
            5:5:void com.example.app.util.Checks.require(boolean):7:7 -> b
            5:5:void onClick(android.view.View):43 -> b
            void overloadA() -> c
            void overloadB(int) -> c
        com.example.app.util.Checks -> a.b:
        # {"id":"sourceFile","fileName":"Checks.kt"}
        com.example.app.BadThing -> a.c:
        """.trimIndent().reader().buffered()
    )

    private val header = "10-22 12:00:00.000 E/AndroidRuntime( 123): "

    @Test fun rangedFrameMapsLine() {
        assertEquals(
            listOf(header + "\tat com.example.app.MainActivity.onClick(MainActivity.kt:41)"),
            Retracer.retrace(header + "\tat a.a.b(SourceFile:3)", mapping)
        )
    }

    @Test fun inlinedFrameExpandsInnermostFirst() {
        assertEquals(
            listOf(
                header + "\tat com.example.app.util.Checks.require(Checks.kt:7)",
                header + "\tat com.example.app.MainActivity.onClick(MainActivity.kt:43)",
            ),
            Retracer.retrace(header + "\tat a.a.b(SourceFile:5)", mapping)
        )
    }

    @Test fun ambiguousOverloadsAreListed() {
        assertEquals(
            listOf(header + "\tat com.example.app.MainActivity.overloadA|overloadB(MainActivity.kt)"),
            Retracer.retrace(header + "\tat a.a.c(Unknown Source)", mapping)
        )
    }

    @Test fun exceptionClassNamesAreRestored() {
        assertEquals(
            listOf(header + "Caused by: com.example.app.BadThing: boom"),
            Retracer.retrace(header + "Caused by: a.c: boom", mapping)
        )
    }

    @Test fun unknownLinesAreLeftAlone() {
        assertNull(Retracer.retrace(header + "\tat android.os.Looper.loop(Looper.java:288)", mapping))
        assertNull(Retracer.retrace(header + "Just a message", mapping))
    }
}
