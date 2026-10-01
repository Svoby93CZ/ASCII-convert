package cz.svoby93.asciistudio.ui.studio

import cz.svoby93.asciistudio.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseLightTest {

    @Test
    fun `marks and gaps take the units of the standard`() {
        // S is three dots, O three dashes; a word is followed by a pause of seven units.
        assertEquals("10101" + "000" + "11101110111" + "0000000", MorseLight("SO").pattern())
    }

    @Test
    fun `the light blinks the nickname of the author`() {
        assertEquals("... ...- --- -... -.--", MorseLight(Signature.NAME).read())
    }

    @Test
    fun `small letters blink like capitals`() {
        assertEquals(MorseLight("SVOBY").pattern(), MorseLight("svoby").pattern())
    }

    @Test
    fun `the word repeats after its pause`() {
        val light = MorseLight("E", unitSeconds = 0.5f)
        // E is a single dot, then seven dark units: a round of eight units, four seconds.
        assertTrue(light.isOn(0f))
        assertFalse(light.isOn(0.5f))
        assertFalse(light.isOn(3.9f))
        assertTrue(light.isOn(4f))
        assertTrue(light.isOn(4.4f))
    }

    private fun MorseLight.pattern(): String = units.joinToString("") { if (it) "1" else "0" }

    /** Reads the light back into dots and dashes, with a space between letters. */
    private fun MorseLight.read(): String = Regex("1+|0+").findAll(pattern()).joinToString("") { run ->
        when (run.value) {
            "1" -> "."
            "111" -> "-"
            "000" -> " "
            else -> ""
        }
    }
}
