package cz.svoby93.asciistudio.engine

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachingConverterTest {

    private val options = AsciiOptions(columns = 30, glyphs = GlyphSet.Ramp(CharRamps.DETAILED), cellAspect = 0.5f)

    /** Soft shapes with every tone, like a photo, 4 pixels per column at 30 columns. */
    private val photo = TestImages.of(120, 90) { x, y ->
        val light = (128 + 100 * kotlin.math.sin(x / 9.0) * kotlin.math.cos(y / 7.0)).toInt().coerceIn(0, 255)
        TestImages.gray(if (hypot(x - 60.0, y - 45.0) < 25) 255 - light else light)
    }

    /** Settings that change neither the size of the grids nor the outline mode. */
    private val toneChanges = listOf<(AsciiOptions) -> AsciiOptions>(
        { it.copy(brightness = 0.4f) },
        { it.copy(contrast = -0.3f) },
        { it.copy(sharpness = 0.8f) },
        { it.copy(autoLevels = false) },
        { it.copy(invert = true) },
        { it.copy(dithering = Dithering.FLOYD_STEINBERG) },
        { it.copy(dithering = Dithering.BAYER) },
    )

    @Test
    fun `samples converted again with other tones give the same art as a fresh conversion`() {
        for (base in listOf(options, options.copy(edgeMode = EdgeMode.MIXED), options.copy(glyphs = GlyphSet.Braille))) {
            val samples = AsciiConverter.sample(photo, base)
            for (change in toneChanges + { it.copy(edgeSensitivity = 0.9f) }) {
                val changed = change(base)
                assertTrue(samples.fit(changed), "$changed")
                assertSameArt(AsciiConverter.convert(photo, changed), AsciiConverter.convert(samples, changed))
            }
        }
    }

    @Test
    fun `samples do not fit another width, glyph kind, cell shape or outline mode`() {
        val samples = AsciiConverter.sample(photo, options)

        assertFalse(samples.fit(options.copy(columns = 31)))
        assertFalse(samples.fit(options.copy(glyphs = GlyphSet.Braille)))
        assertFalse(samples.fit(options.copy(cellAspect = 0.6f)))
        assertFalse(samples.fit(options.copy(edgeMode = EdgeMode.ONLY)))
        assertTrue(samples.fit(options.copy(glyphs = GlyphSet.Ramp(CharRamps.BLOCKS))))
    }

    @Test
    fun `braille samples fit outlines and any text cell shape`() {
        val braille = options.copy(glyphs = GlyphSet.Braille)
        val samples = AsciiConverter.sample(photo, braille)

        assertTrue(samples.fit(braille.copy(edgeMode = EdgeMode.MIXED, cellAspect = 0.7f)))
    }

    @Test
    fun `tone changes reuse the samples`() {
        val converter = CachingConverter()
        converter.convert(photo, options)

        for (change in toneChanges) {
            val changed = change(options)
            assertSameArt(AsciiConverter.convert(photo, changed), converter.convert(photo, changed))
        }
        assertEquals(1, converter.samplings)
    }

    @Test
    fun `width, glyph kind and outlines sample again, and so does a new image`() {
        val converter = CachingConverter()
        converter.convert(photo, options)
        converter.convert(photo, options.copy(columns = 20))
        converter.convert(photo, options.copy(columns = 20, glyphs = GlyphSet.Braille))
        converter.convert(photo, options.copy(columns = 20, edgeMode = EdgeMode.MIXED))
        converter.convert(TestImages.of(120, 90) { x, y -> photo[x, y] }, options.copy(columns = 20, edgeMode = EdgeMode.MIXED))

        assertEquals(5, converter.samplings)
    }

    private fun assertSameArt(expected: AsciiArt, actual: AsciiArt) {
        assertEquals(expected.columns, actual.columns)
        assertEquals(expected.rows, actual.rows)
        assertContentEquals(expected.chars, actual.chars)
        assertContentEquals(expected.colors, actual.colors)
    }
}
