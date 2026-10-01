package cz.svoby93.asciistudio.engine.font

import cz.svoby93.asciistudio.engine.Braille
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrailleGlyphsTest {

    private val font = TestFonts.app
    private val braille = BrailleGlyphs.forFont(font)

    private fun outlineOf(pattern: Char): List<Contour> {
        val subset = TrueTypeFont(FontSubsetter.subset(font, setOf(pattern.code), "Test", braille))
        return subset.outline(subset.glyphOf(pattern.code)!!)
    }

    @Test
    fun `dots sit where the renderer draws them`() {
        // JetBrains Mono: 600 units wide, a line of 1020 + 300 units centred in a cell of 1200.
        // The baseline lies 960 units below the top of the cell, the dot pitch is 300 units.
        val expected = mapOf(
            '⠁' to (150 to 960 - 150),
            '⠈' to (450 to 960 - 150),
            '⠄' to (150 to 960 - 750),
            '⢀' to (450 to 960 - 1050),
        )
        val radius = (300 * Braille.DOT_SIZE / 2).roundToInt()
        for ((pattern, centre) in expected) {
            val dot = outlineOf(pattern).single()
            assertEquals(centre.first - radius, dot.x.min(), "left of $pattern")
            // Controls lie outside the circle, so the extent comes from the points on it.
            val onCurveX = dot.x.filterIndexed { index, _ -> dot.onCurve[index] }
            val onCurveY = dot.y.filterIndexed { index, _ -> dot.onCurve[index] }
            assertEquals(centre.first + radius, onCurveX.max(), "right of $pattern")
            assertEquals(centre.second + radius, onCurveY.max(), "top of $pattern")
            assertEquals(centre.second - radius, dot.y.min(), "bottom of $pattern")
        }
    }

    @Test
    fun `every raised dot is a contour`() {
        assertEquals(8, outlineOf('⣿').size)
        assertEquals(3, outlineOf('⠇').size)
        assertTrue(outlineOf('⠀').isEmpty())
    }

    @Test
    fun `only braille patterns are made`() {
        assertNull(braille.glyph('a'.code) { it })
        assertNull(braille.glyph(0x2900) { it })
    }

    @Test
    fun `all patterns together stay small`() {
        val all = (0x2800..0x28FF).toSet()

        assertTrue(FontSubsetter.subset(font, all, "Test", braille).size < 20_000)
    }
}
