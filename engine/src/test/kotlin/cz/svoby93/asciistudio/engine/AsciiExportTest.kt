package cz.svoby93.asciistudio.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsciiExportTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

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
        val html = AsciiExport.toHtml(art, background = 0xFF000000.toInt(), foreground = 0xFFFFFFFF.toInt(), colored = false)

        assertTrue("<pre>&lt;&amp;&gt;\nab</pre>" in html, html)
        assertTrue("background:#000000" in html)
        assertTrue("color:#ffffff" in html)
    }

    @Test
    fun `coloured html merges runs of the same colour`() {
        val html = AsciiExport.toHtml(art, background = 0xFF000000.toInt(), foreground = 0xFFFFFFFF.toInt(), colored = true)

        assertTrue("<span style=\"color:#f00\">&lt;&amp;</span><span style=\"color:#00f\">&gt;</span>" in html, html)
        assertTrue("<span style=\"color:#00f\">ab</span>" in html, html)
    }

    @Test
    fun `html title is escaped`() {
        val html = AsciiExport.toHtml(art, 0, 0, colored = false, title = "a<b>")

        assertTrue("<title>a&lt;b&gt;</title>" in html)
        assertFalse("<b>" in html)
    }

    @Test
    fun `ansi output switches colours only when needed and resets every line`() {
        val ansi = AsciiExport.toAnsi(art, foreground = 0xFFFFFFFF.toInt(), colored = true)
        val lines = ansi.split('\n')

        assertEquals("\u001B[38;2;255;0;0m<&\u001B[38;2;0;0;255m>\u001B[38;2;255;0;0m \u001B[0m", lines[0])
        assertEquals("\u001B[38;2;0;0;255mab\u001B[38;2;255;0;0m  \u001B[0m", lines[1])
    }

    @Test
    fun `monochrome ansi uses a single colour`() {
        val ansi = AsciiExport.toAnsi(art, foreground = 0xFF00FF00.toInt(), colored = false)

        assertEquals(2, Regex("\u001B\\[38;2;0;255;0m").findAll(ansi).count())
    }
}
