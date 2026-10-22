package com.hereliesaz.logkitty.nativecrash

import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class NativeCrashTest {

    // Tiny x86-64 test library (`.bin`: AGP drops `.so` from unit-test resources): `static helper` @0x1119, `exported_fn` @0x1132, `second_fn` @0x1150.
    private val lib: ElfSymbols by lazy {
        val res = javaClass.classLoader!!.getResource("nativecrash/libtest.so.bin")!!
        ElfSymbols.read(File(res.toURI()))!!
    }

    @Test fun symtabIncludesStaticFunctions() {
        assertEquals("helper", lib.lookup(0x1119))
        assertEquals("helper+0x4", lib.lookup(0x111d))
        assertEquals("exported_fn+0x10", lib.lookup(0x1142))
        assertNull(lib.lookup(0x10))
    }

    @Test fun buildIdIsRead() {
        assertEquals("54d15cdb9dd9c27c17653d0783e57c683b7531ab", lib.buildId)
    }

    @Test fun parsesFrameWithSymbolAndBuildId() {
        val f = NativeFrame.parse(
            "10-22 12:00:00.000 F/DEBUG   ( 999):       #01 pc 0000000000001142  /data/app/~~a/com.foo-b/lib/arm64/libtest.so (exported_fn+16) (BuildId: 54D1)"
        )!!
        assertEquals(0x1142L, f.pc)
        assertEquals("libtest.so", f.libName)
        assertEquals("arm64-v8a", f.abi)
        assertEquals("exported_fn+16", f.existingSymbol)
        assertEquals("54d1", f.buildId)
    }

    @Test fun parsesFrameWithOnlyBuildId() {
        val f = NativeFrame.parse("      #00 pc 0001a2b4  /data/app/x/base.apk!libfoo.so (offset 0x1000) (BuildId: abc)")!!
        assertEquals("libfoo.so", f.libName)
        assertNull(f.existingSymbol)
        assertEquals("abc", f.buildId)
    }

    @Test fun foldsLongTracesOnly() {
        val lines = listOf(IndexedLogLine(1, "E/AndroidRuntime( 1): java.lang.RuntimeException: x")) +
            (2L..9L).map { IndexedLogLine(it, "E/AndroidRuntime( 1): \tat a.b.c$it(SourceFile:$it)") } +
            IndexedLogLine(10, "I/Other( 2): hello")
        val folded = BacktraceFolding.fold(lines, emptySet())
        assertEquals(1 + BacktraceFolding.KEEP + 1 + 1, folded.size)
        val fold = folded[1 + BacktraceFolding.KEEP] as BacktraceFolding.Entry.Fold
        assertEquals(8 - BacktraceFolding.KEEP, fold.hidden)
        assertEquals(lines.size, BacktraceFolding.fold(lines, setOf(fold.firstId)).size)
        assertNotNull(BacktraceFolding.fold(lines.take(5), emptySet()).none { it is BacktraceFolding.Entry.Fold })
    }
}
