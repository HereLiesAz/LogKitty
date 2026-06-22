package com.hereliesaz.logkitty.feature.github

import androidx.compose.ui.graphics.Color

/** Severity of a parsed log line, from GitHub's `##[...]` workflow-command annotations. */
enum class LogSeverity { NORMAL, ERROR, WARNING, NOTICE, DEBUG, GROUP, COMMAND }

/** A run of text sharing one style. */
data class LogSpan(val text: String, val color: Color?, val bold: Boolean)

/**
 * One parsed log line: styled [spans] for rendering, the [plain] text for copy/select, a [severity]
 * (from `##[error]` etc.), and group membership so the view can collapse `##[group]…##[endgroup]`.
 */
data class ParsedLogLine(
    val spans: List<LogSpan>,
    val plain: String,
    val severity: LogSeverity,
    val groupId: Int?,
    val isGroupHeader: Boolean,
)

/**
 * Parses raw GitHub Actions job-log text into styled, group-aware lines.
 *
 * Handles: the leading ISO-8601 timestamp GitHub prefixes to every line (optionally stripped); the
 * `##[group]/##[endgroup]/##[error]/##[warning]/##[notice]/##[debug]/##[command]` workflow commands;
 * and inline ANSI SGR escapes (reset/bold + 8/16/256/truecolor foreground). Background and unknown
 * codes are ignored; malformed escapes are dropped rather than rendered as garbage. Pure function —
 * call it off the main thread.
 */
object LogAnsiClassifier {

    private val TIMESTAMP = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\s""")
    private val COMMAND = Regex("""^##\[(group|endgroup|error|warning|notice|debug|command|section)](.*)$""")

    fun parse(lines: List<String>, stripTimestamps: Boolean = true): List<ParsedLogLine> {
        val out = ArrayList<ParsedLogLine>()
        var groupCounter = 0
        var currentGroup: Int? = null
        for (rawLine in lines) {
            var line = rawLine
            if (stripTimestamps) line = TIMESTAMP.replaceFirst(line, "")

            val cmd = COMMAND.find(line)
            if (cmd != null) {
                val kind = cmd.groupValues[1]
                val rest = cmd.groupValues[2]
                when (kind) {
                    "group" -> {
                        currentGroup = ++groupCounter
                        out.add(headerLine(rest, currentGroup))
                        continue
                    }
                    "endgroup" -> {
                        currentGroup = null
                        continue // the endgroup marker itself isn't shown
                    }
                    else -> {
                        val severity = when (kind) {
                            "error" -> LogSeverity.ERROR
                            "warning" -> LogSeverity.WARNING
                            "notice" -> LogSeverity.NOTICE
                            "debug" -> LogSeverity.DEBUG
                            else -> LogSeverity.COMMAND
                        }
                        val plain = stripAnsi(rest)
                        out.add(ParsedLogLine(listOf(LogSpan(plain, severityColor(severity), severity == LogSeverity.ERROR)), plain, severity, currentGroup, false))
                        continue
                    }
                }
            }

            val spans = parseAnsi(line)
            val plain = spans.joinToString("") { it.text }
            out.add(ParsedLogLine(spans, plain, LogSeverity.NORMAL, currentGroup, false))
        }
        return out
    }

    private fun headerLine(title: String, groupId: Int): ParsedLogLine {
        val plain = stripAnsi(title)
        return ParsedLogLine(
            spans = listOf(LogSpan(plain, severityColor(LogSeverity.GROUP), bold = true)),
            plain = plain,
            severity = LogSeverity.GROUP,
            groupId = groupId,
            isGroupHeader = true,
        )
    }

    private fun severityColor(s: LogSeverity): Color? = when (s) {
        LogSeverity.ERROR -> Color(0xFFE57373)
        LogSeverity.WARNING -> Color(0xFFFFB74D)
        LogSeverity.NOTICE -> Color(0xFF64B5F6)
        LogSeverity.DEBUG -> Color(0xFF90A4AE)
        LogSeverity.GROUP -> Color(0xFF4DD0E1)
        LogSeverity.COMMAND -> Color(0xFF4DD0E1)
        LogSeverity.NORMAL -> null
    }

    // --- ANSI SGR ---

    private const val ESC = '\u001b'

