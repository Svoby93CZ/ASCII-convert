package cz.svoby93.asciistudio.engine.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrueTypeFontTest {

    private val font = TestFonts.app

    @Test
    fun `reads the metrics of the app's font`() {
        assertEquals(1000, font.unitsPerEm)
        assertEquals(600, font.advanceWidth(font.glyphOf('M'.code)!!))
        assertEquals(1020, font.ascender)
        assertEquals(-300, font.descender)
        assertEquals("JetBrains Mono Regular", font.name(4))
    }

    @Test
    fun `the app's font has no braille`() {
        assertNull(font.glyphOf('⠁'.code))
    }

    @Test
    fun `composite glyphs are put together from their parts`() {
        val letter = TestFonts.points(font.outline(font.glyphOf('C'.code)!!))
        val withCaron = TestFonts.points(font.outline(font.glyphOf('Č'.code)!!))

        // Č is a C with a caron above it.
        assertEquals(letter, withCaron.take(letter.size))
        assertTrue(withCaron.size > letter.size)
    }

    @Test
    fun `simple glyphs decode to their contours`() {
        val outline = font.outline(font.glyphOf('o'.code)!!)

        assertEquals(2, outline.size)
        assertTrue(outline.all { contour -> contour.onCurve.any { it } && contour.onCurve.any { !it } })
    }

    @Test
    fun `rejects bytes that are no truetype font`() {
        assertFailsWith<IllegalArgumentException> { TrueTypeFont(ByteArray(100)) }
        assertFailsWith<IllegalArgumentException> { TrueTypeFont("OTTO and more bytes".toByteArray()) }
    }
}
