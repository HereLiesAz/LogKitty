package com.hereliesaz.logkitty.nativecrash

/**
 * One backtrace frame as printed by Android's crash dumper (logcat `DEBUG` tag or a tombstone):
 * `#03 pc 000000000001a2b4  /data/app/~~x/com.foo-y/lib/arm64/libfoo.so (Java_foo+20) (BuildId: ab12…)`
 */
data class NativeFrame(
    val pc: Long,
    val path: String,
    val existingSymbol: String?,
    val buildId: String?,
    /** Index just past the frame's path/symbol/build-id text, where an annotation can be inserted. */
    val end: Int,
) {
    /** Library file name, e.g. `libfoo.so` (also for `base.apk!lib/arm64-v8a/libfoo.so`). */
    val libName: String get() = path.substringAfterLast('/').substringAfterLast('!')

    /** ABI directory implied by the path, normalized to an Android ABI name, if any. */
    val abi: String? get() = ABI_IN_PATH.find(path)?.groupValues?.get(1)?.let { ABI_ALIASES[it] ?: it }

    companion object {
        private val FRAME = Regex(
            """#\d+ pc ([0-9a-fA-F]+)\s+(\S+?\.so)(?: \(offset 0x[0-9a-fA-F]+\))?(?: \(([^)]*)\))?(?: \(BuildId: ([0-9a-fA-F]+)\))?"""
        )
        private val ABI_IN_PATH = Regex("""/lib/([\w-]+)/""")
        private val ABI_ALIASES = mapOf("arm64" to "arm64-v8a", "arm" to "armeabi-v7a")

        fun parse(line: String): NativeFrame? {
            if (" pc " !in line) return null
            val m = FRAME.find(line) ?: return null
            val pc = m.groupValues[1].toLongOrNull(16) ?: return null
            // With no symbol, the optional symbol group swallows the build-id parentheses instead.
            val paren = m.groupValues[3]
            val idFromParen = paren.removePrefix("BuildId: ").takeIf { paren.startsWith("BuildId: ") }
            return NativeFrame(
                pc = pc,
                path = m.groupValues[2],
                existingSymbol = paren.takeIf { it.isNotEmpty() && idFromParen == null },
                buildId = (idFromParen ?: m.groupValues[4]).lowercase().ifEmpty { null },
                end = m.range.last + 1,
            )
        }
    }
}
