package com.hereliesaz.logkitty.retrace

import java.io.BufferedReader

/**
 * In-memory index of an R8/ProGuard `mapping.txt`, keyed by **obfuscated** class name.
 *
 * Only what retracing a stack trace needs is kept: class renames, the original source file name
 * (R8's `# {"id":"sourceFile",...}` metadata), and method mappings with their line ranges. Fields
 * are skipped. Member entries keep file order, which R8 uses to encode inline chains (innermost
 * frame first) for a shared obfuscated line range.
 *
 * Several original classes can share one obfuscated name (R8 class merging), so each obfuscated
 * name maps to a list. Not supported: member-level R8 metadata (`outline`, `outlineCallsite`,
 * `rewriteFrame`, `residualsignature`) — outlined frames stay as printed.
 */
class MappingFile private constructor(
    private val classes: Map<String, List<ClassMapping>>,
    private val byOriginal: Map<String, ClassMapping>,
) {

    class ClassMapping(val original: String, val obfuscated: String) {
        var sourceFile: String? = null
        val methods = HashMap<String, MutableList<MethodMapping>>()
    }

    /**
     * One method line. [obfStart]/[obfEnd] are the minified line range (null = no range).
     * [origClass] is set when the frame was inlined from another class.
     */
    data class MethodMapping(
        val obfStart: Int?,
        val obfEnd: Int?,
        val origClass: String?,
        val origName: String,
        val origStart: Int?,
        val origEnd: Int?,
    ) {
        /** Maps a minified line to the original one, following R8's range rules. */
        fun originalLine(line: Int?): Int? = when {
            origStart == null -> line // no original range: lines were not remapped
            origEnd == null || origEnd == origStart || obfStart == null || line == null -> origStart
            else -> origStart + (line - obfStart)
        }
    }

    val size get() = classes.size

    /** All original classes R8 renamed (or merged) into [obfuscated]. */
    fun classesFor(obfuscated: String): List<ClassMapping> = classes[obfuscated].orEmpty()

    /** Source file for an original class name, if the mapping recorded one. */
    fun sourceFileFor(original: String): String? =
        byOriginal[original]?.sourceFile ?: byOriginal[original.substringBefore('$')]?.sourceFile

    companion object {
        private val CLASS_LINE = Regex("""^(\S+) -> (\S+):$""")
        private val METHOD_LINE = Regex(
            """^\s+(?:(\d+):(\d+):)?\S+\s+([^\s(]+)\([^)]*\)(?::(\d+)(?::(\d+))?)?\s+->\s+(\S+)$"""
        )
        private val SOURCE_FILE = Regex(""""id"\s*:\s*"sourceFile".*"fileName"\s*:\s*"([^"]+)"""")

        fun parse(reader: BufferedReader): MappingFile {
            val classes = HashMap<String, MutableList<ClassMapping>>()
            val byOriginal = HashMap<String, ClassMapping>()
            var current: ClassMapping? = null
            reader.forEachLine { raw ->
                val line = raw.trimEnd() // tolerate CRLF / trailing spaces
                when {
                    line.isBlank() -> Unit
                    line.trimStart().startsWith("#") -> {
                        // Metadata directly under a class line applies to that class.
                        val cls = current
                        if (cls != null && line.startsWith("#")) {
                            SOURCE_FILE.find(line)?.let { cls.sourceFile = it.groupValues[1] }
                        }
                    }
                    !line[0].isWhitespace() -> {
                        current = CLASS_LINE.matchEntire(line)?.let { m ->
                            ClassMapping(m.groupValues[1], m.groupValues[2]).also {
                                classes.getOrPut(it.obfuscated) { ArrayList(1) }.add(it)
                                byOriginal[it.original] = it
                            }
                        }
                    }
                    else -> {
                        val cls = current ?: return@forEachLine
                        if ('(' !in line) return@forEachLine // field: skip without the regex cost
                        val m = METHOD_LINE.matchEntire(line) ?: return@forEachLine
                        val g = m.groupValues
                        val qualified = g[3]
                        val dot = qualified.lastIndexOf('.')
                        cls.methods.getOrPut(g[6]) { ArrayList(1) }.add(
                            MethodMapping(
                                obfStart = g[1].toIntOrNull(),
                                obfEnd = g[2].toIntOrNull(),
                                origClass = if (dot > 0) qualified.substring(0, dot) else null,
                                origName = if (dot > 0) qualified.substring(dot + 1) else qualified,
                                origStart = g[4].toIntOrNull(),
                                origEnd = g[5].toIntOrNull(),
                            )
                        )
                    }
                }
            }
            return MappingFile(classes, byOriginal)
        }
    }
}
