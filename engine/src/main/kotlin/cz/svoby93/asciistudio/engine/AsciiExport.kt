package cz.svoby93.asciistudio.engine

import java.util.Locale

/** Serialises [AsciiArt] into shareable text formats. */
object AsciiExport {

    /**
     * A standalone HTML page. With [colored] every glyph keeps its source colour (runs of equal
     * colours share one span to keep the file small); otherwise [foreground] is used for all text.
     */
    fun toHtml(
        art: AsciiArt,
        background: Int,
        foreground: Int,
        colored: Boolean,
        title: String = "ASCII art",
    ): String {
        // Line height that reproduces the cell shape with a typical 0.6 em monospace advance.
        val lineHeight = 0.6f / art.cellAspect
        return buildString(art.chars.size * if (colored) 24 else 2) {
            append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<title>").append(escapeHtml(title)).append("</title>\n")
            append("<style>\n")
            append("body{margin:0;padding:24px;background:").append(cssColor(background)).append(";}\n")
            append("pre{margin:0;font-family:'JetBrains Mono','DejaVu Sans Mono',Menlo,Consolas,monospace;")
            append("font-size:10px;line-height:").append(String.format(Locale.ROOT, "%.3f", lineHeight))
            append(";letter-spacing:0;font-variant-ligatures:none;color:").append(cssColor(foreground)).append(";}\n")
            append("</style>\n</head>\n<body>\n<pre>")
            for (row in 0 until art.rows) {
                if (row > 0) append('\n')
                if (colored) appendColoredRow(art, row) else appendEscaped(art.line(row).trimEnd(' '))
            }
            append("</pre>\n</body>\n</html>\n")
        }
    }

    /**
     * Text with ANSI escape sequences for terminals (`cat art.ans`). With [colored] every glyph
     * gets its 24-bit source colour, otherwise the whole art uses [foreground].
     */
    fun toAnsi(art: AsciiArt, foreground: Int, colored: Boolean): String = buildString(art.chars.size * 4) {
        for (row in 0 until art.rows) {
            var current: Int? = null
            for (column in 0 until art.columns) {
                val color = if (colored) art.colorAt(column, row) else foreground
                if (color != current) {
                    append(ESC).append("[38;2;")
                        .append((color shr 16) and 0xFF).append(';')
                        .append((color shr 8) and 0xFF).append(';')
                        .append(color and 0xFF).append('m')
                    current = color
                }
                append(art[column, row])
            }
            append(ESC).append("[0m\n")
        }
    }

    private fun StringBuilder.appendColoredRow(art: AsciiArt, row: Int) {
        val end = art.line(row).trimEnd(' ').length
        var column = 0
        while (column < end) {
            val glyph = art[column, row]
            if (glyph == ' ') {
                append(' ')
                column++
                continue
            }
            val color = compactColor(art.colorAt(column, row))
            append("<span style=\"color:").append(color).append("\">")
            while (column < end) {
                val next = art[column, row]
                if (next != ' ' && compactColor(art.colorAt(column, row)) != color) break
                appendEscaped(next)
                column++
            }
            append("</span>")
        }
    }

    /** `#rgb` with 4 bits per channel: plenty for text and much shorter runs. */
    private fun compactColor(color: Int): String {
        val r = ((color shr 16) and 0xFF) * 15 / 255
        val g = ((color shr 8) and 0xFF) * 15 / 255
        val b = (color and 0xFF) * 15 / 255
        return "#" + HEX[r] + HEX[g] + HEX[b]
    }

    private fun cssColor(color: Int): String = String.format(Locale.ROOT, "#%06x", color and 0xFFFFFF)

    private fun escapeHtml(text: String): String = buildString(text.length) { appendEscaped(text) }

    private fun StringBuilder.appendEscaped(text: String) {
        for (c in text) appendEscaped(c)
    }

    private fun StringBuilder.appendEscaped(c: Char) {
        when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private const val ESC = '\u001B'
    private const val HEX = "0123456789abcdef"
}
