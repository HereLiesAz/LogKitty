package com.hereliesaz.logkitty.ui.markdown

/**
 * A small, dependency-free Markdown parser tailored to the documents LogKitty ships in-app
 * (README + the docs/ files). It is deliberately *not* a full CommonMark implementation: it
 * supports the constructs those documents actually use — ATX headings, paragraphs, ordered and
 * unordered lists, blockquotes, fenced code blocks, GitHub-flavoured pipe tables, thematic breaks,
 * and inline emphasis / code / links.
 *
 * Parsing is split into a pure-Kotlin block/inline model here (unit-testable, no Compose types) and a
 * thin Compose renderer in `MarkdownText.kt` that turns the model into UI.
 */

/** A block-level element. */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class BulletItem(val indent: Int, val spans: List<MdSpan>) : MdBlock
    data class NumberedItem(val indent: Int, val number: Int, val spans: List<MdSpan>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class CodeBlock(val code: String, val lang: String?) : MdBlock
    data class Table(val headers: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
    data object Divider : MdBlock
}

/** An inline span within a block. */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val text: String) : MdSpan
    data class Italic(val text: String) : MdSpan
    data class BoldItalic(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val text: String, val url: String) : MdSpan
}

private val HEADING = Regex("^#{1,6}\\s")
private val BULLET = Regex("^[-*+]\\s")
private val NUMBERED = Regex("^\\d+\\.\\s")
private val HR = Regex("^([-*_])\\1{2,}$")
private val TABLE_CELL = Regex("^:?-{1,}:?$")

/**
 * Removes a leading YAML front-matter block (`---` … `---`) if present. The published docs carry
 * front matter for GitHub Pages permalinks; in-app we don't want to render it.
 */
fun stripFrontMatter(raw: String): String {
    val text = raw.replace("\r\n", "\n")
    if (!text.startsWith("---\n")) return raw
    val end = text.indexOf("\n---", 4)
    if (end < 0) return raw
    val after = text.indexOf('\n', end + 1)
    return if (after < 0) "" else text.substring(after + 1)
}

/** Parses a Markdown document into a flat list of block elements. */
fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val md = stripFrontMatter(raw).replace("\r\n", "\n").replace("\t", "    ")
    val lines = md.split("\n")
    val blocks = mutableListOf<MdBlock>()
    val para = mutableListOf<String>()
    var i = 0

    fun flushPara() {
        if (para.isNotEmpty()) {
            val text = para.joinToString(" ") { it.trim() }.trim()
            if (text.isNotEmpty()) blocks += MdBlock.Paragraph(parseInline(text))
            para.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> { flushPara(); i++ }

            trimmed.startsWith("```") -> {
                flushPara()
                val lang = trimmed.removePrefix("```").trim().ifEmpty { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++ // closing fence
                blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'), lang)
            }

            HEADING.containsMatchIn(trimmed) -> {
                flushPara()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks += MdBlock.Heading(level, parseInline(trimmed.drop(level).trim()))
                i++
            }

            trimmed.matches(HR) -> { flushPara(); blocks += MdBlock.Divider; i++ }

            line.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                flushPara()
                val headers = parseTableRow(line)
                i += 2
                val rows = mutableListOf<List<List<MdSpan>>>()
                while (i < lines.size && lines[i].contains("|") && lines[i].trim().isNotEmpty()) {
                    rows += parseTableRow(lines[i]); i++
                }
                blocks += MdBlock.Table(headers, rows)
            }

            trimmed.startsWith(">") -> {
                flushPara()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim(); i++
                }
                blocks += MdBlock.Quote(parseInline(quote.joinToString(" ").trim()))
            }

            BULLET.containsMatchIn(trimmed) -> {
                flushPara()
                val indent = line.takeWhile { it == ' ' }.length / 2
                var text = trimmed.replaceFirst(BULLET, "")
                i++
                i = consumeContinuation(lines, i) { text += " $it" }
                blocks += MdBlock.BulletItem(indent, parseInline(text))
            }

            NUMBERED.containsMatchIn(trimmed) -> {
                flushPara()
                val indent = line.takeWhile { it == ' ' }.length / 2
                val number = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                var text = trimmed.replaceFirst(NUMBERED, "")
                i++
                i = consumeContinuation(lines, i) { text += " $it" }
                blocks += MdBlock.NumberedItem(indent, number, parseInline(text))
            }

            else -> { para += line; i++ }
        }
    }
    flushPara()
    return blocks
}

