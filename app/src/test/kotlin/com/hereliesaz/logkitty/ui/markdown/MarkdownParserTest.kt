package com.hereliesaz.logkitty.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun stripsLeadingYamlFrontMatter() {
        val raw = "---\ntitle: Privacy Policy\npermalink: /privacy/\n---\n\n# Privacy Policy\n"
        val out = stripFrontMatter(raw)
        assertTrue("title:" !in out)
        // The block is gone; the document body (after any leading blank line) starts at the heading.
        assertTrue(out.trimStart().startsWith("# Privacy Policy"))
        // And it parses cleanly to a single heading block.
        val heading = parseMarkdownBlocks(raw).single() as MdBlock.Heading
        assertEquals("Privacy Policy", (heading.spans.single() as MdSpan.Text).text)
    }

    @Test
    fun leavesContentWithoutFrontMatterUntouched() {
        val raw = "# Heading\n\nBody."
        assertEquals(raw, stripFrontMatter(raw))
    }

    @Test
    fun parsesHeadingLevelAndText() {
        val blocks = parseMarkdownBlocks("## Permissions")
        val heading = blocks.single() as MdBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("Permissions", (heading.spans.single() as MdSpan.Text).text)
    }

    @Test
    fun parsesGfmTable() {
        val md = """
            | Permission | Purpose |
            | --- | --- |
            | `READ_LOGS` | Read the system log. |
        """.trimIndent()
        val table = parseMarkdownBlocks(md).single() as MdBlock.Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
        // First cell of the row is an inline code span.
        assertEquals("READ_LOGS", (table.rows[0][0].single() as MdSpan.Code).text)
    }

    @Test
    fun parsesInlineLinkBoldAndCode() {
        val spans = parseInline("See **bold**, `code`, and [the site](https://example.com).")
        assertTrue(spans.any { it is MdSpan.Bold && it.text == "bold" })
        assertTrue(spans.any { it is MdSpan.Code && it.text == "code" })
        val link = spans.filterIsInstance<MdSpan.Link>().single()
        assertEquals("the site", link.text)
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun underscoresInIdentifiersAreNotItalic() {
        // PACKAGE_USAGE_STATS must survive as literal text, not be split into an italic run.
        val spans = parseInline("PACKAGE_USAGE_STATS detail")
        assertTrue(spans.none { it is MdSpan.Italic })
        assertEquals("PACKAGE_USAGE_STATS detail", (spans.single() as MdSpan.Text).text)
    }

    @Test
    fun parsesBulletList() {
        val blocks = parseMarkdownBlocks("- one\n- two")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MdBlock.BulletItem })
    }
}