    /** Strips all ANSI escape sequences, returning plain text. */
    private fun stripAnsi(s: String): String {
        if (s.indexOf(ESC) < 0) return s
        return parseAnsi(s).joinToString("") { it.text }
    }

    /**
     * Splits [s] into styled spans, tracking a per-line SGR state (GitHub resets styling per line, so
     * we start fresh each call). Recognizes `ESC [ params m`; other escape kinds are skipped.
     */
    private fun parseAnsi(s: String): List<LogSpan> {
        if (s.indexOf(ESC) < 0) return listOf(LogSpan(s, null, false))
        val spans = ArrayList<LogSpan>()
        val buf = StringBuilder()
        var color: Color? = null
        var bold = false
        var i = 0
        fun flush() {
            if (buf.isNotEmpty()) {
                spans.add(LogSpan(buf.toString(), color, bold))
                buf.setLength(0)
            }
        }
        while (i < s.length) {
            val c = s[i]
            if (c == ESC && i + 1 < s.length && s[i + 1] == '[') {
                val m = i + 2
                var j = m
                while (j < s.length && s[j] != 'm' && (s[j].isDigit() || s[j] == ';')) j++
                if (j < s.length && s[j] == 'm') {
                    flush()
                    val params = s.substring(m, j)
                    val (nc, nb) = applySgr(params, color, bold)
                    color = nc; bold = nb
                    i = j + 1
                    continue
                }
                // Not an SGR sequence we understand — skip the ESC and continue literally.
                i++
                continue
            }
            buf.append(c)
            i++
        }
        flush()
        return if (spans.isEmpty()) listOf(LogSpan("", null, false)) else spans
    }

    /** Applies one SGR parameter list to the current (color, bold) state. */
    private fun applySgr(params: String, color: Color?, bold: Boolean): Pair<Color?, Boolean> {
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        if (codes.isEmpty()) return null to false // bare ESC[m == reset
        var c = color
        var b = bold
        var k = 0
        while (k < codes.size) {
            when (val code = codes[k]) {
                0 -> { c = null; b = false }
                1 -> b = true
                22 -> b = false
                39 -> c = null
                in 30..37 -> c = standard(code - 30, bright = false)
                in 90..97 -> c = standard(code - 90, bright = true)
                38 -> {
                    // 38;5;n (256) or 38;2;r;g;b (truecolor)
                    when (codes.getOrNull(k + 1)) {
                        5 -> { c = xterm256(codes.getOrNull(k + 2) ?: 0); k += 2 }
                        2 -> {
                            // Coerce — Color(Int,Int,Int) throws if a malformed escape gives out-of-range channels.
                            val r = (codes.getOrNull(k + 2) ?: 0).coerceIn(0, 255)
                            val g = (codes.getOrNull(k + 3) ?: 0).coerceIn(0, 255)
                            val b = (codes.getOrNull(k + 4) ?: 0).coerceIn(0, 255)
                            c = Color(r, g, b); k += 4
                        }
                    }
                }
                // Backgrounds (40-49, 100-107) and other codes are ignored.
            }
            k++
        }
        return c to b
    }

    // Palette tuned for a dark background (no pure black).
    private fun standard(index: Int, bright: Boolean): Color = when (index) {
        0 -> if (bright) Color(0xFF9E9E9E) else Color(0xFF6E6E6E) // black/gray
        1 -> Color(0xFFE57373) // red
        2 -> Color(0xFF81C784) // green
        3 -> if (bright) Color(0xFFFFD54F) else Color(0xFFFFB74D) // yellow
        4 -> Color(0xFF64B5F6) // blue
        5 -> Color(0xFFBA68C8) // magenta
        6 -> Color(0xFF4DD0E1) // cyan
        else -> if (bright) Color(0xFFFFFFFF) else Color(0xFFE0E0E0) // white
    }

    private fun xterm256(n: Int): Color = when (n) {
        in 0..7 -> standard(n, bright = false)
        in 8..15 -> standard(n - 8, bright = true)
        in 16..231 -> {
            val v = n - 16
            fun ch(c: Int) = if (c == 0) 0 else 55 + 40 * c
            Color(ch(v / 36), ch((v / 6) % 6), ch(v % 6))
        }
        in 232..255 -> { val g = 8 + 10 * (n - 232); Color(g, g, g) }
        else -> Color(0xFFE0E0E0)
    }
}
