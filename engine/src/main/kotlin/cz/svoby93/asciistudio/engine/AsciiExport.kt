package cz.svoby93.asciistudio.engine

import java.util.Locale

/** Serialises [AsciiArt] into shareable text formats. */
object AsciiExport {

    /**
     * A standalone HTML page. With [glyphColors] every glyph gets its own colour (runs of equal
     * colours share one span to keep the file small); otherwise [foreground] is used for all text.
     * [tileColors] give every cell a background colour.
     */
    fun toHtml(
        art: AsciiArt,
        background: Int,
        foreground: Int,
        glyphColors: IntArray? = null,
        tileColors: IntArray? = null,
        title: String = "ASCII art",
    ): String {
        // Line height that reproduces the cell shape with a typical 0.6 em monospace advance.
        val lineHeight = 0.6f / art.cellAspect
        val perCell = glyphColors != null || tileColors != null
        return buildString(art.chars.size * if (perCell) 24 else 2) {
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
                if (tileColors == null) {
                    appendGlyphs(art, row, 0, art.line(row).trimEnd(' ').length, glyphColors)
                } else {
                    appendTiles(art, row, glyphColors, tileColors)
                }
            }
            append("</pre>\n</body>\n</html>\n")
        }
    }

    /**
     * Text with ANSI escape sequences for terminals (`cat art.ans`). With [glyphColors] every glyph
     * gets its own 24-bit colour, otherwise the whole art uses [foreground]. [tileColors] give every
     * cell a background colour.
     */
    fun toAnsi(
        art: AsciiArt,
        foreground: Int,
        glyphColors: IntArray? = null,
        tileColors: IntArray? = null,
    ): String = buildString(art.chars.size * if (tileColors != null) 8 else 4) {
        for (row in 0 until art.rows) {
            var currentGlyph: Int? = null
            var currentTile: Int? = null
            for (column in 0 until art.columns) {
                val cell = row * art.columns + column
                val color = glyphColors?.get(cell) ?: foreground
                if (color != currentGlyph) {
                    appendAnsiColor(FOREGROUND, color)
                    currentGlyph = color
                }
                if (tileColors != null && tileColors[cell] != currentTile) {
                    appendAnsiColor(BACKGROUND, tileColors[cell])
                    currentTile = tileColors[cell]
                }
                append(art[column, row])
            }
            append(ESC).append("[0m\n")
        }
    }

    private fun StringBuilder.appendAnsiColor(layer: Int, color: Int) {
        append(ESC).append('[').append(layer).append(";2;")
            .append((color shr 16) and 0xFF).append(';')
            .append((color shr 8) and 0xFF).append(';')
            .append(color and 0xFF).append('m')
    }

    /**
     * A row in spans with the tile colours as backgrounds; blanks are kept, they show their tiles.
     * Tiles keep their full colour: they only mix a little of the photo into the paper, so the short
     * form would leave just a few shades.
     */
    private fun StringBuilder.appendTiles(art: AsciiArt, row: Int, glyphColors: IntArray?, tileColors: IntArray) {
        val start = row * art.columns
        var column = 0
        while (column < art.columns) {
            val tile = tileColors[start + column]
            var end = column + 1
            while (end < art.columns && tileColors[start + end] == tile) end++
            append("<span style=\"background:").append(cssColor(tile)).append("\">")
            appendGlyphs(art, row, column, end, glyphColors)
            append("</span>")
            column = end
        }
    }

    /** Glyphs from [from] until [to] of a row, in spans of equal colour when [glyphColors] are given. */
    private fun StringBuilder.appendGlyphs(art: AsciiArt, row: Int, from: Int, to: Int, glyphColors: IntArray?) {
        if (glyphColors == null) {
            for (column in from until to) appendEscaped(art[column, row])
            return
        }
        val start = row * art.columns
        var column = from
        while (column < to) {
            val glyph = art[column, row]
            if (glyph == ' ') {
                append(' ')
                column++
                continue
            }
            val color = compactColor(glyphColors[start + column])
            append("<span style=\"color:").append(color).append("\">")
            while (column < to) {
                val next = art[column, row]
                if (next != ' ' && compactColor(glyphColors[start + column]) != color) break
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
    private const val FOREGROUND = 38
    private const val BACKGROUND = 48
    private const val HEX = "0123456789abcdef"
}
