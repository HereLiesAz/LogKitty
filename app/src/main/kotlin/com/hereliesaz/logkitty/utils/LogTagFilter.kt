package com.hereliesaz.logkitty.utils

/**
 * Pure (Android-free) helpers for parsing a logcat line's tag and deciding whether it is prohibited.
 *
 * Kept free of framework/Compose types so it is directly unit-testable on the JVM, and so the single
 * tag-extraction regex has one home (see [com.hereliesaz.logkitty.ui.LogLevel.tagFromLine], which
 * delegates here).
 */
object LogTagFilter {

    /**
     * Matches the `L/Tag` portion of a standard logcat line; group 2 is the tag.
     *
     * `(?:^|\s)` lets the level letter sit at the very start of the line too (e.g. `-v brief`
     * output, or any line where the timestamp/pid were stripped), not only after whitespace. The
     * tag class excludes `(` so the trailing `( pid)` in the `-v time` shape
     * (`D/ActivityManager( 1234): …`) isn't captured as part of the tag.
     */
    private val tagWithLetterRegex = Regex("""(?:^|\s)([VDIWEA])/([^\s:(]+)""")

    /** Extracts the tag from a log line, or `null` when the line carries no recognizable tag. */
    fun tagOf(line: String): String? =
        tagWithLetterRegex.find(line)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Whether [lineText] should be hidden given the set of [prohibited] tags.
     *
     * Matches the line's *tag* for equality (case-insensitive) so prohibiting `"A"` doesn't hide
     * every line that merely contains an "a". Lines with no parseable tag (reader warnings, wrapped
     * stack traces) fall back to a substring check so an explicitly prohibited phrase can still hide
     * them.
     */
    fun isProhibited(lineText: String, prohibited: Set<String>): Boolean {
        if (prohibited.isEmpty()) return false
        val tag = tagOf(lineText)
        return prohibited.any { p ->
            if (tag != null) tag.equals(p, ignoreCase = true)
            else lineText.contains(p, ignoreCase = true)
        }
    }
}
