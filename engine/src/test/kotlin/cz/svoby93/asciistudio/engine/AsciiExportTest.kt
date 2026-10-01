package cz.svoby93.asciistudio.engine

import cz.svoby93.asciistudio.engine.font.TestFonts
import cz.svoby93.asciistudio.engine.font.TrueTypeFont
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsciiExportTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val gray = 0xFF404040.toInt()
    private val green = 0xFF00FF00.toInt()

    private val art = AsciiArt(
        columns = 4,
        rows = 2,
        chars = "<&> ab  ".toCharArray(),
        colors = intArrayOf(red, red, blue, red, blue, blue, red, red),
        cellAspect = 0.5f,
        isBraille = false,
    )

    @Test
    fun `html escapes markup characters`() {
        val html = AsciiExport.toHtml(art, background = 0xFF000000.toInt(), foreground = 0xFFFFFFFF.toInt())

        assertTrue("<pre>&lt;&amp;&gt;\nab</pre>" in html, html)
        assertTrue("background:#000000" in html)
        assertTrue("color:#ffffff" in html)
    }

    @Test
    fun `coloured html merges runs of the same colour`() {
        val html = AsciiExport.toHtml(
            art,
            background = 0xFF000000.toInt(),
            foreground = 0xFFFFFFFF.toInt(),
            glyphColors = art.colors,
        )

        assertTrue("<span style=\"color:#f00\">&lt;&amp;</span><span style=\"color:#00f\">&gt;</span>" in html, html)
        assertTrue("<span style=\"color:#00f\">ab</span>" in html, html)
    }

    @Test
    fun `html tiles colour every cell and keep the blanks that show them`() {
        val tiles = intArrayOf(gray, gray, gray, gray, gray, gray, green, green)
        val html = AsciiExport.toHtml(
            art,
            background = 0xFF000000.toInt(),
            foreground = 0xFFFFFFFF.toInt(),
            tileColors = tiles,
        )

        assertTrue("<pre><span style=\"background:#404040\">&lt;&amp;&gt; </span>\n" in html, html)
        assertTrue(
            "<span style=\"background:#404040\">ab</span><span style=\"background:#00ff00\">  </span></pre>" in html,
            html,
        )
    }

    @Test
    fun `html title is escaped`() {
        val html = AsciiExport.toHtml(art, 0, 0, title = "a<b>")

        assertTrue("<title>a&lt;b&gt;</title>" in html)
        assertFalse("<b>" in html)
    }

    @Test
    fun `chat text sits in a code block`() {
        assertEquals("```\n<&>\nab\n```", AsciiExport.toChat(art))
    }

    @Test
    fun `html cells are whole pixels`() {
        val html = AsciiExport.toHtml(art, 0, 0)

        // 10 px wide with the 0.6 em advance, and 20 px high for the cell aspect of 0.5.
        assertTrue("font-size:16.6667px;line-height:20.000px;" in html, html)
    }

    @Test
    fun `html with a font embeds the glyphs the art uses`() {
        val html = AsciiExport.toHtml(art, 0, 0, font = TestFonts.app)
        val data = Regex("url\\(data:font/ttf;base64,([A-Za-z0-9+/=]+)\\)").find(html)!!.groupValues[1]
        val font = TrueTypeFont(Base64.getDecoder().decode(data))

        assertTrue("font-family:'${AsciiExport.EMBEDDED_FAMILY}','JetBrains Mono'" in html, html)
        assertTrue("SIL Open Font License" in html, "the licence notice travels with the font")
        assertEquals(AsciiExport.EMBEDDED_FAMILY, font.name(1))
        for (char in "<&> ab") assertTrue(font.glyphOf(char.code) != null, "$char")
        assertEquals(null, font.glyphOf('c'.code))
    }

    @Test
    fun `html without a font names the usual monospaced fonts`() {
        val html = AsciiExport.toHtml(art, 0, 0)

        assertFalse("@font-face" in html)
        assertTrue("font-family:'JetBrains Mono'" in html)
    }

    @Test
    fun `svg defines every glyph once and places it in every cell`() {
        val svg = AsciiExport.toSvg(art, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), TestFonts.app)

        // Cells of 600 × 1200 units for the cell aspect of 0.5, with a margin of two cells around.
        assertTrue("viewBox=\"0 0 4800 4800\"" in svg, svg)
        assertTrue("width=\"112\" height=\"112\"" in svg, svg)
        assertEquals(5, Regex("<path id=").findAll(svg).count())
        assertEquals(5, Regex("<use ").findAll(svg).count())
        assertTrue("<g fill=\"#ffffff\">" in svg)
        assertTrue("<rect width=\"100%\" height=\"100%\" fill=\"#000000\"/>" in svg)
        // The second row: a and b, two cells apart, on the baseline of the row.
        val row = "<g transform=\"translate(0 2160)\"><use xlink:href=\"#g61\" x=\"0\"/>" +
            "<use xlink:href=\"#g62\" x=\"600\"/></g>"
        assertTrue(row in svg, svg)
    }

    @Test
    fun `svg colours every glyph and tile`() {
        val tiles = intArrayOf(gray, gray, gray, gray, gray, gray, green, green)
        val svg = AsciiExport.toSvg(art, 0, 0, TestFonts.app, glyphColors = art.colors, tileColors = tiles)

        assertTrue("<use xlink:href=\"#g3c\" x=\"0\" fill=\"#ff0000\"/>" in svg, svg)
        assertTrue("<use xlink:href=\"#g3e\" x=\"1200\" fill=\"#0000ff\"/>" in svg, svg)
        // One rectangle per run of equal tiles in a row.
        assertEquals(3, Regex("<rect x=").findAll(svg).count())
        assertTrue("<rect x=\"1200\" y=\"1200\" width=\"1200\" height=\"1200\" fill=\"#00ff00\"/>" in svg, svg)
    }

    @Test
    fun `svg draws braille as dots`() {
        val braille = AsciiArt(2, 1, "⠁⣿".toCharArray(), intArrayOf(red, red), cellAspect = 0.5f, isBraille = true)
        val svg = AsciiExport.toSvg(braille, 0, 0, TestFonts.app)

        assertEquals(9, Regex("<circle ").findAll(svg).count())
        // The first dot of ⠁: half a pitch into the cell, 960 units above the baseline of the row.
        assertTrue("<g id=\"g2801\"><circle cx=\"150\" cy=\"-810\" r=\"117\"/></g>" in svg, svg)
    }

    @Test
    fun `svg writes characters the font lacks as text`() {
        val smiley = AsciiArt(2, 1, "☺a".toCharArray(), intArrayOf(red, red), cellAspect = 0.5f, isBraille = false)
        val svg = AsciiExport.toSvg(smiley, 0, 0, TestFonts.app)

        assertTrue("<text x=\"0\" font-family=\"monospace\" font-size=\"1000\">☺</text>" in svg, svg)
        assertEquals(1, Regex("<path id=").findAll(svg).count())
    }

    @Test
    fun `ansi output switches colours only when needed and resets every line`() {
        val ansi = AsciiExport.toAnsi(art, foreground = 0xFFFFFFFF.toInt(), glyphColors = art.colors)
        val lines = ansi.split('\n')

        assertEquals("\u001B[38;2;255;0;0m<&\u001B[38;2;0;0;255m>\u001B[38;2;255;0;0m \u001B[0m", lines[0])
        assertEquals("\u001B[38;2;0;0;255mab\u001B[38;2;255;0;0m  \u001B[0m", lines[1])
    }

    @Test
    fun `monochrome ansi uses a single colour`() {
        val ansi = AsciiExport.toAnsi(art, foreground = 0xFF00FF00.toInt())

        assertEquals(2, Regex("\u001B\\[38;2;0;255;0m").findAll(ansi).count())
    }

    @Test
    fun `ansi tiles set the background of every cell`() {
        val tiles = intArrayOf(gray, gray, gray, gray, gray, gray, green, green)
        val lines = AsciiExport.toAnsi(art, foreground = 0xFFFFFFFF.toInt(), tileColors = tiles).split('\n')

        assertEquals("\u001B[38;2;255;255;255m\u001B[48;2;64;64;64m<&> \u001B[0m", lines[0])
        assertEquals("\u001B[38;2;255;255;255m\u001B[48;2;64;64;64mab\u001B[48;2;0;255;0m  \u001B[0m", lines[1])
    }
}