/**
 * Appends soft-wrapped continuation lines of a list item (lines that are neither blank nor the start
 * of a new block) and returns the index of the first unconsumed line.
 */
private inline fun consumeContinuation(lines: List<String>, start: Int, append: (String) -> Unit): Int {
    var i = start
    while (i < lines.size) {
        if (lines[i].trim().isEmpty() || isBlockStart(lines, i)) break
        append(lines[i].trim())
        i++
    }
    return i
}

private fun isBlockStart(lines: List<String>, i: Int): Boolean {
    val line = lines[i]
    val t = line.trim()
    if (t.isEmpty()) return false
    return t.startsWith("```") ||
        HEADING.containsMatchIn(t) ||
        t.matches(HR) ||
        BULLET.containsMatchIn(t) ||
        NUMBERED.containsMatchIn(t) ||
        t.startsWith(">") ||
        (line.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]))
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.contains("-") || !t.contains("|")) return false
    val cells = t.trim('|').split("|")
    return cells.isNotEmpty() && cells.all { it.trim().matches(TABLE_CELL) }
}

private fun parseTableRow(line: String): List<List<MdSpan>> =
    line.trim().trim('|').split("|").map { parseInline(it.trim()) }

/** Parses inline emphasis, code spans and links within a single block of text. */
fun parseInline(input: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    var i = 0
    val n = input.length

    fun flush() {
        if (plain.isNotEmpty()) { spans += MdSpan.Text(plain.toString()); plain.clear() }
    }

    while (i < n) {
        val c = input[i]
        when {
            c == '`' -> {
                val end = input.indexOf('`', i + 1)
                if (end > i) { flush(); spans += MdSpan.Code(input.substring(i + 1, end)); i = end + 1 }
                else { plain.append(c); i++ }
            }

            c == '[' -> {
                val close = input.indexOf(']', i + 1)
                if (close > i && close + 1 < n && input[close + 1] == '(') {
                    val paren = input.indexOf(')', close + 2)
                    if (paren > close) {
                        flush()
                        spans += MdSpan.Link(input.substring(i + 1, close), input.substring(close + 2, paren))
                        i = paren + 1
                    } else { plain.append(c); i++ }
                } else { plain.append(c); i++ }
            }

            c == '*' -> {
                when {
                    input.startsWith("***", i) -> {
                        val end = input.indexOf("***", i + 3)
                        if (end > i) { flush(); spans += MdSpan.BoldItalic(input.substring(i + 3, end)); i = end + 3 }
                        else { plain.append(c); i++ }
                    }
                    input.startsWith("**", i) -> {
                        val end = input.indexOf("**", i + 2)
                        if (end > i) { flush(); spans += MdSpan.Bold(input.substring(i + 2, end)); i = end + 2 }
                        else { plain.append(c); i++ }
                    }
                    else -> {
                        val end = input.indexOf('*', i + 1)
                        if (end > i) { flush(); spans += MdSpan.Italic(input.substring(i + 1, end)); i = end + 1 }
                        else { plain.append(c); i++ }
                    }
                }
            }

            c == '_' -> {
                if (input.startsWith("__", i)) {
                    val end = input.indexOf("__", i + 2)
                    if (end > i) { flush(); spans += MdSpan.Bold(input.substring(i + 2, end)); i = end + 2 }
                    else { plain.append(c); i++ }
                } else {
                    // Treat `_` as italic only when both delimiters sit at word boundaries, so
                    // snake_case identifiers survive even when emphasized (e.g. `_PACKAGE_USAGE_STATS_`
                    // italicizes the whole token) and a stray `_` before one doesn't swallow it.
                    val prevOk = i == 0 || !input[i - 1].isLetterOrDigit()
                    var end = -1
                    if (prevOk) {
                        var searchStart = i + 1
                        while (searchStart < n) {
                            val candidate = input.indexOf('_', searchStart)
                            if (candidate < 0) break
                            val nextOk = candidate == n - 1 || !input[candidate + 1].isLetterOrDigit()
                            val prevSpace = input[candidate - 1].isWhitespace()
                            if (nextOk && !prevSpace) { end = candidate; break }
                            searchStart = candidate + 1
                        }
                    }
                    if (end > i) { flush(); spans += MdSpan.Italic(input.substring(i + 1, end)); i = end + 1 }
                    else { plain.append(c); i++ }
                }
            }

            else -> { plain.append(c); i++ }
        }
    }
    flush()
    return spans
}
