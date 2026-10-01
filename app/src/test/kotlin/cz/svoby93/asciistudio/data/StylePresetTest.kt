package cz.svoby93.asciistudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylePresetTest {

    private val mine = StudioSettings(
        columns = 180,
        charset = CharsetPreset.CUSTOM,
        customChars = " .xX",
        brightness = 0.4f,
        backdrop = Backdrop.RAIN,
    )

    @Test
    fun `a look keeps the width, the background and the custom characters`() {
        val newspaper = mine.withLookOf(StylePreset.NEWSPAPER.look)

        assertEquals(180, newspaper.columns)
        assertEquals(Backdrop.RAIN, newspaper.backdrop)
        assertEquals(" .xX", newspaper.customChars)
        assertEquals(ArtPalette.PAPER, newspaper.palette)
        assertEquals(0f, newspaper.brightness)
        assertTrue(newspaper.hasLookOf(StylePreset.NEWSPAPER.look))
    }

    @Test
    fun `a look with custom characters brings its own`() {
        val look = StudioSettings(charset = CharsetPreset.CUSTOM, customChars = " oO")

        assertEquals(" oO", StudioSettings().withLookOf(look).customChars)
    }

    @Test
    fun `any change of tone or colour leaves the look`() {
        val classic = mine.withLookOf(StylePreset.CLASSIC.look)

        assertTrue(classic.hasLookOf(StylePreset.CLASSIC.look))
        assertTrue(classic.copy(columns = 40).hasLookOf(StylePreset.CLASSIC.look))
        assertFalse(classic.copy(contrast = 0.5f).hasLookOf(StylePreset.CLASSIC.look))
        assertFalse(classic.copy(palette = ArtPalette.RUBY).hasLookOf(StylePreset.CLASSIC.look))
    }

    @Test
    fun `every built-in look is different`() {
        val looks = StylePreset.entries.map { StudioSettings().withLookOf(it.look) }

        assertEquals(looks.size, looks.toSet().size)
        assertEquals(StudioSettings(), StudioSettings().withLookOf(StylePreset.CLASSIC.look))
    }
}
