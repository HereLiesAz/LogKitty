package com.hereliesaz.logkitty.nativecrash

/**
 * One backtrace frame as printed by Android's crash dumper (logcat `DEBUG` tag or a tombstone):
 * `#03 pc 000000000001a2b4  /data/app/~~x/com.foo-y/lib/arm64/libfoo.so (ns::Foo::bar(int)+20) (BuildId: ab12…)`
 */
data class NativeFrame(
    val index: Int,
    val pc: Long,
    val path: String,
    val existingSymbol: String?,
    val buildId: String?,
    /** End of the frame's text (trailing whitespace excluded), where an annotation is appended. */
    val end: Int,
) {
    /** Library file name, e.g. `libfoo.so` (also for `base.apk!lib/arm64-v8a/libfoo.so`). */
    val libName: String get() = path.substringAfterLast('/').substringAfterLast('!')

    /** ABI directory implied by the path, normalized to an Android ABI name, if any. */
    val abi: String? get() = ABI_IN_PATH.find(path)?.groupValues?.get(1)?.let { ABI_ALIASES[it] ?: it }

    /**
     * Address to resolve: frames above #00 hold a return address, which can sit just past the end
     * of the calling function (e.g. a call to a noreturn function), so — like the unwinder — use pc-1.
     */
    val lookupPc: Long get() = if (index > 0 && pc > 0) pc - 1 else pc

    companion object {
        // Path is greedy up to the `.so` that ends the token, so `/com.example.social-x/lib/libfoo.so` works.
        private val HEAD = Regex("""#(\d+) pc ([0-9a-fA-F]+)\s+(\S+\.so)(?=\s|$)""")
        private val OFFSET = Regex("""^\s*\(offset 0x[0-9a-fA-F]+\)""")
        private val BUILD_ID = Regex("""\s*\(BuildId: ([0-9a-fA-F]+)\)\s*$""")
        private val ABI_IN_PATH = Regex("""/lib/([\w-]+)/""")
        private val ABI_ALIASES = mapOf("arm64" to "arm64-v8a", "arm" to "armeabi-v7a")

        fun parse(line: String): NativeFrame? {
            if (" pc " !in line) return null
            val m = HEAD.find(line) ?: return null
            val pc = m.groupValues[2].toLongOrNull(16) ?: return null
            // The rest may hold `(offset …)`, a demangled symbol that itself contains parentheses,
            // and `(BuildId: …)` — peel the fixed-shape parts off the ends instead of regex-matching parens.
            var rest = line.substring(m.range.last + 1).trimEnd()
            rest = OFFSET.replace(rest, "")
            val buildId = BUILD_ID.find(rest)?.let { b -> rest = rest.removeRange(b.range); b.groupValues[1].lowercase() }
            val symbol = rest.trim().takeIf { it.length > 2 && it.startsWith("(") && it.endsWith(")") }
                ?.substring(1)?.dropLast(1)
            return NativeFrame(
                index = m.groupValues[1].toInt(),
                pc = pc,
                path = m.groupValues[3],
                existingSymbol = symbol,
                buildId = buildId,
                end = line.trimEnd().length,
            )
        }
    }
}
