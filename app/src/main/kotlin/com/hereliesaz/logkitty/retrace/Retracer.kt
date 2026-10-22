package com.hereliesaz.logkitty.retrace

/**
 * Rewrites obfuscated stack-trace lines using a [MappingFile].
 *
 * Works on whole logcat lines: only the `at cls.method(File:line)` frame (or an obfuscated
 * exception class name) is replaced, so the timestamp/tag header stays intact. An inlined frame
 * expands into several lines, each carrying the original header.
 */
object Retracer {

    private val FRAME = Regex("""at ([\w$.]+)\.([\w$<>-]+)\(([^:)]*)(?::(\d+))?\)""")

    // A dotted class name standing alone or before a colon, e.g. `a.b.c: msg` / `Caused by: a.b.c`.
    private val CLASS_TOKEN = Regex("""(?<![\w$.])([a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+)(?=:|\s*$)""")

    /** Returns the retraced line(s), or null when [line] contains nothing this mapping knows. */
    fun retrace(line: String, mapping: MappingFile): List<String>? {
        FRAME.find(line)?.let { return retraceFrame(line, it, mapping) }
        if (':' !in line && !line.trimEnd().endsWith("Exception") && !line.trimEnd().endsWith("Error")) return null
        var changed = false
        val out = CLASS_TOKEN.replace(line) { m ->
            val cls = mapping.classFor(m.value)
            if (cls != null) { changed = true; cls.original } else m.value
        }
        return if (changed) listOf(out) else null
    }

    private fun retraceFrame(line: String, m: MatchResult, mapping: MappingFile): List<String>? {
        val (obfClass, obfMethod, file, lineStr) = m.destructured
        val cls = mapping.classFor(obfClass) ?: return null
        val obfLine = lineStr.toIntOrNull()
        val candidates = cls.methods[obfMethod].orEmpty()

        val chain: List<MappingFile.MethodMapping> = when {
            candidates.isEmpty() -> emptyList()
            obfLine != null -> candidates.filter { it.obfStart != null && obfLine in it.obfStart..(it.obfEnd ?: it.obfStart) }
                .ifEmpty { candidates.filter { it.obfStart == null } }
            else -> candidates.filter { it.obfStart == null }.ifEmpty { candidates }
        }

        val prefix = line.substring(0, m.range.first)
        val suffix = line.substring(m.range.last + 1)
        if (chain.isEmpty()) {
            // Class known, method not: still worth restoring the class and file name.
            return listOf(prefix + frame(cls.original, obfMethod, fileFor(cls.original, cls, mapping, file), obfLine) + suffix)
        }

        // Several unranged candidates with different names = overloads collapsed by R8 → ambiguous.
        val distinctNames = chain.map { (it.origClass ?: cls.original) + "." + it.origName }.distinct()
        if (chain.all { it.obfStart == null } && distinctNames.size > 1) {
            val owner = cls.original
            val names = chain.map { it.origName }.distinct().joinToString("|")
            return listOf(prefix + frame(owner, names, fileFor(owner, cls, mapping, file), obfLine) + suffix)
        }

        return chain.map { mm ->
            val owner = mm.origClass ?: cls.original
            prefix + frame(owner, mm.origName, fileFor(owner, cls, mapping, file), mm.originalLine(obfLine)) + suffix
        }
    }

    private fun fileFor(owner: String, cls: MappingFile.ClassMapping, mapping: MappingFile, fallback: String): String =
        (if (owner == cls.original) cls.sourceFile else null)
            ?: mapping.sourceFileFor(owner)
            ?: fallback

    private fun frame(cls: String, method: String, file: String, line: Int?) =
        "at $cls.$method($file${if (line != null) ":$line" else ""})"
}
