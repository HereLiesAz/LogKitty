package com.hereliesaz.logkitty.retrace

/**
 * Rewrites obfuscated stack-trace lines using a [MappingFile].
 *
 * Works on whole logcat lines: only the `at cls.method(File:line)` frame, or class names on a
 * throwable line (`a.b: msg`, `Caused by: a.b`, `Suppressed: …`), are replaced, so the
 * timestamp/tag header and ordinary messages stay intact. An inlined frame expands into several
 * lines, each carrying the original header.
 */
object Retracer {

    private val FRAME = Regex("""at ([\w$.]+)\.([\w$<>-]+)\(([^:)]*)(?::(\d+))?\)""")

    // The message part of a `-v time` line starts after the `L/Tag( pid): ` header.
    private val HEADER = Regex("""^.*?[VDIWEFA]/[^(]*\(\s*\d+\):\s?""")

    // A throwable line: optional cause prefix, then a dotted class name ending the line or before `:`.
    private val THROWABLE = Regex("""^\s*(?:Caused by: |Suppressed: )?[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+(?::|$)""")
    private val DOTTED = Regex("""(?<![\w$.])[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+(?![\w$])""")

    /** Returns the retraced line(s), or null when [line] contains nothing this mapping knows. */
    fun retrace(line: String, mapping: MappingFile): List<String>? {
        FRAME.find(line)?.let { m -> retraceFrame(line, m, mapping)?.let { return it } }
        val headerEnd = HEADER.find(line)?.range?.last?.plus(1) ?: 0
        val message = line.substring(headerEnd)
        if (!THROWABLE.containsMatchIn(message)) return null
        // Within a throwable line, class names in the message are fair game (e.g. ClassCastException).
        var changed = false
        val out = DOTTED.replace(message) { m ->
            mapping.classesFor(m.value).singleOrNull()?.let { changed = true; it.original } ?: m.value
        }
        return if (changed) listOf(line.substring(0, headerEnd) + out) else null
    }

    private fun retraceFrame(line: String, m: MatchResult, mapping: MappingFile): List<String>? {
        val (obfClass, obfMethod, file, lineStr) = m.destructured
        val obfLine = lineStr.toIntOrNull()
        val classes = mapping.classesFor(obfClass).ifEmpty { return null }
        // Merged classes: prefer the one whose methods cover this frame.
        val cls = classes.firstOrNull { c -> c.methods[obfMethod].orEmpty().any { obfLine != null && it.covers(obfLine) } }
            ?: classes.firstOrNull { obfMethod in it.methods }
            ?: classes.singleOrNull()
            ?: return null
        val candidates = cls.methods[obfMethod].orEmpty()
        val prefix = line.substring(0, m.range.first)
        val suffix = line.substring(m.range.last + 1)

        val ranged = if (obfLine != null) candidates.filter { it.covers(obfLine) } else emptyList()
        if (ranged.isNotEmpty()) {
            return ranged.map { mm ->
                val owner = mm.origClass ?: cls.original
                prefix + frame(owner, mm.origName, fileFor(owner, cls, mapping, file), mm.originalLine(obfLine)) + suffix
            }
        }

        // No line to pick a range (or none matched): never invent an inline chain — name the
        // method if unambiguous, else list the candidates, and keep the printed line number.
        val pool = candidates.filter { it.obfStart == null }.ifEmpty { candidates }
        val names = pool.map { it.origName }.distinct()
        val method = when {
            names.isEmpty() -> obfMethod
            names.size == 1 -> names[0]
            else -> names.joinToString("|")
        }
        return listOf(prefix + frame(cls.original, method, fileFor(cls.original, cls, mapping, file), obfLine) + suffix)
    }

    private fun MappingFile.MethodMapping.covers(line: Int) =
        obfStart != null && line in obfStart..(obfEnd ?: obfStart)

    private fun fileFor(owner: String, cls: MappingFile.ClassMapping, mapping: MappingFile, printed: String): String =
        if (owner == cls.original) cls.sourceFile ?: printed
        // An inlined frame from another class: its own metadata, else honestly unknown.
        else mapping.sourceFileFor(owner) ?: "Unknown Source"

    private fun frame(cls: String, method: String, file: String, line: Int?) =
        "at $cls.$method($file${if (line != null) ":$line" else ""})"
}
