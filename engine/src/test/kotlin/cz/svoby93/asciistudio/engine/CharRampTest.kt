package cz.svoby93.asciistudio.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CharRampTest {

    @Test
    fun `glyphs are sorted by coverage and normalised to 0 to 1`() {
        val ramp = CharRamp.of("#. ", floatArrayOf(0.4f, 0.1f, 0f))

        assertEquals(" .#", ramp.chars)
        assertEquals(0f, ramp.levels.first())
        assertEquals(1f, ramp.levels.last())
        assertEquals(0.25f, ramp.levels[1], 1e-6f)
        assertEquals(' ', ramp.blank)
    }

    @Test
    fun `duplicate glyphs keep their first coverage`() {
        val ramp = CharRamp.of("a.a", floatArrayOf(1f, 0f, 0.5f))

        assertEquals(".a", ramp.chars)
    }

    @Test
    fun `uniform ramp keeps order and spaces levels evenly`() {
        val ramp = CharRamp.uniform("abcaa")

        assertEquals("abc", ramp.chars)
        assertEquals(listOf(0f, 0.5f, 1f), ramp.levels.toList())
    }

    @Test
    fun `ramps need two distinct glyphs`() {
        assertFailsWith<IllegalArgumentException> { CharRamp.uniform("aaa") }
        assertFailsWith<IllegalArgumentException> { CharRamp.of("x", floatArrayOf(1f)) }
    }

    @Test
    fun `built in ramps are strictly increasing and start with a blank`() {
        for (ramp in listOf(CharRamps.STANDARD, CharRamps.DETAILED, CharRamps.BLOCKS, CharRamps.BINARY)) {
            assertEquals(' ', ramp.blank, "$ramp should start with a space")
            assertEquals(1f, ramp.levels.last())
            for (i in 1 until ramp.size) {
                assertTrue(ramp.levels[i] > ramp.levels[i - 1], "$ramp is not increasing at $i")
            }
        }
    }

    @Test
    fun `equal ramps are equal`() {
        assertEquals(CharRamp.uniform(" .#"), CharRamp.uniform(" .#"))
        assertEquals(CharRamp.uniform(" .#").hashCode(), CharRamp.uniform(" .#").hashCode())
    }
}
