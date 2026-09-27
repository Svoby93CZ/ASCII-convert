package cz.svoby93.asciistudio.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DonutTest {

    @Test
    fun `frames have the requested size and draw a shaded torus`() {
        val donut = Donut(columns = 60, rows = 26)
        val frame = donut.frame(timeSeconds = 1.3f) { level -> (level * 255).toInt() }

        assertEquals(60, frame.columns)
        assertEquals(26, frame.rows)
        val inked = frame.chars.count { it != ' ' }
        assertTrue(inked > 200, "Expected a visible donut, got $inked glyphs:\n${frame.toText()}")
        assertTrue(frame.chars.all { it == ' ' || it in ".,-~:;=!*#\$@" })
        // The torus is centred, so both halves of the frame are inked.
        assertTrue((0 until frame.rows).any { frame.line(it).substring(0, 30).isNotBlank() })
        assertTrue((0 until frame.rows).any { frame.line(it).substring(30).isNotBlank() })
    }

    @Test
    fun `frames are deterministic`() {
        val first = Donut().frame(2f) { 0 }
        val second = Donut().frame(2f) { 0 }

        assertEquals(first.toText(), second.toText())
    }
}
