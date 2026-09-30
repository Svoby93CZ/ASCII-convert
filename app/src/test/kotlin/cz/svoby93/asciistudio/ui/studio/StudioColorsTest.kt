package cz.svoby93.asciistudio.ui.studio

import cz.svoby93.asciistudio.data.ArtPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole app takes its colours from the art palette, so every palette must keep texts readable. */
class StudioColorsTest {

    @Test
    fun `secondary texts are readable in windows and on the desk of every palette`() {
        for (palette in ArtPalette.entries) {
            val colors = palette.studioColors()
            assertAtLeast(4.5f, contrastRatio(colors.secondary, colors.paper), "$palette on paper")
            assertAtLeast(4.5f, contrastRatio(colors.secondary, colors.desk), "$palette on desk")
        }
    }

    @Test
    fun `outlines of controls stand out from the paper of every palette`() {
        for (palette in ArtPalette.entries) {
            val colors = palette.studioColors()
            assertAtLeast(3f, contrastRatio(colors.outline, colors.paper), "$palette")
        }
    }

    @Test
    fun `palettes that are readable already keep their designed shades`() {
        val colors = ArtPalette.TERMINAL.studioColors()

        assertEquals(colors.tint(0.72f), colors.secondary)
        assertEquals(colors.tint(0.5f), colors.outline)
    }

    @Test
    fun `contrast ratio runs from 1 to 21`() {
        val colors = ArtPalette.INK.studioColors()

        assertEquals(21f, contrastRatio(colors.ink, colors.paper), 0.01f)
        assertEquals(1f, contrastRatio(colors.ink, colors.ink), 0.001f)
    }

    private fun assertAtLeast(expected: Float, actual: Float, what: String) {
        assertTrue("$what: contrast $actual is below $expected", actual >= expected)
    }
}
