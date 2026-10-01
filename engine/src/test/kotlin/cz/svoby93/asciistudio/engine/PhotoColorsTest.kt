package cz.svoby93.asciistudio.engine

import cz.svoby93.asciistudio.engine.TestImages.BLACK
import cz.svoby93.asciistudio.engine.TestImages.WHITE
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoColorsTest {

    /** Papers like those of the app's dark palettes, plus pure black. */
    private val darkPapers = listOf(0x0B120E, 0x150E03, 0x16060A, 0x04141B, 0x111318, 0x1B0B33, 0x0E3A73, 0x000000)

    /** Papers like those of the app's light palettes. */
    private val lightPapers = listOf(0x9BBC0F, 0xF4EEE1, 0xFFFFFF)

    /** 216 colours, every channel in steps of 51. */
    private val colors = (0..5).flatMap { r -> (0..5).flatMap { g -> (0..5).map { b -> rgb(r * 51, g * 51, b * 51) } } }

    @Test
    fun `glyphs keep the hue of their cell at full brightness on dark paper`() {
        val glyph = PhotoColors(rgb(11, 18, 14)).glyph(rgb(128, 64, 0))

        assertEquals(rgb(255, 134, 0), glyph)
    }

    @Test
    fun `glyphs are readable on every paper`() {
        for (paper in darkPapers) {
            val photo = PhotoColors(paper)
            for (color in colors) {
                val glyph = photo.glyph(color)
                assertTrue(contrast(glyph, paper) >= 4.45, "${hex(glyph)} on ${hex(paper)} from ${hex(color)}")
            }
        }
        for (paper in lightPapers) {
            val photo = PhotoColors(paper)
            for (color in colors) {
                val glyph = photo.glyph(color)
                assertTrue(contrast(glyph, paper) >= 6.9, "${hex(glyph)} on ${hex(paper)} from ${hex(color)}")
            }
        }
    }

    @Test
    fun `deep colours are lifted on dark paper but keep their hue`() {
        val glyph = PhotoColors(BLACK).glyph(rgb(0, 0, 255))

        assertTrue(contrast(glyph, BLACK) >= 4.45, hex(glyph))
        assertEquals(255, blue(glyph))
        assertTrue(red(glyph) < 160 && green(glyph) < 160, hex(glyph))
    }

    @Test
    fun `dark cells lose their unreliable hue`() {
        val glyph = PhotoColors(BLACK).glyph(rgb(6, 0, 3))

        val spread = max(red(glyph), max(green(glyph), blue(glyph))) - min(red(glyph), min(green(glyph), blue(glyph)))
        assertTrue(spread <= 8, hex(glyph))
    }

    @Test
    fun `black cells get neutral glyphs that contrast with the paper`() {
        assertEquals(WHITE, PhotoColors(BLACK).glyph(BLACK))
        val onWhite = PhotoColors(WHITE).glyph(BLACK)
        assertTrue(abs(red(onWhite) - 89) <= 1 && red(onWhite) == green(onWhite) && green(onWhite) == blue(onWhite))
    }

    @Test
    fun `tiles mix a muted cell colour into the paper`() {
        assertEquals(rgb(77, 77, 77), PhotoColors(BLACK).tile(WHITE))
    }

    @Test
    fun `tiles leave the paper as it is where the picture is transparent`() {
        val paper = rgb(20, 30, 40)

        assertEquals(paper, PhotoColors(paper).tile(0x00FFFFFF))
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = rgb((r shl 16) or (g shl 8) or b)

    private fun rgb(color: Int): Int = color or 0xFF000000.toInt()

    private fun red(color: Int) = (color shr 16) and 0xFF

    private fun green(color: Int) = (color shr 8) and 0xFF

    private fun blue(color: Int) = color and 0xFF

    private fun hex(color: Int) = "#%06x".format(color and 0xFFFFFF)

    /** WCAG contrast ratio. */
    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double =
        0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color))

    private fun linear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
}
