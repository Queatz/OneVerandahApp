package apap.oneverandah.components

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/**
 * Conservative markdown renderer for location story text.
 *
 * Supports headings, lists, paragraphs, **bold**, *italic*, `code`, and
 * [links](url). HTML is never injected.
 */
@Composable
fun MarkdownView(source: String) {
    val blocks = parseMarkdownBlocks(source)
    Div(attrs = { classes(Styles.locationMarkdown) }) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (block.level <= 2) {
                        H2 { InlineMarkdown(block.text) }
                    } else {
                        H3 { InlineMarkdown(block.text) }
                    }
                }
                is MdBlock.ListBlock -> Ul {
                    block.items.forEach { item ->
                        Li { InlineMarkdown(item) }
                    }
                }
                is MdBlock.Paragraph -> P { InlineMarkdown(block.text) }
            }
        }
    }
}

@Composable
private fun InlineMarkdown(text: String) {
    parseInline(text).forEach { node ->
        when (node) {
            is MdInline.Text -> Text(node.value)
            is MdInline.Bold -> B { Text(node.value) }
            is MdInline.Italic -> Span(attrs = { classes(Styles.locationMarkdownEm) }) { Text(node.value) }
            is MdInline.Code -> Code { Text(node.value) }
            is MdInline.Link -> A(href = node.href) { Text(node.label) }
        }
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListBlock(val items: List<String>) : MdBlock()
}

private sealed class MdInline {
    data class Text(val value: String) : MdInline()
    data class Bold(val value: String) : MdInline()
    data class Italic(val value: String) : MdInline()
    data class Code(val value: String) : MdInline()
    data class Link(val label: String, val href: String) : MdInline()
}

private fun parseMarkdownBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val listItems = mutableListOf<String>()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    fun flushList() {
        if (listItems.isNotEmpty()) {
            blocks += MdBlock.ListBlock(listItems.toList())
            listItems.clear()
        }
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        when {
            trimmed.isEmpty() -> {
                flushList()
                flushParagraph()
            }
            trimmed.startsWith("### ") -> {
                flushList(); flushParagraph()
                blocks += MdBlock.Heading(3, trimmed.removePrefix("### ").trim())
            }
            trimmed.startsWith("## ") -> {
                flushList(); flushParagraph()
                blocks += MdBlock.Heading(2, trimmed.removePrefix("## ").trim())
            }
            trimmed.startsWith("# ") -> {
                flushList(); flushParagraph()
                blocks += MdBlock.Heading(1, trimmed.removePrefix("# ").trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                listItems += trimmed.drop(2).trim()
            }
            else -> {
                flushList()
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    flushList()
    flushParagraph()
    return blocks
}

private fun parseInline(text: String): List<MdInline> {
    val nodes = mutableListOf<MdInline>()
    var i = 0
    val buffer = StringBuilder()
    fun flushText() {
        if (buffer.isNotEmpty()) {
            nodes += MdInline.Text(buffer.toString())
            buffer.clear()
        }
    }
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    flushText()
                    nodes += MdInline.Bold(text.substring(i + 2, end))
                    i = end + 2
                } else {
                    buffer.append(text[i]); i++
                }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flushText()
                    nodes += MdInline.Code(text.substring(i + 1, end))
                    i = end + 1
                } else {
                    buffer.append(text[i]); i++
                }
            }
            rest.startsWith("[") -> {
                val labelEnd = text.indexOf(']', i + 1)
                val hrefStart = if (labelEnd >= 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                    labelEnd + 2
                } else -1
                val hrefEnd = if (hrefStart >= 0) text.indexOf(')', hrefStart) else -1
                if (labelEnd > i && hrefStart > 0 && hrefEnd > hrefStart) {
                    val href = text.substring(hrefStart, hrefEnd).trim()
                    if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("/")) {
                        flushText()
                        nodes += MdInline.Link(text.substring(i + 1, labelEnd), href)
                        i = hrefEnd + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                } else {
                    buffer.append(text[i]); i++
                }
            }
            rest.startsWith("*") && rest.length > 1 && rest[1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    flushText()
                    nodes += MdInline.Italic(text.substring(i + 1, end))
                    i = end + 1
                } else {
                    buffer.append(text[i]); i++
                }
            }
            else -> {
                buffer.append(text[i]); i++
            }
        }
    }
    flushText()
    return nodes
}
