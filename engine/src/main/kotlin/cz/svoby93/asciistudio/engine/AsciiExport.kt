package cz.svoby93.asciistudio.engine

import cz.svoby93.asciistudio.engine.font.BrailleGlyphs
import cz.svoby93.asciistudio.engine.font.FontSubsetter
import cz.svoby93.asciistudio.engine.font.TrueTypeFont
import cz.svoby93.asciistudio.engine.font.toSvgPath
import java.util.Base64
import java.util.Locale

/** Serialises [AsciiArt] into shareable text formats. */
object AsciiExport {

    /** The family of the fonts that exports embed: a subset of the app's font for one piece of art. */
    const val EMBEDDED_FAMILY = "ASCII Studio Mono"

    /**
     * Text for chat apps: a code block between ``` fences, which WhatsApp, Telegram and Discord show
     * in a monospaced font, so that the columns stay aligned.
     */
    fun toChat(art: AsciiArt): String = "```\n" + art.toText() + "\n```"

    /**
     * A standalone HTML page. With [glyphColors] every glyph gets its own colour (runs of equal
     * colours share one span to keep the file small); otherwise [foreground] is used for all text.
     * [tileColors] give every cell a background colour. With [font] the page carries the glyphs it
     * uses from that font, plus Braille dots the font lacks, so it looks the same in every browser.
     */
    fun toHtml(
        art: AsciiArt,
        background: Int,
        foreground: Int,
        glyphColors: IntArray? = null,
        tileColors: IntArray? = null,
        title: String = "ASCII art",
        font: TrueTypeFont? = null,
    ): String {
        // Cells of whole pixels, 10 wide with the 0.6 em advance of the font: rows then meet on
        // pixel edges, so block glyphs and tiles show no seams between them.
        val lineHeight = HTML_CELL_WIDTH_PX / art.cellAspect
        val perCell = glyphColors != null || tileColors != null
        return buildString(art.chars.size * if (perCell) 24 else 2) {
            append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<title>").append(escapeHtml(title)).append("</title>\n")
            append("<style>\n")
            if (font != null) appendFontFace(art, font)
            append("body{margin:0;padding:24px;background:").append(cssColor(background)).append(";}\n")
            append("pre{margin:0;font-family:")
            if (font != null) append('\'').append(EMBEDDED_FAMILY).append("',")
            append("'JetBrains Mono','DejaVu Sans Mono',Menlo,Consolas,monospace;")
            append("font-size:").append(String.format(Locale.ROOT, "%.4f", HTML_CELL_WIDTH_PX / 0.6f))
            append("px;line-height:").append(String.format(Locale.ROOT, "%.3f", lineHeight)).append("px")
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
     * A standalone SVG picture. Glyphs are drawn from the outlines of [font] and Braille as dots,
     * like on screen, so the picture looks the same in browsers, editors such as Inkscape, and on
     * plotters and cutters, without the font installed. Characters the font lacks fall back to text.
     * Colours work as in [toHtml]; [pixelsPerCell] sets the size the picture opens at.
     */
    fun toSvg(
        art: AsciiArt,
        background: Int,
        foreground: Int,
        font: TrueTypeFont,
        glyphColors: IntArray? = null,
        tileColors: IntArray? = null,
        title: String = "ASCII art",
        pixelsPerCell: Float = 14f,
    ): String {
        val cellWidth = (font.glyphOf('M'.code)?.let(font::advanceWidth) ?: (font.unitsPerEm * 3 / 5)).toFloat()
        val cellHeight = cellWidth / art.cellAspect
        // Like the renderer, the line of the font is centred in the cell.
        val baseline = (cellHeight - (font.ascender - font.descender)) / 2f + font.ascender
        val margin = cellWidth * SVG_MARGIN_CELLS
        val width = art.columns * cellWidth + 2 * margin
        val height = art.rows * cellHeight + 2 * margin
        val scale = pixelsPerCell / cellWidth
        // One definition per glyph, which every cell then uses.
        val ids = HashMap<Char, String>()
        val missing = HashSet<Char>()
        return buildString(art.chars.size * if (glyphColors != null) 48 else 32) {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
            append(" width=\"").appendCoordinate(width * scale).append("\" height=\"").appendCoordinate(height * scale)
            append("\" viewBox=\"0 0 ").appendCoordinate(width).append(' ').appendCoordinate(height).append("\">\n")
            append("<title>").append(escapeHtml(title)).append("</title>\n<defs>\n")
            for (glyph in art.chars.toSortedSet()) {
                if (glyph == ' ' || glyph == Braille.BLANK) continue
                val id = "g" + Integer.toHexString(glyph.code)
                if (Braille.isPattern(glyph)) {
                    appendBrailleDots(id, glyph, cellWidth, cellHeight, baseline)
                } else {
                    val outline = font.glyphOf(glyph.code)?.let(font::outline)
                    if (outline == null) {
                        missing += glyph
                        continue
                    }
                    append("<path id=\"").append(id).append("\" d=\"").append(outline.toSvgPath()).append("\"/>\n")
                }
                ids[glyph] = id
            }
            append("</defs>\n")
            append("<rect width=\"100%\" height=\"100%\" fill=\"").append(cssColor(background)).append("\"/>\n")
            append("<g transform=\"translate(").appendCoordinate(margin).append(' ').appendCoordinate(margin)
            append(")\">\n")
            if (tileColors != null) appendSvgTiles(art, tileColors, cellWidth, cellHeight)
            append("<g fill=\"").append(cssColor(foreground)).append("\">\n")
            for (row in 0 until art.rows) {
                append("<g transform=\"translate(0 ").appendCoordinate(row * cellHeight + baseline).append(")\">")
                for (column in 0 until art.columns) {
                    val glyph = art[column, row]
                    val color = glyphColors?.let { cssColor(it[row * art.columns + column]) }
                    val id = ids[glyph]
                    if (id != null) {
                        append("<use xlink:href=\"#").append(id).append("\" x=\"").appendCoordinate(column * cellWidth)
                        if (color != null) append("\" fill=\"").append(color)
                        append("\"/>")
                    } else if (glyph in missing) {
                        append("<text x=\"").appendCoordinate(column * cellWidth)
                        append("\" font-family=\"monospace\" font-size=\"").append(font.unitsPerEm)
                        if (color != null) append("\" fill=\"").append(color)
                        append("\">")
                        appendEscaped(glyph)
                        append("</text>")
                    }
                }
                append("</g>\n")
            }
            append("</g>\n</g>\n</svg>\n")
        }
    }

    /** A Braille pattern as dots where the renderer draws them in a cell, relative to its baseline. */
    private fun StringBuilder.appendBrailleDots(
        id: String,
        glyph: Char,
        cellWidth: Float,
        cellHeight: Float,
        baseline: Float,
    ) {
        val pitchX = cellWidth / Braille.DOTS_X
        val pitchY = cellHeight / Braille.DOTS_Y
        append("<g id=\"").append(id).append("\">")
        for (dotY in 0 until Braille.DOTS_Y) {
            for (dotX in 0 until Braille.DOTS_X) {
                if (!Braille.isRaised(glyph, dotX, dotY)) continue
                append("<circle cx=\"").appendCoordinate((dotX + 0.5f) * pitchX)
                append("\" cy=\"").appendCoordinate((dotY + 0.5f) * pitchY - baseline)
                append("\" r=\"").appendCoordinate(pitchX * Braille.DOT_SIZE / 2f).append("\"/>")
            }
        }
        append("</g>\n")
    }

    /** Tiles as one rectangle per run of equal colour in a row; crisp edges leave no seams between them. */
    private fun StringBuilder.appendSvgTiles(art: AsciiArt, tileColors: IntArray, cellWidth: Float, cellHeight: Float) {
        append("<g shape-rendering=\"crispEdges\">\n")
        for (row in 0 until art.rows) {
            val start = row * art.columns
            var column = 0
            while (column < art.columns) {
                val tile = tileColors[start + column]
                var end = column + 1
                while (end < art.columns && tileColors[start + end] == tile) end++
                append("<rect x=\"").appendCoordinate(column * cellWidth)
                append("\" y=\"").appendCoordinate(row * cellHeight)
                append("\" width=\"").appendCoordinate((end - column) * cellWidth)
                append("\" height=\"").appendCoordinate(cellHeight)
                append("\" fill=\"").append(cssColor(tile)).append("\"/>")
                column = end
            }
            append('\n')
        }
        append("</g>\n")
    }

    /**
     * An @font-face rule with the glyphs that [art] uses, from [font] or generated for Braille. The
     * comment keeps the licence notice of the font with it, as the SIL Open Font License asks.
     */
    private fun StringBuilder.appendFontFace(art: AsciiArt, font: TrueTypeFont) {
        val codePoints = art.chars.mapTo(HashSet()) { it.code }
        val braille = BrailleGlyphs.forFont(font)
        val subset = FontSubsetter.subset(font, codePoints, EMBEDDED_FAMILY, braille)
        val notice = listOfNotNull(
            "$EMBEDDED_FAMILY: the glyphs of this art from ${font.name(NAME_FULL) ?: "the app's font"}",
            font.name(NAME_COPYRIGHT),
            font.name(NAME_LICENSE),
        ).joinToString(". ")
        append("/* ").append(notice.replace("*/", "* /")).append(" */\n")
        append("@font-face{font-family:'").append(EMBEDDED_FAMILY).append("';src:url(data:font/ttf;base64,")
        append(Base64.getEncoder().encodeToString(subset)).append(") format('truetype');}\n")
    }

    /** Coordinates with at most two decimals, which is far below a printer dot at any size. */
    private fun StringBuilder.appendCoordinate(value: Float): StringBuilder {
        val hundredths = Math.round(value * 100.0)
        if (hundredths % 100 == 0L) return append(hundredths / 100)
        return append(String.format(Locale.ROOT, "%.2f", hundredths / 100.0).trimEnd('0'))
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
    private const val HTML_CELL_WIDTH_PX = 10f
    private const val SVG_MARGIN_CELLS = 2f
    private const val NAME_COPYRIGHT = 0
    private const val NAME_FULL = 4
    private const val NAME_LICENSE = 13
}
